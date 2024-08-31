/*
 * Copyright (C) 2022-2024 Eli Wittman
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

import static com.wittmane.testingedittext.settings.fragments.TestFieldGroupListSettingsFragment.getGroupDisplayName;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabWidget;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.SettingsActivity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity
        implements TabContentFactory, TabHost.OnTabChangeListener {
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

    // Use the ugly view with a bunch of random fields built in xml for quickly comparing various
    // standard EditText features. This is helpful when editing core EditText code, such as when
    // copying from AOSP and making sure the xml generation works right or with other attributes not
    // supported in the settings.
    private static final boolean USE_DEBUG_SCREEN = false;

    private static final String TAB_TAG_PREFIX = "tab_";

    private TestField[][] mTestFields;
    private int mCurrentTabIndex = -1;
    private String[] mCurrentTabTitles;
    private final Map<Integer, View> mTabViews = new HashMap<>();

    private static class TestField {
        private final int mId;
        private final EditTextProxy mFrameworkEditText;
        private final EditTextProxy mCustomEditText;
        private final LinearLayout mLayout;
        public TestField(int id, Context context) {
            mId = id;

            mLayout = new LinearLayout(context);
            mLayout.setOrientation(LinearLayout.HORIZONTAL);
            mLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            LinearLayout frameworkEditTextWrapper = new LinearLayout(context);
            frameworkEditTextWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));
            mLayout.addView(frameworkEditTextWrapper);

            android.widget.EditText frameworkEditText = new android.widget.EditText(context);
            frameworkEditText.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            mFrameworkEditText = new EditTextProxy(frameworkEditText);
            frameworkEditTextWrapper.addView(frameworkEditText);

            LinearLayout customEditTextWrapper = new LinearLayout(context);
            customEditTextWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));
            mLayout.addView(customEditTextWrapper);

            com.wittmane.testingedittext.aosp.widget.EditText customEditText =
                    new com.wittmane.testingedittext.aosp.widget.EditText(context);
            customEditText.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            mCustomEditText = new EditTextProxy(customEditText);
            customEditTextWrapper.addView(customEditText);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Settings.init(this);

        if (USE_DEBUG_SCREEN) {
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
            //TODO: (EW) it seems that the key listener shouldn't matter if the field is already
            // disabled (I can't focus or scroll the field). figure out why this is actually
            // necessary to allow ellipsize to work and see if that can be handled better without
            // needing to null out the key listener. it doesn't really make sense for an edit test
            // to have no key listener unless the view was disabled, so it probably makes more sense
            // to do some of that handling automatically when disabling the view (possibly not
            // actually clearing the key listener, but just adding checks for the view being
            // disabled), rather than forcing this manual call.
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
            final TabHost tabHost = findViewById(R.id.tabHost);
            tabHost.setup();
            setTabs(tabHost);
        }
    }

    private void setTabs(final TabHost tabHost) {
        tabHost.setOnTabChangedListener(null);
        tabHost.clearAllTabs();
        mTabViews.clear();
        mTestFields = null;
        int groupCount = Settings.getTestFieldGroupCount();
        mCurrentTabTitles = new String[groupCount];
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            TabHost.TabSpec spec = tabHost.newTabSpec(TAB_TAG_PREFIX + groupIndex);
            String groupName = getGroupDisplayName(this, groupIndex);
            spec.setIndicator(groupName);
            spec.setContent(this);
            tabHost.addTab(spec);
            mCurrentTabTitles[groupIndex] = groupName;
        }
        mCurrentTabIndex = 0;
        tabHost.setOnTabChangedListener(this);
    }

    @Override
    public View createTabContent(String tag) {
        mCurrentTabIndex = getGroupIndex(tag);

        final FrameLayout tabContent = findViewById(android.R.id.tabcontent);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.activity_main_tab, tabContent, false);
        mTabViews.put(mCurrentTabIndex, view);

        return view;
    }

    @Override
    public void onTabChanged(String tabId) {
        mCurrentTabIndex = getGroupIndex(tabId);
        updateFields();
    }

    private int getGroupIndex(String tabId) {

        if (tabId != null && tabId.startsWith(TAB_TAG_PREFIX)) {
            try {
                return Integer.parseInt(tabId.substring(TAB_TAG_PREFIX.length()));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse group index from " + tabId);
            }
        } else {
            Log.e(TAG, "Unexpected tab ID: " + tabId);
        }
        return -1;

    }

    private void updateFields() {
        if (USE_DEBUG_SCREEN || mCurrentTabIndex < 0) {
            return;
        }

        final TabHost tabHost = findViewById(R.id.tabHost);
        final TabWidget tabs = findViewById(android.R.id.tabs);
        int groupCount = Settings.getTestFieldGroupCount();

        boolean tabsChanged = false;
        if (mCurrentTabTitles.length != groupCount) {
            tabsChanged = true;
        } else {
            for (int i = 0; i < groupCount; i++) {
                if (!TextUtils.equals(mCurrentTabTitles[i], getGroupDisplayName(this, i))) {
                    tabsChanged = true;
                    break;
                }
            }
        }
        if (tabsChanged) {
            setTabs(tabHost);
        }

        tabs.setVisibility(groupCount < 2 ? View.GONE : View.VISIBLE);

        View currentView = mTabViews.get(mCurrentTabIndex);
        if (currentView == null) {
            return;
        }
        LinearLayout testFieldContainer = currentView.findViewById(R.id.testFieldContainer);

        // build or rebuild the list of fields in case any were added or removed and update the ui
        TestField[][] testFields = new TestField[groupCount][];
        for (int curGroupIndex = 0; curGroupIndex < testFields.length; curGroupIndex++) {
            if (curGroupIndex == mCurrentTabIndex) {

                // find the first field that changed (added, removed, or reordered - not considering
                // changes in the settings of the same field), and find all of the existing fields
                // that are still part of the group
                TestField[] groupTestFields = new TestField[Settings.getTestFieldCount(curGroupIndex)];
                int firstChangedFieldIndex = 0;
                for (int curFieldIndex = 0; curFieldIndex < groupTestFields.length; curFieldIndex++) {
                    TestField testField = null;
                    int id = Settings.getTestFieldId(curGroupIndex, curFieldIndex);
                    // see if the field already existed to be able to keep using it
                    if (mTestFields != null && mTestFields[curGroupIndex] != null) {
                        for (int oldFieldIndex = 0; oldFieldIndex < mTestFields[curGroupIndex].length; oldFieldIndex++) {
                            TestField existingField = mTestFields[curGroupIndex][oldFieldIndex];
                            if (existingField.mId == id) {
                                testField = existingField;
                                if (curFieldIndex == oldFieldIndex
                                        && firstChangedFieldIndex == curFieldIndex) {
                                    // field is in the same spot, so this field didn't change
                                    firstChangedFieldIndex++;
                                }
                                break;
                            }
                        }
                    }
                    if (testField == null) {
                        testField = new TestField(id, this);
                    }
                    groupTestFields[curFieldIndex] = testField;
                }

                // only add/remove fields starting where there was a change to avoid messing with
                // things like focus
                if (firstChangedFieldIndex >= 0) {
                    if (mTestFields != null && mTestFields[curGroupIndex] != null) {
                        for (int i = firstChangedFieldIndex; i < mTestFields[curGroupIndex].length; i++) {
                            testFieldContainer.removeView(mTestFields[curGroupIndex][i].mLayout);
                        }
                    }
                    for (int fieldIndex = firstChangedFieldIndex; fieldIndex < groupTestFields.length; fieldIndex++) {
                        testFieldContainer.addView(groupTestFields[fieldIndex].mLayout);
                        groupTestFields[fieldIndex].mCustomEditText.mCustomEditText.setSettings(
                                Settings.getTestFieldSettings(curGroupIndex, fieldIndex));
                    }
                }

                testFields[curGroupIndex] = groupTestFields;

            } else {
                if (mTestFields != null && curGroupIndex < mTestFields.length) {
                    testFields[curGroupIndex] = mTestFields[curGroupIndex];
                }
            }
        }
        mTestFields = testFields;

        for (int i = 0; i < mTestFields[mCurrentTabIndex].length; i++) {
            updateField(mTestFields[mCurrentTabIndex][i].mFrameworkEditText, mCurrentTabIndex, i);
            updateField(mTestFields[mCurrentTabIndex][i].mCustomEditText, mCurrentTabIndex, i);
        }
    }

    private static void updateField(EditTextProxy editText, int groupIndex, int fieldIndex) {
        //TODO: (EW) use the settings object tied to the text field, rather than look up the value
        // by the index in order to consolidate logic

        // since we have a custom setting for making a null input type field still allow multiple
        // lines (which is normally handled as part of the input type), we'll need to trigger
        // setting the input type (even if that didn't change) to trigger a change in the field
        // allowing multiple lines if that setting changed. also, since the input type isn't always
        // set to exactly what we try to set it to, we need to check if the setting for the input
        // type matches what we last requested (rather than what it actually is) to avoid trying to
        // set again unnecessarily.
        int inputType = Settings.getTestFieldInputType(groupIndex, fieldIndex);
        boolean nullInputTypeSingleLine =
                !Settings.getTestFieldNullInputTypeMultiline(groupIndex, fieldIndex);
        if (editText.getRequestedInputType() != inputType
                || (inputType == InputType.TYPE_NULL
                        && editText.isSingleLine() != nullInputTypeSingleLine
                        && editText.isCustom())) {
            editText.setInputType(inputType);
        }

        int imeOptions = Settings.getTestFieldImeOptions(groupIndex, fieldIndex);
        if (editText.getImeOptions() != imeOptions) {
            editText.setImeOptions(imeOptions);
        }

        int imeActionId = Settings.getTestFieldImeActionId(groupIndex, fieldIndex);
        String imeActionLabel = Settings.getTestFieldImeActionLabel(groupIndex, fieldIndex);
        int currentImeActionId = editText.getImeActionId();
        CharSequence currentImeActionLabel = editText.getImeActionLabel();
        if (currentImeActionId != imeActionId
                || !TextUtils.equals(currentImeActionLabel, imeActionLabel)) {
            editText.setImeActionLabel(imeActionLabel, imeActionId);
        }

        String privateImeOptions = Settings.getTestFieldPrivateImeOptions(groupIndex, fieldIndex);
        if (!TextUtils.equals(editText.getPrivateImeOptions(), privateImeOptions)) {
            editText.setPrivateImeOptions(privateImeOptions);
        }

        boolean selectAllOnFocus = Settings.shouldTestFieldSelectAllOnFocus(groupIndex, fieldIndex);
        if (editText.getSelectAllOnFocus() != selectAllOnFocus) {
            editText.setSelectAllOnFocus(selectAllOnFocus);
        }

        int maxLength = Settings.getTestFieldMaxLength(groupIndex, fieldIndex);
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

        boolean allowUndo = Settings.shouldTestFieldAllowUndo(groupIndex, fieldIndex);
        if (editText.getAllowUndo() != allowUndo) {
            editText.setAllowUndo(allowUndo);
        }

        Locale[] textLocales = Settings.getTestFieldTextLocales(groupIndex, fieldIndex);
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

        Locale[] imeHintLocales = Settings.getTestFieldImeHintLocales(groupIndex, fieldIndex);
        Locale[] currentImeHintLocales = editText.getImeHintLocales();
        if (!equals(currentImeHintLocales, imeHintLocales)) {
            editText.setImeHintLocales(imeHintLocales);
        }

        CharSequence defaultText = Settings.getTestFieldDefaultText(groupIndex, fieldIndex);
        if (!editText.wasTextSet(defaultText)) {
            editText.setText(defaultText);
        }

        CharSequence hint = Settings.getTestFieldHintText(groupIndex, fieldIndex);
        if (!editText.wasHintSet(hint)) {
            editText.setHint(hint);
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
        updateFields();
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

        private int mRequestedInputType;
        private boolean mSelectAllOnFocus;
        private CharSequence mSetText;
        private CharSequence mSetHint;

        private final Locale[] mDefaultTextLocales;

        public EditTextProxy(@NonNull android.widget.EditText editText) {
            mFrameworkEditText = editText;
            mCustomEditText = null;
            mRequestedInputType = editText.getInputType();
            mDefaultTextLocales = getTextLocales();
        }

        public EditTextProxy(@NonNull com.wittmane.testingedittext.aosp.widget.EditText editText) {
            mCustomEditText = editText;
            mFrameworkEditText = null;
            mRequestedInputType = editText.getInputType();
            mDefaultTextLocales = getTextLocales();
        }

        public boolean isCustom() {
            return mCustomEditText != null;
        }

        public void setInputType(int type) {
            mRequestedInputType = type;
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setInputType(type);
            } else {
                mCustomEditText.setInputType(type);
            }
        }

        public int getInputType() {
            int inputType;
            if (mFrameworkEditText != null) {
                inputType = mFrameworkEditText.getInputType();
            } else {
                inputType = mCustomEditText.getInputType();
            }
            return inputType;
        }

        public int getRequestedInputType() {
            return mRequestedInputType;
        }

        public boolean isSingleLine() {
            if (mFrameworkEditText != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return mFrameworkEditText.isSingleLine();
                } else {
                    // although it was only made public in Android 10, isSingleLine has existed as
                    // package-private since at least Kitkat so we can fairly safely still access it
                    // with reflection.
                    try {
                        Method isSingleLineMethod =
                                TextView.class.getDeclaredMethod("isSingleLine");
                        isSingleLineMethod.setAccessible(true);
                        return (boolean) isSingleLineMethod.invoke(mFrameworkEditText);
                    } catch (NoSuchMethodException | IllegalAccessException |
                             InvocationTargetException e) {
                        Log.e(TAG, "Reflection failed on TextView.isSingleLine: " + e.getMessage());
                        return true;
                    }
                }
            } else {
                return mCustomEditText.isSingleLine();
            }
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

        public void setText(CharSequence text) {
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setText(text);
            } else {
                mCustomEditText.setText(text);
            }
            mSetText = text;
        }

        public boolean wasTextSet(CharSequence text) {
            if (TextUtils.isEmpty(mSetText) && TextUtils.isEmpty(text)) {
                // null and empty are functionally equivalent since the edit text's text can't
                // actually be null
                return true;
            }
            return SpanUtils.textAndSpansMatch(mSetText, text);
        }

        public void setHint(CharSequence hint) {
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setHint(hint);
            } else {
                mCustomEditText.setHint(hint);
            }
            mSetHint = hint;
        }

        public boolean wasHintSet(CharSequence hint) {
            if (TextUtils.isEmpty(mSetHint) && TextUtils.isEmpty(hint)) {
                // null and empty are functionally equivalent for the edit text's hint
                return true;
            }
            return SpanUtils.textAndSpansMatch(mSetHint, hint);
        }
    }
}