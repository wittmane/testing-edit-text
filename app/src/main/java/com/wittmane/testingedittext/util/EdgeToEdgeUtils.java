/*
 * Copyright (C) 2025-2026 Eli Wittman
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

package com.wittmane.testingedittext.util;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.OnHierarchyChangeListener;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.MainActivity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EdgeToEdgeUtils {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final Map<View, ParentViewInfo> mParentViewInfo = new HashMap<>();

    private static class InsetEdgeHandleInfo {
        protected final boolean mHandleLeft;
        protected final boolean mHandleTop;
        protected final boolean mHandleRight;
        protected final boolean mHandleBottom;
        public InsetEdgeHandleInfo(boolean handleLeft, boolean handleTop,
                                   boolean handleRight, boolean handleBottom) {
            mHandleLeft = handleLeft;
            mHandleTop = handleTop;
            mHandleRight = handleRight;
            mHandleBottom = handleBottom;
        }
    }

    private static class ParentViewInfo extends InsetEdgeHandleInfo {
        @Nullable
        private WindowInsets mLastInsets;
        private final Map<View, Rect> mImmediateChildViewBasePadding = new HashMap<>();
        private final Map<View, AdditionalViewInfo> mAdditionalViewInfo = new HashMap<>();
        public ParentViewInfo(boolean handleLeft, boolean handleTop,
                              boolean handleRight, boolean handleBottom) {
            super(handleLeft, handleTop, handleRight, handleBottom);
        }
    }

    private static class AdditionalViewInfo extends InsetEdgeHandleInfo {
        @Nullable
        private Rect mBasePadding;
        public AdditionalViewInfo(boolean handleLeft, boolean handleTop,
                                  boolean handleRight, boolean handleBottom) {
            super(handleLeft, handleTop, handleRight, handleBottom);
        }
    }

    public static boolean isEdgeToEdgeEnforced() {
        // edge-to-edge is only enforced starting in Android 15
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }

    public static void addInsetHandling(Activity activity, boolean left, boolean top,
                                        boolean right, boolean bottom) {
        // edge-to-edge is only enforced starting in Android 15
        if (isEdgeToEdgeEnforced()) {
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

            synchronized (mParentViewInfo) {
                mParentViewInfo.put(contentView, new ParentViewInfo(left, top, right, bottom));
            }

            contentView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull final View view,
                                                        @NonNull final WindowInsets insets) {
                    synchronized (mParentViewInfo) {
                        ParentViewInfo parentViewInfo = mParentViewInfo.get(view);
                        if (parentViewInfo == null) {
                            // we must have cleaned up by now, so don't bother with anything.
                            // realistically, I doubt this will ever be hit.
                            return insets;
                        }
                        applyWindowInsets(view, parentViewInfo, insets);
                        parentViewInfo.mLastInsets = new WindowInsets(insets);
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
                    // start tracking the new child view and apply the insets
                    synchronized (mParentViewInfo) {
                        ParentViewInfo parentViewInfo = mParentViewInfo.get(parent);
                        if (parentViewInfo != null && parentViewInfo.mLastInsets != null) {
                            applyWindowInsets(parent, parentViewInfo, parentViewInfo.mLastInsets);
                        }
                    }
                }

                @Override
                public void onChildViewRemoved(View parent, View child) {
                    // stop tracking the child view
                    synchronized (mParentViewInfo) {
                        ParentViewInfo parentViewInfo = mParentViewInfo.get(parent);
                        if (parentViewInfo != null) {
                            parentViewInfo.mImmediateChildViewBasePadding.remove(child);
                        }
                    }
                }
            });
        }
    }

    public static void addInsetHandling(Activity activity, View view, boolean left, boolean top,
                                        boolean right, boolean bottom) {
        if (!isEdgeToEdgeEnforced()) {
            return;
        }
        synchronized (mParentViewInfo) {
            View parent = activity.findViewById(android.R.id.content);
            ParentViewInfo parentViewInfo = mParentViewInfo.get(parent);
            if (parentViewInfo == null) {
                return;
            }
            // only add handling if the top level parent didn't already handle it to avoid double
            // padding
            parentViewInfo.mAdditionalViewInfo.put(view, new AdditionalViewInfo(
                    left && !parentViewInfo.mHandleLeft,
                    top && !parentViewInfo.mHandleTop,
                    right && !parentViewInfo.mHandleRight,
                    bottom && !parentViewInfo.mHandleBottom));
            if (parentViewInfo.mLastInsets != null) {
                applyWindowInsets(parent, parentViewInfo, parentViewInfo.mLastInsets);
            }
        }
    }

    public static void removeInsetHandling(Activity activity) {
        if (!isEdgeToEdgeEnforced()) {
            return;
        }
        View contentView = activity.findViewById(android.R.id.content);
        synchronized (mParentViewInfo) {
            mParentViewInfo.remove(contentView);
        }
    }

    public static void removeInsetHandling(Activity activity, View view) {
        if (isEdgeToEdgeEnforced()) {
            // edge-to-edge is only enforced starting in Android 15
            return;
        }
        View contentView = activity.findViewById(android.R.id.content);
        synchronized (mParentViewInfo) {
            ParentViewInfo parentViewInfo = mParentViewInfo.get(contentView);
            if (parentViewInfo != null) {
                parentViewInfo.mAdditionalViewInfo.remove(view);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static void applyWindowInsets(@NonNull final View parentView,
                                          @NonNull final ParentViewInfo parentViewInfo,
                                          @NonNull final WindowInsets insets) {
        for (int i = 0; i < ((ViewGroup) parentView).getChildCount(); i++) {
            // since this view is android.R.id.content, it should be a FrameLayout to fill the full
            // screen, and multiple children will just be stacked on each other (last one is the
            // visible one), so we should just apply the insets to all children
            View child = ((ViewGroup) parentView).getChildAt(i);
            if (child == null) {
                continue;
            }

            Rect basePadding = parentViewInfo.mImmediateChildViewBasePadding.get(child);
            basePadding = updatePaddingForInsets(child, parentViewInfo, insets,
                    parentViewInfo.mLastInsets, basePadding);
            parentViewInfo.mImmediateChildViewBasePadding.put(child, basePadding);
        }

        for (View extra : parentViewInfo.mAdditionalViewInfo.keySet()) {
            AdditionalViewInfo specificViewInfo = parentViewInfo.mAdditionalViewInfo.get(extra);
            if (specificViewInfo == null) {
                continue;
            }
            Rect basePadding = specificViewInfo.mBasePadding;
            basePadding = updatePaddingForInsets(extra, specificViewInfo, insets,
                    parentViewInfo.mLastInsets, basePadding);
            specificViewInfo.mBasePadding = basePadding;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static Rect updatePaddingForInsets(View view, InsetEdgeHandleInfo viewInfo,
                                               WindowInsets insets, WindowInsets lastInsets,
                                               Rect lastBasePadding) {
        Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());

        Rect basePadding = lastBasePadding;
        Rect padding = getPadding(view);
        if (basePadding != null && lastInsets != null) {
            Insets lastSystemBarsInsets =
                    lastInsets.getInsets(WindowInsets.Type.systemBars());
            // update the last tracked padding in case they changed from something else since we
            // last updated the insets
            if (viewInfo.mHandleLeft) {
                basePadding.left = padding.left - lastSystemBarsInsets.left;
            }
            if (viewInfo.mHandleTop) {
                basePadding.top = padding.top - lastSystemBarsInsets.top;
            }
            if (viewInfo.mHandleRight) {
                basePadding.right = padding.right - lastSystemBarsInsets.right;
            }
            if (viewInfo.mHandleBottom) {
                basePadding.right = padding.bottom - lastSystemBarsInsets.bottom;
            }
        } else {
            basePadding = new Rect(padding);
        }

        if (viewInfo.mHandleLeft) {
            padding.left = basePadding.left + systemBarsInsets.left;
        }
        if (viewInfo.mHandleTop) {
            padding.top = basePadding.top + systemBarsInsets.top;
        }
        if (viewInfo.mHandleRight) {
            padding.right = basePadding.right + systemBarsInsets.right;
        }
        if (viewInfo.mHandleBottom) {
            padding.bottom = basePadding.bottom + systemBarsInsets.bottom;
        }
        setPadding(view, padding);

        return basePadding;
    }

    private static Rect getPadding(View view) {
        return new Rect(view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(), view.getPaddingBottom());
    }

    private static void setPadding(View view, Rect padding) {
        view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
    }

    // (EW) for debug purposes
    public static void printViewStructure(View view) {
        printViewStructure(view, 0);
    }

    private static String visibilityName(int visibility) {
        if (visibility == View.VISIBLE) {
            return "VISIBLE";
        }
        if (visibility == View.INVISIBLE) {
            return "INVISIBLE";
        }
        if (visibility == View.GONE) {
            return "GONE";
        }
        return "unknown";
    }

    private static void printViewStructure(View view, int indent) {
        Log.d(TAG, "printViewStructure: " + repeat(' ', indent) + view
                + " (" + visibilityName(view.getVisibility()) + ") "
                + view.getX() + "," + view.getY() + " " + view.getWidth() + "x" + view.getHeight());
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                printViewStructure(((ViewGroup) view).getChildAt(i), indent + 2);
            }
        }
    }

    private static String repeat(char c, int count) {
        char[] charArray = new char[count];
        Arrays.fill(charArray, c);
        return new String(charArray);
    }
}
