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

package com.android.gamespace;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.android.gamespace.runtime.GameSpaceService;
import com.android.gamespace.settings.GameSpaceSettingsFragment;

public class GameSpaceSettingsActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.activity_gamespace_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_container, new GameSpaceSettingsFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        GameSpaceService.requestStart(this);
    }
}
