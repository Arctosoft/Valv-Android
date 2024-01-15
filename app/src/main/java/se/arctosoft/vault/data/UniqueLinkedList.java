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

package se.arctosoft.vault.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;

public class UniqueLinkedList<E> extends LinkedList<E> {
    private final HashSet<E> keys = new HashSet<>();

    @Override
    public boolean add(E e) {
        if (keys.add(e)) {
            return super.add(e);
        }
        return false;
    }

    @Override
    public void add(int index, E e) {
        if (keys.add(e)) {
            super.add(index, e);
        }
    }

    @Override
    public boolean contains(@Nullable Object o) {
        return keys.contains(o);
    }

    @Override
    public boolean remove(@Nullable Object o) {
        if (keys.remove(o)) {
            return super.remove(o);
        }
        return false;
    }

    @Override
    public void clear() {
        keys.clear();
        super.clear();
    }

    @Override
    public E remove(int index) {
        E e = get(index);
        keys.remove(e);
        return super.remove(index);
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> c) {
        keys.removeAll(c);
        return super.removeAll(c);
    }

}