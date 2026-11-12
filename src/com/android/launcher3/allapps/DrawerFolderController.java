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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherState.ALL_APPS;

import android.graphics.Point;
import android.graphics.Rect;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Owns the folders shown in the app drawer and the drag and drop behavior of the drawer.
 *
 * Drawer folders are the same {@link FolderInfo}, {@link FolderIcon} and {@link Folder} classes the
 * home screen uses. They are stored in the favorites table with
 * {@link com.android.launcher3.LauncherSettings.Favorites#CONTAINER_ALL_APPS} as their container,
 * and their children point at the folder id, so persistence goes through the regular ModelWriter.
 *
 * Drag behavior: a drag that starts in the drawer stays in the drawer. This class is then the drop
 * target for everything below the "home zone" (the top part of the display). Dropping an app on
 * another app creates a folder, dropping it on a folder adds it. When the drag enters the home zone
 * the drag is handed over to the workspace, which continues the normal home screen flow.
 */
public class DrawerFolderController implements DropTarget, DragController.DragListener,
        DrawerFoldersProvider {

    private static final float HOVER_SCALE = 1.15f;
    /** Half size of the area (relative to the cell) in which a hover counts as "on the icon". */
    private static final float HOVER_HIT_FRACTION = 0.35f;

    private final Launcher mLauncher;
    private final List<FolderInfo> mFolders = new ArrayList<>();
    private final SparseArray<FolderIcon> mIcons = new SparseArray<>();
    private final float[] mTmpCenter = new float[2];
    private final int[] mTmpPoint = new int[2];

    private boolean mDragInDrawer = false;
    private DragOptions mDragOptions;

    private View mHoverView;
    private FolderIcon mHoverFolderIcon;
    private AppInfo mHoverApp;

    public DrawerFolderController(Launcher launcher) {
        mLauncher = launcher;
    }

    /** Whether drawer folders are enabled on this device. */
    public boolean isEnabled() {
        return mLauncher.getResources().getBoolean(R.bool.config_enableAllAppsFolders);
    }

    // ---------------------------------------------------------------------------------------
    // Model state
    // ---------------------------------------------------------------------------------------

    /** Replaces the folders with the ones from a freshly loaded model. Call on the UI thread. */
    public void syncFromModel(List<FolderInfo> folders) {
        mFolders.clear();
        mFolders.addAll(folders);
        mIcons.clear();
        notifyChanged();
    }

    /** Called when apps are removed from the device. */
    public void onItemsRemoved(Predicate<ItemInfo> matcher) {
        boolean changed = false;
        for (FolderInfo folder : new ArrayList<>(mFolders)) {
            if (folder.getContents().removeIf(matcher)) {
                changed = true;
                Folder open = Folder.getOpen(mLauncher);
                if (open != null && open.getInfo() == folder) {
                    open.close(false);
                }
                if (folder.getContents().size() <= 1) {
                    dissolve(folder);
                }
            }
        }
        if (changed) {
            notifyChanged();
        }
    }

    /**
     * Called by {@link com.android.launcher3.folder.LauncherDelegate} when a drawer folder is left
     * with a single item. The remaining app goes back to the top level of the drawer.
     */
    public void dissolveFolder(Folder folder) {
        dissolve(folder.getInfo());
        notifyChanged();
    }

    /** Whether the folder lives in the app drawer (as opposed to the home screen). */
    public static boolean isDrawerFolder(FolderInfo info) {
        return info != null && info.container == CONTAINER_ALL_APPS;
    }

    private void dissolve(FolderInfo info) {
        mLauncher.getModelWriter().deleteCollectionAndContentsFromDatabase(info);
        mFolders.remove(info);
        mIcons.remove(info.id);
    }

    private void notifyChanged() {
        mLauncher.getAppsView().onDrawerFoldersChanged();
    }

    // ---------------------------------------------------------------------------------------
    // DrawerFoldersProvider
    // ---------------------------------------------------------------------------------------

    @Override
    public List<FolderInfo> getDrawerFolders() {
        List<FolderInfo> result = new ArrayList<>();
        for (FolderInfo folder : mFolders) {
            if (!folder.getContents().isEmpty()) {
                result.add(folder);
            }
        }
        result.sort(Comparator
                .comparing((FolderInfo f) -> f.title == null ? "" : f.title.toString(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(f -> f.id));
        return result;
    }

    @Override
    public Set<ComponentKey> getFolderMemberKeys() {
        Set<ComponentKey> keys = new HashSet<>();
        for (FolderInfo folder : mFolders) {
            for (WorkspaceItemInfo item : folder.getAppContents()) {
                ComponentKey key = item.getComponentKey();
                if (key != null) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    @Override
    public void bindFolderIcon(FolderInfo info, FrameLayout host) {
        FolderIcon icon = mIcons.get(info.id);
        if (icon == null) {
            View inflated = mLauncher.getItemInflater().inflateItem(info, host);
            if (!(inflated instanceof FolderIcon)) {
                return;
            }
            icon = (FolderIcon) inflated;
            mIcons.put(info.id, icon);
        }
        if (icon.getParent() != host) {
            if (icon.getParent() instanceof ViewGroup) {
                ((ViewGroup) icon.getParent()).removeView(icon);
            }
            host.removeAllViews();
            host.addView(icon, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
    }

    // ---------------------------------------------------------------------------------------
    // Drag session
    // ---------------------------------------------------------------------------------------

    /**
     * Called by the workspace when a drag starts. Returns true if the drag should stay in the
     * drawer for now, in which case the workspace must not leave the all apps state.
     */
    public boolean beginDrag(DragObject d, DragOptions options) {
        mDragInDrawer = isDrawerDrag(d);
        mDragOptions = options;
        return mDragInDrawer;
    }

    /** True while a drag started in the drawer has not been handed over to the workspace. */
    public boolean isDragInDrawer() {
        return mDragInDrawer;
    }

    private boolean isDrawerDrag(DragObject d) {
        if (!isEnabled() || !mLauncher.isInState(ALL_APPS)) {
            return false;
        }
        ActivityAllAppsContainerView<?> apps = mLauncher.getAppsView();
        if (d.dragSource == apps) {
            return d.dragInfo instanceof AppInfo && apps.supportsDrawerFolders();
        }
        return d.dragSource instanceof Folder
                && isDrawerFolder(((Folder) d.dragSource).getInfo());
    }

    @Override
    public void onDragStart(DragObject dragObject, DragOptions options) {
        // The workspace decides whether the drag stays in the drawer, see beginDrag().
    }

    @Override
    public void onDragEnd() {
        clearHover();
        mDragInDrawer = false;
        mDragOptions = null;
    }

    private int getHomeZoneBottom() {
        float fraction = mLauncher.getResources()
                .getFraction(R.fraction.all_apps_drag_to_home_zone, 1, 1);
        return Math.round(mLauncher.getDragLayer().getHeight() * fraction);
    }

    private void handOverToWorkspace(DragObject d) {
        mDragInDrawer = false;
        mLauncher.getWorkspace().onDrawerDragHandedOver(d, mDragOptions);
    }

    // ---------------------------------------------------------------------------------------
    // DropTarget
    // ---------------------------------------------------------------------------------------

    @Override
    public boolean isDropEnabled() {
        return mDragInDrawer;
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(mLauncher.getAppsView(), outRect);
        outRect.top = Math.max(outRect.top, getHomeZoneBottom());
    }

    /** Raw DragLayer coordinates are wanted, so there is no drop view. */
    @Override
    public View getDropView() {
        return null;
    }

    @Override
    public void onDragEnter(DragObject d) {
        updateHover(d);
    }

    @Override
    public void onDragOver(DragObject d) {
        updateHover(d);
    }

    @Override
    public void onDragExit(DragObject d) {
        if (d.dragComplete) {
            // The drop is about to happen on this target: keep the hover target for onDrop().
            return;
        }
        clearHover();
        if (!mDragInDrawer) {
            return;
        }
        // d.x/y are already relative to the next target here, so use the raw touch position.
        Point touch = mLauncher.getDragController().getLastTouch();
        Rect appsRect = new Rect();
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(mLauncher.getAppsView(), appsRect);
        if (touch.y < getHomeZoneBottom() || touch.y < appsRect.top) {
            handOverToWorkspace(d);
        }
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        // Drops that do nothing are rejected so that the drag view simply returns. Items taken out
        // of a drawer folder are always accepted, they move back to the top level of the drawer.
        return mHoverView != null || d.dragSource instanceof Folder;
    }

    @Override
    public void onDrop(DragObject d, DragOptions options) {
        FolderIcon hoverFolder = mHoverFolderIcon;
        AppInfo hoverApp = mHoverApp;
        clearHover();

        if (d.dragSource instanceof Folder) {
            if (hoverFolder != null) {
                hoverFolder.onDrop(d, false);
            } else if (d.dragInfo instanceof ItemInfo) {
                // Folder#onDragStart already took the item out of the folder contents.
                mLauncher.getModelWriter().deleteItemFromDatabase(
                        (ItemInfo) d.dragInfo, "drawer folder item dropped in drawer");
            }
        } else if (d.dragInfo instanceof AppInfo) {
            if (hoverFolder != null) {
                hoverFolder.onDrop(d, false);
            } else if (hoverApp != null) {
                createFolder(hoverApp, (AppInfo) d.dragInfo);
            }
        }
        // The list hides apps that are now inside a folder and shows new folders.
        notifyChanged();
    }

    private void createFolder(AppInfo target, AppInfo dragged) {
        FolderInfo info = new FolderInfo();
        mLauncher.getModelWriter().addItemToDatabase(info, CONTAINER_ALL_APPS, 0, 0, 0);
        mFolders.add(info);

        FrameLayout scratchHost = new FrameLayout(mLauncher);
        FolderIcon icon = (FolderIcon) mLauncher.getItemInflater().inflateItem(info, scratchHost);
        mIcons.put(info.id, icon);

        icon.getFolder().addFolderContent(target.makeWorkspaceItem(mLauncher));
        icon.getFolder().addFolderContent(dragged.makeWorkspaceItem(mLauncher));
    }

    // ---------------------------------------------------------------------------------------
    // Hover feedback
    // ---------------------------------------------------------------------------------------

    private void updateHover(DragObject d) {
        View target = null;
        FolderIcon folderIcon = null;
        AppInfo app = null;

        View child = findChildUnderDrag(d);
        if (child instanceof FrameLayout && ((FrameLayout) child).getChildAt(0)
                instanceof FolderIcon) {
            folderIcon = (FolderIcon) ((FrameLayout) child).getChildAt(0);
            target = child;
        } else if (child != null && child.getTag() instanceof AppInfo
                && d.dragInfo instanceof AppInfo && d.dragSource != null
                && !(d.dragSource instanceof Folder)) {
            AppInfo candidate = (AppInfo) child.getTag();
            ComponentKey dragged = ((AppInfo) d.dragInfo).getComponentKey();
            if (dragged == null || !dragged.equals(candidate.getComponentKey())) {
                app = candidate;
                target = child;
            }
        }

        if (target == mHoverView) {
            return;
        }
        clearHover();
        mHoverView = target;
        mHoverFolderIcon = folderIcon;
        mHoverApp = app;
        if (target != null) {
            target.animate().scaleX(HOVER_SCALE).scaleY(HOVER_SCALE).setDuration(120).start();
        }
    }

    private void clearHover() {
        if (mHoverView != null) {
            mHoverView.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }
        mHoverView = null;
        mHoverFolderIcon = null;
        mHoverApp = null;
    }

    /** Returns the drawer cell whose central area contains the visual center of the drag. */
    private View findChildUnderDrag(DragObject d) {
        AllAppsRecyclerView rv = mLauncher.getAppsView().getActiveRecyclerView();
        if (rv == null) {
            return null;
        }
        d.getVisualCenter(mTmpCenter);
        mTmpPoint[0] = Math.round(mTmpCenter[0]);
        mTmpPoint[1] = Math.round(mTmpCenter[1]);
        mLauncher.getDragLayer().mapCoordInSelfToDescendant(rv, mTmpPoint);

        View child = rv.findChildViewUnder(mTmpPoint[0], mTmpPoint[1]);
        if (child == null) {
            return null;
        }
        float dx = Math.abs(mTmpPoint[0] - (child.getLeft() + child.getWidth() / 2f));
        float dy = Math.abs(mTmpPoint[1] - (child.getTop() + child.getHeight() / 2f));
        boolean onIcon = dx <= child.getWidth() * HOVER_HIT_FRACTION
                && dy <= child.getHeight() * HOVER_HIT_FRACTION;
        return onIcon ? child : null;
    }
}
