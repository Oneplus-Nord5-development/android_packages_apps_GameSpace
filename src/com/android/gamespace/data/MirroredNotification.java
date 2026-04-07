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

public final class MirroredNotification {
    private final String mKey;
    private final String mPackageName;
    private final CharSequence mTitle;
    private final CharSequence mText;
    private final String mCategory;

    public MirroredNotification(String key, String packageName, CharSequence title,
            CharSequence text, String category) {
        mKey = key;
        mPackageName = packageName;
        mTitle = title;
        mText = text;
        mCategory = category;
    }

    public String getKey() {
        return mKey;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public CharSequence getText() {
        return mText;
    }

    public String getCategory() {
        return mCategory;
    }
}
