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

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.gamespace.GameSpaceSettingsActivity;
import com.android.gamespace.R;
import com.android.gamespace.data.GameSpacePreferences;
import com.android.gamespace.data.MirroredNotification;

import java.util.List;
import java.util.Locale;

final class OverlayController {

    interface Callback {
        void onToggleFpsMeter();
        void onToggleSystemMeter();
        void onToggleDnd();
        void onToggleCompactNotifications();
        void onCycleCallHandlingMode();
        void onLaunchPackage(@NonNull String packageName);
    }

    private static final int MAX_NOTIFICATIONS = 3;
    private static final int MAX_QUICK_LAUNCH = 4;
    private static final int MARGIN_DP = 16;
    private static final int HANDLE_WIDTH_DP = 6;
    private static final int HANDLE_HEIGHT_DP = 112;
    private static final int FAB_SIZE_DP = 52;
    private static final int FAB_FRAME_SIZE_DP = 60;
    private static final int FAB_BADGE_SIZE_DP = 28;
    private static final int PANEL_CONTENT_WIDTH_DP = 292;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final PackageManager mPackageManager;
    private final Callback mCallback;

    private View mHandleView;
    private WindowManager.LayoutParams mHandleLayoutParams;
    private View mBackdropView;
    private WindowManager.LayoutParams mBackdropLayoutParams;
    private View mPanelView;
    private WindowManager.LayoutParams mPanelLayoutParams;
    private ScrollView mPanelScrollView;
    private LinearLayout mQuickLaunchContainer;
    private LinearLayout mNotificationContainer;
    private ImageView mPanelIcon;
    private TextView mPanelTitle;
    private TextView mPanelSubtitle;
    private View mFpsTile;
    private ImageView mFpsTileIcon;
    private TextView mFpsTileTitle;
    private TextView mFpsTileSummary;
    private View mSystemTile;
    private ImageView mSystemTileIcon;
    private TextView mSystemTileTitle;
    private TextView mSystemTileSummary;
    private View mDndTile;
    private ImageView mDndTileIcon;
    private TextView mDndTileTitle;
    private TextView mDndTileSummary;
    private View mCompactTile;
    private ImageView mCompactTileIcon;
    private TextView mCompactTileTitle;
    private TextView mCompactTileSummary;
    private View mCallTile;
    private ImageView mCallTileIcon;
    private TextView mCallTileTitle;
    private TextView mCallTileSummary;
    private View mSettingsTile;
    private ImageView mSettingsTileIcon;
    private TextView mSettingsTileTitle;
    private TextView mSettingsTileSummary;
    private View mFabView;
    private TextView mFabFpsBadge;
    private WindowManager.LayoutParams mFabLayoutParams;

    private View mFpsMeterView;
    private WindowManager.LayoutParams mFpsMeterLayoutParams;
    private TextView mFpsMeterText;

    private View mSystemMeterView;
    private WindowManager.LayoutParams mSystemMeterLayoutParams;
    private TextView mSystemMeterText;
    private View mSystemCpuItem;
    private TextView mSystemCpuText;
    private View mSystemTempItem;
    private TextView mSystemTempText;
    private View mSystemGpuItem;
    private TextView mSystemGpuText;
    private View mSystemFpsItem;
    private TextView mSystemFpsText;

    private boolean mGameVisible;
    private boolean mShowFpsInFab;
    @NonNull
    private String mLastFpsText = "--";

    OverlayController(@NonNull Context context, @NonNull Callback callback) {
        mContext = context;
        mWindowManager = context.getSystemService(WindowManager.class);
        mPackageManager = context.getPackageManager();
        mCallback = callback;
    }

    void updateGame(boolean visible, @Nullable CharSequence gameLabel, @Nullable String packageName,
            @NonNull List<String> sidebarPackages) {
        mGameVisible = visible;
        if (!visible) {
            hideAll();
            hideMeterViews();
            return;
        }
        ensurePanel();
        if (mPanelView != null && mPanelView.getParent() != null) {
            removeViewIfAttached(mHandleView);
            removeViewIfAttached(mFabView);
        } else {
            ensureFab();
        }
        updateTitle(gameLabel, packageName);
        updateQuickLaunch(sidebarPackages);
    }

    void updatePanelState(boolean fpsEnabled, boolean systemEnabled,
            boolean dndEnabled, boolean compactEnabled, @NonNull String callMode) {
        ensurePanel();
        setTileState(mFpsTile, mFpsTileIcon, mFpsTileTitle, mFpsTileSummary, fpsEnabled);
        setTileState(mSystemTile, mSystemTileIcon, mSystemTileTitle, mSystemTileSummary,
                systemEnabled);

        mDndTileSummary.setText(dndEnabled ? R.string.panel_dnd_on : R.string.panel_dnd_off);
        setTileState(mDndTile, mDndTileIcon, mDndTileTitle, mDndTileSummary, dndEnabled);

        mCompactTileSummary.setText(compactEnabled
                ? R.string.panel_compact_on : R.string.panel_compact_off);
        setTileState(mCompactTile, mCompactTileIcon, mCompactTileTitle, mCompactTileSummary,
                compactEnabled);

        if (GameSpacePreferences.CALL_MODE_ANSWER.equals(callMode)) {
            mCallTileSummary.setText(R.string.panel_calls_answer);
            setTileState(mCallTile, mCallTileIcon, mCallTileTitle, mCallTileSummary, true);
        } else if (GameSpacePreferences.CALL_MODE_REJECT.equals(callMode)) {
            mCallTileSummary.setText(R.string.panel_calls_reject);
            setTileState(mCallTile, mCallTileIcon, mCallTileTitle, mCallTileSummary, true);
        } else {
            mCallTileSummary.setText(R.string.panel_calls_off);
            setTileState(mCallTile, mCallTileIcon, mCallTileTitle, mCallTileSummary, false);
        }
    }

