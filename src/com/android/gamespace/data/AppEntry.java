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

import android.graphics.drawable.Drawable;

public final class AppEntry {
    private final String mPackageName;
    private final CharSequence mLabel;
    private final Drawable mIcon;
    private final boolean mAutoDetectedGame;

    public AppEntry(String packageName, CharSequence label, Drawable icon,
            boolean autoDetectedGame) {
        mPackageName = packageName;
        mLabel = label;
        mIcon = icon;
        mAutoDetectedGame = autoDetectedGame;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public boolean isAutoDetectedGame() {
        return mAutoDetectedGame;
    }
}
