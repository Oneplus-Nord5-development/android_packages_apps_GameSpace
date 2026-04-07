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

package com.android.gamespace.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.gamespace.R;
import com.android.gamespace.data.GameSpacePreferences;
import com.android.gamespace.runtime.GameSpaceService;

import java.util.Set;

public class GameSpaceSettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Preference mLibraryPreference;
    private Preference mSidebarPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.gamespace_settings, rootKey);

        mLibraryPreference = requirePreference("game_library");
        mSidebarPreference = requirePreference("sidebar_apps");

        mLibraryPreference.setOnPreferenceClickListener(preference -> {
            launchPicker(AppSelectionActivity.MODE_LIBRARY);
            return true;
        });

        mSidebarPreference.setOnPreferenceClickListener(preference -> {
            launchPicker(AppSelectionActivity.MODE_SIDEBAR);
            return true;
        });

        requirePreference("reset_meter_positions").setOnPreferenceClickListener(preference -> {
            GameSpacePreferences.resetMeterPositions(requireContext());
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
            GameSpaceService.requestStart(requireContext());
            return true;
        });

        updateDynamicSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        GameSpacePreferences.get(requireContext()).registerOnSharedPreferenceChangeListener(this);
        updateDynamicSummaries();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView listView = getListView();
        int bottomPadding = dpToPx(24);
        listView.setClipToPadding(false);
        listView.setPadding(
                listView.getPaddingLeft(),
                listView.getPaddingTop(),
                listView.getPaddingRight(),
                bottomPadding);
    }

    @Override
    public void onPause() {
        GameSpacePreferences.get(requireContext()).unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateDynamicSummaries();
        if (GameSpacePreferences.KEY_ENABLED.equals(key)) {
            if (GameSpacePreferences.isEnabled(requireContext())) {
                GameSpaceService.requestStart(requireContext());
            } else {
                GameSpaceService.requestStop(requireContext());
            }
            return;
        }
        GameSpaceService.requestStart(requireContext());
    }

    private void launchPicker(@NonNull String mode) {
        Intent intent = new Intent(requireContext(), AppSelectionActivity.class);
        intent.putExtra(AppSelectionActivity.EXTRA_MODE, mode);
        startActivity(intent);
    }

    private void updateDynamicSummaries() {
        Set<String> libraryPackages = GameSpacePreferences.getManualLibraryPackages(requireContext());
        Set<String> sidebarPackages = GameSpacePreferences.getSidebarPackages(requireContext());
        mLibraryPreference.setSummary(getString(
                R.string.selector_count_summary, libraryPackages.size()));
        mSidebarPreference.setSummary(getString(
                R.string.selector_count_summary, sidebarPackages.size()));
    }

    @SuppressWarnings("unchecked")
    private <T extends Preference> T requirePreference(String key) {
        Preference preference = findPreference(key);
        if (preference == null) {
            throw new IllegalStateException("Missing preference: " + key);
        }
        return (T) preference;
    }

    private int dpToPx(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                requireContext().getResources().getDisplayMetrics()));
    }
}
