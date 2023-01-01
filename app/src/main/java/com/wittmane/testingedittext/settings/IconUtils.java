/*
 * Copyright (C) 2022 Eli Wittman
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

package com.wittmane.testingedittext.settings;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.wittmane.testingedittext.R;

import java.util.ArrayList;

public class IconUtils {
    private static final int RESOURCES_ID_NULL =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Resources.ID_NULL : 0;

    /**
     * Set a menu item's icon to matching text color.
     * @param view the view to use to look up the root view to find the action bar text.
     * @param menuItem the menu item that should change colors.
     * @param actionBar target ActionBar.
     */
    public static void matchMenuIconColor(final View view, final MenuItem menuItem,
                                           final ActionBar actionBar) {
        ArrayList<View> views = new ArrayList<>();

        view.getRootView().findViewsWithText(views, actionBar.getTitle(),
                View.FIND_VIEWS_WITH_TEXT);
        if (views.size() == 1 && views.get(0) instanceof TextView) {
            int color = ((TextView) views.get(0)).getCurrentTextColor();
            setIconColor(menuItem, color);
        }
    }

    /**
     * Set a menu item's icon to specific color.
     * @param menuItem the menu item that should change colors.
     * @param color the color that the icon should be changed to.
     */
    public static void setIconColor(final MenuItem menuItem, final int color) {
        if (menuItem != null) {
            Drawable drawable = menuItem.getIcon();
            if (drawable != null) {
                drawable.mutate();
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    /**
     * Get the color that should be applied for an icon based on how EditText gets it normal text
     * color.
     * @param context The current context.
     * @param view The view for the icon.
     * @return The appropriate color for the icon
     */
    public static int getColorForIcon(Context context, View view) {
        Theme theme = context.getTheme();
        TypedArray typedArray = theme.obtainStyledAttributes(R.styleable.TextViewAppearance);
        TypedArray appearance;
        int ap = typedArray.getResourceId(
                R.styleable.TextViewAppearance_android_textAppearance, -1);
        typedArray.recycle();
        int color = 0;
        if (ap != -1) {
            appearance = theme.obtainStyledAttributes(ap, R.styleable.TextAppearance);
            if (appearance.hasValue(R.styleable.TextAppearance_android_textColor)) {
                ColorStateList textColor = appearance.getColorStateList(
                        R.styleable.TextAppearance_android_textColor);
                color = textColor.getColorForState(view.getDrawableState(), 0);
            }
            appearance.recycle();
        }
        return color;
    }

    /**
     * Create an icon-only button
     * @param context The current context.
     * @param imageResId The resource ID of the drawable.
     * @return The button that was created.
     */
    public static ImageButton createImageButton(Context context, int imageResId) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(imageResId);
        button.setColorFilter(IconUtils.getColorForIcon(context, button));
        TypedArray typedArray = context.getTheme()
                .obtainStyledAttributes(new int[] {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                ? android.R.attr.selectableItemBackgroundBorderless
                                : android.R.attr.selectableItemBackground
                });
        int background = typedArray.getResourceId(0, RESOURCES_ID_NULL);
        typedArray.recycle();
        button.setBackgroundResource(background);
        button.setPadding(0, 0, 0, 0);
        return button;
    }
}
