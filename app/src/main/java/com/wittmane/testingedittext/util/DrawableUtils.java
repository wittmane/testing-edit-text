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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.HashSet;

public class DrawableUtils {
    public static int DRAWABLE_LEVEL_MAX = 10000;

    /**
     * Return a drawable object associated with a particular resource ID.
     *
     * This is a wrapper function to get a drawable on any version.
     * @param context The current context.
     * @param res The drawable resource ID.
     * @return An object that can be used to draw this resource.
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    public static Drawable getDrawable(Context context, int res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return context.getDrawable(res);
        } else {
            return context.getResources().getDrawable(res);
        }
    }

    /**
     * Look up the background that is visible behind the view content. This checks for a background
     * on the view and then traverses up the parent views until it finds one. This ignores any
     * backgrounds that are just transparent.
     * @param view The view to check.
     * @return The nearest background drawable or null if no opaque background exists in the
     *         hierarchy.
     */
    public static Drawable getNearestBackground(View view) {
        Drawable background = null;
        View currentView = view;
        // track the views traversed to avoid an infinite loop if a view lists itself (or some
        // descendant) as its parent
        HashSet<View> traversedViews = new HashSet<>();
        traversedViews.add(view);
        while (currentView != null) {
            background = currentView.getBackground();
            if (background instanceof ColorDrawable
                    && ((ColorDrawable) background).getColor() == Color.TRANSPARENT) {
                // ignore transparent backgrounds
                background = null;
            }
            if (background != null) {
                break;
            }
            ViewParent parent = currentView.getParent();
            if (parent instanceof ViewGroup && !traversedViews.contains(parent)) {
                currentView = (View) parent;
                traversedViews.add(currentView);
            } else {
                currentView = null;
            }
        }
        return background;
    }

    /**
     * Create a deep copy of a {@link Drawable}.
     * @param drawable The {@link Drawable} to copy.
     * @return A deep copy of the specified {@link Drawable} or null if it couldn't be copied.
     */
    public static Drawable copyDrawable(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        ConstantState constantState = drawable.getConstantState();
        if (constantState == null) {
            return null;
        }
        return constantState.newDrawable().mutate();
    }
}
