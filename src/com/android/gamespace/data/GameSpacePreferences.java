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

package com.android.gamespace.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GameSpacePreferences {
    public static final String KEY_ENABLED = "gamespace_enabled";
    public static final String KEY_EDGE_POSITION = "edge_position";
    public static final String KEY_FPS_METER_ENABLED = "fps_meter_enabled";
    public static final String KEY_SYSTEM_METER_ENABLED = "system_meter_enabled";
    public static final String KEY_COMPACT_NOTIFICATIONS = "compact_notifications";
    public static final String KEY_CALL_HANDLING_MODE = "call_handling_mode";
    public static final String KEY_MANUAL_LIBRARY = "manual_library_packages";
    public static final String KEY_SIDEBAR_APPS = "sidebar_packages";
    public static final String KEY_FPS_METER_POSITION = "fps_meter_position";
    public static final String KEY_SYSTEM_METER_POSITION = "system_meter_position";
    public static final String KEY_FAB_X_FRACTION = "fab_x_fraction";
    public static final String KEY_FAB_Y_FRACTION = "fab_y_fraction";

    public static final String EDGE_LEFT = "left";
    public static final String EDGE_RIGHT = "right";

    public static final String CALL_MODE_OFF = "off";
    public static final String CALL_MODE_REJECT = "reject";
    public static final String CALL_MODE_ANSWER = "answer";

    public static final String POSITION_TOP_LEFT = "top_left";
    public static final String POSITION_TOP_RIGHT = "top_right";
    public static final String POSITION_BOTTOM_LEFT = "bottom_left";
    public static final String POSITION_BOTTOM_RIGHT = "bottom_right";

    private GameSpacePreferences() {
    }

    public static SharedPreferences get(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isEnabled(Context context) {
        return get(context).getBoolean(KEY_ENABLED, true);
    }

    public static String getEdgePosition(Context context) {
        return get(context).getString(KEY_EDGE_POSITION, EDGE_LEFT);
    }

    public static boolean isFpsMeterEnabled(Context context) {
        return get(context).getBoolean(KEY_FPS_METER_ENABLED, true);
    }

    public static boolean isSystemMeterEnabled(Context context) {
        return get(context).getBoolean(KEY_SYSTEM_METER_ENABLED, true);
    }

    public static boolean isCompactNotificationsEnabled(Context context) {
        return get(context).getBoolean(KEY_COMPACT_NOTIFICATIONS, true);
    }

    public static String getCallHandlingMode(Context context) {
        return get(context).getString(KEY_CALL_HANDLING_MODE, CALL_MODE_OFF);
    }

    public static String getFpsMeterPosition(Context context) {
        return get(context).getString(KEY_FPS_METER_POSITION, POSITION_TOP_LEFT);
    }

    public static String getSystemMeterPosition(Context context) {
        return get(context).getString(KEY_SYSTEM_METER_POSITION, POSITION_TOP_RIGHT);
    }

    public static void resetMeterPositions(Context context) {
        get(context).edit()
                .putString(KEY_FPS_METER_POSITION, POSITION_TOP_LEFT)
                .putString(KEY_SYSTEM_METER_POSITION, POSITION_TOP_RIGHT)
                .apply();
    }

    public static void setMeterPosition(Context context, String key, String position) {
        get(context).edit().putString(key, position).apply();
    }

    public static boolean hasFabPosition(Context context) {
        SharedPreferences preferences = get(context);
        return preferences.contains(KEY_FAB_X_FRACTION) && preferences.contains(KEY_FAB_Y_FRACTION);
    }

    public static float getFabXFraction(Context context) {
        return get(context).getFloat(KEY_FAB_X_FRACTION, 0f);
    }

    public static float getFabYFraction(Context context) {
        return get(context).getFloat(KEY_FAB_Y_FRACTION, 0f);
    }

    public static void setFabPosition(Context context, float xFraction, float yFraction) {
        get(context).edit()
                .putFloat(KEY_FAB_X_FRACTION, xFraction)
                .putFloat(KEY_FAB_Y_FRACTION, yFraction)
                .apply();
    }

    public static Set<String> getManualLibraryPackages(Context context) {
        return getStringSet(context, KEY_MANUAL_LIBRARY);
    }

    public static void setManualLibraryPackages(Context context, Set<String> packages) {
        putStringSet(context, KEY_MANUAL_LIBRARY, packages);
    }

    public static Set<String> getSidebarPackages(Context context) {
        return getStringSet(context, KEY_SIDEBAR_APPS);
    }

    public static void setSidebarPackages(Context context, Set<String> packages) {
        putStringSet(context, KEY_SIDEBAR_APPS, packages);
    }

    public static List<String> getSidebarPackagesSorted(Context context) {
        List<String> packages = new ArrayList<>(getSidebarPackages(context));
        Collections.sort(packages);
        return packages;
    }

    private static Set<String> getStringSet(Context context, String key) {
        Set<String> stored = get(context).getStringSet(key, Collections.emptySet());
        return new LinkedHashSet<>(stored);
    }

    private static void putStringSet(Context context, String key, Set<String> values) {
        get(context).edit().putStringSet(key, new LinkedHashSet<>(values)).apply();
    }
}