    void updateNotifications(@NonNull List<MirroredNotification> notifications, boolean enabled) {
        if (!mGameVisible) {
            return;
        }
        ensurePanel();
        mNotificationContainer.removeAllViews();
        if (!enabled || notifications.isEmpty()) {
            TextView empty = buildNotificationChip(
                    mContext.getString(R.string.panel_no_notifications));
            mNotificationContainer.addView(empty);
            return;
        }
        int count = Math.min(MAX_NOTIFICATIONS, notifications.size());
        for (int i = notifications.size() - count; i < notifications.size(); i++) {
            MirroredNotification notification = notifications.get(i);
            CharSequence title = TextUtils.isEmpty(notification.getTitle())
                    ? notification.getPackageName() : notification.getTitle();
            CharSequence text = TextUtils.isEmpty(notification.getText()) ? "" : notification.getText();
            TextView chip = buildNotificationChip(mContext.getString(
                    R.string.notification_compact_item, title, text));
            mNotificationContainer.addView(chip);
        }
    }

    void updateMeters(@NonNull SystemStatsSampler.Snapshot snapshot,
            boolean showFpsMeter, boolean showSystemMeter) {
        if (!mGameVisible) {
            hideMeterViews();
            return;
        }
        ensurePanel();
        mFpsTileSummary.setText(formatFpsValue(snapshot.fps));
        mSystemTileSummary.setText(formatSystemInfo(snapshot));
        mShowFpsInFab = showFpsMeter;
        mLastFpsText = formatFpsValue(snapshot.fps);
        updateFabBadge();

        removeViewIfAttached(mFpsMeterView);

        if (showSystemMeter) {
            ensureSystemMeter();
            mSystemMeterText.setText(formatSystemInfo(snapshot));
            setSystemStat(mSystemCpuItem, mSystemCpuText,
                    formatPercentCompact(snapshot.cpuUsagePercent),
                    !Float.isNaN(snapshot.cpuUsagePercent));
            setSystemStat(mSystemTempItem, mSystemTempText,
                    formatTempCompact(snapshot.cpuTempCelsius),
                    !Float.isNaN(snapshot.cpuTempCelsius));
            setSystemStat(mSystemGpuItem, mSystemGpuText,
                    formatPercentCompact(snapshot.gpuUsagePercent),
                    !Float.isNaN(snapshot.gpuUsagePercent));
            setSystemStat(mSystemFpsItem, mSystemFpsText,
                    formatFpsValue(snapshot.fps),
                    !Float.isNaN(snapshot.fps));
            applyMeterPosition(mSystemMeterView, mSystemMeterLayoutParams,
                    GameSpacePreferences.getSystemMeterPosition(mContext));
        } else {
            removeViewIfAttached(mSystemMeterView);
        }
    }

    void setMeterVisibility(boolean showFpsMeter, boolean showSystemMeter) {
        mShowFpsInFab = showFpsMeter;
        updateFabBadge();
        removeViewIfAttached(mFpsMeterView);

        if (!showSystemMeter) {
            removeViewIfAttached(mSystemMeterView);
        } else if (mSystemMeterView != null) {
            addOrUpdateView(mSystemMeterView, mSystemMeterLayoutParams);
        }
    }

    void destroy() {
        hideAll();
        hideMeterViews();
        mGameVisible = false;
        mShowFpsInFab = false;
        mLastFpsText = "--";
        clearViewReferences();
    }

