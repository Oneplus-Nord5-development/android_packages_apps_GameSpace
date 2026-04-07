/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gamespace.runtime;

import static android.content.pm.ApplicationInfo.CATEGORY_GAME;

import android.app.ActivityTaskManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.app.TaskInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;
import android.window.TaskFpsCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.gamespace.data.GameSpacePreferences;
import com.android.gamespace.data.MirroredNotification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class GameSpaceService extends Service implements ForegroundGameTracker.Callback,
        SharedPreferences.OnSharedPreferenceChangeListener, OverlayController.Callback {

    private static final int MAX_NOTIFICATIONS = 5;
    private static final long STATS_REFRESH_MS = 1200L;

    private final LinkedHashMap<String, MirroredNotification> mNotifications = new LinkedHashMap<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final TaskFpsCallback mTaskFpsCallback = new TaskFpsCallback() {
        @Override
        public void onFpsReported(float fps) {
            mLastFps = fps;
            updateMetersNow();
        }
    };
    private final Runnable mStatsRunnable = new Runnable() {
        @Override
        public void run() {
            updateMetersNow();
            if (shouldSampleStats()) {
                mBackgroundHandler.postDelayed(this, STATS_REFRESH_MS);
            }
        }
    };

    private SharedPreferences mSharedPreferences;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ForegroundGameTracker mForegroundGameTracker;
    private OverlayController mOverlayController;
    private SystemStatsSampler mSystemStatsSampler;
    private NotificationManager mNotificationManager;
    private AppOpsManager mAppOpsManager;
    private TelecomManager mTelecomManager;
    private WindowManager mWindowManager;
    private PackageManager mPackageManager;
    private BroadcastReceiver mNotificationReceiver;

    private boolean mStarted;
    private boolean mInGame;
    private int mTrackedTaskId = ActivityTaskManager.INVALID_TASK_ID;
    private float mLastFps = Float.NaN;
    private long mLastCallActionUptime;
    private String mCurrentGamePackage;
    private CharSequence mCurrentGameLabel;

    public static void requestStart(@NonNull Context context) {
        Intent intent = new Intent(context, GameSpaceService.class);
        context.startService(intent);
    }

    public static void requestStop(@NonNull Context context) {
        Intent intent = new Intent(context, GameSpaceService.class);
        context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mSharedPreferences = GameSpacePreferences.get(this);
        mBackgroundThread = new HandlerThread("GameSpaceService");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mForegroundGameTracker = new ForegroundGameTracker(this);
        mSystemStatsSampler = new SystemStatsSampler(this);
        mNotificationManager = getSystemService(NotificationManager.class);
        mAppOpsManager = getSystemService(AppOpsManager.class);
        mTelecomManager = getSystemService(TelecomManager.class);
        mWindowManager = getSystemService(WindowManager.class);
        mPackageManager = getPackageManager();
        mOverlayController = new OverlayController(createOverlayContext(), this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!GameSpacePreferences.isEnabled(this)) {
            stopServiceInternal();
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureStarted();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopServiceInternal();
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            mBackgroundThread = null;
        }
        mBackgroundHandler = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @NonNull
    private Context createOverlayContext() {
        DisplayManager displayManager = getSystemService(DisplayManager.class);
        Display display = displayManager != null
                ? displayManager.getDisplay(Display.DEFAULT_DISPLAY) : null;
        if (display != null) {
            return createDisplayContext(display).createWindowContext(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        }
        return this;
    }

    @Override
    public void onTopTaskChanged(@Nullable TaskInfo taskInfo) {
        if (mBackgroundHandler == null) {
            return;
        }
        mBackgroundHandler.post(() -> handleTopTaskChanged(taskInfo));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (GameSpacePreferences.KEY_ENABLED.equals(key)
                && !GameSpacePreferences.isEnabled(this)) {
            requestStop(this);
            return;
        }
        if (mBackgroundHandler != null) {
            mBackgroundHandler.post(() -> handleTopTaskChanged(mForegroundGameTracker.getTopTask()));
        }
    }

    @Override
    public void onToggleFpsMeter() {
        boolean enabled = !GameSpacePreferences.isFpsMeterEnabled(this);
        mSharedPreferences.edit()
                .putBoolean(GameSpacePreferences.KEY_FPS_METER_ENABLED, enabled)
                .apply();
        refreshOverlayState();
    }

    @Override
    public void onToggleSystemMeter() {
        boolean enabled = !GameSpacePreferences.isSystemMeterEnabled(this);
        mSharedPreferences.edit()
                .putBoolean(GameSpacePreferences.KEY_SYSTEM_METER_ENABLED, enabled)
                .apply();
        refreshOverlayState();
    }

    @Override
    public void onToggleDnd() {
        int nextFilter = isDndEnabled()
                ? NotificationManager.INTERRUPTION_FILTER_ALL
                : NotificationManager.INTERRUPTION_FILTER_NONE;
        try {
            mNotificationManager.setInterruptionFilter(nextFilter);
        } catch (RuntimeException ignored) {
        }
        postPanelState();
    }

    @Override
    public void onToggleCompactNotifications() {
        boolean enabled = !GameSpacePreferences.isCompactNotificationsEnabled(this);
        mSharedPreferences.edit()
                .putBoolean(GameSpacePreferences.KEY_COMPACT_NOTIFICATIONS, enabled)
                .apply();
        postPanelState();
        postNotificationState();
    }

    @Override
    public void onCycleCallHandlingMode() {
        String current = GameSpacePreferences.getCallHandlingMode(this);
        String nextMode;
        if (GameSpacePreferences.CALL_MODE_OFF.equals(current)) {
            nextMode = GameSpacePreferences.CALL_MODE_REJECT;
        } else if (GameSpacePreferences.CALL_MODE_REJECT.equals(current)) {
            nextMode = GameSpacePreferences.CALL_MODE_ANSWER;
        } else {
            nextMode = GameSpacePreferences.CALL_MODE_OFF;
        }
        mSharedPreferences.edit()
                .putString(GameSpacePreferences.KEY_CALL_HANDLING_MODE, nextMode)
                .apply();
        postPanelState();
    }

    @Override
    public void onLaunchPackage(@NonNull String packageName) {
        Intent launchIntent = mPackageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launchIntent);
    }

    private void ensureStarted() {
        if (mStarted) {
            handleTopTaskChanged(mForegroundGameTracker.getTopTask());
            return;
        }
        mStarted = true;
        ensureOverlayPermission();
        ensureNotificationAccess();
        registerNotificationReceiver();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mForegroundGameTracker.start();
    }

    private void stopServiceInternal() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mForegroundGameTracker.stop();
        unregisterNotificationReceiver();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        unregisterTaskFps();
        mMainHandler.removeCallbacksAndMessages(null);
        if (mBackgroundHandler != null) {
            mBackgroundHandler.removeCallbacksAndMessages(null);
        }
        mOverlayController.destroy();
        mNotifications.clear();
        mInGame = false;
        mCurrentGamePackage = null;
        mCurrentGameLabel = null;
    }

    private void registerNotificationReceiver() {
        if (mNotificationReceiver != null) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(GameSpaceNotificationListenerService.ACTION_NOTIFICATIONS_RESET);
        filter.addAction(GameSpaceNotificationListenerService.ACTION_NOTIFICATION_POSTED);
        filter.addAction(GameSpaceNotificationListenerService.ACTION_NOTIFICATION_REMOVED);
        mNotificationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleNotificationIntent(intent);
            }
        };
        registerReceiver(mNotificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterNotificationReceiver() {
        if (mNotificationReceiver == null) {
            return;
        }
        unregisterReceiver(mNotificationReceiver);
        mNotificationReceiver = null;
    }

    private void handleNotificationIntent(@NonNull Intent intent) {
        String action = intent.getAction();
        if (GameSpaceNotificationListenerService.ACTION_NOTIFICATIONS_RESET.equals(action)) {
            mNotifications.clear();
        } else if (GameSpaceNotificationListenerService.ACTION_NOTIFICATION_REMOVED.equals(action)) {
            mNotifications.remove(intent.getStringExtra(GameSpaceNotificationListenerService.EXTRA_KEY));
        } else if (GameSpaceNotificationListenerService.ACTION_NOTIFICATION_POSTED.equals(action)) {
            MirroredNotification mirroredNotification = new MirroredNotification(
                    intent.getStringExtra(GameSpaceNotificationListenerService.EXTRA_KEY),
                    intent.getStringExtra(GameSpaceNotificationListenerService.EXTRA_PACKAGE),
                    intent.getCharSequenceExtra(GameSpaceNotificationListenerService.EXTRA_TITLE),
                    intent.getCharSequenceExtra(GameSpaceNotificationListenerService.EXTRA_TEXT),
                    intent.getStringExtra(GameSpaceNotificationListenerService.EXTRA_CATEGORY));
            mNotifications.remove(mirroredNotification.getKey());
            mNotifications.put(mirroredNotification.getKey(), mirroredNotification);
            while (mNotifications.size() > MAX_NOTIFICATIONS) {
                String firstKey = mNotifications.entrySet().iterator().next().getKey();
                mNotifications.remove(firstKey);
            }
            maybeHandleIncomingCall(mirroredNotification);
        }
        postNotificationState();
    }

    private void maybeHandleIncomingCall(@NonNull MirroredNotification notification) {
        if (!mInGame || !Notification.CATEGORY_CALL.equals(notification.getCategory())
                || mTelecomManager == null || !mTelecomManager.isRinging()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - mLastCallActionUptime < 3000L) {
            return;
        }
        String mode = GameSpacePreferences.getCallHandlingMode(this);
        if (GameSpacePreferences.CALL_MODE_ANSWER.equals(mode)) {
            try {
                mTelecomManager.acceptRingingCall();
                mLastCallActionUptime = now;
            } catch (RuntimeException ignored) {
            }
        } else if (GameSpacePreferences.CALL_MODE_REJECT.equals(mode)) {
            try {
                mTelecomManager.endCall();
                mLastCallActionUptime = now;
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void handleTopTaskChanged(@Nullable TaskInfo taskInfo) {
        String nextPackage = taskInfo != null && taskInfo.topActivity != null
                ? taskInfo.topActivity.getPackageName() : null;
        boolean nextInGame = isGamePackage(nextPackage);
        if (!nextInGame) {
            mInGame = false;
            mCurrentGamePackage = null;
            mCurrentGameLabel = null;
            unregisterTaskFps();
            mBackgroundHandler.removeCallbacks(mStatsRunnable);
            mMainHandler.post(() -> {
                mOverlayController.setMeterVisibility(false, false);
                mOverlayController.updateGame(false, null, null, new ArrayList<>());
            });
            return;
        }

        mInGame = true;
        mCurrentGamePackage = nextPackage;
        mCurrentGameLabel = loadAppLabel(nextPackage);
        registerTaskFpsIfNeeded(taskInfo != null ? taskInfo.taskId : ActivityTaskManager.INVALID_TASK_ID);
        if (shouldSampleStats()) {
            mBackgroundHandler.removeCallbacks(mStatsRunnable);
            mBackgroundHandler.post(mStatsRunnable);
        } else {
            mBackgroundHandler.removeCallbacks(mStatsRunnable);
            mMainHandler.post(() -> mOverlayController.setMeterVisibility(false, false));
        }
        List<String> sidebarPackages = GameSpacePreferences.getSidebarPackagesSorted(this);
        mMainHandler.post(() -> {
            mOverlayController.updateGame(true, mCurrentGameLabel, mCurrentGamePackage,
                    sidebarPackages);
            postPanelState();
            postNotificationState();
        });
    }

    private void registerTaskFpsIfNeeded(int taskId) {
        if (!GameSpacePreferences.isFpsMeterEnabled(this)
                && !GameSpacePreferences.isSystemMeterEnabled(this)) {
            unregisterTaskFps();
            return;
        }
        if (taskId == ActivityTaskManager.INVALID_TASK_ID || taskId == mTrackedTaskId) {
            return;
        }
        unregisterTaskFps();
        try {
            mWindowManager.registerTaskFpsCallback(taskId, Runnable::run, mTaskFpsCallback);
            mTrackedTaskId = taskId;
            mLastFps = Float.NaN;
        } catch (RuntimeException e) {
            mTrackedTaskId = ActivityTaskManager.INVALID_TASK_ID;
        }
    }

    private void unregisterTaskFps() {
        if (mTrackedTaskId == ActivityTaskManager.INVALID_TASK_ID) {
            return;
        }
        try {
            mWindowManager.unregisterTaskFpsCallback(mTaskFpsCallback);
        } catch (RuntimeException ignored) {
        }
        mTrackedTaskId = ActivityTaskManager.INVALID_TASK_ID;
        mLastFps = Float.NaN;
    }

    private boolean shouldSampleStats() {
        return mInGame && (GameSpacePreferences.isFpsMeterEnabled(this)
                || GameSpacePreferences.isSystemMeterEnabled(this));
    }

    private void updateMetersNow() {
        if (!shouldSampleStats()) {
            return;
        }
        SystemStatsSampler.Snapshot snapshot = mSystemStatsSampler.sample(mLastFps);
        mMainHandler.post(() -> mOverlayController.updateMeters(snapshot,
                GameSpacePreferences.isFpsMeterEnabled(this),
                GameSpacePreferences.isSystemMeterEnabled(this)));
    }

    private void postPanelState() {
        mMainHandler.post(() -> mOverlayController.updatePanelState(
                GameSpacePreferences.isFpsMeterEnabled(this),
                GameSpacePreferences.isSystemMeterEnabled(this),
                isDndEnabled(),
                GameSpacePreferences.isCompactNotificationsEnabled(this),
                GameSpacePreferences.getCallHandlingMode(this)));
    }

    private void refreshOverlayState() {
        postPanelState();
        if (mBackgroundHandler == null) {
            return;
        }
        mBackgroundHandler.post(() -> handleTopTaskChanged(mForegroundGameTracker.getTopTask()));
    }

    private void postNotificationState() {
        if (!mInGame) {
            return;
        }
        List<MirroredNotification> notificationList = new ArrayList<>(mNotifications.values());
        mMainHandler.post(() -> mOverlayController.updateNotifications(notificationList,
                GameSpacePreferences.isCompactNotificationsEnabled(this)));
    }

    private boolean isDndEnabled() {
        return mNotificationManager.getCurrentInterruptionFilter()
                != NotificationManager.INTERRUPTION_FILTER_ALL;
    }

    private void ensureNotificationAccess() {
        if (mNotificationManager == null) {
            return;
        }
        ComponentName listener = new ComponentName(this, GameSpaceNotificationListenerService.class);
        try {
            if (!mNotificationManager.isNotificationListenerAccessGranted(listener)) {
                mNotificationManager.setNotificationListenerAccessGranted(listener, true);
            }
        } catch (SecurityException ignored) {
        }
        try {
            if (!mNotificationManager.isNotificationPolicyAccessGranted()) {
                mNotificationManager.setNotificationPolicyAccessGranted(getPackageName(), true);
            }
        } catch (SecurityException ignored) {
        }
    }

    private void ensureOverlayPermission() {
        if (mAppOpsManager == null) {
            return;
        }
        try {
            mAppOpsManager.setMode(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(),
                    getPackageName(),
                    AppOpsManager.MODE_ALLOWED);
        } catch (SecurityException ignored) {
        }
    }

    private boolean isGamePackage(@Nullable String packageName) {
        if (TextUtils.isEmpty(packageName) || getPackageName().equals(packageName)) {
            return false;
        }
        if (GameSpacePreferences.getManualLibraryPackages(this).contains(packageName)) {
            return true;
        }
        try {
            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(
                    packageName, PackageManager.ApplicationInfoFlags.of(0));
            return applicationInfo.category == CATEGORY_GAME;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Nullable
    private CharSequence loadAppLabel(@Nullable String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        try {
            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(
                    packageName, PackageManager.ApplicationInfoFlags.of(0));
            return mPackageManager.getApplicationLabel(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }
}
