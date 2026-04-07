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

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskInfo;
import android.app.TaskStackListener;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import java.util.List;

final class ForegroundGameTracker {

    interface Callback {
        void onTopTaskChanged(@Nullable TaskInfo taskInfo);
    }

    private final ActivityTaskManager mActivityTaskManager;
    private final Callback mCallback;
    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
            mCallback.onTopTaskChanged(taskInfo);
        }

        @Override
        public void onTaskStackChanged() {
            mCallback.onTopTaskChanged(getTopTask());
        }
    };

    private boolean mRegistered;

    ForegroundGameTracker(Callback callback) {
        mActivityTaskManager = ActivityTaskManager.getInstance();
        mCallback = callback;
    }

    void start() {
        if (mRegistered) {
            return;
        }
        mActivityTaskManager.registerTaskStackListener(mTaskStackListener);
        mRegistered = true;
        mCallback.onTopTaskChanged(getTopTask());
    }

    void stop() {
        if (!mRegistered) {
            return;
        }
        mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
        mRegistered = false;
    }

    @Nullable
    TaskInfo getTopTask() {
        try {
            TaskInfo focusedTask = ActivityTaskManager.getService().getFocusedRootTaskInfo();
            if (focusedTask != null && focusedTask.topActivity != null) {
                return focusedTask;
            }
        } catch (RemoteException ignored) {
        }
        List<ActivityManager.RunningTaskInfo> tasks = mActivityTaskManager.getTasks(1);
        return tasks.isEmpty() ? null : tasks.get(0);
    }
}