    private void ensureFab() {
        if (mFabView != null) {
            updateFabBadge();
            updateFabLocation();
            addOrUpdateView(mFabView, mFabLayoutParams);
            return;
        }
        FrameLayout root = new FrameLayout(mContext);
        ImageButton button = new ImageButton(mContext);
        button.setBackgroundResource(R.drawable.bg_gamespace_fab);
        button.setImageResource(R.drawable.ic_gamespace_gamepad);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int padding = dpToPx(10);
        button.setPadding(padding, padding, padding, padding);
        button.setClickable(false);
        button.setFocusable(false);
        FrameLayout.LayoutParams buttonLayoutParams = new FrameLayout.LayoutParams(
                dpToPx(FAB_SIZE_DP),
                dpToPx(FAB_SIZE_DP),
                Gravity.BOTTOM | Gravity.START);
        root.addView(button, buttonLayoutParams);

        TextView badge = new TextView(mContext);
        badge.setBackgroundResource(R.drawable.bg_gamespace_fps_badge);
        badge.setMinWidth(dpToPx(FAB_BADGE_SIZE_DP));
        badge.setMinHeight(dpToPx(FAB_BADGE_SIZE_DP));
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setTextColor(mContext.getColor(android.R.color.white));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setClickable(false);
        badge.setFocusable(false);
        FrameLayout.LayoutParams badgeLayoutParams = new FrameLayout.LayoutParams(
                dpToPx(FAB_BADGE_SIZE_DP),
                dpToPx(FAB_BADGE_SIZE_DP),
                Gravity.TOP | Gravity.END);
        root.addView(badge, badgeLayoutParams);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setContentDescription(mContext.getString(R.string.panel_open));

        WindowManager.LayoutParams params = baseLayoutParams(
                dpToPx(FAB_FRAME_SIZE_DP),
                dpToPx(FAB_FRAME_SIZE_DP),
                "GameSpaceFab");
        params.gravity = Gravity.TOP | Gravity.START;
        attachFabGesture(root, params);
        mFabView = root;
        mFabFpsBadge = badge;
        mFabLayoutParams = params;
        updateFabBadge();
        updateFabLocation();
        addOrUpdateView(mFabView, mFabLayoutParams);
    }

    private void ensureHandle() {
        if (mHandleView != null) {
            updateHandleGravity();
            addOrUpdateView(mHandleView, mHandleLayoutParams);
            return;
        }
        TextView handle = new TextView(mContext);
        handle.setBackgroundResource(R.drawable.bg_gamespace_handle);
        handle.setMinWidth(dpToPx(HANDLE_WIDTH_DP));
        handle.setMinHeight(dpToPx(HANDLE_HEIGHT_DP));
        handle.setAlpha(0.98f);

        WindowManager.LayoutParams params = baseLayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                "GameSpaceHandle");
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dpToPx(6);
        mHandleView = handle;
        mHandleLayoutParams = params;
        updateHandleGravity();

