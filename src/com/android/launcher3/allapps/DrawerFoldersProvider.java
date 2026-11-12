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
package com.android.launcher3.allapps;

import android.widget.FrameLayout;

import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.util.ComponentKey;

import java.util.List;
import java.util.Set;

/**
 * Supplies the all apps list with the folders that are shown in the app drawer. The folders are
 * regular {@link FolderInfo}s whose container is
 * {@link com.android.launcher3.LauncherSettings.Favorites#CONTAINER_ALL_APPS}.
 */
public interface DrawerFoldersProvider {

    /** Returns the folders to show in the drawer, in display order. Never returns empty folders. */
    List<FolderInfo> getDrawerFolders();

    /**
     * Returns the keys of every app which lives inside a drawer folder. These apps are not shown
     * in the top level of the drawer.
     */
    Set<ComponentKey> getFolderMemberKeys();

    /** Attaches the (cached) {@link com.android.launcher3.folder.FolderIcon} of {@code info}. */
    void bindFolderIcon(FolderInfo info, FrameLayout host);
}
