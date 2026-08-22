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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.android.gamespace.data.GameSpacePreferences;
import com.android.gamespace.runtime.GameSpaceService;
import com.android.gamespace.settings.AppSelectionActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Set;

public class GameSpaceSettingsActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private MaterialCardView mCardMasterSwitch;
    private MaterialSwitch mSwitchMaster;
    private View mRowEdgePosition;
    private TextView mSummaryEdgePosition;
    private View mRowFpsMeter;
    private MaterialSwitch mSwitchFpsMeter;
    private View mRowSystemMeter;
    private MaterialSwitch mSwitchSystemMeter;
    private View mRowResetMeters;
    private View mRowCompactNotifications;
    private MaterialSwitch mSwitchCompactNotifications;
    private View mRowCallHandling;
    private TextView mSummaryCallHandling;
    private View mRowGameLibrary;
    private TextView mSummaryGameLibrary;
    private View mRowSidebarApps;
    private TextView mSummarySidebarApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.activity_gamespace_settings);

        initViews();
        setupListeners();
    }

    private void initViews() {
        mCardMasterSwitch = findViewById(R.id.card_master_switch);
        mSwitchMaster = findViewById(R.id.switch_gamespace_enabled);
        mRowEdgePosition = findViewById(R.id.row_edge_position);
        mSummaryEdgePosition = findViewById(R.id.summary_edge_position);
        mRowFpsMeter = findViewById(R.id.row_fps_meter);
        mSwitchFpsMeter = findViewById(R.id.switch_fps_meter);
        mRowSystemMeter = findViewById(R.id.row_system_meter);
        mSwitchSystemMeter = findViewById(R.id.switch_system_meter);
        mRowResetMeters = findViewById(R.id.row_reset_meters);
        mRowCompactNotifications = findViewById(R.id.row_compact_notifications);
        mSwitchCompactNotifications = findViewById(R.id.switch_compact_notifications);
        mRowCallHandling = findViewById(R.id.row_call_handling);
        mSummaryCallHandling = findViewById(R.id.summary_call_handling);
        mRowGameLibrary = findViewById(R.id.row_game_library);
        mSummaryGameLibrary = findViewById(R.id.summary_game_library);
        mRowSidebarApps = findViewById(R.id.row_sidebar_apps);
        mSummarySidebarApps = findViewById(R.id.summary_sidebar_apps);
    }

    private void setupListeners() {
        mCardMasterSwitch.setOnClickListener(v -> {
            boolean newState = !mSwitchMaster.isChecked();
            mSwitchMaster.setChecked(newState);
            GameSpacePreferences.get(this).edit()
                    .putBoolean(GameSpacePreferences.KEY_ENABLED, newState)
                    .apply();
        });

        mRowEdgePosition.setOnClickListener(v -> showEdgePositionDialog());

        mRowFpsMeter.setOnClickListener(v -> {
            boolean newState = !mSwitchFpsMeter.isChecked();
            mSwitchFpsMeter.setChecked(newState);
            GameSpacePreferences.get(this).edit()
                    .putBoolean(GameSpacePreferences.KEY_FPS_METER_ENABLED, newState)
                    .apply();
        });

        mRowSystemMeter.setOnClickListener(v -> {
            boolean newState = !mSwitchSystemMeter.isChecked();
            mSwitchSystemMeter.setChecked(newState);
            GameSpacePreferences.get(this).edit()
                    .putBoolean(GameSpacePreferences.KEY_SYSTEM_METER_ENABLED, newState)
                    .apply();
        });

        mRowResetMeters.setOnClickListener(v -> {
            GameSpacePreferences.resetMeterPositions(this);
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
            GameSpaceService.requestStart(this);
        });

        mRowCompactNotifications.setOnClickListener(v -> {
            boolean newState = !mSwitchCompactNotifications.isChecked();
            mSwitchCompactNotifications.setChecked(newState);
            GameSpacePreferences.get(this).edit()
                    .putBoolean(GameSpacePreferences.KEY_COMPACT_NOTIFICATIONS, newState)
                    .apply();
        });

        mRowCallHandling.setOnClickListener(v -> showCallHandlingDialog());

        mRowGameLibrary.setOnClickListener(v -> launchPicker(AppSelectionActivity.MODE_LIBRARY));
        mRowSidebarApps.setOnClickListener(v -> launchPicker(AppSelectionActivity.MODE_SIDEBAR));
    }

    @Override
    protected void onResume() {
        super.onResume();
        GameSpacePreferences.get(this).registerOnSharedPreferenceChangeListener(this);
        updateUiState();
        if (GameSpacePreferences.isEnabled(this)) {
            GameSpaceService.requestStart(this);
        }
    }

    @Override
    protected void onPause() {
        GameSpacePreferences.get(this).unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateUiState();
        if (GameSpacePreferences.KEY_ENABLED.equals(key)) {
            if (GameSpacePreferences.isEnabled(this)) {
                GameSpaceService.requestStart(this);
            } else {
                GameSpaceService.requestStop(this);
            }
            return;
        }
        GameSpaceService.requestStart(this);
    }

    private void updateUiState() {
        boolean enabled = GameSpacePreferences.isEnabled(this);
        mSwitchMaster.setChecked(enabled);
        mSwitchFpsMeter.setChecked(GameSpacePreferences.isFpsMeterEnabled(this));
        mSwitchSystemMeter.setChecked(GameSpacePreferences.isSystemMeterEnabled(this));
        mSwitchCompactNotifications.setChecked(GameSpacePreferences.isCompactNotificationsEnabled(this));

        // Edge position summary
        String edgePos = GameSpacePreferences.getEdgePosition(this);
        String[] edgeValues = getResources().getStringArray(R.array.edge_position_values);
        String[] edgeEntries = getResources().getStringArray(R.array.edge_position_entries);
        int edgeIndex = findIndex(edgeValues, edgePos);
        mSummaryEdgePosition.setText(edgeIndex >= 0 ? edgeEntries[edgeIndex] : edgePos);

        // Call handling summary
        String callMode = GameSpacePreferences.getCallHandlingMode(this);
        String[] callValues = getResources().getStringArray(R.array.call_handling_values);
        String[] callEntries = getResources().getStringArray(R.array.call_handling_entries);
        int callIndex = findIndex(callValues, callMode);
        mSummaryCallHandling.setText(callIndex >= 0 ? callEntries[callIndex] : callMode);

        // App selection counts
        Set<String> libraryPackages = GameSpacePreferences.getManualLibraryPackages(this);
        Set<String> sidebarPackages = GameSpacePreferences.getSidebarPackages(this);
        mSummaryGameLibrary.setText(getString(R.string.selector_count_summary, libraryPackages.size()));
        mSummarySidebarApps.setText(getString(R.string.selector_count_summary, sidebarPackages.size()));
    }

    private void showEdgePositionDialog() {
        String currentPos = GameSpacePreferences.getEdgePosition(this);
        String[] entries = getResources().getStringArray(R.array.edge_position_entries);
        String[] values = getResources().getStringArray(R.array.edge_position_values);
        int checkedItem = Math.max(0, findIndex(values, currentPos));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edge_position_title)
                .setSingleChoiceItems(entries, checkedItem, (dialog, which) -> {
                    GameSpacePreferences.get(this).edit()
                            .putString(GameSpacePreferences.KEY_EDGE_POSITION, values[which])
                            .apply();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCallHandlingDialog() {
        String currentMode = GameSpacePreferences.getCallHandlingMode(this);
        String[] entries = getResources().getStringArray(R.array.call_handling_entries);
        String[] values = getResources().getStringArray(R.array.call_handling_values);
        int checkedItem = Math.max(0, findIndex(values, currentMode));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.call_handling_title)
                .setSingleChoiceItems(entries, checkedItem, (dialog, which) -> {
                    GameSpacePreferences.get(this).edit()
                            .putString(GameSpacePreferences.KEY_CALL_HANDLING_MODE, values[which])
                            .apply();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void launchPicker(@NonNull String mode) {
        Intent intent = new Intent(this, AppSelectionActivity.class);
        intent.putExtra(AppSelectionActivity.EXTRA_MODE, mode);
        startActivity(intent);
    }

    private static int findIndex(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
