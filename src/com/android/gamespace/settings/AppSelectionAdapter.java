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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.gamespace.R;
import com.android.gamespace.data.AppEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AppSelectionAdapter extends RecyclerView.Adapter<AppSelectionAdapter.ViewHolder> {

    interface SelectionListener {
        void onSelectionChanged(Set<String> selectedPackages);
    }

    private final List<AppEntry> mApps;
    private final Set<String> mSelectedPackages;
    private final SelectionListener mSelectionListener;

    AppSelectionAdapter(List<AppEntry> apps, Set<String> selectedPackages,
            SelectionListener selectionListener) {
        mApps = apps;
        mSelectedPackages = new HashSet<>(selectedPackages);
        mSelectionListener = selectionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.app_selection_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppEntry entry = mApps.get(position);
        holder.icon.setImageDrawable(entry.getIcon());
        holder.title.setText(entry.getLabel());
        holder.summary.setText(entry.isAutoDetectedGame()
                ? itemViewContext(holder).getString(R.string.selector_auto_detected_game)
                : entry.getPackageName());

        boolean checked = mSelectedPackages.contains(entry.getPackageName());
        holder.checkbox.setChecked(checked);

        View.OnClickListener clickListener = v -> {
            if (mSelectedPackages.contains(entry.getPackageName())) {
                mSelectedPackages.remove(entry.getPackageName());
            } else {
                mSelectedPackages.add(entry.getPackageName());
            }
            int positionInAdapter = holder.getBindingAdapterPosition();
            if (positionInAdapter != RecyclerView.NO_POSITION) {
                notifyItemChanged(positionInAdapter);
            }
            mSelectionListener.onSelectionChanged(new HashSet<>(mSelectedPackages));
        };
        holder.itemView.setOnClickListener(clickListener);
        holder.checkbox.setOnClickListener(clickListener);
    }

    @Override
    public int getItemCount() {
        return mApps.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView summary;
        final CheckBox checkbox;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            title = itemView.findViewById(R.id.title);
            summary = itemView.findViewById(R.id.summary);
            checkbox = itemView.findViewById(R.id.checkbox);
        }
    }

    private static android.content.Context itemViewContext(ViewHolder holder) {
        return holder.itemView.getContext();
    }
}
