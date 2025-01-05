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

package com.wittmane.testingedittext;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewGroup.OnHierarchyChangeListener;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class EdgeToEdgeUtils {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Map<View, ParentViewInfo> mParentViewInfo = new HashMap<>();

    private static class ParentViewInfo {
        @NonNull
        private WindowInsets mLastInsets;
        private final Map<View, MarginLayoutParams> mOriginalMargins = new HashMap<>();
        public ParentViewInfo(@NonNull WindowInsets lastInsets) {
            mLastInsets = lastInsets;
        }
    }

    public static void onCreate(Activity activity) {
        // edge-to-edge is only enforced starting in Android 15
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            View contentView = activity.findViewById(android.R.id.content);
            if (!(contentView instanceof ViewGroup)) {
                // I don't think this can happen
                Log.e(TAG, "android.R.id.content isn't a ViewGroup");
                return;
            }
            if (!(contentView instanceof FrameLayout)) {
                // I don't think this should happen, but if it does, the content may not fill the
                // full screen, which could make this not fill the whole screen, in which case the
                // handling for insets isn't going to apply correctly
                Log.e(TAG, "android.R.id.content isn't a FrameLayout");
            }
            contentView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {

                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull final View view,
                                                        @NonNull final WindowInsets insets) {
                    synchronized (mParentViewInfo) {
                        ParentViewInfo parentViewInfo = mParentViewInfo.get(view);
                        WindowInsets lastInsets;
                        if (parentViewInfo != null) {
                            lastInsets = parentViewInfo.mLastInsets;
                            parentViewInfo.mLastInsets = insets;
                        } else {
                            lastInsets = null;
                            mParentViewInfo.put(view, new ParentViewInfo(new WindowInsets(insets)));
                        }
                        EdgeToEdgeUtils.onApplyWindowInsets(view, lastInsets, insets);
                    }
                    return insets;
                }
            });
            ((ViewGroup) contentView).setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    if (parent != contentView) {
                        return;
                    }
                    synchronized (mParentViewInfo) {
                        ParentViewInfo parentViewInfo = mParentViewInfo.get(parent);
                        if (parentViewInfo != null) {
                            onApplyWindowInsets(parent, parentViewInfo.mLastInsets,
                                    parentViewInfo.mLastInsets);
                        }
                    }
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {
                    synchronized (mParentViewInfo) {
                        ParentViewInfo parentViewInfo = mParentViewInfo.get(parent);
                        if (parentViewInfo != null) {
                            parentViewInfo.mOriginalMargins.remove(child);
                        }
                    }
                }
            });

            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    public static void onDestroy(Activity activity) {
        // edge-to-edge is only enforced starting in Android 15
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            View contentView = activity.findViewById(android.R.id.content);
            synchronized (mParentViewInfo) {
                mParentViewInfo.remove(contentView);
            }
        }
    }

    private static void onApplyWindowInsets(@NonNull final View view,
                                            @Nullable final WindowInsets lastInsets,
                                            @NonNull final WindowInsets insets) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }

        ParentViewInfo parentViewInfo = mParentViewInfo.get(view);
        if (parentViewInfo == null) {
            // this shouldn't happen
            return;
        }

        Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());

        for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            // since this view is android.R.id.content, it should be a FrameLayout to fill the full
            // screen, and multiple children will just be stacked on each other (last one is the
            // visible one), so we should just apply the insets to all children
            View child = ((ViewGroup) view).getChildAt(i);
            if (child == null) {
                continue;
            }
            MarginLayoutParams margins = (MarginLayoutParams) child.getLayoutParams();
            MarginLayoutParams lastMargins = parentViewInfo.mOriginalMargins.get(child);
            if (lastMargins != null && lastInsets != null) {
                Insets lastSystemBarsInsets = lastInsets.getInsets(WindowInsets.Type.systemBars());
                // update the last margins in case they changed since we last updated the insets
                lastMargins.topMargin = margins.topMargin - lastSystemBarsInsets.top;
                lastMargins.leftMargin = margins.leftMargin - lastSystemBarsInsets.left;
                lastMargins.bottomMargin = margins.bottomMargin - lastSystemBarsInsets.bottom;
                lastMargins.rightMargin = margins.rightMargin - lastSystemBarsInsets.right;
            } else {
                lastMargins = new MarginLayoutParams(margins);
                parentViewInfo.mOriginalMargins.put(child, lastMargins);
            }

            margins.topMargin = lastMargins.topMargin + systemBarsInsets.top;
            margins.leftMargin = lastMargins.leftMargin + systemBarsInsets.left;
            margins.bottomMargin = lastMargins.bottomMargin + systemBarsInsets.bottom;
            margins.rightMargin = lastMargins.rightMargin + systemBarsInsets.right;
            child.setLayoutParams(margins);
        }
    }
}
