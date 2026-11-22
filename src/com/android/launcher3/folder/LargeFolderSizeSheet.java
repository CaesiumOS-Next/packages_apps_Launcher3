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

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.FolderInfo;

/**
 * Bottom sheet for choosing the size of a home screen folder. It shows a live preview of the
 * selected size; nothing changes on the home screen until the check mark is tapped.
 */
public final class LargeFolderSizeSheet {

    private static final int[] SIZES = {
            FolderInfo.LARGE_SIZE_REGULAR,
            FolderInfo.LARGE_SIZE_ENLARGED,
            FolderInfo.LARGE_SIZE_XXL,
    };
    private static final int[] SIZE_LABELS = {
            R.string.large_folder_size_regular,
            R.string.large_folder_size_enlarged,
            R.string.large_folder_size_xxl,
    };

    private LargeFolderSizeSheet() { }

    /** Shows the sheet for the given folder. */
    public static void show(Launcher launcher, FolderIcon icon) {
        FolderInfo info = icon.mInfo;
        Dialog dialog = new Dialog(launcher, R.style.LargeFolderSizeDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        int[] selected = {info.getLargeFolderSize()};
        int pad = launcher.getResources().getDimensionPixelSize(R.dimen.large_folder_sheet_padding);

        LinearLayout root = new LinearLayout(launcher);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(launcher.getColor(R.color.materialColorSurfaceContainer));
        float r = dp(launcher, 28);
        bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        root.setBackground(bg);

        // Header: cancel, title, apply.
        LinearLayout header = new LinearLayout(launcher);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView cancel = headerButton(launcher, R.drawable.ic_remove_no_shadow,
                R.string.large_folder_cancel);
        ImageView apply = headerButton(launcher, R.drawable.ic_check_sheet,
                R.string.large_folder_apply);
        apply.setColorFilter(launcher.getColor(R.color.materialColorPrimary));
        TextView title = new TextView(launcher);
        CharSequence folderTitle = info.title;
        title.setText(TextUtils.isEmpty(folderTitle)
                ? launcher.getString(R.string.large_folder_size_title) : folderTitle);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(launcher.getColor(R.color.materialColorOnSurface));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(cancel);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(apply);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Live preview.
        PreviewView preview = new PreviewView(launcher, selected[0],
                info.getContents().size());
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                launcher.getResources().getDimensionPixelSize(
                        R.dimen.large_folder_preview_height)));

