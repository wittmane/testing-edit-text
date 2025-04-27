/*
 * Copyright (C) 2025 Eli Wittman
 * Copyright (C) 2006 The Android Open Source Project
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

package com.wittmane.testingedittext.widget;

import static android.widget.LinearLayout.SHOW_DIVIDER_BEGINNING;
import static android.widget.LinearLayout.SHOW_DIVIDER_END;
import static android.widget.LinearLayout.SHOW_DIVIDER_MIDDLE;
import static android.widget.LinearLayout.SHOW_DIVIDER_NONE;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.util.ResourceUtils;
import com.wittmane.testingedittext.util.ViewUtils;

import java.util.ArrayList;

/**
 * A layout that arranges other views either horizontally in a single column or vertically in a
 * single row, similar to {@link LinearLayout}, that also supports scrolling and pinning the first
 * and last views to the edge of the layout.
 */
public class LinearScrollLayout extends WrappedView<LinearLayout> {
    private static final String TAG = LinearScrollLayout.class.getSimpleName();

    private final LinearLayout mPinnedFirstLayout = new LinearLayout(getContext());
    private final LinearLayout mScrollWrapperLayout = new LinearLayout(getContext());
    private FrameLayout mScrollView;
    private final LinearLayout mScrolledLayout = new LinearLayout(getContext());
    private final LinearLayout mPinnedLastLayout = new LinearLayout(getContext());

    private final ArrayList<View> mManagedChildren = new ArrayList<>();

    private boolean mShouldPinFirst = false;
    private boolean mShouldPinLast = false;
    private boolean mCanPin = true;

    private int mContentMinLength;

    private int mPaddingStart;
    private int mPaddingTop;
    private int mPaddingEnd;
    private int mPaddingBottom;
    private boolean mPaddingIsRelative;

    private int mGravity;

    private int mShowDividers;
    private Drawable mDivider;
    private int mDividerPadding;

    public LinearScrollLayout(Context context) {
        super(context);
        init(null, 0, 0);
    }

