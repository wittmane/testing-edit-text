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

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.SettingsActivity;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static final String TAG = MainActivity.class.getSimpleName();

    private boolean mUseDebugScreen;

    private EditTextProxy frameworkEditText1;
    private EditTextProxy frameworkEditText2;
    private EditTextProxy customEditText1;
    private EditTextProxy customEditText2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Settings.init(this);

        mUseDebugScreen = Settings.useDebugScreen();
        if (mUseDebugScreen) {
            setContentView(R.layout.activity_main_debug);

            InputFilter filter = new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end,
                                           Spanned dest, int dstart, int dend) {
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
            com.wittmane.testingedittext.aosp.widget.EditText customEditText1 =
                    findViewById(R.id.customEditTextDebug1);
            customEditText1.setFilters(new InputFilter[]{filter});


            android.widget.EditText doNotScrollFrameworkEditText =
                    findViewById(R.id.ellipsizeFrameworkEditText);
            doNotScrollFrameworkEditText.setKeyListener(null);
            com.wittmane.testingedittext.aosp.widget.EditText doNotScrollEditText =
                    findViewById(R.id.ellipsizeCustomEditText);
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

            frameworkEditText1 = new EditTextProxy((android.widget.EditText)
                    findViewById(R.id.frameworkEditText1));
            frameworkEditText2 = new EditTextProxy((android.widget.EditText)
                    findViewById(R.id.frameworkEditText2));
            customEditText1 = new EditTextProxy((com.wittmane.testingedittext.aosp.widget.EditText)
                    findViewById(R.id.customEditText1));
            customEditText2 = new EditTextProxy((com.wittmane.testingedittext.aosp.widget.EditText)
                    findViewById(R.id.customEditText2));

            updateFields();

        }
    }

    private void updateFields() {
        if (mUseDebugScreen) {
            return;
        }

        updateField(frameworkEditText1);
        updateField(frameworkEditText2);
        updateField(customEditText1);
        updateField(customEditText2);
    }

    private static void updateField(EditTextProxy editText) {
        int inputType = Settings.getInputType();
        if (editText.getInputType() != inputType) {
            editText.setInputType(inputType);
        }

        int imeOptions = Settings.getImeOptions();
        if (editText.getImeOptions() != imeOptions) {
            editText.setImeOptions(imeOptions);
        }

        int imeActionId = Settings.getImeActionId();
        String imeActionLabel = Settings.getImeActionLabel();
        int currentImeActionId = editText.getImeActionId();
        CharSequence currentImeActionLabel = editText.getImeActionLabel();
        if (currentImeActionId != imeActionId
                || !TextUtils.equals(currentImeActionLabel, imeActionLabel)) {
            editText.setImeActionLabel(imeActionLabel, imeActionId);
        }

        String privateImeOptions = Settings.getPrivateImeOptions();
        if (!TextUtils.equals(editText.getPrivateImeOptions(), privateImeOptions)) {
            editText.setPrivateImeOptions(privateImeOptions);
        }

        boolean selectAllOnFocus = Settings.shouldSelectAllOnFocus();
        if (editText.getSelectAllOnFocus() != selectAllOnFocus) {
            editText.setSelectAllOnFocus(selectAllOnFocus);
        }

        int maxLength = Settings.getMaxLength();
        InputFilter[] filters = editText.getFilters();
        List<InputFilter> newFilters = new ArrayList<>();
        boolean filtersChanged = false;
        InputFilter.LengthFilter lengthFilter = null;
        for (InputFilter filter : filters) {
            if (filter instanceof InputFilter.LengthFilter) {
                lengthFilter = (InputFilter.LengthFilter) filter;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    int currentMaxLength = lengthFilter.getMax();
                    if (currentMaxLength != maxLength) {
                        if (maxLength >= 0) {
                            newFilters.add(new InputFilter.LengthFilter(maxLength));
                        }
                        filtersChanged = true;
                        continue;
                    }
                } else {
                    // can't tell if there is a difference, so just force a change
                    if (maxLength >= 0) {
                        newFilters.add(new InputFilter.LengthFilter(maxLength));
                    }
                    filtersChanged = true;
                    continue;
                }
            }
            newFilters.add(filter);
        }
        if (maxLength >= 0 && lengthFilter == null) {
            // there wasn't an existing filter to update, so add a new one
            newFilters.add(new InputFilter.LengthFilter(maxLength));
            filtersChanged = true;
        }
        if (filtersChanged) {
            editText.setFilters(newFilters.toArray(new InputFilter[0]));
        }

        boolean allowUndo = Settings.shouldAllowUndo();
        if (editText.getAllowUndo() != allowUndo) {
            editText.setAllowUndo(allowUndo);
        }

        Locale[] textLocales = Settings.getTextLocales();
        Locale[] currentTextLocales = editText.getTextLocales();
        if (textLocales.length > 0) {
            if (!equals(currentTextLocales, textLocales)) {
                editText.setTextLocales(textLocales);
            }
        } else {
            Locale[] defaultTextLocales = editText.getDefaultTextLocales();
            if (!equals(currentTextLocales, defaultTextLocales)) {
                editText.setTextLocales(defaultTextLocales);
            }
        }

        Locale[] imeHintLocales = Settings.getImeHintLocales();
        Locale[] currentImeHintLocales = editText.getImeHintLocales();
        if (!equals(currentImeHintLocales, imeHintLocales)) {
            editText.setImeHintLocales(imeHintLocales);
        }
    }

    private static boolean equals(Locale[] a, Locale[] b) {
        int aLength = a == null ? 0 : a.length;
        int bLength = b == null ? 0 : b.length;
        if (aLength != bLength) {
            return false;
        }
        for (int i = 0; i < aLength; i++) {
            if (!a[i].equals(b[i])) {
                return false;
            }
        }
        return true;
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
                IconUtils.matchMenuIconColor(view, item, getActionBar());
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

    private static class EditTextProxy {
        private final android.widget.EditText mFrameworkEditText;
        private final com.wittmane.testingedittext.aosp.widget.EditText mCustomEditText;

        private int mInputType;
        private boolean mSelectAllOnFocus;

        private final Locale[] mDefaultTextLocales;

        public EditTextProxy(@NonNull android.widget.EditText editText) {
            mFrameworkEditText = editText;
            mCustomEditText = null;
            mDefaultTextLocales = getTextLocales();
        }

        public EditTextProxy(@NonNull com.wittmane.testingedittext.aosp.widget.EditText editText) {
            mCustomEditText = editText;
            mFrameworkEditText = null;
            mDefaultTextLocales = getTextLocales();
        }

        public void setInputType(int type) {
            mInputType = type;
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setInputType(type);
            } else {
                mCustomEditText.setInputType(type);
            }
        }

        public int getInputType() {
            return mInputType;
        }

        public void setImeOptions(int imeOptions) {
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setImeOptions(imeOptions);
            } else {
                mCustomEditText.setImeOptions(imeOptions);
            }
        }

        public int getImeOptions() {
            if (mFrameworkEditText != null) {
                return mFrameworkEditText.getImeOptions();
            } else {
                return mCustomEditText.getImeOptions();
            }
        }

        public void setImeActionLabel(CharSequence label, int actionId) {
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setImeActionLabel(label, actionId);
            } else {
                mCustomEditText.setImeActionLabel(label, actionId);
            }
        }

        public CharSequence getImeActionLabel() {
            if (mFrameworkEditText != null) {
                return mFrameworkEditText.getImeActionLabel();
            } else {
                return mCustomEditText.getImeActionLabel();
            }
        }

        public int getImeActionId() {
            if (mFrameworkEditText != null) {
                return mFrameworkEditText.getImeActionId();
            } else {
                return mCustomEditText.getImeActionId();
            }
        }

        public void setPrivateImeOptions(String type) {
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setPrivateImeOptions(type);
            } else {
                mCustomEditText.setPrivateImeOptions(type);
            }
        }

        public String getPrivateImeOptions() {
            if (mFrameworkEditText != null) {
                return mFrameworkEditText.getPrivateImeOptions();
            } else {
                return mCustomEditText.getPrivateImeOptions();
            }
        }

        public void setSelectAllOnFocus(boolean selectAllOnFocus) {
            mSelectAllOnFocus = selectAllOnFocus;
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setSelectAllOnFocus(selectAllOnFocus);
            } else {
                mCustomEditText.setSelectAllOnFocus(selectAllOnFocus);
            }
        }

        public boolean getSelectAllOnFocus() {
            return mSelectAllOnFocus;
        }

        public void setFilters(InputFilter[] filters) {
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setFilters(filters);
            } else {
                mCustomEditText.setFilters(filters);
            }
        }

        public InputFilter[] getFilters() {
            if (mFrameworkEditText != null) {
                return mFrameworkEditText.getFilters();
            } else {
                return mCustomEditText.getFilters();
            }
        }

        public void setAllowUndo(boolean allowUndo) {
            if (mFrameworkEditText != null) {
                // the framework version doesn't have any method to do this and at least on some
                // version reflection is blocked from just setting it directly
                //TODO: (EW) maybe if we create the EditText in code, we could pass an AttributeSet
                // with it
            } else {
                mCustomEditText.setAllowUndo(allowUndo);
            }
        }

        public boolean getAllowUndo() {
            if (mFrameworkEditText != null) {
                return true;
            } else {
                return mCustomEditText.getAllowUndo();
            }
        }

        public void setTextLocales(@NonNull Locale[] locales) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList localeList = new LocaleList(locales);
                if (mFrameworkEditText != null) {
                    mFrameworkEditText.setTextLocales(localeList);
                } else {
                    mCustomEditText.setTextLocales(localeList);
                }
            } else {
                Locale locale = locales.length > 0 ? locales[0] : null;
                if (mFrameworkEditText != null) {
                    mFrameworkEditText.setTextLocale(locale);
                } else {
                    mCustomEditText.setTextLocale(locale);
                }
            }
        }

        public Locale[] getTextLocales() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                LocaleList localeList;
                if (mFrameworkEditText != null) {
                    localeList = mFrameworkEditText.getTextLocales();
                } else {
                    localeList = mCustomEditText.getTextLocales();
                }
                return getLocaleArray(localeList);
            } else {
                Locale locale;
                if (mFrameworkEditText != null) {
                    locale = mFrameworkEditText.getTextLocale();
                } else {
                    locale = mCustomEditText.getTextLocale();
                }
                return new Locale[] { locale };
            }
        }

        public Locale[] getDefaultTextLocales() {
            return mDefaultTextLocales;
        }

        public void setImeHintLocales(@Nullable Locale[] locales) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return;
            }
            LocaleList localeList = new LocaleList(locales);
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setImeHintLocales(localeList);
            } else {
                mCustomEditText.setImeHintLocales(localeList);
            }
        }

        @Nullable
        public Locale[] getImeHintLocales() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return null;
            }
            LocaleList localeList;
            if (mFrameworkEditText != null) {
                localeList = mFrameworkEditText.getImeHintLocales();
            } else {
                localeList = mCustomEditText.getImeHintLocales();
            }
            return getLocaleArray(localeList);
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        private static Locale[] getLocaleArray(LocaleList localeList) {
            if (localeList == null) {
                return null;
            }
            Locale[] locales = new Locale[localeList.size()];
            for (int i = 0; i < localeList.size(); i++) {
                locales[i] = localeList.get(i);
            }
            return locales;
        }
    }
}