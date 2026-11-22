/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.folder;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;

import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.model.data.FolderInfo;

/**
 * Changes the size of a home screen folder between a regular 1x1 folder and a large NxN folder,
 * moving it to the nearest free area when it does not fit where it is.
 */
public final class LargeFolderResizer {

    private LargeFolderResizer() { }

    /**
     * Resizes the folder to {@code newSize} cells per side.
     *
     * @return false (after telling the user) if there is no room on the page.
     */
    public static boolean resize(Launcher launcher, FolderIcon icon, int newSize) {
        FolderInfo info = icon.mInfo;
        if (!FolderInfo.isValidLargeSize(newSize) || info.container != CONTAINER_DESKTOP) {
            return false;
        }
        if (!(icon.getParent() instanceof ShortcutAndWidgetContainer container)
                || !(container.getParent() instanceof CellLayout cl)
                || !(icon.getLayoutParams() instanceof CellLayoutLayoutParams lp)) {
            return false;
        }
        if (newSize == info.getLargeFolderSize()) {
            return true;
        }

        cl.markCellsAsUnoccupiedForView(icon);
        int[] origin = findNearestVacantOrigin(cl, lp.getCellX(), lp.getCellY(), newSize);
        if (origin == null) {
            cl.markCellsAsOccupiedForView(icon);
            Toast.makeText(launcher, R.string.large_folder_no_space, Toast.LENGTH_SHORT).show();
            return false;
        }

        lp.setCellX(origin[0]);
        lp.setCellY(origin[1]);
        lp.cellHSpan = newSize;
        lp.cellVSpan = newSize;
        cl.markCellsAsOccupiedForView(icon);

        int screenId = launcher.getWorkspace().getCellLayoutId(cl);
        launcher.getModelWriter().modifyItemInDatabase(info, CONTAINER_DESKTOP, screenId,
                origin[0], origin[1], newSize, newSize);
        // The writer sets these too, but the in-memory model must be right even if it is delayed.
        info.spanX = newSize;
        info.spanY = newSize;

        icon.onLargeSizeChanged();
        icon.setAlpha(0f);
        icon.animate().alpha(1f).setDuration(220).start();
        return true;
    }

    /**
     * Returns the top-left cell of the vacant {@code size}x{@code size} area closest to
     * ({@code cellX}, {@code cellY}), or null if the page has none.
     */
    @Nullable
    private static int[] findNearestVacantOrigin(CellLayout cl, int cellX, int cellY, int size) {
        int best = Integer.MAX_VALUE;
        int[] result = null;
        for (int y = 0; y + size <= cl.getCountY(); y++) {
            for (int x = 0; x + size <= cl.getCountX(); x++) {
                if (!cl.isRegionVacant(x, y, size, size)) continue;
                int distance = Math.abs(x - cellX) + Math.abs(y - cellY);
                if (distance < best) {
                    best = distance;
                    result = new int[] {x, y};
                }
            }
        }
        return result;
    }
}
