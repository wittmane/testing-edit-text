/*
 * Copyright (C) 2022-2025 Eli Wittman
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
import android.app.ActionBar;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.wittmane.testingedittext.R;

import java.util.ArrayList;

public class IconUtils {
    private static final String TAG = IconUtils.class.getSimpleName();

    //TODO: (EW) this is defined multiple places. find a good place to put it for everything to
    // reference.
    private static final int RESOURCES_ID_NULL =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Resources.ID_NULL : 0;

    /**
     * Set all item icons in a menu to match the action bar's text color.
     * @param view the view to use to look up the root view to find the action bar text.
     * @param menu the menu item that should change colors.
     * @param actionBar target ActionBar.
     */
    public static void matchMenuIconColor(final View view, final Menu menu,
                                          final ActionBar actionBar) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            matchMenuIconColor(view, item, actionBar);
        }
    }

    /**
     * Set a menu item's icon to match the action bar's text color.
     * @param view the view to use to look up the root view to find the action bar text.
     * @param menuItem the menu item that should change colors.
     * @param actionBar target ActionBar.
     */
    public static void matchMenuIconColor(final View view, final MenuItem menuItem,
                                          final ActionBar actionBar) {
        if (actionBar == null) {
            return;
        }
        ArrayList<View> views = new ArrayList<>();
        view.getRootView().findViewsWithText(views, actionBar.getTitle(),
                View.FIND_VIEWS_WITH_TEXT);
        TextView textView;
        if (views.size() == 1 && views.get(0) instanceof TextView) {
            textView = (TextView) views.get(0);
        } else {
            // since we can't find the text view for the action bar title, fall back to just create
            // a new EditText with the themed context to see what the default text color is, which
            // is probably what the action bar title would/will be
            textView = new EditText(actionBar.getThemedContext());
        }
        setIconColor(menuItem, textView.getCurrentTextColor());
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
     * Set a view's icon to match the color that a EditText would have for its normal text.
     * @param context The current context.
     * @param imageView The view with the icon.
     */
    public static void matchIconColor(Context context, final ImageView imageView) {
        imageView.setColorFilter(IconUtils.getColorForIcon(context, imageView));
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
        ImageButton button = new EnabledStateListenerImageButton(context,
                (buttonView, isEnabled) -> {
                    matchIconColor(context, buttonView);
                });
        button.setImageResource(imageResId);
        matchIconColor(context, button);
        button.setBackgroundResource(getResourceId(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? android.R.attr.selectableItemBackgroundBorderless
                        : android.R.attr.selectableItemBackground, context));
        button.setLayoutParams(
                new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    /**
     * Create a button with an icon with a tint to match the text color.
     * @param context The current context.
     * @param imageResId The resource ID of the drawable.
     * @param textResId The resource ID of the string.
     * @return The button that was created.
     */
    public static Button createButton(Context context, int imageResId, int textResId) {
        Drawable drawable = getDrawable(context, imageResId).mutate();

        Button button = new EnabledStateListenerButton(context, (buttonView, isEnabled) -> {
            // update the icon color when the enabled state changes
            setColorFilter(drawable, IconUtils.getColorForIcon(context, buttonView));
        });
        setColorFilter(drawable, IconUtils.getColorForIcon(context, button));
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable,null, null, null);

        button.setBackgroundResource(
                getResourceId(android.R.attr.selectableItemBackground, context));

        button.setText(textResId);

        button.setLayoutParams(
                new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // adding some padding to roughly match the look of a checkbox. the checkbox padding is
        // actually built into the checkbox drawable (btn_check_material_anim). that drawable has a
        // height/width of 32dp but the actual icon matches the size of a 24dp drawable, which means
        // it effectively embedded a 4dp padding to the drawable. we'll just apply that padding to
        // the button, which is technically different, since embedded drawable padding wouldn't
        // affect the right side of the button, and if text wraps multiple lines, the drawable
        // padding wouldn't affect the top or bottom, but this keeps a consistent padding around the
        // whole button, which seems fine.
        int padding = (int) dpToPx(4, context);
        button.setPadding(padding, padding, padding, padding);
        button.setCompoundDrawablePadding(padding);
        // remove the minimum height/width from the button
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        // align the text to be next to the icon and centered vertically
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        return button;
    }

    public static void setColorFilter(Drawable drawable, int color) {
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
    }

    @SuppressLint("AppCompatCustomView")
    private static class EnabledStateListenerButton extends Button {
        private final OnEnabledChangeListener<Button> mOnEnabledChangeListener;
        public EnabledStateListenerButton(Context context,
                                          OnEnabledChangeListener<Button> onEnabledChangeListener) {
            super(context);
            mOnEnabledChangeListener = onEnabledChangeListener;
        }

        @Override
        public void setEnabled(boolean enabled) {
            boolean wasEnabled = isEnabled();
            super.setEnabled(enabled);
            boolean isEnabled = isEnabled();
            if (wasEnabled != isEnabled) {
                mOnEnabledChangeListener.onEnabledChanged(this, isEnabled);
            }
        }
    }

    @SuppressLint("AppCompatCustomView")
    private static class EnabledStateListenerImageButton extends ImageButton {
        private final OnEnabledChangeListener<ImageButton> mOnEnabledChangeListener;
        public EnabledStateListenerImageButton(Context context,
                OnEnabledChangeListener<ImageButton> onEnabledChangeListener) {
            super(context);
            mOnEnabledChangeListener = onEnabledChangeListener;
        }

        @Override
        public void setEnabled(boolean enabled) {
            boolean wasEnabled = isEnabled();
            super.setEnabled(enabled);
            boolean isEnabled = isEnabled();
            if (wasEnabled != isEnabled) {
                mOnEnabledChangeListener.onEnabledChanged(this, isEnabled);
            }
        }
    }

    private interface OnEnabledChangeListener<T extends View> {
        void onEnabledChanged(T view, boolean isEnabled);
    }

    private static int getResourceId(int attr, Context context) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[] { attr });
        int resId = typedArray.getResourceId(0, RESOURCES_ID_NULL);
        typedArray.recycle();
        return resId;
    }

    private static float dpToPx(float px, Context context) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px,
                context.getResources().getDisplayMetrics());
    }

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
}
