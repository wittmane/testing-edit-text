/*
 * Copyright (C) 2022-2026 Eli Wittman
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

import static com.wittmane.testingedittext.settings.Settings.getGroupDisplayName;
import static com.wittmane.testingedittext.settings.SettingsActivity.FIELD_ID_BUNDLE_KEY;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.function.Function;
import com.wittmane.testingedittext.settings.FieldPosition;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.Settings.TestFieldSettings;
import com.wittmane.testingedittext.settings.SettingsActivity;
import com.wittmane.testingedittext.util.EdgeToEdgeUtils;
import com.wittmane.testingedittext.util.IconUtils;
import com.wittmane.testingedittext.util.ResourceUtils;
import com.wittmane.testingedittext.util.SpanUtils;
import com.wittmane.testingedittext.widget.ActionBarTabHost;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends ThemedActivity
        implements TabContentFactory, TabHost.OnTabChangeListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    // Use the ugly view with a bunch of random fields built in xml for quickly comparing various
    // standard EditText features. This is helpful when editing core EditText code, such as when
    // copying from AOSP and making sure the xml generation works right or with other attributes not
    // supported in the settings.
    private static final boolean USE_DEBUG_SCREEN = false;
    private static final int DEBUG_SCREEN_GROUP_ID = Integer.MIN_VALUE;

    private static final String TAB_TAG_PREFIX = "tab_";

    private static final String STATE_CURRENT_TAB_INDEX = "CURRENT_TAB_INDEX";
    private static final String STATE_ALL_TEST_FIELDS = "ALL_TEST_FIELDS";

    private Group[] mGroups = new Group[0];
    private final HashMap<Integer, TestField> mAllTestFields = new HashMap<>();
    private int mCurrentTabIndex = -1;
    private final Map<Integer, View> mTabViews = new HashMap<>();

    private static class Group {
        private final int mId;
        private final String mTitle;
        private final List<TestField> mTestFields = new ArrayList<TestField>();
        public Group(int id, String title) {
            mId = id;
            mTitle = title;
        }
    }

    private static class TestField {
        private final int mId;
        private final TextView mLabel;
        private final EditTextProxy mFrameworkEditText;
        private final EditTextProxy mCustomEditText;
        private final ImageButton mQuickSettingsButton;
        private final LinearLayout mLayout;
        private final MarginLayoutParams mTextFieldWrapperLayoutParams;
        private boolean mFloatHintAsLabel;
        private final int mEditTextPaddingTop;
        public TestField(int id, Context context) {
            mId = id;

            mLayout = new LinearLayout(context);
            mLayout.setOrientation(LinearLayout.VERTICAL);
            mLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            mLabel = new TextView(context);
            mLabel.setVisibility(View.GONE);
            mLayout.addView(mLabel);

            LinearLayout textFieldWrapperLayout = new LinearLayout(context);
            textFieldWrapperLayout.setOrientation(LinearLayout.HORIZONTAL);
            mTextFieldWrapperLayoutParams = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            textFieldWrapperLayout.setLayoutParams(mTextFieldWrapperLayoutParams);
            mLayout.addView(textFieldWrapperLayout);

            mFrameworkEditText = addEditText(context, textFieldWrapperLayout,
                    android.widget.EditText::new, EditTextProxy::new);

            mCustomEditText = addEditText(context, textFieldWrapperLayout,
                    com.wittmane.testingedittext.aosp.android.widget.EditText::new,
                    EditTextProxy::new);

            mEditTextPaddingTop = Math.min(mFrameworkEditText.getView().getPaddingTop(),
                    mCustomEditText.getView().getPaddingTop())
                    + textFieldWrapperLayout.getPaddingTop();

            mFrameworkEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateLabelVisibility();
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
            });

            mCustomEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    updateLabelVisibility();
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }
            });

            mQuickSettingsButton = addImageButton(context, textFieldWrapperLayout,
                    R.drawable.ic_tune_white_24, R.string.edit_field_settings);
            mQuickSettingsButton.setOnClickListener(v -> {
                final Intent intent = new Intent();
                intent.setClass(context, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.putExtra(FIELD_ID_BUNDLE_KEY, id);
                context.startActivity(intent);
            });
        }

        private boolean isFrameworkEditTextVisible() {
            View frameworkView = mFrameworkEditText.getView();
            return frameworkView.getVisibility() == View.VISIBLE
                    && ((ViewGroup) frameworkView.getParent()).getVisibility() == View.VISIBLE;
        }

        private boolean updateLabelVisibility() {
            boolean visible;
            if (TextUtils.isEmpty(mLabel.getText())) {
                // don't show the label if there isn't any text for it
                visible = false;
            } else if (mFloatHintAsLabel
                    && (!isFrameworkEditTextVisible()
                            || TextUtils.isEmpty(mFrameworkEditText.getText()))
                    && TextUtils.isEmpty(mCustomEditText.getText())) {
                // don't show the label if both fields are blank (the hint handles indicating what
                // the field is). if the reference framework field isn't shown, only consider the
                // single visible field.
                visible = false;
            } else {
                visible = true;
            }
            int visibility = visible ? View.VISIBLE : View.GONE;
            if (mLabel.getVisibility() != visibility) {
                mLabel.setVisibility(visibility);

                int marginTop;
                if (visible) {
                    // set the text field wrapper margin to negate most of the top padding of the
                    // edit text and wrapper to make the label much closer for better visual
                    // association and to minimize layout shifting when the label appears and
                    // disappears. we're not just removing the padding to avoid messing with how the
                    // quick settings button is oriented with the line. this just shifts everything
                    // in the row to be nearly flush against the label.
                    int minGap = (int) ResourceUtils.dpToPx(2, mLabel.getContext());
                    marginTop = Math.min(0, minGap - mEditTextPaddingTop);
                } else {
                    marginTop = 0;
                }
                mTextFieldWrapperLayoutParams.topMargin = marginTop;

                return true;
            }
            return false;
        }

        private static <TEditText extends View> EditTextProxy addEditText(
                Context context, ViewGroup textFieldWrapperLayout,
                Function<Context, TEditText> createEditText,
                Function<TEditText, EditTextProxy> createEditTextProxy) {
            LinearLayout editTextWrapper = new LinearLayout(context);
            editTextWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1));
            textFieldWrapperLayout.addView(editTextWrapper);

            TEditText editText = createEditText.apply(context);
            editText.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            editTextWrapper.addView(editText);

            // enable ACTION_PROCESS_TEXT (see EditText#canProcessText)
            editText.setId(View.generateViewId());

            return createEditTextProxy.apply(editText);
        }

        private static ImageButton addImageButton(Context context, ViewGroup textFieldWrapperLayout,
                                                  int imageResId, int tooltipResId) {

            LinearLayout imageButtonWrapperWrapper = new LinearLayout(context);
            imageButtonWrapperWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, 0));
            textFieldWrapperLayout.addView(imageButtonWrapperWrapper);

            LinearLayout imageButtonWrapper = new LinearLayout(context);
            imageButtonWrapper.setLayoutParams(new LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            imageButtonWrapperWrapper.addView(imageButtonWrapper);

            // use an invisible zero width edit text to allow centering the icon button to the
            // default edit text height so it can stay pinned there, regardless of how large the
            // real edit texts expand
            android.widget.EditText layoutHelperEditText = new android.widget.EditText(context);
            layoutHelperEditText.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT));
            layoutHelperEditText.setFocusable(false);
            layoutHelperEditText.setEnabled(false);
            layoutHelperEditText.setVisibility(View.INVISIBLE);
            imageButtonWrapper.addView(layoutHelperEditText);

            ImageButton imageButton =
                    IconUtils.createImageButton(context, imageResId, tooltipResId);
            LinearLayout.LayoutParams imageButtonLayoutParams =
                    new LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            imageButtonLayoutParams.gravity = Gravity.CENTER;
            imageButton.setLayoutParams(imageButtonLayoutParams);
            imageButtonWrapper.addView(imageButton);

            return imageButton;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        EdgeToEdgeUtils.addInsetHandling(this);

        final TabHost tabHost = findViewById(R.id.tabHost);
        tabHost.setup();
        setTabs(tabHost);
    }

    private void setTabs(final TabHost tabHost) {
        int testFieldGroupCount = Settings.getTestFieldGroupCount();

        int initialSelectedTabIndex = 0;
        if (mCurrentTabIndex >= 0) {
            if (mGroups[mCurrentTabIndex].mId == DEBUG_SCREEN_GROUP_ID) {
                // the debug screen is added as an extra tab
                initialSelectedTabIndex = testFieldGroupCount;
            } else {
                for (int groupIndex = 0; groupIndex < testFieldGroupCount; groupIndex++) {
                    if (Settings.getTestGroupId(groupIndex) == mGroups[mCurrentTabIndex].mId) {
                        initialSelectedTabIndex = groupIndex;
                    }
                }
            }
        }

        tabHost.setOnTabChangedListener(null);
        tabHost.clearAllTabs();
        mTabViews.clear();
        // add the debug screen as an extra tab
        mGroups = new Group[USE_DEBUG_SCREEN ? testFieldGroupCount + 1 : testFieldGroupCount];
        for (int groupIndex = 0; groupIndex < mGroups.length; groupIndex++) {
            TabHost.TabSpec spec = tabHost.newTabSpec(TAB_TAG_PREFIX + groupIndex);
            int groupId;
            String groupName;
            if (USE_DEBUG_SCREEN && groupIndex >= testFieldGroupCount) {
                groupId = DEBUG_SCREEN_GROUP_ID;
                groupName = getString(R.string.debug_screen_title);
            } else {
                groupId = Settings.getTestGroupId(groupIndex);
                // the display name preference is hidden when there is only 1 group since that
                // normally wouldn't be shown, so if we're showing the debug screen, we'll also need
                // to specify a name that isn't numbered
                groupName = USE_DEBUG_SCREEN && testFieldGroupCount == 1
                        ? getString(R.string.main_screen_title)
                        : getGroupDisplayName(this, groupIndex);
            }
            // the default TabHost uses the layout from R.styleable.TabWidget_tabLayout, which is
            // defined as tab_indicator_material in styles_material or tab_indicator_holo in
            // styles_holo
            View tabIndicator = LayoutInflater.from(this).inflate(
                    R.layout.activity_main_tab_indicator, tabHost.getTabWidget(), false);
            final TextView tabTitleView = tabIndicator.findViewById(android.R.id.title);
            tabTitleView.setText(groupName);
            spec.setIndicator(tabIndicator);
            spec.setContent(this);
            tabHost.addTab(spec);
            mGroups[groupIndex] = new Group(groupId, groupName);
        }
        mCurrentTabIndex = 0;
        tabHost.setOnTabChangedListener(this);

        if (initialSelectedTabIndex != mCurrentTabIndex) {
            tabHost.setCurrentTab(initialSelectedTabIndex);
        }
    }

    @Override
    public View createTabContent(String tag) {
        mCurrentTabIndex = getGroupIndex(tag);

        final FrameLayout tabContent = findViewById(android.R.id.tabcontent);
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View view;
        if (USE_DEBUG_SCREEN && mCurrentTabIndex == mGroups.length - 1) {
            view = layoutInflater.inflate(R.layout.activity_main_debug, tabContent, false);

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

            android.widget.EditText frameworkEditText1 =
                    view.findViewById(R.id.frameworkEditTextDebug1);
            frameworkEditText1.setFilters(new InputFilter[]{filter});
            com.wittmane.testingedittext.aosp.android.widget.EditText customEditText1 =
                    view.findViewById(R.id.customEditTextDebug1);
            customEditText1.setFilters(new InputFilter[]{filter});


            android.widget.EditText doNotScrollFrameworkEditText =
                    view.findViewById(R.id.ellipsizeFrameworkEditText);
            doNotScrollFrameworkEditText.setKeyListener(null);
            com.wittmane.testingedittext.aosp.android.widget.EditText doNotScrollEditText =
                    view.findViewById(R.id.ellipsizeCustomEditText);
            //TODO: (EW) it seems that the key listener shouldn't matter if the field is already
            // disabled (I can't focus or scroll the field). figure out why this is actually
            // necessary to allow ellipsize to work and see if that can be handled better without
            // needing to null out the key listener. it doesn't really make sense for an edit test
            // to have no key listener unless the view was disabled, so it probably makes more sense
            // to do some of that handling automatically when disabling the view (possibly not
            // actually clearing the key listener, but just adding checks for the view being
            // disabled), rather than forcing this manual call.
            doNotScrollEditText.setKeyListener(null);

            Button testButton1 = view.findViewById(R.id.testButton1);
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

            Button testButton2 = view.findViewById(R.id.testButton2);
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
            view = layoutInflater.inflate(R.layout.activity_main_tab_content, tabContent, false);
        }
        mTabViews.put(mCurrentTabIndex, view);
        // handle the bottom insets in the tab's scrolling content since it isn't handled on the
        // activity level to allow showing content behind the navigation bar
        EdgeToEdgeUtils.addInsetHandling(this, view.findViewById(R.id.testFieldContainer),
                false, false, false, true);

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
        if (mCurrentTabIndex < 0) {
            return;
        }

        final ActionBarTabHost tabHost = findViewById(R.id.tabHost);
        int testFieldGroupCount = Settings.getTestFieldGroupCount();
        int tabCount = USE_DEBUG_SCREEN ? testFieldGroupCount + 1 : testFieldGroupCount;

        boolean tabsChanged = false;
        if (mGroups.length != tabCount) {
            tabsChanged = true;
        } else if (testFieldGroupCount > 1) {
            // check if the tab names change (including if groups were reordered). this isn't
            // relevant for a single group since we don't actually show the name from the
            // preference.
            for (int i = 0; i < testFieldGroupCount; i++) {
                if (!TextUtils.equals(mGroups[i].mTitle, getGroupDisplayName(this, i))) {
                    tabsChanged = true;
                    break;
                }
            }
        }
        if (tabsChanged) {
            setTabs(tabHost);
        }

        tabHost.setTabWidgetVisible(mGroups.length > 1);

        if (mCurrentTabIndex >= testFieldGroupCount) {
            return;
        }

        View currentView = mTabViews.get(mCurrentTabIndex);
        if (currentView == null) {
            return;
        }

        // build or rebuild the list of fields for the current tab in case any were added or removed
        // and update the ui. don't bother updating fields for the other tabs since we might never
        // go to them or they may change before we do.
        updateFields(mCurrentTabIndex, currentView);
    }

    private void updateFields(int groupIndex, View currentView) {
        //TODO: (EW) see if we can determine which field has focus and assuming it still exists,
        // ensure that it doesn't get removed and just insert/shift fields around it

        LinearLayout testFieldContainer = currentView.findViewById(R.id.testFieldContainer);
        List<TestField> fieldsOnLayout = mGroups[groupIndex].mTestFields;

        int groupFieldCount = Settings.getTestFieldCount(groupIndex);
        final List<TestField> newFields = new ArrayList<>();

        // compare the current fields listed in the settings to the fields that are already in the
        // layout. starting at the point that the fields differ (added, removed, or reordered - not
        // considering changes in the settings of the same field), try to find the new field
        // (somewhere else in the group, in another group, or orphaned from lazy loading) or crate a
        // new field
        boolean hasChanges = false;
        for (int fieldIndex = 0; fieldIndex < groupFieldCount; fieldIndex++) {
            TestField testField = null;
            int fieldId = Settings.getTestFieldId(groupIndex, fieldIndex);

            // see if the field already existed to be able to keep using it
            FieldPosition position = findFieldId(fieldId, groupIndex);
            if (position != null) {
                testField = mGroups[position.groupIndex].mTestFields.get(position.fieldIndex);
                if (groupIndex != position.groupIndex || fieldIndex != position.fieldIndex) {
                    hasChanges = true;
                }
                if (groupIndex != position.groupIndex) {
                    // remove from other group
                    ViewGroup parent = (ViewGroup) testField.mLayout.getParent();
                    if (parent != null) {
                        parent.removeView(testField.mLayout);
                    }
                    mGroups[position.groupIndex].mTestFields.remove(position.fieldIndex);
                }
            } else {
                hasChanges = true;
                // check if the field already exists (got removed from some group and wasn't loaded
                // yet for the group it moved to due to lazy loading)
                testField = mAllTestFields.get(fieldId);
                if (testField != null) {
                    ViewGroup parent = (ViewGroup) testField.mLayout.getParent();
                    if (parent != null) {
                        parent.removeView(testField.mLayout);
                    }
                }
            }

            if (testField == null) {
                testField = new TestField(fieldId, this);
                mAllTestFields.put(fieldId, testField);
                hasChanges = true;
            }

            if (hasChanges) {
                newFields.add(testField);
            }
        }

        // only add/remove fields starting where there was a change to avoid messing with things
        // like focus
        int changeStartIndex = groupFieldCount - newFields.size();
        while (fieldsOnLayout.size() > changeStartIndex) {
            int fieldIndex = fieldsOnLayout.size() - 1;
            testFieldContainer.removeView(fieldsOnLayout.get(fieldIndex).mLayout);
            fieldsOnLayout.remove(fieldIndex);
        }
        for (TestField testField : newFields) {
            testFieldContainer.addView(testField.mLayout);
            fieldsOnLayout.add(testField);

            int fieldIndex = fieldsOnLayout.size() - 1;
            testField.mCustomEditText.mCustomEditText.setSettings(
                    Settings.getTestFieldSettings(groupIndex, fieldIndex));
        }

        boolean showReferenceEditText = Settings.getShowReferenceEditText();
        currentView.findViewById(R.id.edittext_type_column_labels)
                .setVisibility(showReferenceEditText ? View.VISIBLE : View.GONE);

        boolean showFieldQuickSettingsButton = Settings.getShowFieldQuickSettingsButton();
        currentView.findViewById(R.id.quick_settings_header_space)
                .setVisibility(showFieldQuickSettingsButton ? View.VISIBLE : View.GONE);

        // update the settings for the individual fields
        for (int fieldIndex = 0; fieldIndex < fieldsOnLayout.size(); fieldIndex++) {
            TestField testField = fieldsOnLayout.get(fieldIndex);

            TestFieldSettings fieldSettings = Settings.getTestFieldSettings(groupIndex, fieldIndex);

            testField.mLabel.setText(fieldSettings.getLabelText());
            testField.mFloatHintAsLabel = fieldSettings.shouldFloatHintAsLabel();

            updateField(testField.mFrameworkEditText, groupIndex, fieldIndex);
            ((ViewGroup)testField.mFrameworkEditText.getView().getParent())
                    .setVisibility(showReferenceEditText ? View.VISIBLE : View.GONE);

            testField.updateLabelVisibility();

            updateField(testField.mCustomEditText, groupIndex, fieldIndex);

            testField.mQuickSettingsButton.setVisibility(
                    showFieldQuickSettingsButton ? View.VISIBLE : View.GONE);
        }
    }

    private FieldPosition findFieldId(int fieldId, int startGroupIndex) {
        for (int i = 0; i < mGroups.length; i++) {
            int groupIndex;
            if (i == 0) {
                groupIndex = startGroupIndex;
            } else if (i <= startGroupIndex) {
                groupIndex = i - 1;
            } else {
                groupIndex = i;
            }
            List<TestField> fields = mGroups[groupIndex].mTestFields;
            for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
                if (fields.get(fieldIndex).mId == fieldId) {
                    return new FieldPosition(groupIndex, fieldIndex);
                }
            }
        }
        return null;
    }

    private static void updateField(EditTextProxy editText, int groupIndex, int fieldIndex) {
        //TODO: (EW) use the settings object tied to the text field, rather than look up the value
        // by the index in order to consolidate logic

        TestFieldSettings fieldSettings = Settings.getTestFieldSettings(groupIndex, fieldIndex);

        // since we have a custom setting for making a null input type field still allow multiple
        // lines (which is normally handled as part of the input type), we'll need to trigger
        // setting the input type (even if that didn't change) to trigger a change in the field
        // allowing multiple lines if that setting changed. also, since the input type isn't always
        // set to exactly what we try to set it to, we need to check if the setting for the input
        // type matches what we last requested (rather than what it actually is) to avoid trying to
        // set again unnecessarily.
        int inputType = fieldSettings.getInputType();
        boolean nullInputTypeSingleLine = !fieldSettings.getNullInputTypeMultiline();
        if (editText.getRequestedInputType() != inputType
                || (inputType == InputType.TYPE_NULL
                        && editText.isSingleLine() != nullInputTypeSingleLine
                        && editText.isCustom())) {
            editText.setInputType(inputType);
        }

        int imeOptions = fieldSettings.getImeOptions();
        if (editText.getImeOptions() != imeOptions) {
            editText.setImeOptions(imeOptions);
        }

        int imeActionId = fieldSettings.getImeActionId();
        String imeActionLabel = fieldSettings.getImeActionLabel();
        int currentImeActionId = editText.getImeActionId();
        CharSequence currentImeActionLabel = editText.getImeActionLabel();
        if (currentImeActionId != imeActionId
                || !TextUtils.equals(currentImeActionLabel, imeActionLabel)) {
            editText.setImeActionLabel(imeActionLabel, imeActionId);
        }

        String privateImeOptions = fieldSettings.getPrivateImeOptions();
        if (!TextUtils.equals(editText.getPrivateImeOptions(), privateImeOptions)) {
            editText.setPrivateImeOptions(privateImeOptions);
        }

        boolean selectAllOnFocus = fieldSettings.shouldSelectAllOnFocus();
        if (editText.getSelectAllOnFocus() != selectAllOnFocus) {
            editText.setSelectAllOnFocus(selectAllOnFocus);
        }

        int maxLength = fieldSettings.getMaxLength();
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

        boolean allowUndo = fieldSettings.shouldAllowUndo();
        if (editText.getAllowUndo() != allowUndo) {
            editText.setAllowUndo(allowUndo);
        }

        Locale[] textLocales = fieldSettings.getTextLocales();
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

        Locale[] imeHintLocales = fieldSettings.getImeHintLocales();
        Locale[] currentImeHintLocales = editText.getImeHintLocales();
        if (!equals(currentImeHintLocales, imeHintLocales)) {
            editText.setImeHintLocales(imeHintLocales);
        }

        CharSequence defaultText = fieldSettings.getDefaultText();
        if (!editText.wasTextSet(defaultText)) {
            editText.setText(defaultText);
        }

        CharSequence hint = fieldSettings.getHintText();
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

    private static class TestFieldParcelable implements Parcelable {
        private final int mId;
        private final int mDebugIndex;
        private final CharSequence mFrameworkText;
        private final CharSequence mCustomText;
        private final CharSequence mFrameworkSetText;
        private final CharSequence mCustomSetText;
        private final boolean mFrameworkHasFocus;
        private final boolean mCustomHasFocus;
        private final int mFrameworkCursorStart;
        private final int mCustomCursorStart;
        private final int mFrameworkCursorEnd;
        private final int mCustomCursorEnd;

        public TestFieldParcelable(TestField testField) {
            mId = testField.mId;
            mDebugIndex = -1;
            mFrameworkText = testField.mFrameworkEditText.getText();
            mCustomText = testField.mCustomEditText.getText();
            mFrameworkSetText = testField.mFrameworkEditText.mSetText;
            mCustomSetText = testField.mCustomEditText.mSetText;
            mFrameworkHasFocus = testField.mFrameworkEditText.hasFocus();
            mCustomHasFocus = testField.mCustomEditText.hasFocus();
            mFrameworkCursorStart = testField.mFrameworkEditText.getSelectionStart();
            mCustomCursorStart = testField.mCustomEditText.getSelectionStart();
            mFrameworkCursorEnd = testField.mFrameworkEditText.getSelectionEnd();
            mCustomCursorEnd = testField.mCustomEditText.getSelectionEnd();
        }

        protected TestFieldParcelable(Parcel in) {
            mId = in.readInt();
            mDebugIndex = in.readInt();
            mFrameworkText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            mCustomText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            mFrameworkSetText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            mCustomSetText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            mFrameworkHasFocus = in.readInt() == 1;
            mCustomHasFocus = in.readInt() == 1;
            mFrameworkCursorStart = in.readInt();
            mCustomCursorStart = in.readInt();
            mFrameworkCursorEnd = in.readInt();
            mCustomCursorEnd = in.readInt();
        }

        public static final Creator<TestFieldParcelable> CREATOR =
                new Creator<TestFieldParcelable>() {
                    @Override
                    public TestFieldParcelable createFromParcel(Parcel in) {
                        return new TestFieldParcelable(in);
                    }

                    @Override
                    public TestFieldParcelable[] newArray(int size) {
                        return new TestFieldParcelable[size];
                    }
                };

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mId);
            dest.writeInt(mDebugIndex);
            TextUtils.writeToParcel(mFrameworkText, dest, 0);
            TextUtils.writeToParcel(mCustomText, dest, 0);
            TextUtils.writeToParcel(mFrameworkSetText, dest, 0);
            TextUtils.writeToParcel(mCustomSetText, dest, 0);
            dest.writeInt(mFrameworkHasFocus ? 1 : 0);
            dest.writeInt(mCustomHasFocus ? 1 : 0);
            dest.writeInt(mFrameworkCursorStart);
            dest.writeInt(mCustomCursorStart);
            dest.writeInt(mFrameworkCursorEnd);
            dest.writeInt(mCustomCursorEnd);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_CURRENT_TAB_INDEX, mCurrentTabIndex);

        TestFieldParcelable[] testFieldParcelables = new TestFieldParcelable[mAllTestFields.size()];
        int testFieldIndex = 0;
        for (int fieldId : mAllTestFields.keySet()) {
            TestField testField = mAllTestFields.get(fieldId);
            if (testField != null) {
                testFieldParcelables[testFieldIndex++] = new TestFieldParcelable(testField);
            }
        }
        outState.putParcelableArray(STATE_ALL_TEST_FIELDS, testFieldParcelables);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int currentTabIndex = savedInstanceState.getInt(STATE_CURRENT_TAB_INDEX);
        final TabHost tabHost = findViewById(R.id.tabHost);
        tabHost.setCurrentTab(currentTabIndex);

        TestFieldParcelable[] testFieldParcelables = getTestFieldParcelableArray(savedInstanceState,
                STATE_ALL_TEST_FIELDS);
        if (testFieldParcelables != null) {
            for (TestFieldParcelable parcelable : testFieldParcelables) {
                TestField testField = mAllTestFields.get(parcelable.mId);
                if (testField == null) {
                    testField = new TestField(parcelable.mId, this);
                    mAllTestFields.put(parcelable.mId, testField);
                }
                testField.mFrameworkEditText.setText(parcelable.mFrameworkText);
                testField.mFrameworkEditText.mSetText = parcelable.mFrameworkSetText;
                testField.mFrameworkEditText.setSelection(
                        parcelable.mFrameworkCursorStart, parcelable.mFrameworkCursorEnd);
                if (parcelable.mFrameworkHasFocus) {
                    testField.mFrameworkEditText.requestFocus();
                }
                testField.mCustomEditText.setText(parcelable.mCustomText);
                testField.mCustomEditText.mSetText = parcelable.mCustomSetText;
                testField.mCustomEditText.setSelection(
                        parcelable.mCustomCursorStart, parcelable.mCustomCursorEnd);
                if (parcelable.mCustomHasFocus) {
                    testField.mCustomEditText.requestFocus();
                }
            }
        }
    }

    private static TestFieldParcelable[] getTestFieldParcelableArray(
            @NonNull Bundle savedInstanceState, String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return savedInstanceState.getParcelableArray(key, TestFieldParcelable.class);
        } else {
            Parcelable[] parcelableArray = savedInstanceState.getParcelableArray(key);
            if (parcelableArray instanceof TestFieldParcelable[]) {
                return (TestFieldParcelable[]) parcelableArray;
            } else if (parcelableArray != null) {
                // for some reason in some cases the array gets typed generically as Parcelable[],
                // but all of the elements are the expected type. move the elements into a new
                // properly typed array in this case.
                boolean allElementsCorrectType = true;
                for (Parcelable parcelable : parcelableArray) {
                    if (parcelable != null && !(parcelable instanceof TestFieldParcelable)) {
                        allElementsCorrectType = false;
                        break;
                    }
                }
                if (allElementsCorrectType) {
                    Log.w(TAG, "Parcelable array type is wrong, but elements are correct");
                    TestFieldParcelable[] testFieldParcelables =
                            new TestFieldParcelable[parcelableArray.length];
                    for (int i = 0; i < parcelableArray.length; i++) {
                        testFieldParcelables[i] = (TestFieldParcelable) parcelableArray[i];
                    }
                    return testFieldParcelables;
                } else {
                    Log.e(TAG, "Unexpected Parcelable[]: " + parcelableArray.getClass());
                }
            }
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFields();
    }

    @Override
    protected void onDestroy() {
        EdgeToEdgeUtils.removeInsetHandling(this);
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
        private final com.wittmane.testingedittext.aosp.android.widget.EditText mCustomEditText;

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

        public EditTextProxy(
                @NonNull com.wittmane.testingedittext.aosp.android.widget.EditText editText) {
            mCustomEditText = editText;
            mFrameworkEditText = null;
            mRequestedInputType = editText.getInputType();
            mDefaultTextLocales = getTextLocales();
        }

        public View getView() {
            if (mFrameworkEditText != null) {
                return mFrameworkEditText;
            } else {
                return mCustomEditText;
            }
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

        public Editable getText() {
            return mFrameworkEditText != null
                    ? mFrameworkEditText.getText()
                    : mCustomEditText.getText();
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

        public int getSelectionStart() {
            return mFrameworkEditText != null
                    ? mFrameworkEditText.getSelectionStart()
                    : mCustomEditText.getSelectionStart();
        }

        public int getSelectionEnd() {
            return mFrameworkEditText != null
                    ? mFrameworkEditText.getSelectionEnd()
                    : mCustomEditText.getSelectionEnd();
        }

        public void setSelection(int start, int stop) {
            if (mFrameworkEditText != null) {
                mFrameworkEditText.setSelection(start, stop);
            } else {
                mCustomEditText.setSelection(start, stop);
            }
        }

        public boolean hasFocus() {
            return mFrameworkEditText != null
                    ? mFrameworkEditText.hasFocus()
                    : mCustomEditText.hasFocus();
        }

        public boolean requestFocus() {
            return mFrameworkEditText != null
                    ? mFrameworkEditText.requestFocus()
                    : mCustomEditText.requestFocus();
        }

        public void addTextChangedListener(TextWatcher watcher) {
            if (mFrameworkEditText != null) {
                mFrameworkEditText.addTextChangedListener(watcher);
            } else {
                mCustomEditText.addTextChangedListener(watcher);
            }
        }
    }
}