    public LinearScrollLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0, 0);
    }

    public LinearScrollLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public LinearScrollLayout(Context context, AttributeSet attrs, int defStyleAttr,
                              int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs, defStyleAttr, defStyleRes);
    }

    private void init(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mInternalView = new LinearLayout(getContext());
        addView(mInternalView);
        updateHelperLayouts();
        updateInternalPadding();

        // move the padding to the helper layouts since the padding set in the parent constructor
        // didn't apply to the helper layouts since they weren't instantiated at that point
        if (super.isPaddingRelative()) {
            int paddingStart = super.getPaddingStart();
            int paddingTop = super.getPaddingTop();
            int paddingEnd = super.getPaddingEnd();
            int paddingBottom = super.getPaddingBottom();
            setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom);
            super.setPaddingRelative(0, 0, 0, 0);
        } else {
            int paddingLeft = super.getPaddingLeft();
            int paddingTop = super.getPaddingTop();
            int paddingRight = super.getPaddingRight();
            int paddingBottom = super.getPaddingBottom();
            setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
            super.setPadding(0, 0, 0, 0);
        }

        try (TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.LinearScrollLayout, defStyleAttr, defStyleRes)) {
            setPinFirst(a.getBoolean(R.styleable.LinearScrollLayout_pinFirst, false));
            setPinLast(a.getBoolean(R.styleable.LinearScrollLayout_pinLast, false));

            setContentMinLength(a.getDimensionPixelSize(
                    R.styleable.LinearScrollLayout_contentMinLength,
                    (int) ResourceUtils.dpToPx(30, getContext())));

            setOrientation(
                    a.getInt(R.styleable.LinearScrollLayout_android_orientation,
                            LinearLayout.HORIZONTAL));

            setGravity(a.getInt(R.styleable.LinearScrollLayout_android_gravity, -1));

            setClipToPadding(
                    a.getBoolean(R.styleable.LinearScrollLayout_android_clipToPadding, true));

            setShowDividers(a.getInt(R.styleable.LinearScrollLayout_android_showDividers,
                    SHOW_DIVIDER_NONE));
            setDividerPadding(a.getDimensionPixelSize(
                    R.styleable.LinearScrollLayout_android_dividerPadding, 0));
            setDividerDrawable(a.getDrawable(R.styleable.LinearScrollLayout_android_divider));
        }
    }

    private void updateHelperLayouts() {
        int orientation = getOrientation();

        mPinnedFirstLayout.setOrientation(orientation);
        if (mInternalView.indexOfChild(mPinnedFirstLayout) < 0) {
            mInternalView.addView(mPinnedFirstLayout, getLayoutParams(orientation, false));
        } else {
            mPinnedFirstLayout.setLayoutParams(getLayoutParams(orientation, false));
        }

        if (mScrollView != null) {
            mScrollWrapperLayout.removeView(mScrollView);
            mScrollView.removeView(mScrolledLayout);
        }
        mScrollView = orientation == LinearLayout.VERTICAL
                ? new ScrollView(getContext())
                : new HorizontalScrollView(getContext());
        mScrolledLayout.setOrientation(orientation);
        mScrollView.addView(mScrolledLayout, getLayoutParams(orientation, false));
        mScrollWrapperLayout.addView(mScrollView, getLayoutParams(orientation, false));
        if (mInternalView.indexOfChild(mScrollWrapperLayout) < 0) {
            mScrollWrapperLayout.setOrientation(orientation);
            mInternalView.addView(mScrollWrapperLayout, 1, getLayoutParams(orientation, true));
        } else {
            mScrollWrapperLayout.setOrientation(orientation);
            mScrollWrapperLayout.setLayoutParams(getLayoutParams(orientation, true));
        }

        mPinnedLastLayout.setOrientation(orientation);
        if (mInternalView.indexOfChild(mPinnedLastLayout) < 0) {
            mInternalView.addView(mPinnedLastLayout, 2, getLayoutParams(orientation, false));
        } else {
            mPinnedLastLayout.setLayoutParams(getLayoutParams(orientation, false));
        }
    }

    private static LinearLayout.LayoutParams getLayoutParams(int orientation, boolean fillSpace) {
        LinearLayout.LayoutParams params = orientation == LinearLayout.VERTICAL
                ? new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        fillSpace ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(
                        fillSpace ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
        if (fillSpace) {
            params.weight = 1;
        }
        return params;
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        // (EW) from LinearLayout
        return p instanceof LinearLayout.LayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        // (EW) from LinearLayout
        int orientation = mInternalView.getOrientation();
        if (orientation == LinearLayout.HORIZONTAL) {
            return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        } else if (orientation == LinearLayout.VERTICAL) {
            return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        return null;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        // (EW) from LinearLayout
        return new LinearLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        // (EW) based on LinearLayout
        if (lp instanceof LinearLayout.LayoutParams) {
            return new LinearLayout.LayoutParams((LinearLayout.LayoutParams) lp);
        } else if (lp instanceof MarginLayoutParams) {
            return new LinearLayout.LayoutParams((MarginLayoutParams) lp);
        }
        return new LinearLayout.LayoutParams(lp);
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);
        mInternalView.setLayoutParams(params);
    }

    /**
     * Should the layout be a column or a row.
     * @param orientation Pass {@link LinearLayout#HORIZONTAL} or {@link LinearLayout#VERTICAL}.
     * Default value is {@link LinearLayout#HORIZONTAL}.
     *
     * @attr ref RR.styleable.LinearScrollLayout_android_orientation
     */
    public void setOrientation(int orientation) {
        if (getOrientation() != orientation) {
            mInternalView.setOrientation(orientation);
            if (mPinnedLastLayout != null) {
                updateHelperLayouts();

                mPinnedFirstLayout.requestLayout();
                mScrolledLayout.requestLayout();
                mPinnedLastLayout.requestLayout();
            }
        }
    }

    /**
     * Returns the current orientation.
     *
     * @return either {@link LinearLayout#HORIZONTAL} or {@link LinearLayout#VERTICAL}
     */
    public int getOrientation() {
        return mInternalView.getOrientation();
    }

    /**
     * Describes how the child views are positioned. Defaults to GRAVITY_TOP. If
     * this layout has a VERTICAL orientation, this controls where all the child
     * views are placed if there is extra vertical space. If this layout has a
     * HORIZONTAL orientation, this controls the alignment of the children.
     *
     * @param gravity See {@link android.view.Gravity}
     *
     * @attr ref R.styleable.LinearScrollLayout_android_gravity
     */
    public void setGravity(int gravity) {
        mGravity = gravity;
        mScrollWrapperLayout.setGravity(gravity);
    }

    /**
     * Returns the current gravity. See {@link android.view.Gravity}
     *
     * @return the current gravity.
     * @see #setGravity
     */
    public int getGravity() {
        return mGravity;
    }

    /**
     * Set whether the first view should be pinned to the start of the layout.
     * @param pin Whether the first view should be pinned.
     *
     * @attr ref R.styleable.LinearScrollLayout_pinFirst
     */
    public void setPinFirst(boolean pin) {
        if (mShouldPinFirst != pin) {
            mShouldPinFirst = pin;
            updateContentViewPlacement();
            mPinnedFirstLayout.requestLayout();
            mScrolledLayout.requestLayout();
            if (mShouldPinLast) {
                mPinnedLastLayout.requestLayout();
            }
        }
    }

    /**
     * Get whether the first view should be pinned to the start of the layout. Note that this may be
     * true despite a view may not being pinned due to not having enough space or no view present.
     * @return Whether the first view should be pinned.
     */
    public boolean getPinnedFirst() {
        return mShouldPinFirst;
    }

    /**
     * Set whether the last view should be pinned to the end of the layout.
     * @param pin Whether the last view should be pinned.
     *
     * @attr ref R.styleable.LinearScrollLayout_pinLast
     */
    public void setPinLast(boolean pin) {
        if (mShouldPinLast != pin) {
            mShouldPinLast = pin;
            updateContentViewPlacement();
            if (mShouldPinFirst) {
                mPinnedFirstLayout.requestLayout();
            }
            mScrolledLayout.requestLayout();
            mPinnedLastLayout.requestLayout();
        }
    }

    /**
     * Get whether the last view should be pinned to the end of the layout. Note that this may be
     * true despite a view may not being pinned due to not having enough space or no view present.
     * @return Whether the last view should be pinned.
     */
    public boolean getPinnedLast() {
        return mShouldPinLast;
    }

    /**
     * Set the minimum space (width in horizontal orientation or height in vertical orientation) for
     * the scrolled content. If there isn't enough space with the first and last views pinned, they
     * won't be pinned, and everything will scroll together. If the actual content of the scrolled
     * section is less, then this setting will have no effect.
     * @param contentMinLength The minimum length for the scrolled content.
     *
     * @attr ref R.styleable.LinearScrollLayout_contentMinLength
     */
    public void setContentMinLength(int contentMinLength) {
        if (mContentMinLength != contentMinLength) {
            mContentMinLength = contentMinLength;
            if (mShouldPinFirst) {
                mPinnedFirstLayout.requestLayout();
            }
            mScrolledLayout.requestLayout();
            if (mShouldPinLast) {
                mPinnedLastLayout.requestLayout();
            }
        }
    }

    /**
     * Get the minimum space (width in horizontal orientation or height in vertical orientation) for
     * the scrolled content.
     * @return The minimum length for the scrolled content.
     */
    public int getContentMinLength() {
        return mContentMinLength;
    }

    @Override
    public void setClipToPadding(boolean clipToPadding) {
        super.setClipToPadding(clipToPadding);
        if (mPinnedFirstLayout != null) {
            mPinnedFirstLayout.setClipToPadding(clipToPadding);
        }
        if (mScrolledLayout != null) {
            mScrolledLayout.setClipToPadding(clipToPadding);
        }
        if (mPinnedLastLayout != null) {
            mPinnedLastLayout.setClipToPadding(clipToPadding);
        }
    }

    /**
     * Sets the padding.
     * Note that this technically doesn't set the padding for this view but for the helper layout
     * views this uses and the views may add on the space required to display the scrollbars,
     * depending on the style and visibility of the scrollbars. To get this conceptual padding back,
     * use {@link #getPaddingRelative} instead of {@link #getPaddingLeft}, {@link #getPaddingTop},
     * @link #getPaddingRight}, and {@link #getPaddingBottom}, which get the the actual padding for
     * this view (always 0).
     * @param left the left padding in pixels
     * @param top the top padding in pixels
     * @param right the right padding in pixels
     * @param bottom the bottom padding in pixels
     *
     * @attr ref android.R.styleable#View_padding
     * @attr ref android.R.styleable#View_paddingBottom
     * @attr ref android.R.styleable#View_paddingLeft
     * @attr ref android.R.styleable#View_paddingRight
     * @attr ref android.R.styleable#View_paddingTop
     */
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        if (mPaddingIsRelative || mPaddingStart != left || mPaddingTop != top
                || mPaddingEnd != right || mPaddingBottom != bottom) {
            mPaddingStart = left;
            mPaddingTop = top;
            mPaddingEnd = right;
            mPaddingBottom = bottom;
            mPaddingIsRelative = false;
            updateInternalPadding();
        }
    }

    /**
     * Sets the relative padding.
     * Note that this technically doesn't set the padding for this view but for the helper layout
     * views this uses and the views may add on the space required to display the scrollbars,
     * depending on the style and visibility of the scrollbars. To get this conceptual padding back,
     * use {@link #getPaddingRelative} instead of {@link #getPaddingStart}, {@link #getPaddingTop},
     * @link #getPaddingEnd}, and {@link #getPaddingBottom}, which get the the actual padding for
     * this view (always 0).
     * @param start The start padding in pixels
     * @param top The top padding in pixels
     * @param end The end padding in pixels
     * @param bottom the bottom padding in pixels
     *
     * @attr ref android.R.styleable#View_padding
     * @attr ref android.R.styleable#View_paddingBottom
     * @attr ref android.R.styleable#View_paddingStart
     * @attr ref android.R.styleable#View_paddingEnd
     * @attr ref android.R.styleable#View_paddingTop
     */
    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        if (!mPaddingIsRelative || mPaddingStart != start || mPaddingTop != top
                || mPaddingEnd != end || mPaddingBottom != bottom) {
            mPaddingStart = start;
            mPaddingTop = top;
            mPaddingEnd = end;
            mPaddingBottom = bottom;
            mPaddingIsRelative = true;
            updateInternalPadding();
        }
    }

    /**
     * Get the conceptual padding. {@link #setPadding} technically doesn't set the padding for this
     * view but the helper layout views it uses. This allows getting that padding since
     * {@link #getPaddingLeft}, {@link #getPaddingTop}, {@link #getPaddingRight} and
     * {@link #getPaddingBottom} are kept returning the actual padding for this view (always 0). If
     * there are inset and enabled scrollbars, this value may include the space required to display
     * the scrollbars as well.
     * @return The padding in pixels.
     */
    public Rect getPadding() {
        if (mPinnedFirstLayout == null || mScrolledLayout == null || mPinnedLastLayout == null) {
            return new Rect(0, 0, 0, 0);
        }
        Rect padding = new Rect();
        if (getOrientation() == LinearLayout.VERTICAL) {
            padding.left = mScrolledLayout.getPaddingLeft();
            if (hasFirstPinned()) {
                padding.top = mPinnedFirstLayout.getPaddingTop();
            } else {
                padding.top = mScrolledLayout.getPaddingTop();
            }
            padding.right = mScrolledLayout.getPaddingRight();
            if (hasLastPinned()) {
                padding.bottom = mPinnedLastLayout.getPaddingBottom();
            } else {
                padding.bottom = mScrolledLayout.getPaddingBottom();
            }
        } else {
            if (hasFirstPinned()) {
                padding.left = mPinnedFirstLayout.getPaddingLeft();
            } else {
                padding.left = mScrolledLayout.getPaddingLeft();
            }
            padding.top = mScrolledLayout.getPaddingTop();
            if (hasLastPinned()) {
                padding.right = mPinnedLastLayout.getPaddingRight();
            } else {
                padding.right = mScrolledLayout.getPaddingRight();
            }
        }
        return padding;
    }

    /**
     * Get the conceptual relative padding. {@link #setPadding} technically doesn't set the padding
     * for this view but the helper layout views it uses. This allows getting that padding since
     * {@link #getPaddingStart}, {@link #getPaddingTop}, {@link #getPaddingEnd} and
     * {@link #getPaddingBottom} are kept returning the actual padding for this view (always 0). If
     * there are inset and enabled scrollbars, this value may include the space required to display
     * the scrollbars as well.
     * @return The padding in pixels (note {@link Rect#left} property represents start and
     *         {@link Rect#right} represents end.
     */
    public Rect getPaddingRelative() {
        if (mPinnedFirstLayout == null || mScrolledLayout == null || mPinnedLastLayout == null) {
            return new Rect(0, 0, 0, 0);
        }
        Rect padding = new Rect();
        if (getOrientation() == LinearLayout.VERTICAL) {
            padding.left = mScrolledLayout.getPaddingStart();
            if (hasFirstPinned()) {
                padding.top = mPinnedFirstLayout.getPaddingTop();
            } else {
                padding.top = mScrolledLayout.getPaddingTop();
            }
            padding.right = mScrolledLayout.getPaddingEnd();
            if (hasLastPinned()) {
                padding.bottom = mPinnedLastLayout.getPaddingBottom();
            } else {
                padding.bottom = mScrolledLayout.getPaddingBottom();
            }
        } else {
            if (hasFirstPinned()) {
                padding.left = mPinnedFirstLayout.getPaddingStart();
            } else {
                padding.left = mScrolledLayout.getPaddingStart();
            }
            padding.top = mScrolledLayout.getPaddingTop();
            if (hasLastPinned()) {
                padding.right = mPinnedLastLayout.getPaddingEnd();
            } else {
                padding.right = mScrolledLayout.getPaddingEnd();
            }
        }
        return padding;
    }

    private void updateInternalPadding() {
        if (mPinnedFirstLayout == null || mScrolledLayout == null | mPinnedLastLayout == null) {
            return;
        }
        Rect firstLayoutPadding = new Rect();
        Rect scrollWrapperPadding = new Rect();
        Rect scrolledLayoutPadding = new Rect();
        Rect lastLayoutPadding = new Rect();
        if (getOrientation() == LinearLayout.VERTICAL) {
            firstLayoutPadding.left = mPaddingStart;
            scrolledLayoutPadding.left = mPaddingStart;
            lastLayoutPadding.left = mPaddingStart;
            if (hasFirstPinned()) {
                firstLayoutPadding.top = mPaddingTop;
            } else {
                scrollWrapperPadding.top = mPaddingTop;
            }
            firstLayoutPadding.right = mPaddingEnd;
            scrolledLayoutPadding.right = mPaddingEnd;
            lastLayoutPadding.right = mPaddingEnd;
            if (hasLastPinned()) {
                lastLayoutPadding.bottom = mPaddingBottom;
            } else {
                scrollWrapperPadding.bottom = mPaddingBottom;
            }
        } else {
            if (hasFirstPinned()) {
                firstLayoutPadding.left = mPaddingStart;
            } else {
                scrollWrapperPadding.left = mPaddingStart;
            }
            firstLayoutPadding.top = mPaddingTop;
            scrolledLayoutPadding.top = mPaddingTop;
            lastLayoutPadding.top = mPaddingTop;
            if (hasLastPinned()) {
                lastLayoutPadding.right = mPaddingEnd;
            } else {
                scrollWrapperPadding.right = mPaddingEnd;
            }
            firstLayoutPadding.bottom = mPaddingBottom;
            scrolledLayoutPadding.bottom = mPaddingBottom;
            lastLayoutPadding.bottom = mPaddingBottom;
        }
        boolean firstLayoutChanged =
                setPadding(mPinnedFirstLayout, mPaddingIsRelative, firstLayoutPadding);
        boolean scrollWrapperLayoutChanged =
                setPadding(mScrollWrapperLayout, mPaddingIsRelative, scrollWrapperPadding);
        boolean scrolledLayoutChanged =
                setPadding(mScrolledLayout, mPaddingIsRelative, scrolledLayoutPadding);
        boolean lastLayoutChanged =
                setPadding(mPinnedLastLayout, mPaddingIsRelative, lastLayoutPadding);
        if (firstLayoutChanged || scrollWrapperLayoutChanged || scrolledLayoutChanged
                || lastLayoutChanged) {
            mPinnedFirstLayout.requestLayout();
            mScrolledLayout.requestLayout();
            mPinnedLastLayout.requestLayout();
        }
    }

    private static boolean setPadding(View view, boolean isRelative, Rect padding) {
        if (view.isPaddingRelative() == isRelative
                && (view.isPaddingRelative()
                        ? view.getPaddingStart()
                        : view.getPaddingLeft()) == padding.left
                && view.getPaddingTop() == padding.top
                && (view.isPaddingRelative()
                        ? view.getPaddingEnd()
                        : view.getPaddingRight()) == padding.right
                && view.getPaddingBottom() == padding.bottom) {
            // not changing anything
            return false;
        }
        if (isRelative) {
            view.setPaddingRelative(padding.left, padding.top, padding.right, padding.bottom);
        } else {
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
        }
        return true;
    }

    /**
     * Check whether there is a first item that is or will be pinned.
     * @return Whether there is a pinned first item.
     */
    private boolean hasFirstPinned() {
        return mCanPin && mShouldPinFirst && !mManagedChildren.isEmpty();
    }

    /**
     * Check whether there are any views that are or will be included in the scrolled section.
     * @return Whether there are any items in the scrolled section.
     */
    private boolean hasScrolledContent() {
        int viewCount = mManagedChildren.size();
        if (mCanPin) {
            if (mShouldPinFirst) {
                viewCount--;
            }
            if (mShouldPinLast) {
                viewCount--;
            }
        }
        return viewCount > 0;
    }

    /**
     * Check whether there is a last item that is or will be pinned.
     * @return Whether there is a pinned last item.
     */
    private boolean hasLastPinned() {
        return mCanPin && mShouldPinLast && mManagedChildren.size() > (mShouldPinFirst ? 1 : 0);
    }

    /**
     * Set how dividers should be shown between items in this layout
     *
     * @param showDividers One or more of {@link LinearLayout#SHOW_DIVIDER_BEGINNING},
     *                     {@link LinearLayout#SHOW_DIVIDER_MIDDLE}, or
     *                     {@link LinearLayout#SHOW_DIVIDER_END} to show dividers, or
     *                     {@link LinearLayout#SHOW_DIVIDER_NONE} to show no dividers.
     */
    public void setShowDividers(int showDividers) {
        if (showDividers == mShowDividers) {
            return;
        }
        mShowDividers = showDividers;

        updateInternalDivider();
    }

    /**
     * @return A flag set indicating how dividers should be shown around items.
     * @see #setShowDividers(int)
     */
    public int getShowDividers() {
        return mShowDividers;
    }

    /**
     * Set a drawable to be used as a divider between items.
     *
     * @param divider Drawable that will divide each item.
     *
     * @see #setShowDividers(int)
     *
     * @attr ref R.styleable.LinearScrollLayout_android_divider
     */
    public void setDividerDrawable(Drawable divider) {
        if (divider == mDivider) {
            return;
        }
        mDivider = divider;

        updateInternalDivider();
    }

    /**
     * @return the divider Drawable that will divide each item.
     *
     * @see #setDividerDrawable(Drawable)
     *
     * @attr ref R.styleable.LinearScrollLayout_android_divider
     */
    public Drawable getDividerDrawable() {
        return mDivider;
    }

    /**
     * Set padding displayed on both ends of dividers. For a vertical layout, the padding is applied
     * to left and right end of dividers. For a horizontal layout, the padding is applied to top and
     * bottom end of dividers.
     *
     * @param padding Padding value in pixels that will be applied to each end.
     *
     * @see #setShowDividers(int)
     * @see #setDividerDrawable(Drawable)
     * @see #getDividerPadding()
     */
    public void setDividerPadding(int padding) {
        if (padding == mDividerPadding) {
            return;
        }
        mDividerPadding = padding;

        updateInternalDivider();
    }

    /**
     * Get the padding size used to inset dividers in pixels.
     *
     * @see #setShowDividers(int)
     * @see #setDividerDrawable(Drawable)
     * @see #setDividerPadding(int)
     */
    public int getDividerPadding() {
        return mDividerPadding;
    }

    private void updateInternalDivider() {
        if (mPinnedFirstLayout == null || mScrolledLayout == null | mPinnedLastLayout == null) {
            return;
        }
        boolean showBeginningDivider = (mShowDividers & SHOW_DIVIDER_BEGINNING) > 0;
        boolean showMiddleDivider = (mShowDividers & SHOW_DIVIDER_MIDDLE) > 0;
        boolean showEndDivider = (mShowDividers & SHOW_DIVIDER_END) > 0;
        boolean hasFirstPinned = hasFirstPinned();
        boolean hasLastPinned = hasLastPinned();
        int firstLayoutShowDividers = 0;
        int scrolledLayoutShowDividers = 0;
        int lastLayoutShowDividers = 0;
        if (showBeginningDivider) {
            if (hasFirstPinned) {
                firstLayoutShowDividers |= SHOW_DIVIDER_BEGINNING;
            } else {
                scrolledLayoutShowDividers |= SHOW_DIVIDER_BEGINNING;
            }
        }
        if (showMiddleDivider) {
            if (hasFirstPinned) {
                firstLayoutShowDividers |= SHOW_DIVIDER_END;
            }
            scrolledLayoutShowDividers |= SHOW_DIVIDER_MIDDLE;
            if (hasLastPinned) {
                lastLayoutShowDividers |= SHOW_DIVIDER_BEGINNING;
            }
        }
        if (showEndDivider) {
            if (hasLastPinned) {
                lastLayoutShowDividers |= SHOW_DIVIDER_END;
            } else {
                scrolledLayoutShowDividers |= SHOW_DIVIDER_END;
            }
        }

        boolean firstLayoutChanged =
                setDividers(mPinnedFirstLayout, firstLayoutShowDividers, mDivider, mDividerPadding);
        boolean scrolledLayoutChanged =
                setDividers(mScrolledLayout, scrolledLayoutShowDividers, mDivider, mDividerPadding);
        boolean lastLayoutChanged =
                setDividers(mPinnedLastLayout, lastLayoutShowDividers, mDivider, mDividerPadding);
        if (firstLayoutChanged || scrolledLayoutChanged || lastLayoutChanged) {
            mPinnedFirstLayout.requestLayout();
            mScrolledLayout.requestLayout();
            mPinnedLastLayout.requestLayout();
        }
    }

    private static boolean setDividers(LinearLayout view, int showDividers,
                                       Drawable dividerDrawable, int dividerPadding) {
        boolean changed = false;
        if (view.getShowDividers() != showDividers) {
            view.setShowDividers(showDividers);
            changed = true;
        }
        if (view.getDividerDrawable() != dividerDrawable) {
            view.setDividerDrawable(dividerDrawable);
            changed = true;
        }
        if (view.getDividerPadding() != dividerPadding) {
            view.setDividerPadding(dividerPadding);
            changed = true;
        }
        return changed;
    }

    @Override
    public void addView(View child) {
        // (EW) from ViewGroup
        addView(child, -1);
    }

    @Override
    public void addView(View child, int index) {
        // (EW) from ViewGroup
        if (child == null) {
            throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        }
        ViewGroup.LayoutParams params = child.getLayoutParams();
        if (params == null) {
            params = generateDefaultLayoutParams();
            if (params == null) {
                throw new IllegalArgumentException(
                        "generateDefaultLayoutParams() cannot return null  ");
            }
        }
        addView(child, index, params);
    }

    @Override
    public void addView(View child, int width, int height) {
        // (EW) from ViewGroup
        final ViewGroup.LayoutParams params = generateDefaultLayoutParams();
        params.width = width;
        params.height = height;
        addView(child, -1, params);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        // (EW) from ViewGroup
        addView(child, -1, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child == mInternalView) {
            super.addView(child, index, params);
            return;
        }

        // (EW) from ViewGroup
        if (child == null) {
            throw new IllegalArgumentException("Cannot add a null child view to a ViewGroup");
        }

        child.setLayoutParams(params);
        mManagedChildren.add(child);
        updateContentViewPlacement();
    }

    @Override
    public void removeAllViews() {
        removeAllViewsInLayout();
    }

    @Override
    public void removeAllViewsInLayout() {
        mPinnedFirstLayout.removeAllViewsInLayout();
        mScrolledLayout.removeAllViewsInLayout();
        mPinnedLastLayout.removeAllViewsInLayout();
    }

    @Override
    public void removeView(View view) {
        removeViewInternal(view, false);
    }

    @Override
    public void removeViewInLayout(View view) {
        removeViewInternal(view, true);
    }

    private void removeViewInternal(View view, boolean inLayout) {
        final int index = mManagedChildren.indexOf(view);
        if (index >= 0) {
            removeViewsInternal(index, 1, inLayout);
        }
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
        removeViewsInternal(start, count, true);
    }

    @Override
    public void removeViewAt(int index) {
        removeViewsInternal(index, 1, false);
    }

    @Override
    public void removeViews(int start, int count) {
        removeViewsInternal(start, count, false);
    }

    public void removeViewsInternal(int start, int count, boolean inLayout) {
        final int end = start + count;
        for (int i = end - 1; i >= start; i--) {
            mManagedChildren.remove(i);
        }
        updateContentViewPlacement(inLayout);
    }

    /**
     * Returns the position in the group of the specified child view.
     * Note that this is the position of direct children, which are internal helper views for
     * managing the layout of the main content. This should only need to be called by framework
     * code. {@link #indexOfManagedChild} should be called instead to get the position of content
     * views this view group is meant to manage.
     *
     * @param child The view for which to get the position.
     * @return A positive integer representing the position of the view in the
     *         group, or -1 if the view does not exist in the group.
     */
    @Override
    public int indexOfChild(View child) {
        return super.indexOfChild(child);
    }

    /**
     * Returns the position in the group of the specified child view that this layout manages (i.e.
     * not direct children - {@link #indexOfChild} is for that).
     *
     * @param child The view for which to get the position.
     * @return A positive integer representing the position of the view in the
     *         group, or -1 if the view does not exist in the group.
     */
    public int indexOfManagedChild(View child) {
        final int count = mManagedChildren.size();
        for (int i = 0; i < count; i++) {
            if (mManagedChildren.get(i) == child) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the number of children in the group.
     * Note that this is the number of direct children, which are internal helper views for managing
     * the layout of the main content views. This should only need to be called by framework code.
     * {@link #getManagedChildCount()} should be called instead to get the number of content views
     * this view group is meant to manage.
     *
     * @return A positive integer representing the number of children in
     *         the group.
     */
    @Override
    public int getChildCount() {
        return super.getChildCount();
    }

    /**
     * Returns the number of children in the group that this layout manages (i.e. not direct
     * children - {@link #getChildCount} is for that).
     *
     * @return A positive integer representing the number of children in
     *         the group.
     */
    public int getManagedChildCount() {
        return mManagedChildren.size();
    }

    /**
     * Returns the view at the specified position in the group.
     * Note that this is a direct children, which is an internal helper view for managing the layout
     * of the main content. This should only need to be called by framework code.
     * {@link #getManagedChildAt(int)} should be called instead to get a content view this view
     * group is meant to manage.
     *
     * @param index The position at which to get the view from.
     * @return The view at the specified position or null if the position
     *         does not exist within the group.
     */
    @Override
    public View getChildAt(int index) {
        return super.getChildAt(index);
    }

    /**
     * Returns the view at the specified position in the group that this layout manages (i.e. not
     * direct children - {@link #getChildCount} is for that).
     *
     * @param index The position at which to get the view from.
     * @return The view at the specified position or null if the position
     *         does not exist within the group.
     */
    public View getManagedChildAt(int index) {
        if (index < 0 || index >= getChildCount()) {
            return null;
        }
        return mManagedChildren.get(index);
    }

    private static class Position {
        public final ViewGroup group;
        public final int index;
        public Position(ViewGroup group, int index) {
            this.group = group;
            this.index = index;
        }
    }

    private Position getIntendedPosition(int managedChildIndex) {
        if (mCanPin && mShouldPinFirst && managedChildIndex == 0) {
            return new Position(mPinnedFirstLayout, 0);
        }
        if (mCanPin && mShouldPinLast && managedChildIndex == mManagedChildren.size() - 1) {
            return new Position(mPinnedLastLayout, 0);
        }
        return new Position(mScrolledLayout,
                managedChildIndex - (mCanPin && mShouldPinFirst ? 1 : 0));
    }

    private View findRootContentView(View view) {
        while (view != null) {
            if (mManagedChildren.contains(view)) {
                return view;
            }
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private boolean updateContentViewPlacement() {
        return updateContentViewPlacement(false);
    }

    private boolean updateContentViewPlacement(boolean inLayout) {
        Activity activity = ViewUtils.getActivity(this);
        View focusedView = activity != null ? activity.getCurrentFocus() : null;
        View contentViewWithFocus = findRootContentView(focusedView);
        boolean viewsMoved = false;
        // add or move views
        for (int i = 0; i < mManagedChildren.size(); i++) {
            View contentView = mManagedChildren.get(i);
            ViewGroup parent = (ViewGroup) contentView.getParent();
            Position intendedPosition = getIntendedPosition(i);
            if (parent == null) {
                intendedPosition.group.addView(contentView, intendedPosition.index);
                viewsMoved = true;
            } else if (parent != intendedPosition.group) {
                parent.removeView(contentView);
                intendedPosition.group.addView(contentView, intendedPosition.index,
                        contentView.getLayoutParams());
                if (contentView == contentViewWithFocus) {
                    // try to set the focus back now that the view moved
                    focusedView.requestFocus();
                }
                viewsMoved = true;
            }
        }
        // remove views
        int position = 0;
        for (ViewGroup group : new ViewGroup[] {
                mPinnedFirstLayout, mScrolledLayout, mPinnedLastLayout
        }) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (position < mManagedChildren.size()
                        && mManagedChildren.get(position) == group.getChildAt(i)) {
                    position++;
                    continue;
                }
                if (inLayout) {
                    group.removeViewInLayout(group.getChildAt(i));
                } else {
                    group.removeViewAt(i);
                }
                viewsMoved = true;
            }
        }
        mPinnedFirstLayout.setVisibility(mPinnedFirstLayout.getChildCount() == 0
                ? View.GONE
                : View.VISIBLE);
        mPinnedLastLayout.setVisibility(mPinnedLastLayout.getChildCount() == 0
                ? View.GONE
                : View.VISIBLE);

        if (viewsMoved) {
            updateInternalPadding();
            updateInternalDivider();
        }
        return viewsMoved;
    }

    /**
     * Check if there is enough room for the first and last views can be pinned (if requested).
     * @return Whether the requested views can be pinned.
     */
    private boolean canPin() {
        int fullLength;
        int scrollContentLength = 0;
        int pinnedTargetLength = 0;
        int count = mManagedChildren.size();
        View pinnedFirst = mShouldPinFirst && !mManagedChildren.isEmpty()
                ? mManagedChildren.get(0)
                : null;
        View pinnedLast = mShouldPinLast && count > (mShouldPinFirst ? 1 : 0)
                ? mManagedChildren.get(count - 1)
                : null;
        if (getOrientation() == LinearLayout.VERTICAL) {
            fullLength = getMeasuredHeight();
            if (pinnedFirst != null) {
                pinnedTargetLength += pinnedFirst.getMeasuredHeight();
            }
            for (int i = 0; i < mScrolledLayout.getChildCount(); i++) {
                scrollContentLength += mScrolledLayout.getChildAt(i).getMeasuredHeight();
            }
            if (pinnedLast != null) {
                pinnedTargetLength += pinnedLast.getMeasuredHeight();
            }
        } else {
            fullLength = getMeasuredWidth();
            if (pinnedFirst != null) {
                pinnedTargetLength += pinnedFirst.getMeasuredWidth();
            }
            for (int i = 0; i < mScrolledLayout.getChildCount(); i++) {
                scrollContentLength += mScrolledLayout.getChildAt(i).getMeasuredWidth();
            }
            if (pinnedLast != null) {
                pinnedTargetLength += pinnedLast.getMeasuredWidth();
            }
        }

        // make sure the pinned items and a reasonable portion of the main content can fit
        return pinnedTargetLength + Math.min(scrollContentLength, mContentMinLength) <= fullLength;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // check if we can pin based on the new measurements and if that state changes, reorganize
        // the content views to match that
        if (mCanPin != canPin()) {
            mCanPin = !mCanPin;
            if (updateContentViewPlacement()) {
                // since we changed what content is in which helper layout, force them to remeasure
                // immediately
                measure(View.MeasureSpec.makeMeasureSpec(getMeasuredWidth(),
                                View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(getMeasuredHeight(),
                                View.MeasureSpec.EXACTLY));
            }
        }
    }
}