        final float[] down = new float[2];
        handle.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    down[1] = event.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float horizontalTravel = event.getRawX() - down[0];
                    boolean opening = isRightEdge()
                            ? horizontalTravel < -dpToPx(20)
                            : horizontalTravel > dpToPx(20);
                    if (opening || Math.abs(horizontalTravel) < dpToPx(8)) {
                        openPanel();
                    }
                    return true;
                default:
                    return true;
            }
        });
        addOrUpdateView(mHandleView, mHandleLayoutParams);
    }

    private void ensurePanel() {
        if (mPanelView != null) {
            updatePanelGravity();
            return;
        }
        View panel = LayoutInflater.from(mContext).inflate(R.layout.gamespace_panel, null);
        mPanelScrollView = panel.findViewById(R.id.panel_scroll);
        mQuickLaunchContainer = panel.findViewById(R.id.quick_launch_container);
        mNotificationContainer = panel.findViewById(R.id.notification_container);
        mPanelIcon = panel.findViewById(R.id.panel_icon);
        mPanelTitle = panel.findViewById(R.id.panel_title);
        mPanelSubtitle = panel.findViewById(R.id.panel_subtitle);
        mFpsTile = panel.findViewById(R.id.fps_tile);
        mFpsTileIcon = panel.findViewById(R.id.fps_tile_icon);
        mFpsTileTitle = panel.findViewById(R.id.fps_tile_title);
        mFpsTileSummary = panel.findViewById(R.id.fps_tile_summary);
        mSystemTile = panel.findViewById(R.id.system_tile);
        mSystemTileIcon = panel.findViewById(R.id.system_tile_icon);
        mSystemTileTitle = panel.findViewById(R.id.system_tile_title);
        mSystemTileSummary = panel.findViewById(R.id.system_tile_summary);
        mDndTile = panel.findViewById(R.id.dnd_tile);
        mDndTileIcon = panel.findViewById(R.id.dnd_tile_icon);
        mDndTileTitle = panel.findViewById(R.id.dnd_tile_title);
        mDndTileSummary = panel.findViewById(R.id.dnd_tile_summary);
        mCompactTile = panel.findViewById(R.id.compact_tile);
        mCompactTileIcon = panel.findViewById(R.id.compact_tile_icon);
        mCompactTileTitle = panel.findViewById(R.id.compact_tile_title);
        mCompactTileSummary = panel.findViewById(R.id.compact_tile_summary);
        mCallTile = panel.findViewById(R.id.call_tile);
        mCallTileIcon = panel.findViewById(R.id.call_tile_icon);
        mCallTileTitle = panel.findViewById(R.id.call_tile_title);
        mCallTileSummary = panel.findViewById(R.id.call_tile_summary);
        mSettingsTile = panel.findViewById(R.id.settings_tile);
        mSettingsTileIcon = panel.findViewById(R.id.settings_tile_icon);
        mSettingsTileTitle = panel.findViewById(R.id.settings_tile_title);
        mSettingsTileSummary = panel.findViewById(R.id.settings_tile_summary);
        View closeButton = panel.findViewById(R.id.close_button);

        setTileState(mFpsTile, mFpsTileIcon, mFpsTileTitle, mFpsTileSummary, false);
        setTileState(mSystemTile, mSystemTileIcon, mSystemTileTitle, mSystemTileSummary, false);
        setTileState(mDndTile, mDndTileIcon, mDndTileTitle, mDndTileSummary, false);
        setTileState(mCompactTile, mCompactTileIcon, mCompactTileTitle, mCompactTileSummary,
                false);
        setTileState(mCallTile, mCallTileIcon, mCallTileTitle, mCallTileSummary, false);
        setTileState(mSettingsTile, mSettingsTileIcon, mSettingsTileTitle, mSettingsTileSummary,
                false);

        mFpsTile.setOnClickListener(v -> mCallback.onToggleFpsMeter());
        mSystemTile.setOnClickListener(v -> mCallback.onToggleSystemMeter());
        mDndTile.setOnClickListener(v -> mCallback.onToggleDnd());
        mCompactTile.setOnClickListener(v -> mCallback.onToggleCompactNotifications());
        mCallTile.setOnClickListener(v -> mCallback.onCycleCallHandlingMode());
        mSettingsTile.setOnClickListener(v -> {
            Intent intent = new Intent(mContext, GameSpaceSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mContext.startActivity(intent);
            closePanel();
        });
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> closePanel());
        }

        WindowManager.LayoutParams params = baseLayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                "GameSpacePanel");
        mPanelView = panel;
        mPanelLayoutParams = params;
        updatePanelGravity();
    }

    private void ensureFpsMeter() {
        if (mFpsMeterView != null) {
            addOrUpdateView(mFpsMeterView, mFpsMeterLayoutParams);
            return;
        }
        MeterViews meter = createMeterView(true);
        mFpsMeterView = meter.root;
        mFpsMeterText = meter.value;
        mFpsMeterLayoutParams = baseLayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                "GameSpaceFpsStrip");
        attachDragGesture(mFpsMeterView, mFpsMeterLayoutParams,
                GameSpacePreferences.KEY_FPS_METER_POSITION);
        addOrUpdateView(mFpsMeterView, mFpsMeterLayoutParams);
    }

    private void ensureSystemMeter() {
        if (mSystemMeterView != null) {
            addOrUpdateView(mSystemMeterView, mSystemMeterLayoutParams);
            return;
        }
        MeterViews meter = createSystemMeterView();
        mSystemMeterView = meter.root;
        mSystemMeterText = meter.value;
        mSystemMeterLayoutParams = baseLayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                "GameSpaceSystemStrip");
        attachDragGesture(mSystemMeterView, mSystemMeterLayoutParams,
                GameSpacePreferences.KEY_SYSTEM_METER_POSITION);
        addOrUpdateView(mSystemMeterView, mSystemMeterLayoutParams);
    }

    private MeterViews createMeterView(boolean fpsStyle) {
        FrameLayout root = new FrameLayout(mContext);
        root.setPadding(
                fpsStyle ? dpToPx(12) : dpToPx(10),
                fpsStyle ? dpToPx(8) : dpToPx(7),
                fpsStyle ? dpToPx(12) : dpToPx(10),
                fpsStyle ? dpToPx(8) : dpToPx(7));
        root.setBackgroundResource(R.drawable.bg_gamespace_button_secondary);

        TextView value = new TextView(mContext);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.CENTER);
        value.setTextAppearance(fpsStyle
                ? android.R.style.TextAppearance_DeviceDefault_Large
                : android.R.style.TextAppearance_DeviceDefault_Small);
        value.setTextColor(resolveColor(android.R.attr.textColorPrimary));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, fpsStyle ? 18 : 12);
        root.addView(value, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return new MeterViews(root, value);
    }

    private MeterViews createSystemMeterView() {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dpToPx(10), dpToPx(7), dpToPx(10), dpToPx(7));
        root.setBackgroundResource(R.drawable.bg_gamespace_button_secondary);

        StatViews cpu = addSystemStat(root, R.drawable.ic_meter_cpu, true);
        mSystemCpuItem = cpu.root;
        mSystemCpuText = cpu.value;
        StatViews temp = addSystemStat(root, R.drawable.ic_meter_temp, true);
        mSystemTempItem = temp.root;
        mSystemTempText = temp.value;
        StatViews gpu = addSystemStat(root, R.drawable.ic_meter_gpu, true);
        mSystemGpuItem = gpu.root;
        mSystemGpuText = gpu.value;
        StatViews fps = addSystemStat(root, R.drawable.ic_meter_fps, false);
        mSystemFpsItem = fps.root;
        mSystemFpsText = fps.value;

        TextView summary = new TextView(mContext);
        summary.setVisibility(View.GONE);
        return new MeterViews(root, summary);
    }

    @NonNull
    private StatViews addSystemStat(@NonNull LinearLayout parent, int iconRes, boolean addMargin) {
        LinearLayout item = new LinearLayout(mContext);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams itemLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        itemLayoutParams.rightMargin = addMargin ? dpToPx(8) : 0;
        item.setLayoutParams(itemLayoutParams);

        ImageView icon = new ImageView(mContext);
        LinearLayout.LayoutParams iconLayoutParams = new LinearLayout.LayoutParams(
                dpToPx(12), dpToPx(12));
        icon.setLayoutParams(iconLayoutParams);
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                resolveColor(android.R.attr.textColorPrimary)));
        item.addView(icon);

        TextView value = new TextView(mContext);
        LinearLayout.LayoutParams valueLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        valueLayoutParams.leftMargin = dpToPx(4);
        value.setLayoutParams(valueLayoutParams);
        value.setSingleLine(true);
        value.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Small);
        value.setTextColor(resolveColor(android.R.attr.textColorPrimary));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        item.addView(value);

        parent.addView(item);
        return new StatViews(item, value);
    }

    private void setSystemStat(@NonNull View item, @NonNull TextView textView,
            @NonNull String value, boolean available) {
        item.setVisibility(available ? View.VISIBLE : View.GONE);
        if (available) {
            textView.setText(value);
        }
    }

    private void updateTitle(@Nullable CharSequence gameLabel, @Nullable String packageName) {
        ensurePanel();
        if (!TextUtils.isEmpty(gameLabel)) {
            mPanelTitle.setText(mContext.getString(R.string.panel_active_game, gameLabel));
        } else {
            mPanelTitle.setText(R.string.panel_unknown_game);
        }
        mPanelSubtitle.setText(packageName);
        if (mPanelIcon != null) {
            if (!TextUtils.isEmpty(packageName)) {
                try {
                    Drawable appIcon = mPackageManager.getApplicationIcon(packageName);
                    mPanelIcon.setImageDrawable(appIcon);
                } catch (PackageManager.NameNotFoundException ignored) {
                    mPanelIcon.setImageResource(R.drawable.ic_gamespace_gamepad);
                }
            } else {
                mPanelIcon.setImageResource(R.drawable.ic_gamespace_gamepad);
            }
        }
    }

    private void updateQuickLaunch(@NonNull List<String> sidebarPackages) {
        ensurePanel();
        mQuickLaunchContainer.removeAllViews();
        if (sidebarPackages.isEmpty()) {
            ImageView add = buildQuickLaunchIcon(
                    mContext.getDrawable(R.drawable.ic_quick_launch_add));
            add.setOnClickListener(v -> {
                Intent intent = new Intent(mContext, GameSpaceSettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                closePanel();
            });
            mQuickLaunchContainer.addView(add);
            return;
        }
        int count = Math.min(MAX_QUICK_LAUNCH, sidebarPackages.size());
        for (int i = 0; i < count; i++) {
            String packageName = sidebarPackages.get(i);
            try {
                Drawable icon = mPackageManager.getApplicationIcon(packageName);
                ImageView imageView = buildQuickLaunchIcon(icon);
                imageView.setOnClickListener(v -> {
                    mCallback.onLaunchPackage(packageName);
                    closePanel();
                });
                mQuickLaunchContainer.addView(imageView);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        if (sidebarPackages.size() > MAX_QUICK_LAUNCH) {
            ImageView add = buildQuickLaunchIcon(
                    mContext.getDrawable(R.drawable.ic_quick_launch_add));
            add.setOnClickListener(v -> {
                Intent intent = new Intent(mContext, GameSpaceSettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                closePanel();
            });
            mQuickLaunchContainer.addView(add);
        }
    }

    private ImageView buildQuickLaunchIcon(@Nullable Drawable icon) {
        ImageView imageView = new ImageView(mContext);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                dpToPx(38), dpToPx(38));
        layoutParams.bottomMargin = dpToPx(8);
        imageView.setLayoutParams(layoutParams);
        imageView.setBackgroundResource(R.drawable.bg_gamespace_button_secondary);
        imageView.setImageDrawable(icon);
        imageView.setPadding(dpToPx(7), dpToPx(7), dpToPx(7), dpToPx(7));
        return imageView;
    }

    private TextView buildNotificationChip(@NonNull CharSequence text) {
        TextView chip = new TextView(mContext);
        chip.setBackgroundResource(R.drawable.bg_gamespace_button_secondary);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxLines(1);
        chip.setText(text);
        chip.setTextColor(mContext.getColor(R.color.gamespace_tile_summary));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(6);
        chip.setLayoutParams(params);
        return chip;
    }

    private void openPanel() {
        if (!mGameVisible) {
            return;
        }
        ensurePanel();
        ensureBackdrop();
        addOrUpdateView(mBackdropView, mBackdropLayoutParams);
        addOrUpdateView(mPanelView, mPanelLayoutParams);
        removeViewIfAttached(mHandleView);
        removeViewIfAttached(mFabView);
    }

    private void closePanel() {
        removeViewIfAttached(mPanelView);
        removeViewIfAttached(mBackdropView);
        if (mGameVisible) {
            ensureFab();
        }
    }

    void hideAll() {
        closePanel();
        removeViewIfAttached(mHandleView);
        removeViewIfAttached(mFabView);
    }

    private void hideMeterViews() {
        removeViewIfAttached(mFpsMeterView);
        removeViewIfAttached(mSystemMeterView);
    }

    private void ensureBackdrop() {
        if (mBackdropView != null) {
            return;
        }
        View backdrop = new View(mContext);
        backdrop.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        backdrop.setOnClickListener(v -> closePanel());
        backdrop.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                closePanel();
                return true;
            }
            return false;
        });
        mBackdropView = backdrop;
        mBackdropLayoutParams = baseLayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                "GameSpaceBackdrop");
        mBackdropLayoutParams.gravity = Gravity.TOP | Gravity.START;
    }

    private void updateHandleGravity() {
        if (mHandleLayoutParams == null) {
            return;
        }
        mHandleLayoutParams.gravity = (isRightEdge()
                ? Gravity.END : Gravity.START) | Gravity.TOP;
        mHandleLayoutParams.x = dpToPx(6);
        Rect bounds = getDisplayBounds();
        mHandleLayoutParams.y = Math.max(dpToPx(72),
                bounds.top + ((bounds.height() - dpToPx(HANDLE_HEIGHT_DP)) / 2));
    }

    private void updateFabLocation() {
        if (mFabLayoutParams == null) {
            return;
        }
        Rect bounds = getDisplayBounds();
        int fabWidth = resolveViewWidth(mFabView, mFabLayoutParams);
        int fabHeight = resolveViewHeight(mFabView, mFabLayoutParams);
        int minX = bounds.left;
        int maxX = Math.max(minX, bounds.right - fabWidth);
        int minY = bounds.top;
        int maxY = Math.max(minY, bounds.bottom - fabHeight);
        mFabLayoutParams.gravity = Gravity.TOP | Gravity.START;
        if (GameSpacePreferences.hasFabPosition(mContext)) {
            float xFraction = GameSpacePreferences.getFabXFraction(mContext);
            float yFraction = GameSpacePreferences.getFabYFraction(mContext);
            mFabLayoutParams.x = minX + Math.round((maxX - minX) * clampFraction(xFraction));
            mFabLayoutParams.y = minY + Math.round((maxY - minY) * clampFraction(yFraction));
            return;
        }
        int margin = dpToPx(MARGIN_DP);
        mFabLayoutParams.x = isRightEdge()
                ? Math.max(minX, maxX - margin)
                : Math.min(maxX, minX + margin);
        mFabLayoutParams.y = clamp(bounds.top + margin, minY, maxY);
    }

    private void updateFabBadge() {
        if (mFabFpsBadge == null) {
            return;
        }
        mFabFpsBadge.setVisibility(mShowFpsInFab ? View.VISIBLE : View.GONE);
        if (mShowFpsInFab) {
            mFabFpsBadge.setText(mLastFpsText);
        }
    }

    private void updatePanelGravity() {
        if (mPanelLayoutParams == null) {
            return;
        }
        updatePanelScrollBounds();
        mPanelLayoutParams.gravity = (isRightEdge()
                ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
        mPanelLayoutParams.x = dpToPx(12);
        addOrUpdateViewIfAttached(mPanelView, mPanelLayoutParams);
    }

    private void updatePanelScrollBounds() {
        if (mPanelScrollView == null) {
            return;
        }
        View content = mPanelScrollView.getChildCount() > 0 ? mPanelScrollView.getChildAt(0) : null;
        if (content == null) {
            return;
        }
        Rect bounds = getDisplayBounds();
        int maxHeight = Math.max(dpToPx(220), bounds.height() - dpToPx(96));
        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                dpToPx(PANEL_CONTENT_WIDTH_DP), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST);
        content.measure(widthSpec, heightSpec);

        ViewGroup.LayoutParams layoutParams = mPanelScrollView.getLayoutParams();
        int desiredHeight = content.getMeasuredHeight();
        layoutParams.height = Math.min(desiredHeight, maxHeight);
        mPanelScrollView.setLayoutParams(layoutParams);
    }

    private boolean isRightEdge() {
        return GameSpacePreferences.EDGE_RIGHT.equals(
                GameSpacePreferences.getEdgePosition(mContext));
    }

    private void attachDragGesture(@NonNull View view,
            @NonNull WindowManager.LayoutParams params, @NonNull String prefKey) {
        final DragState dragState = new DragState();
        view.setOnLongClickListener(v -> {
            dragState.armed = true;
            return true;
        });
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragState.downRawX = event.getRawX();
                    dragState.downRawY = event.getRawY();
                    dragState.startX = params.x;
                    dragState.startY = params.y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!dragState.armed) {
                        break;
                    }
                    params.x = dragState.startX + Math.round(event.getRawX() - dragState.downRawX);
                    params.y = dragState.startY + Math.round(event.getRawY() - dragState.downRawY);
                    addOrUpdateView(v, params);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragState.armed) {
                        break;
                    }
                    dragState.armed = false;
                    snapMeter(v, params, prefKey);
                    return true;
                default:
                    break;
            }
            return false;
        });
    }

    private void attachFabGesture(@NonNull View view,
            @NonNull WindowManager.LayoutParams params) {
        final DragState dragState = new DragState();
        final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        view.setOnClickListener(v -> openPanel());
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragState.downRawX = event.getRawX();
                    dragState.downRawY = event.getRawY();
                    dragState.startX = params.x;
                    dragState.startY = params.y;
                    dragState.dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - dragState.downRawX;
                    float deltaY = event.getRawY() - dragState.downRawY;
                    if (!dragState.dragging && (Math.abs(deltaX) > touchSlop
                            || Math.abs(deltaY) > touchSlop)) {
                        dragState.dragging = true;
                    }
                    if (!dragState.dragging) {
                        return true;
                    }
                    Rect bounds = getDisplayBounds();
                    int maxX = Math.max(bounds.left, bounds.right - resolveViewWidth(v, params));
                    int maxY = Math.max(bounds.top, bounds.bottom - resolveViewHeight(v, params));
                    params.x = clamp(dragState.startX + Math.round(deltaX), bounds.left, maxX);
                    params.y = clamp(dragState.startY + Math.round(deltaY), bounds.top, maxY);
                    addOrUpdateView(v, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragState.dragging) {
                        persistFabPosition(v, params);
                        dragState.dragging = false;
                    } else {
                        v.performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragState.dragging) {
                        persistFabPosition(v, params);
                        dragState.dragging = false;
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void snapMeter(@NonNull View view, @NonNull WindowManager.LayoutParams params,
            @NonNull String prefKey) {
        Rect bounds = getDisplayBounds();
        String snappedPosition;
        boolean left = params.x < (bounds.width() / 2);
        boolean top = params.y < (bounds.height() / 2);
        if (top && left) {
            snappedPosition = GameSpacePreferences.POSITION_TOP_LEFT;
        } else if (top) {
            snappedPosition = GameSpacePreferences.POSITION_TOP_RIGHT;
        } else if (left) {
            snappedPosition = GameSpacePreferences.POSITION_BOTTOM_LEFT;
        } else {
            snappedPosition = GameSpacePreferences.POSITION_BOTTOM_RIGHT;
        }
        GameSpacePreferences.setMeterPosition(mContext, prefKey, snappedPosition);
        applyMeterPosition(view, params, snappedPosition);
    }

    private void applyMeterPosition(@NonNull View view, @NonNull WindowManager.LayoutParams params,
            @NonNull String position) {
        Rect bounds = getDisplayBounds();
        view.measure(
                View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.AT_MOST));
        int width = view.getMeasuredWidth();
        int height = view.getMeasuredHeight();
        int margin = dpToPx(MARGIN_DP);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = position.endsWith("right")
                ? Math.max(margin, bounds.width() - width - margin)
                : margin;
        params.y = position.startsWith("bottom")
                ? Math.max(margin, bounds.height() - height - margin)
                : margin;
        addOrUpdateView(view, params);
    }

    private void persistFabPosition(@NonNull View view,
            @NonNull WindowManager.LayoutParams params) {
        Rect bounds = getDisplayBounds();
        int minX = bounds.left;
        int maxX = Math.max(minX, bounds.right - resolveViewWidth(view, params));
        int minY = bounds.top;
        int maxY = Math.max(minY, bounds.bottom - resolveViewHeight(view, params));
        int clampedX = clamp(params.x, minX, maxX);
        int clampedY = clamp(params.y, minY, maxY);
        float xFraction = maxX == minX ? 0f : (float) (clampedX - minX) / (float) (maxX - minX);
        float yFraction = maxY == minY ? 0f : (float) (clampedY - minY) / (float) (maxY - minY);
        GameSpacePreferences.setFabPosition(mContext, xFraction, yFraction);
    }

    private Rect getDisplayBounds() {
        Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
        WindowInsets insets = mWindowManager.getCurrentWindowMetrics().getWindowInsets();
        Insets insetValues = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        return new Rect(
                bounds.left + insetValues.left,
                bounds.top + insetValues.top,
                bounds.right - insetValues.right,
                bounds.bottom - insetValues.bottom);
    }

    private WindowManager.LayoutParams baseLayoutParams(int width, int height, String title) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.setTitle(title);
        params.setSystemApplicationOverlay(true);
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        return params;
    }

    private void addOrUpdateView(@Nullable View view, @NonNull WindowManager.LayoutParams params) {
        if (view == null) {
            return;
        }
        if (view.getParent() == null) {
            mWindowManager.addView(view, params);
        } else {
            mWindowManager.updateViewLayout(view, params);
        }
    }

    private void addOrUpdateViewIfAttached(@Nullable View view,
            @NonNull WindowManager.LayoutParams params) {
        if (view != null && view.getParent() != null) {
            mWindowManager.updateViewLayout(view, params);
        }
    }

    private void removeViewIfAttached(@Nullable View view) {
        if (view != null && view.getParent() != null) {
            mWindowManager.removeView(view);
        }
    }

    private void clearViewReferences() {
        mHandleView = null;
        mHandleLayoutParams = null;
        mBackdropView = null;
        mBackdropLayoutParams = null;
        mPanelView = null;
        mPanelLayoutParams = null;
        mPanelScrollView = null;
        mQuickLaunchContainer = null;
        mNotificationContainer = null;
        mPanelIcon = null;
        mPanelTitle = null;
        mPanelSubtitle = null;
        mFpsTile = null;
        mFpsTileIcon = null;
        mFpsTileTitle = null;
        mFpsTileSummary = null;
        mSystemTile = null;
        mSystemTileIcon = null;
        mSystemTileTitle = null;
        mSystemTileSummary = null;
        mDndTile = null;
        mDndTileIcon = null;
        mDndTileTitle = null;
        mDndTileSummary = null;
        mCompactTile = null;
        mCompactTileIcon = null;
        mCompactTileTitle = null;
        mCompactTileSummary = null;
        mCallTile = null;
        mCallTileIcon = null;
        mCallTileTitle = null;
        mCallTileSummary = null;
        mSettingsTile = null;
        mSettingsTileIcon = null;
        mSettingsTileTitle = null;
        mSettingsTileSummary = null;
        mFabView = null;
        mFabFpsBadge = null;
        mFabLayoutParams = null;
        mFpsMeterView = null;
        mFpsMeterLayoutParams = null;
        mFpsMeterText = null;
        mSystemMeterView = null;
        mSystemMeterLayoutParams = null;
        mSystemMeterText = null;
        mSystemCpuItem = null;
        mSystemCpuText = null;
        mSystemTempItem = null;
        mSystemTempText = null;
        mSystemGpuItem = null;
        mSystemGpuText = null;
        mSystemFpsItem = null;
        mSystemFpsText = null;
    }

    private int dpToPx(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, mContext.getResources().getDisplayMetrics()));
    }

    private int resolveViewWidth(@Nullable View view,
            @NonNull WindowManager.LayoutParams params) {
        if (params.width > 0) {
            return params.width;
        }
        if (view == null) {
            return 0;
        }
        Rect bounds = getDisplayBounds();
        view.measure(
                View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.AT_MOST));
        return view.getMeasuredWidth();
    }

    private int resolveViewHeight(@Nullable View view,
            @NonNull WindowManager.LayoutParams params) {
        if (params.height > 0) {
            return params.height;
        }
        if (view == null) {
            return 0;
        }
        Rect bounds = getDisplayBounds();
        view.measure(
                View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.AT_MOST));
        return view.getMeasuredHeight();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clampFraction(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int resolveColor(int attr) {
        TypedArray typedArray = mContext.obtainStyledAttributes(new int[] {attr});
        int color = typedArray.getColor(0, 0);
        typedArray.recycle();
        return color;
    }

    private void setTileState(@NonNull View tile, @NonNull ImageView icon,
            @NonNull TextView title, @NonNull TextView summary, boolean active) {
        tile.setBackgroundResource(active
                ? R.drawable.bg_gamespace_tile_active
                : R.drawable.bg_gamespace_tile);
        int titleColor = mContext.getColor(active
                ? R.color.gamespace_tile_active_text
                : R.color.gamespace_tile_text);
        int summaryColor = mContext.getColor(active
                ? R.color.gamespace_tile_active_summary
                : R.color.gamespace_tile_summary);
        int iconColor = mContext.getColor(active
                ? R.color.gamespace_tile_active_icon
                : R.color.gamespace_tile_icon);
        title.setTextColor(titleColor);
        summary.setTextColor(summaryColor);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(iconColor));
    }

    @NonNull
    private String formatFpsValue(float fps) {
        return Float.isNaN(fps) ? "--" : String.format(Locale.getDefault(), "%.0f", fps);
    }

    @NonNull
    private String formatSystemInfo(@NonNull SystemStatsSampler.Snapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, formatCpuPercent(snapshot.cpuUsagePercent));
        appendPart(builder, formatGpuPercent(snapshot.gpuUsagePercent));
        appendPart(builder, formatTemp(snapshot.cpuTempCelsius));
        appendPart(builder, formatFpsLabel(snapshot.fps));
        return builder.length() == 0 ? "--" : builder.toString();
    }

    @NonNull
    private String formatCpuPercent(float value) {
        return Float.isNaN(value)
                ? "" : String.format(Locale.getDefault(), "CPU %.0f%%", value);
    }

    @NonNull
    private String formatPercentCompact(float value) {
        return Float.isNaN(value) ? "--" : String.format(Locale.getDefault(), "%.0f%%", value);
    }

    @NonNull
    private String formatGpuPercent(float value) {
        return Float.isNaN(value)
                ? "" : String.format(Locale.getDefault(), "GPU %.0f%%", value);
    }

    @NonNull
    private String formatTemp(float value) {
        return Float.isNaN(value) ? "" : String.format(Locale.getDefault(), "%.1fC", value);
    }

    @NonNull
    private String formatTempCompact(float value) {
        return Float.isNaN(value) ? "--" : String.format(Locale.getDefault(), "%.1fC", value);
    }

    @NonNull
    private String formatFpsLabel(float value) {
        return Float.isNaN(value) ? "" : String.format(Locale.getDefault(), "FPS %.0f", value);
    }

    private void appendPart(@NonNull StringBuilder builder, @NonNull String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("  ");
        }
        builder.append(value);
    }

    private static final class MeterViews {
        final View root;
        final TextView value;

        MeterViews(View root, TextView value) {
            this.root = root;
            this.value = value;
        }
    }

    private static final class StatViews {
        final View root;
        final TextView value;

        StatViews(View root, TextView value) {
            this.root = root;
            this.value = value;
        }
    }

    private static final class DragState {
        boolean armed;
        boolean dragging;
        float downRawX;
        float downRawY;
        int startX;
        int startY;
    }
}
