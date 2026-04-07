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

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

public class GameSpaceNotificationListenerService extends NotificationListenerService {
    public static final String ACTION_NOTIFICATIONS_RESET =
            "com.android.gamespace.action.NOTIFICATIONS_RESET";
    public static final String ACTION_NOTIFICATION_POSTED =
            "com.android.gamespace.action.NOTIFICATION_POSTED";
    public static final String ACTION_NOTIFICATION_REMOVED =
            "com.android.gamespace.action.NOTIFICATION_REMOVED";

    public static final String EXTRA_KEY = "key";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_CATEGORY = "category";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sendBroadcastToSelf(new Intent(ACTION_NOTIFICATIONS_RESET));
        StatusBarNotification[] activeNotifications = getActiveNotifications();
        if (activeNotifications == null) {
            return;
        }
        for (StatusBarNotification statusBarNotification : activeNotifications) {
            dispatchPosted(statusBarNotification);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        dispatchPosted(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Intent intent = new Intent(ACTION_NOTIFICATION_REMOVED)
                .putExtra(EXTRA_KEY, sbn.getKey());
        sendBroadcastToSelf(intent);
    }

    private void dispatchPosted(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null || getPackageName().equals(sbn.getPackageName())) {
            return;
        }

        Bundle extras = notification.extras;
        CharSequence title = extras != null ? extras.getCharSequence(Notification.EXTRA_TITLE) : null;
        CharSequence text = extras != null ? extras.getCharSequence(Notification.EXTRA_TEXT) : null;
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(text)
                && !Notification.CATEGORY_CALL.equals(notification.category)) {
            return;
        }

        Intent intent = new Intent(ACTION_NOTIFICATION_POSTED)
                .putExtra(EXTRA_KEY, sbn.getKey())
                .putExtra(EXTRA_PACKAGE, sbn.getPackageName())
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_CATEGORY, notification.category);
        sendBroadcastToSelf(intent);
    }

    private void sendBroadcastToSelf(Intent intent) {
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
