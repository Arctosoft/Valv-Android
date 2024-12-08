/*
 * Valv-Android
 * Copyright (c) 2024 Arctosoft AB.
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
 * You should have received a copy of the GNU General Public License along with this program.  If not, see https://www.gnu.org/licenses/.
 */

package se.arctosoft.vault.data;

public class ProgressData {
    private final int total, progress, progressPercentage;
    private final String doneMB, totalMB;

    public ProgressData(int total, int progress, int progressPercentage, String doneMB, String totalMB) {
        this.total = total;
        this.progress = progress;
        this.progressPercentage = progressPercentage;
        this.doneMB = doneMB;
        this.totalMB = totalMB;
    }

    public int getTotal() {
        return total;
    }

    public int getProgress() {
        return progress;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public String getDoneMB() {
        return doneMB;
    }

    public String getTotalMB() {
        return totalMB;
    }
}
