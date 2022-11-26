/*
 * Copyright (C) 2022 Eli Wittman
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

package com.wittmane.testingedittext;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.SettingsActivity;

import java.util.ArrayList;

public class MainActivity extends Activity {
    // Note that if using AppCompatActivity instead of Activity on versions earlier than Lollipop,
    // the built-in EditText will look different from this custom one by being styled more like
    // modern versions (custom colored cursor, controllers, and bottom line, thicker cursor,
    // straight line bottom bar, and gray hint text). Based on digging through the code, this seems
    // to be because AppCompatViewInflater#createView injects AppCompatEditText in the place of a
    // defined EditText. AppCompatEditText uses a TintContextWrapper, which automatically recolors
    // the cursor and controllers' drawables (R.drawable.abc_text_cursor_material,
    // R.drawable.abc_text_select_handle_left_mtrl, R.drawable.abc_text_select_handle_middle_mtrl,
    // and R.drawable.abc_text_select_handle_right_mtrl) (see AppCompatDrawableManager).
    // Interestingly, AppCompatViewInflater looks for "EditText" to be the tag in the xml, so
    // specifying "android.widget.EditText" wouldn't get replaced. It seems that there is no way to
    // automatically tie this custom copy of the EditText into the same tint handling. If we want
    // that, we'd have to add custom handling around loading the drawables, which would deviate from
    // the AOSP version that this copies from, and it would force this custom EditText to be used
    // with AppCompat, so in order to keep it more generic, we'll skip that and just style to match
    // the android version, rather than have a consistent view between versions of this app. I
    // didn't look into the hint color much, but it also seems to be coming from the replaced
    // EditText.

    private boolean mUseDebugScreen;
    private int mInputType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Settings.init(this);

        mUseDebugScreen = Settings.useDebugScreen();
        if (mUseDebugScreen) {
            setContentView(R.layout.activity_main_debug);

            InputFilter filter = new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < source.length(); i++) {
                        char c = source.charAt(i);
                        if (c >= 'A' && c <= 'Z') {
                            continue;
                        }
                        sb.append(c);
                    }
                    return sb;
                }
            };

            android.widget.EditText frameworkEditText1 = findViewById(R.id.frameworkEditTextDebug1);
            frameworkEditText1.setFilters(new InputFilter[]{filter});
            com.wittmane.testingedittext.aosp.widget.EditText customEditText1 = findViewById(R.id.customEditTextDebug1);
            customEditText1.setFilters(new InputFilter[]{filter});


            android.widget.EditText doNotScrollFrameworkEditText = findViewById(R.id.ellipsizeFrameworkEditText);
            doNotScrollFrameworkEditText.setKeyListener(null);
            com.wittmane.testingedittext.aosp.widget.EditText doNotScrollEditText = findViewById(R.id.ellipsizeCustomEditText);
            doNotScrollEditText.setKeyListener(null);

            Button testButton1 = findViewById(R.id.testButton1);
            testButton1.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        int[] outLocation = new int[2];
                        frameworkEditText1.getLocationInWindow(outLocation);
                        frameworkEditText1.showContextMenu(outLocation[0], outLocation[1]);
                    } else {
                        frameworkEditText1.showContextMenu();
                    }
                }
            });

            Button testButton2 = findViewById(R.id.testButton2);
            testButton2.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        int[] outLocation = new int[2];
                        customEditText1.getLocationInWindow(outLocation);
                        customEditText1.showContextMenu(outLocation[0], outLocation[1]);
                    } else {
                        customEditText1.showContextMenu();
                    }
                }
            });
        } else {
            setContentView(R.layout.activity_main);

            updateFields();

        }
    }

    private void updateFields() {
        if (mUseDebugScreen) {
            return;
        }

        android.widget.EditText frameworkEditText1 = findViewById(R.id.frameworkEditText1);
        android.widget.EditText frameworkEditText2 = findViewById(R.id.frameworkEditText2);
        com.wittmane.testingedittext.aosp.widget.EditText customEditText1 = findViewById(R.id.customEditText1);
        com.wittmane.testingedittext.aosp.widget.EditText customEditText2 = findViewById(R.id.customEditText2);

        int inputType = Settings.getInputType();
        if (mInputType != inputType) {
            mInputType = inputType;
            frameworkEditText1.setInputType(mInputType);
            frameworkEditText2.setInputType(mInputType);
            customEditText1.setInputType(mInputType);
            customEditText2.setInputType(mInputType);
        }

        int imeOptions = Settings.getImeOptions();
        if (frameworkEditText1.getImeOptions() != imeOptions) {
            frameworkEditText1.setImeOptions(imeOptions);
            frameworkEditText2.setImeOptions(imeOptions);
            customEditText1.setImeOptions(imeOptions);
            customEditText2.setImeOptions(imeOptions);
        }

        int imeActionId = Settings.getImeActionId();
        String imeActionLabel = Settings.getImeActionLabel();
        int currentImeActionId = frameworkEditText1.getImeActionId();
        CharSequence currentImeActionLabel = frameworkEditText1.getImeActionLabel();
        if (imeActionId != currentImeActionId
                || !TextUtils.equals(imeActionLabel, currentImeActionLabel)) {
            frameworkEditText1.setImeActionLabel(imeActionLabel, imeActionId);
            frameworkEditText2.setImeActionLabel(imeActionLabel, imeActionId);
            customEditText1.setImeActionLabel(imeActionLabel, imeActionId);
            customEditText2.setImeActionLabel(imeActionLabel, imeActionId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mUseDebugScreen != Settings.useDebugScreen()) {
            recreate();
        } else {
            updateFields();
        }
    }

    @Override
    protected void onDestroy() {
        Settings.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater findMenuItems = getMenuInflater();
        findMenuItems.inflate(R.menu.main, menu);
        View view = findViewById(android.R.id.content);
        if (view != null && menu != null) {
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                matchMenuIconColor(view, item, getActionBar());
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            final Intent intent = new Intent();
            intent.setClass(this, SettingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Set a menu item's icon to matching text color.
     * @param view the view to use to look up the root view to find the action bar text.
     * @param menuItem the menu item that should change colors.
     * @param actionBar target ActionBar.
     */
    private static void matchMenuIconColor(final View view, final MenuItem menuItem,
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
    private static void setIconColor(final MenuItem menuItem, final int color) {
        if (menuItem != null) {
            Drawable drawable = menuItem.getIcon();
            if (drawable != null) {
                drawable.mutate();
                drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            }
        }
    }
}