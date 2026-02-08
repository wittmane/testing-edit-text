/*
 * Copyright (C) 2025-2026 Eli Wittman
 * Copyright (C) 2012 The Android Open Source Project
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
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Iterator;

public class ViewUtils {
    // (EW) copied from MediaRouteButton. this is necessary because the way the AOSP Editor gets the
    // DragAndDropPermissions isn't accessible for apps, so we need to find the activity to get it.
    @Nullable
    public static Activity getActivity(View view) {
        // Gross way of unwrapping the Activity so we can get the FragmentManager
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        // (EW) MediaRouteButton threw an IllegalStateException because its Context was not an
        // Activity, but an something else could be added to a view with a Context that isn't an
        // Activity, so we'll return null to allow it to be handled.
        return null;
    }

    /**
     * Create an iterable to iterate up the parent hierarchy of a view. This iteration ends when a
     * given view doesn't have a parent {@link ViewGroup} or the parent was already traversed
     * (creating a looped hierarchy).
     * @param view The view to iterate the hierarchy.
     * @param includeSelf Whether to include {@code view} in the iteration (as opposed to starting
     *                    with its parent).
     * @return An iterable to iterate up the parent hierarchy of a view.
     */
    public static Iterable<View> iterateUpHierarchy(View view, boolean includeSelf) {
        return new Iterable<View>() {
            @NonNull
            @Override
            public Iterator<View> iterator() {
                return new Iterator<View>() {
                    /** tracker for the views traversed to avoid an infinite loop if a view lists
                     *  itself (or some descendant) as its parent */
                    private final HashSet<View> mTraversedViews = new HashSet<>();

                    private View mNextView = includeSelf ? view : getParentView(view);

                    @Override
                    public boolean hasNext() {
                        return mNextView != null;
                    }

                    @Override
                    public View next() {
                        View nextView = mNextView;
                        mTraversedViews.add(mNextView);
                        mNextView = getParentView(mNextView);
                        return nextView;
                    }

                    private View getParentView(View curView) {
                        if (curView == null) {
                            return null;
                        }
                        ViewParent parent = curView.getParent();
                        if (parent instanceof ViewGroup && !mTraversedViews.contains(parent)) {
                            return (View) parent;
                        } else {
                            return null;
                        }
                    }
                };
            }
        };
    }

    /**
     * Check the user-visibility of a view (i.e. whether the view and all of its ancestors'
     * visibility is {@link View#VISIBLE}).
     * @param view The view to check.
     * @return Whether the view and all of its ancestors' visibility is {@link View#VISIBLE}.
     */
    public static boolean aggregateIsVisible(View view) {
        if (view == null) {
            return false;
        }
        for (View currentView : iterateUpHierarchy(view, true)) {
            if (currentView.getVisibility() != View.VISIBLE) {
                // either the view or one of its ancestors isn't visible, so this view isn't visible
                return false;
            }
        }
        // the view and all of its ancestors are visible
        return true;
    }
}
