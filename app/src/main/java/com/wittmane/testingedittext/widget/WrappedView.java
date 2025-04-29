/*
 * Copyright (C) 2025 Eli Wittman
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

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public abstract class WrappedView<TView extends View> extends ViewGroup {

    protected TView mInternalView;

    public WrappedView(Context context) {
        super(context);
    }

    public WrappedView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WrappedView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public WrappedView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void addView(View child) {
        if (child != mInternalView) {
            return;
        }
        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (child != mInternalView) {
            return;
        }
        super.addView(child, index);
    }

    @Override
    public void addView(View child, int width, int height) {
        if (child != mInternalView) {
            return;
        }
        super.addView(child, width, height);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (child != mInternalView) {
            return;
        }
        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child != mInternalView) {
            return;
        }
        super.addView(child, index, params);
    }

    @Override
    public void removeAllViews() {
    }

    @Override
    public void removeAllViewsInLayout() {
    }

    @Override
    public void removeView(View view) {
    }

    @Override
    public void removeViewInLayout(View view) {
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
    }

    @Override
    public void removeViewAt(int index) {
    }

    @Override
    public void removeViews(int start, int count) {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mInternalView != null) {
            measureChild(mInternalView, widthMeasureSpec, heightMeasureSpec);
            int maxHeight = Math.max(
                    mInternalView.getMeasuredHeight() + getPaddingTop() + getPaddingBottom(),
                    getSuggestedMinimumHeight());
            int maxWidth = Math.max(
                    mInternalView.getMeasuredWidth() + getPaddingLeft() + getPaddingRight(),
                    getSuggestedMinimumWidth());
            int childState = mInternalView.getMeasuredState();
            setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                    resolveSizeAndState(maxHeight, heightMeasureSpec,
                            childState << MEASURED_HEIGHT_STATE_SHIFT));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mInternalView != null) {
            int parentLeft = getPaddingLeft();
            int parentRight = right - left - getPaddingRight();
            final int parentTop = getPaddingTop();
            final int parentBottom = bottom - top - getPaddingBottom();
            mInternalView.layout(parentLeft, parentTop, parentRight, parentBottom);
        }
    }
}