        // Size tiles.
        LinearLayout tiles = new LinearLayout(launcher);
        tiles.setGravity(Gravity.CENTER);
        SizeTile[] tileViews = new SizeTile[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            final int size = SIZES[i];
            LinearLayout column = new LinearLayout(launcher);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            SizeTile tile = new SizeTile(launcher, size);
            tile.setSelected(size == selected[0]);
            tileViews[i] = tile;
            int tileSize = launcher.getResources().getDimensionPixelSize(
                    R.dimen.large_folder_tile_size);
            column.addView(tile, new LinearLayout.LayoutParams(tileSize, tileSize));
            TextView label = new TextView(launcher);
            label.setText(SIZE_LABELS[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            label.setTextColor(launcher.getColor(R.color.materialColorOnSurface));
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, (int) dp(launcher, 8), 0, 0);
            column.addView(label);
            column.setOnClickListener(v -> {
                selected[0] = size;
                for (SizeTile t : tileViews) {
                    t.setSelected(t.mSize == size);
                }
                preview.setSize(size);
            });
            tiles.addView(column, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams tilesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tilesLp.topMargin = (int) dp(launcher, 8);
        root.addView(tiles, tilesLp);

        cancel.setOnClickListener(v -> dialog.dismiss());
        apply.setOnClickListener(v -> {
            dialog.dismiss();
            LargeFolderResizer.resize(launcher, icon, selected[0]);
        });

        dialog.setContentView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
        dialog.show();
    }

    private static ImageView headerButton(Context context, int iconRes, int descriptionRes) {
        ImageView button = new ImageView(context);
        button.setImageResource(iconRes);
        button.setContentDescription(context.getString(descriptionRes));
        int p = (int) dp(context, 12);
        button.setPadding(p, p, p, p);
        button.setLayoutParams(new LinearLayout.LayoutParams((int) dp(context, 48),
                (int) dp(context, 48)));
        return button;
    }

    private static float dp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    /** Draws a schematic of a folder with the chosen size and as many icons as it has items. */
    private static final class PreviewView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final int mItemCount;
        private float mDrawSize;
        private int mTargetSize;
        private ValueAnimator mAnimator;

        PreviewView(Context context, int size, int itemCount) {
            super(context);
            mItemCount = itemCount;
            mDrawSize = mTargetSize = size;
        }

        void setSize(int size) {
            if (size == mTargetSize) return;
            mTargetSize = size;
            if (mAnimator != null) mAnimator.cancel();
            mAnimator = ValueAnimator.ofFloat(mDrawSize, size);
            mAnimator.setDuration(260);
            mAnimator.setInterpolator(new DecelerateInterpolator());
            mAnimator.addUpdateListener(a -> {
                mDrawSize = (float) a.getAnimatedValue();
                invalidate();
            });
            mAnimator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (mAnimator != null) mAnimator.cancel();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // One cell is a third of the available height, so XXL fills the preview.
            float cell = Math.min(getHeight() / 3f, getWidth() / 3f);
            float side = cell * Math.max(mDrawSize, 1f);
            float left = (getWidth() - side) / 2f;
            float top = (getHeight() - side) / 2f;
            boolean large = mTargetSize > FolderInfo.LARGE_SIZE_REGULAR;

            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(getContext().getColor(R.color.materialColorSurfaceContainerHighest));
            float radius = side * 0.22f;
            mRect.set(left, top, left + side, top + side);
            canvas.drawRoundRect(mRect, radius, radius, mPaint);

            int n = large ? mTargetSize : 2;
            int slots = n * n;
            // Regular and large folders both show up to slots-1 icons plus the overflow preview.
            boolean overflow = mItemCount > slots;
            int shown = large ? (overflow ? slots - 1 : Math.min(mItemCount, slots))
                    : Math.min(mItemCount, slots);
            float slot = side / n;
            float iconSize = slot * 0.56f;
            mPaint.setColor(getContext().getColor(R.color.materialColorOnSurfaceVariant));
            mPaint.setAlpha(150);
            for (int i = 0; i < Math.max(shown, 1); i++) {
                if (i >= shown) break;
                float cx = left + (i % n + 0.5f) * slot;
                float cy = top + (i / n + 0.5f) * slot;
                mRect.set(cx - iconSize / 2, cy - iconSize / 2,
                        cx + iconSize / 2, cy + iconSize / 2);
                canvas.drawRoundRect(mRect, iconSize * 0.3f, iconSize * 0.3f, mPaint);
            }
            if (large && overflow) {
                int i = slots - 1;
                float cx = left + (i % n + 0.5f) * slot;
                float cy = top + (i / n + 0.5f) * slot;
                mPaint.setAlpha(255);
                mPaint.setColor(getContext().getColor(R.color.materialColorSurfaceContainer));
                canvas.drawCircle(cx, cy, iconSize * 0.62f, mPaint);
                mPaint.setColor(getContext().getColor(R.color.materialColorOnSurfaceVariant));
                mPaint.setAlpha(150);
                for (int d = -1; d <= 1; d++) {
                    canvas.drawCircle(cx + d * iconSize * 0.3f, cy, iconSize * 0.07f, mPaint);
                }
            }
        }
    }

    /** A selectable tile that outlines the footprint of one size. */
    private static final class SizeTile extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        final int mSize;

        SizeTile(Context context, int size) {
            super(context);
            mSize = size;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float radius = dp(getContext(), 16);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(getContext().getColor(R.color.materialColorSurfaceContainerHighest));
            mRect.set(0, 0, w, h);
            canvas.drawRoundRect(mRect, radius, radius, mPaint);

            // A mini grid whose filled area is proportional to the size.
            float unit = w / 3f;
            float shape = Math.min(mSize, 3) * unit * 0.85f;
            if (mSize == FolderInfo.LARGE_SIZE_REGULAR) {
                shape = unit * 0.85f;
            }
            float left = (w - shape) / 2f;
            float top = (h - shape) / 2f;
            mPaint.setColor(getContext().getColor(R.color.materialColorOnSurfaceVariant));
            mPaint.setAlpha(isSelected() ? 255 : 130);
            mRect.set(left, top, left + shape, top + shape);
            canvas.drawRoundRect(mRect, shape * 0.25f, shape * 0.25f, mPaint);

            if (isSelected()) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(dp(getContext(), 2.5f));
                mPaint.setColor(getContext().getColor(R.color.materialColorPrimary));
                mPaint.setAlpha(255);
                float inset = mPaint.getStrokeWidth() / 2f;
                mRect.set(inset, inset, w - inset, h - inset);
                canvas.drawRoundRect(mRect, radius, radius, mPaint);
            }
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            invalidate();
        }
    }
}
