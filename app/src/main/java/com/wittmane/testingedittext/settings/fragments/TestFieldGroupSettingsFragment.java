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

package com.wittmane.testingedittext.settings.fragments;

import static com.wittmane.testingedittext.settings.PreferenceKeys.*;
import static com.wittmane.testingedittext.settings.Settings.getFieldDisplayName;
import static com.wittmane.testingedittext.settings.fragments.TestFieldGroupListSettingsFragment.openGroupPreference;

import android.content.Context;
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
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.Settings.TestFieldSettings;
import com.wittmane.testingedittext.settings.fragments.TestFieldGroupListSettingsFragment.FieldEntry;
import com.wittmane.testingedittext.settings.preferences.PerTestFieldPreference;
import com.wittmane.testingedittext.settings.preferences.ImeActionPreference;
import com.wittmane.testingedittext.settings.preferences.ImeOptionsPreference;
import com.wittmane.testingedittext.settings.preferences.InputTypePreference;
import com.wittmane.testingedittext.settings.preferences.TextDialogPreference;
import com.wittmane.testingedittext.util.AlertDialogBuilder;
import com.wittmane.testingedittext.util.ResourceUtils;
import com.wittmane.testingedittext.widget.DraggableListAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TestFieldGroupSettingsFragment extends PerTestGroupSettingsFragment {
    private static final String TAG = TestFieldGroupSettingsFragment.class.getSimpleName();

    public static final String ARE_GROUPS_USED_BUNDLE_KEY = "ARE_GROUPS_USED";

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
        buildContent();
        return mView;
    }

    @Override
    protected void onRedisplay() {
        buildContent();
    }

    @Override
    protected void onCreateOptionsMenuInternal(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.test_field_list, menu);

        if (mAreGroupsUsed) {
            menu.removeItem(R.id.action_add_group);
        } else {
            menu.removeItem(R.id.action_remove_group);
        }

        if (getGroupIndex() >= Settings.getTestFieldGroupCount()
                || Settings.getTestFieldCount(getGroupIndex()) < 2) {
            menu.removeItem(R.id.action_reorder_fields);
        }
    }

    @Override
    protected boolean onOptionsItemSelectedInternal(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_add_group) {
            // add a preference for a new group
            Settings.addTestFieldGroup();

            // exit this group and add the group list to the back stack (since that was skipped due
            // to having a single group) before opening the new group so backing out of the new
            // group goes to the group list, rather than this other group
            navigateBack(true, () -> {
                Preference groupListPref = new Preference(getActivity());
                groupListPref.setFragment(TestFieldGroupListSettingsFragment.class.getName());
                launchPrefFragment(groupListPref, () -> {
                    openGroupPreference(this, Settings.getTestFieldGroupCount() - 1, true);
                }, true);
            });
        } else if (itemId == R.id.action_add_field) {
            int groupIndex = getGroupIndex();
            // add a preference for a new field
            Settings.addTestField(groupIndex);

            Preference newPref = new IndividualTestFieldPreference(getActivity(),
                    groupIndex, Settings.getTestFieldCount(groupIndex) - 1);
            // launch sub setting screen for the new field preference
            launchPrefFragment(newPref);
        } else if (itemId == R.id.action_reorder_fields) {
            showReorderFieldsDialog();
        } else if (itemId == R.id.action_remove_group) {
            showWarningConfirmationDialog(R.string.delete_group, R.string.delete_group_confirmation,
                    () -> {
                        // remove the group and go back to the field list
                        Settings.removeTestFieldGroup(getGroupIndex());
                        navigateBack();
                    }, getActivity());

        }
        return super.onOptionsItemSelectedInternal(item);
    }

    private void showReorderFieldsDialog() {
        ListView listView = new ListView(getActivity());
        int dialogHorizontalPadding = ResourceUtils.getDimensionPixels(
                R.attr.dialogPreferredPaddingHorizontal, getActivity());
        listView.setPadding(dialogHorizontalPadding, 0, dialogHorizontalPadding, 0);
        listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        DraggableListAdapter<FieldEntry> adapter = new DraggableListAdapter<>(getActivity(),
                (view, item) -> {
                    TextView titleView = view.findViewById(R.id.title);
                    titleView.setText(item.getDisplayName());
                });
        int fieldCount = Settings.getTestFieldCount(getGroupIndex());
        for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
            adapter.add(new FieldEntry(getActivity(), getGroupIndex(), fieldIndex));
        }
        listView.setAdapter(adapter);

        new AlertDialogBuilder(getActivity())
                .setTitle(R.string.reorder_fields)
                .setView(listView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int[] testFields = new int[adapter.getCount()];
                    for (int fieldIndex = 0; fieldIndex < testFields.length; fieldIndex++) {
                        testFields[fieldIndex] = adapter.getItem(fieldIndex).getId();
                    }
                    Settings.setTestGroupFieldIds(getGroupIndex(), testFields);
                    buildContent();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    static void showWarningConfirmationDialog(int titleId, int messageId, Runnable onConfirm,
                                              Context context) {
        showWarningConfirmationDialog(titleId, context.getString(messageId), onConfirm, context);
    }

    static void showWarningConfirmationDialog(int titleId, String message, Runnable onConfirm,
                                              Context context) {
        new AlertDialogBuilder(context)
                .setTitle(titleId)
                .setMessage(message)
                .setIcon(R.drawable.ic_warning_white_24)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> onConfirm.run())
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    /**
     * Build the preferences and them to this settings screen.
     */
    private void buildContent() {
        final Context context = getActivity();
        final PreferenceGroup group = getPreferenceScreen();
        group.removeAll();

        int groupIndex = getGroupIndex();

        // add the name for the group (only if there are multiple groups since it won't be shown
        // otherwise)
        if (mAreGroupsUsed) {
            TextDialogPreference namePref = new TextDialogPreference(context, null);
            int groupId = Settings.getTestGroupId(groupIndex);
            namePref.setKey(PREF_TEST_GROUP_NAME_PREFIX + GROUP_INFIX + groupId);
            namePref.setTitle(context.getString(R.string.group_name));
            namePref.setDialogTitle(context.getString(R.string.group_name));
            group.addPreference(namePref);
        }

        PreferenceCategory testFieldPrefCategory = new PreferenceCategory(context);
        testFieldPrefCategory.setTitle(R.string.test_field_list_screen);
        group.addPreference(testFieldPrefCategory);

        // add the test fields
        for (int i = 0; i < Settings.getTestFieldCount(groupIndex); i++) {
            testFieldPrefCategory.addPreference(
                    new IndividualTestFieldPreference(context, groupIndex, i));
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
        protected void updateDisplayText() {
            Context context = getContext();
            int groupIndex = getGroupIndex();
            int fieldIndex = getFieldIndex();
            TestFieldSettings fieldSettings = Settings.getTestFieldSettings(groupIndex, fieldIndex);
            setTitle(getFieldDisplayName(context, groupIndex, fieldIndex));
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
                    getLabeledPrivateImeOptions(fieldSettings.getPrivateImeOptions(), context),
                    fieldSettings.shouldSelectAllOnFocus()
                            ? context.getString(R.string.select_all_on_focus)
                            : null,
                    getLabeledMaxLength(fieldSettings.getMaxLength(), context),
                    fieldSettings.shouldAllowUndo() ? context.getString(R.string.allow_undo) : null,
                    getLabeledTextLocales(fieldSettings.getTextLocales(), context),
                    getLabeledImeHintLocales(fieldSettings.getImeHintLocales(), context),
                    fieldSettings.overridesTextInputModification()
                            ? getLabeledProperty(R.string.modify_text_input_screen,
                                    context.getString(R.string.overrides_app_level_defaults),
                                    context)
                            : null,
                    fieldSettings.overridesTextReturn()
                            ? getLabeledProperty(R.string.returning_text_screen,
                                    context.getString(R.string.overrides_app_level_defaults),
                                    context)
                            : null,
                    fieldSettings.overridesTextComposition()
                            ? getLabeledProperty(R.string.composing_text_screen,
                                    context.getString(R.string.overrides_app_level_defaults),
                                    context)
                            : null,
                    fieldSettings.overridesTargetVersion()
                            ? getLabeledProperty(R.string.simulate_old_target_versions_screen,
                                    context.getString(R.string.overrides_app_level_defaults),
                                    context)
                            : null,
                    fieldSettings.overridesSystemBehavior()
                            ? getLabeledProperty(R.string.simulate_system_behavior_screen,
                                    context.getString(R.string.overrides_app_level_defaults),
                                    context)
                            : null
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
