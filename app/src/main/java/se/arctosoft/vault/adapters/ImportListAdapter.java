/*
 * Valv-Android
 * Copyright (C) 2024 Arctosoft AB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import se.arctosoft.vault.R;
import se.arctosoft.vault.adapters.viewholders.ImportListViewHolder;
import se.arctosoft.vault.databinding.AdapterImportListItemBinding;
import se.arctosoft.vault.utils.Dialogs;

public class ImportListAdapter extends RecyclerView.Adapter<ImportListViewHolder> {
    private final List<String> names;
    private final Context context;
    private final String firstPosName;
    private Dialogs.IOnPositionSelected onPositionSelected;

    public ImportListAdapter(List<String> names, Context context, String firstPosName) {
        this.names = names;
        this.context = context;
        this.firstPosName = firstPosName;
    }

    public void setOnPositionSelected(Dialogs.IOnPositionSelected onPositionSelected) {
        this.onPositionSelected = onPositionSelected;
    }

    @NonNull
    @Override
    public ImportListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterImportListItemBinding binding = AdapterImportListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ImportListViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ImportListViewHolder holder, int position) {
        if (position == 0 && firstPosName != null) {
            holder.binding.text.setText(context.getString(R.string.bottom_modal_directory_current, firstPosName));
        } else {
            holder.binding.text.setText(context.getString(R.string.bottom_modal_directory_item, names.get(position)));
        }
        holder.binding.text.setOnClickListener(v -> onPositionSelected.onSelected(position));
    }

    @Override
    public int getItemCount() {
        return names.size();
    }
}
