/*
 * Copyright (C) 2026 Eli Wittman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wittmane.testingedittext.widget;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.Toolbar;

import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.util.ResourceUtils;
import com.wittmane.testingedittext.util.ViewUtils;

/**
 * A {@link TabHost} that puts its tabs in the action bar.
 */
public class ActionBarTabHost extends TabHost {
    private static final String TAG = ActionBarTabHost.class.getSimpleName();

    private ActionBar mActionBar;
    private int mActionBarDisplayOptions;
    private int mActionBarContentInsetLeft;
    private int mActionBarContentInsetRight;

    private View mTabWidgetOrWrapper;
    private ViewGroup mTabWidgetFallbackParent;
    private int mFallbackIndex;
    private int mFallbackChildCount;

    private View.OnLayoutChangeListener mActionBarContentLayoutChangeListener;

    public ActionBarTabHost(Context context) {
        super(context);
    }

    public ActionBarTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ActionBarTabHost(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ActionBarTabHost(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setup() {
        // in case this is called multiple times, make sure the TabWidget is in the tab host
        // instead of the action bar because TabHost#setup requires the TabHost to have a TabWidget
        // whose id attribute is 'android.R.id.tabs'
        removeTabWidgetFromAppBar();

        super.setup();

        TabWidget tabWidget = getTabWidget();
        Activity activity = ViewUtils.getActivity(this);
        if (activity != null) {
            mActionBar = activity.getActionBar();
        }
        ViewParent parent = tabWidget.getParent();
        HorizontalScrollView scrollView = parent instanceof HorizontalScrollView
                ? (HorizontalScrollView) parent
                : null;
        mTabWidgetOrWrapper = scrollView != null ? scrollView : tabWidget;

        moveTabWidgetToAppBar();
    }

    public synchronized void setTabWidgetVisible(boolean visible) {
        if (mTabWidgetOrWrapper == null) {
            // nothing to change visibility of
            return;
        }
        if (visible) {
            moveTabWidgetToAppBar();
        } else {
            removeTabWidgetFromAppBar();
        }
        mTabWidgetOrWrapper.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private synchronized void moveTabWidgetToAppBar() {
        if (mTabWidgetOrWrapper == null) {
            // nothing to move
            return;
        }
        if (mActionBar == null) {
            // nothing to move to
            return;
        }
        if (mTabWidgetFallbackParent != null) {
            // this is already in the app bar
            return;
        }
        ViewParent originalParent = mTabWidgetOrWrapper.getParent();
        if (!(originalParent instanceof ViewGroup)) {
            // can't remove this view
            return;
        }

        mTabWidgetFallbackParent = (ViewGroup) originalParent;
        mFallbackIndex = mTabWidgetFallbackParent.indexOfChild(mTabWidgetOrWrapper);
        mFallbackChildCount = mTabWidgetFallbackParent.getChildCount();

        mTabWidgetFallbackParent.removeView(mTabWidgetOrWrapper);

        mActionBarDisplayOptions = mActionBar.getDisplayOptions();

        mActionBar.setCustomView(mTabWidgetOrWrapper);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setDisplayShowHomeEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Toolbar toolbar = (Toolbar) mTabWidgetOrWrapper.getParent();
            mActionBarContentInsetLeft = toolbar.getContentInsetLeft();
            mActionBarContentInsetRight = toolbar.getContentInsetRight();
            // remove left margin
            toolbar.setContentInsetsAbsolute(0, 0);
        }

        // listen to layout changes to make sure there is enough space to keep the tabs in the
        // action bar
        mActionBarContentLayoutChangeListener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int currentWidth = right - left;
                float minWidth = ResourceUtils.dpToPx(200, getContext());
                if (currentWidth < minWidth) {
                    TabWidget tabWidget = getTabWidget();
                    boolean contentFits;
                    if (tabWidget == null) {
                        // we can't find the tab widget for some reason, so assume it doesn't fit
                        contentFits = false;
                    } else if (currentWidth < tabWidget.getWidth()) {
                        // the scroll view needs to scroll the tabs
                        contentFits = false;
                    } else if (mTabWidgetOrWrapper == tabWidget) {
                        int tabsTotalMinWidth = 0;
                        for (int i = 0; i < tabWidget.getTabCount(); i++) {
                            tabsTotalMinWidth += tabWidget.getChildTabViewAt(i).getMinimumWidth();
                        }
                        contentFits = tabsTotalMinWidth <= currentWidth;
                    } else {
                        contentFits = true;
                    }
                    if (!contentFits) {
                        // not enough space
                        removeTabWidgetFromAppBar();
                    }
                }
            }
        };
        mTabWidgetOrWrapper.addOnLayoutChangeListener(mActionBarContentLayoutChangeListener);
    }

    private synchronized void removeTabWidgetFromAppBar() {
        if (mActionBar == null || mTabWidgetOrWrapper == null || mTabWidgetFallbackParent == null) {
            // the tab widget was never moved to the app bar
            return;
        }
        if (mActionBarContentLayoutChangeListener != null) {
            mTabWidgetOrWrapper.removeOnLayoutChangeListener(mActionBarContentLayoutChangeListener);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Toolbar toolbar = (Toolbar) mTabWidgetOrWrapper.getParent();
            toolbar.setContentInsetsAbsolute(mActionBarContentInsetLeft,
                    mActionBarContentInsetRight);
        }
        mActionBar.setCustomView(null);
        mActionBar.setDisplayOptions(mActionBarDisplayOptions);

        if (mFallbackIndex == 0) {
            // the tab widget originally was the first child, so put it back as first
            mTabWidgetFallbackParent.addView(mTabWidgetOrWrapper, 0);
        } else if (mFallbackIndex == mFallbackChildCount - 1) {
            // the tab widget originally was the last child, so put it back as last
            mTabWidgetFallbackParent.addView(mTabWidgetOrWrapper,
                    mTabWidgetFallbackParent.getChildCount());
        } else if (mFallbackChildCount - 1 == mTabWidgetFallbackParent.getChildCount()) {
            // there are the same number of children as when the tab widget was removed, so put it
            // back where it was
            mTabWidgetFallbackParent.addView(mTabWidgetOrWrapper, mFallbackIndex);
        } else {
            // default to putting the tab widget first
            mTabWidgetFallbackParent.addView(mTabWidgetOrWrapper, 0);
        }

        mTabWidgetFallbackParent = null;
    }

    @Override
    public TabWidget getTabWidget() {
        // TabHost#getTabWidget just returns the cached tab widget, but in case that changes for
        // some reason and it starts complaining about missing a TabWidget whose id attribute is
        // 'android.R.id.tabs', guard against an exception
        try {
            return super.getTabWidget();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
