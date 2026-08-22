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

import static android.content.pm.ApplicationInfo.CATEGORY_GAME;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.gamespace.R;
import com.android.gamespace.data.AppEntry;
import com.android.gamespace.data.GameSpacePreferences;
import com.android.gamespace.runtime.GameSpaceService;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppSelectionActivity extends FragmentActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_LIBRARY = "library";
    public static final String MODE_SIDEBAR = "sidebar";

    private final Set<String> mSelectedPackages = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(true);
        setContentView(R.layout.app_selection_activity);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!MODE_LIBRARY.equals(mode) && !MODE_SIDEBAR.equals(mode)) {
            mode = MODE_LIBRARY;
        }

        TextView subtitle = findViewById(R.id.subtitle);
        TextView emptyState = findViewById(R.id.empty_state);
        RecyclerView recyclerView = findViewById(R.id.app_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (MODE_LIBRARY.equals(mode)) {
            setTitle(R.string.selector_library_title);
            if (toolbar != null) {
                toolbar.setTitle(R.string.selector_library_title);
            }
            subtitle.setText(R.string.selector_library_subtitle);
            mSelectedPackages.addAll(GameSpacePreferences.getManualLibraryPackages(this));
        } else {
            setTitle(R.string.selector_sidebar_title);
            if (toolbar != null) {
                toolbar.setTitle(R.string.selector_sidebar_title);
            }
            subtitle.setText(R.string.selector_sidebar_subtitle);
            mSelectedPackages.addAll(GameSpacePreferences.getSidebarPackages(this));
        }

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        List<AppEntry> apps = loadLaunchableApps();
        emptyState.setVisibility(apps.isEmpty() ? TextView.VISIBLE : TextView.GONE);
        recyclerView.setAdapter(new AppSelectionAdapter(apps, mSelectedPackages, selected -> {
            mSelectedPackages.clear();
            mSelectedPackages.addAll(selected);
        }));
    }

    @Override
    protected void onPause() {
        persistSelection();
        super.onPause();
    }

    private void persistSelection() {
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_SIDEBAR.equals(mode)) {
            GameSpacePreferences.setSidebarPackages(this, mSelectedPackages);
        } else {
            GameSpacePreferences.setManualLibraryPackages(this, mSelectedPackages);
        }
        GameSpaceService.requestStart(this);
    }

    private List<AppEntry> loadLaunchableApps() {
        PackageManager packageManager = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                launcherIntent, PackageManager.ResolveInfoFlags.of(0));
        Collator collator = Collator.getInstance(Locale.getDefault());
        resolveInfos.sort((left, right) -> collator.compare(
                left.loadLabel(packageManager), right.loadLabel(packageManager)));

        List<AppEntry> apps = new ArrayList<>(resolveInfos.size());
        Set<String> seenPackages = new HashSet<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo == null) {
                continue;
            }
            String packageName = resolveInfo.activityInfo.packageName;
            if (getPackageName().equals(packageName) || !seenPackages.add(packageName)) {
                continue;
            }
            ApplicationInfo appInfo = resolveInfo.activityInfo.applicationInfo;
            boolean autoDetectedGame = appInfo != null && appInfo.category == CATEGORY_GAME;
            apps.add(new AppEntry(
                    packageName,
                    resolveInfo.loadLabel(packageManager),
                    resolveInfo.loadIcon(packageManager),
                    autoDetectedGame));
        }
        return apps;
    }
}
