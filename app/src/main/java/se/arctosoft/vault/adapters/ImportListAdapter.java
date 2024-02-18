/*
 * Valv-Android
 * Copyright (C) 2023 Arctosoft AB
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

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import se.arctosoft.vault.adapters.viewholders.ImportListViewHolder;
import se.arctosoft.vault.databinding.AdapterImportListItemBinding;
import se.arctosoft.vault.utils.Dialogs;

public class ImportListAdapter extends RecyclerView.Adapter<ImportListViewHolder> {
    private final List<String> names;
    private final Dialogs.IOnPositionSelected onPositionSelected;

    public ImportListAdapter(List<String> names, Dialogs.IOnPositionSelected onPositionSelected) {
        this.names = names;
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
        holder.binding.text.setText(names.get(position));
        holder.binding.text.setOnClickListener(v -> onPositionSelected.onSelected(position));
    }

    @Override
    public int getItemCount() {
        return names.size();
    }
}
