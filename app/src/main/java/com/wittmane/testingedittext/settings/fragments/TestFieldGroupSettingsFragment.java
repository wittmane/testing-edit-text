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

package com.wittmane.testingedittext.settings.fragments;

import static com.wittmane.testingedittext.settings.Settings.GROUP_INFIX;
import static com.wittmane.testingedittext.settings.Settings.PREF_TEST_GROUP_NAME_PREFIX;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.DraggableListAdapter;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.fragments.TestFieldGroupListSettingsFragment.IndividualTestFieldGroupPreference;
import com.wittmane.testingedittext.settings.preferences.PerTestFieldPreference;
import com.wittmane.testingedittext.settings.preferences.ImeActionPreference;
import com.wittmane.testingedittext.settings.preferences.ImeOptionsPreference;
import com.wittmane.testingedittext.settings.preferences.InputTypePreference;
import com.wittmane.testingedittext.settings.preferences.TextDialogPreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TestFieldGroupSettingsFragment extends PerTestGroupSettingsFragment {
    private static final String TAG = TestFieldGroupSettingsFragment.class.getSimpleName();

    public static final String ARE_GROUPS_USED_BUNDLE_KEY = "ARE_GROUPS_USED";

    private static final String PREF_KEY_TEST_GROUP_TEST_FIELDS = "pref_key_test_group_test_fields";

    private View mView;
    private boolean mAreGroupsUsed = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_empty);

        final Bundle args = getArguments();
        mAreGroupsUsed = args == null || args.getBoolean(ARE_GROUPS_USED_BUNDLE_KEY);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);
        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        buildContent();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.test_field_list, menu);

        ActionBar actionBar = getActivity().getActionBar();
        MenuItem addFieldMenuItem = menu.findItem(R.id.action_add_field);
        IconUtils.matchMenuIconColor(mView, addFieldMenuItem, actionBar);
        MenuItem reorderFieldsMenuItem = menu.findItem(R.id.action_reorder_fields);
        IconUtils.matchMenuIconColor(mView, reorderFieldsMenuItem, actionBar);
        if (mAreGroupsUsed) {
            menu.removeItem(R.id.action_add_group);
            MenuItem removeGroupMenuItem = menu.findItem(R.id.action_remove_group);
            IconUtils.matchMenuIconColor(mView, removeGroupMenuItem, actionBar);
        } else {
            menu.removeItem(R.id.action_remove_group);
            MenuItem addGroupMenuItem = menu.findItem(R.id.action_add_group);
            IconUtils.matchMenuIconColor(mView, addGroupMenuItem, actionBar);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_add_group) {
            // add a preference for a new group
            Settings.addTestFieldGroup();
            // exit this group before opening the new group
            getFragmentManager().popBackStackImmediate();
            // create the preference to manage launching the group preference screen
            Preference newPref = new IndividualTestFieldGroupPreference(getActivity(),
                    Settings.getTestFieldGroupCount() - 1);
            // launch sub setting screen for the new field group preference
            ((OnPreferenceStartFragmentCallback)getActivity()).onPreferenceStartFragment(
                    this, newPref);
        } else if (itemId == R.id.action_add_field) {
            // add a preference for a new field
            Settings.addTestField(getGroupIndex());
            final PreferenceGroup group = (PreferenceGroup) getPreferenceScreen().findPreference(
                    PREF_KEY_TEST_GROUP_TEST_FIELDS);
            Preference newPref = new IndividualTestFieldPreference(getActivity(),
                    getGroupIndex(), group.getPreferenceCount());
            group.addPreference(newPref);
            // launch sub setting screen for the new field preference
            ((OnPreferenceStartFragmentCallback)getActivity()).onPreferenceStartFragment(
                    this, newPref);
        } else if (itemId == R.id.action_reorder_fields) {
            ListView content = new ListView(getActivity());
            DraggableListAdapter<TestField> adapter = new DraggableListAdapter<>(getActivity(),
                    new DraggableListAdapter.ListItemBuilder<TestField>() {
                        @Override
                        public void populateView(View view, TestField item) {
                            TextView titleView = view.findViewById(R.id.title);
                            titleView.setText(item.mTitle);
                        }
                    });
            for (int i = 0; i < Settings.getTestFieldCount(getGroupIndex()); i++) {
                adapter.add(new TestField(getActivity(), getGroupIndex(), i));
            }
            content.setAdapter(adapter);

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reorder_fields)
                    .setView(content)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    int[] testFields;
                                    testFields = new int[adapter.getCount()];
                                    for (int fieldIndex = 0; fieldIndex < adapter.getCount(); fieldIndex++) {
                                        testFields[fieldIndex] = adapter.getItem(fieldIndex).getId();
                                    }
                                    Settings.setTestGroupFieldIds(getGroupIndex(), testFields);
                                    buildContent();
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                }
                            })
                    .create();
            dialog.show();
        } else if (itemId == R.id.action_remove_group) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.delete_group)
                    .setMessage(R.string.delete_group_confirmation)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                            int which) {
                            // remove the group and go back to the field list
                            Settings.removeTestFieldGroup(getGroupIndex());
                            getFragmentManager().popBackStackImmediate();
                        }
                    })
                    .setNegativeButton(android.R.string.no, null).show();

        }
        return super.onOptionsItemSelected(item);
    }

    private static class TestField {
        private final CharSequence mTitle;
        private final int mId;

        public TestField(Context context, int groupIndex, int fieldIndex) {
            mTitle = getFieldTitle(context, groupIndex, fieldIndex);
            mId = Settings.getTestFieldId(groupIndex, fieldIndex);
        }

        public int getId() {
            return mId;
        }

        public CharSequence getTitle() {
            return mTitle;
        }
    }

    /**
     * Build the preferences and them to this settings screen.
     */
    private void buildContent() {
        final Context context = getActivity();
        final PreferenceGroup group = getPreferenceScreen();
        group.removeAll();

        if (mAreGroupsUsed) {
            TextDialogPreference namePref = new TextDialogPreference(context, null);
            int groupId = Settings.getTestGroupId(getGroupIndex());
            namePref.setKey(PREF_TEST_GROUP_NAME_PREFIX + GROUP_INFIX + groupId);
            namePref.setTitle(context.getString(R.string.group_name));
            namePref.setDialogTitle(context.getString(R.string.group_name));
            group.addPreference(namePref);
        }

        PreferenceCategory testFieldPrefCategory = new PreferenceCategory(context);
        testFieldPrefCategory.setTitle(R.string.test_field_list_screen);
        testFieldPrefCategory.setKey(PREF_KEY_TEST_GROUP_TEST_FIELDS);
        group.addPreference(testFieldPrefCategory);

        for (int i = 0; i < Settings.getTestFieldCount(getGroupIndex()); i++) {
            testFieldPrefCategory.addPreference(
                    new IndividualTestFieldPreference(context, getGroupIndex(), i));
        }
    }

    static CharSequence getFieldTitle(final Context context, final int groupIndex,
                                              final int fieldIndex) {
        CharSequence defaultText = Settings.getTestFieldDefaultText(groupIndex, fieldIndex);
        if (!TextUtils.isEmpty(defaultText)) {
            return defaultText;
        } else {
            CharSequence hintText = Settings.getTestFieldHintText(groupIndex, fieldIndex);
            if (!TextUtils.isEmpty(hintText)) {
                return hintText;
            } else {
                return context.getString(R.string.test_field_default_name, (fieldIndex + 1));
            }
        }
    }

    /**
     * Preference to link to the main settings screen for a specific test field.
     */
    private static class IndividualTestFieldPreference extends PerTestFieldPreference {

        /**
         * Create a new preference for a test field.
         * @param context the context for this application.
         * @param groupIndex the index of the group in the UI.
         * @param fieldIndex the index of the field in the UI.
         */
        public IndividualTestFieldPreference(final Context context, final int groupIndex,
                                             final int fieldIndex) {
            super(context);
            setFieldIndex(groupIndex, fieldIndex);

            setFragment(TestFieldSettingsFragment.class.getName());
        }

        @Override
        protected void updateSummary() {
            Context context = getContext();
            int groupIndex = getGroupIndex();
            int fieldIndex = getFieldIndex();
            setTitle(getFieldTitle(context, groupIndex, fieldIndex));
            String[] summaryInfo = new String[] {
                    getLabeledProperty(R.string.input_type,
                            InputTypePreference.getInputTypeDescription(groupIndex, fieldIndex,
                                    context),
                            context),
                    getLabeledProperty(R.string.ime_options,
                            ImeOptionsPreference.getImeOptionsDescription(groupIndex, fieldIndex,
                                    context),
                            context),
                    getLabeledProperty(R.string.ime_action,
                            ImeActionPreference.getImeActionDescription(groupIndex, fieldIndex,
                                    context),
                            context),
                    getLabeledPrivateImeOptions(
                            Settings.getTestFieldPrivateImeOptions(groupIndex, fieldIndex),
                            context),
                    Settings.shouldTestFieldSelectAllOnFocus(groupIndex, fieldIndex)
                            ? context.getString(R.string.select_all_on_focus) : null,
                    getLabeledMaxLength(Settings.getTestFieldMaxLength(groupIndex, fieldIndex),
                            context),
                    Settings.shouldTestFieldAllowUndo(groupIndex, fieldIndex)
                            ? context.getString(R.string.allow_undo) : null,
                    getLabeledTextLocales(
                            Settings.getTestFieldTextLocales(groupIndex, fieldIndex), context),
                    getLabeledImeHintLocales(
                            Settings.getTestFieldImeHintLocales(groupIndex, fieldIndex), context)
            };
            StringBuilder sb = new StringBuilder();
            for (String summaryPiece : summaryInfo) {
                if (summaryPiece == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(summaryPiece);
            }
            setSummary(sb);
        }

        private static String getLabeledProperty(int titleRes, String description,
                                                 Context context) {
            if (TextUtils.isEmpty(description)) {
                return null;
            }
            return context.getString(R.string.test_field_property_description,
                    context.getString(titleRes), description);
        }

        private static String getLabeledPrivateImeOptions(String privateImeOptions,
                                                          Context context) {
            return getLabeledProperty(R.string.private_ime_options, privateImeOptions, context);
        }

        private static String getLabeledMaxLength(int maxLength, Context context) {
            return maxLength >= 0
                    ? getLabeledProperty(R.string.max_length, "" + maxLength, context)
                    : null;
        }

        private static String getLabeledTextLocales(Locale[] textLocales, Context context) {
            return getLabeledLocales(R.string.text_locales, textLocales, context);
        }

        private static String getLabeledImeHintLocales(Locale[] imeHintLocales, Context context) {
            return getLabeledLocales(R.string.ime_hint_locales, imeHintLocales, context);
        }

        private static String getLabeledLocales(int titleRes, Locale[] locales, Context context) {
            if (locales == null || locales.length < 1) {
                return null;
            }
            List<String> localeDisplayNames = new ArrayList<>();
            for (Locale locale : locales) {
                localeDisplayNames.add(locale.getDisplayName());
            }
            return getLabeledProperty(titleRes, getDescription(localeDisplayNames, context),
                    context);
        }
    }
}
