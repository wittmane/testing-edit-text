/*
 * Copyright (C) 2024-2025 Eli Wittman
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

import static com.wittmane.testingedittext.settings.Settings.getFieldDisplayName;
import static com.wittmane.testingedittext.settings.Settings.getGroupDisplayName;
import static com.wittmane.testingedittext.settings.Settings.getTestFieldId;
import static com.wittmane.testingedittext.settings.fragments.TestFieldGroupSettingsFragment.ARE_GROUPS_USED_BUNDLE_KEY;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.Settings.FieldIdGroup;
import com.wittmane.testingedittext.settings.preferences.PerTestGroupPreference;
import com.wittmane.testingedittext.util.ResourceUtils;
import com.wittmane.testingedittext.widget.DraggableGroupedListAdapter;

public class TestFieldGroupListSettingsFragment extends SettingsFragment {
    private static final String TAG = TestFieldGroupListSettingsFragment.class.getSimpleName();

    private static final String STATE_AUTO_LAUNCHED_ONLY_GROUP = "AUTO_LAUNCHED_ONLY_GROUP";

    private View mView;

    private static boolean mUseGroups = false;
    private boolean mAutoLaunchedOnlyGroup = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mAutoLaunchedOnlyGroup = savedInstanceState.getBoolean(STATE_AUTO_LAUNCHED_ONLY_GROUP);
        }

        addPreferencesFromResource(R.xml.preference_screen_empty);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);

        if (!mUseGroups) {
            if (Settings.getTestFieldGroupCount() == 1) {
                // since there is only a single group and the user hasn't interacted with any groups
                // since first opening this fragment, there isn't much value in showing a preference
                // screen to show the single group
                if (mAutoLaunchedOnlyGroup) {
                    // we just backed out of the group preference that we auto-launched, so we go to
                    // the previous fragment to continue skipping the unnecessary groups setting
                    getFragmentManager().popBackStack();
                } else {
                    // jump directly into the only group
                    mAutoLaunchedOnlyGroup = true;
                    IndividualTestFieldGroupPreference pref =
                            new IndividualTestFieldGroupPreference(getActivity(), 0);
                    pref.setAreGroupsUsed(false);
                    ((OnPreferenceStartFragmentCallback)getActivity()).onPreferenceStartFragment(
                            this, pref);
                }
            } else {
                mUseGroups = true;
            }
        }
        if (mUseGroups) {
            buildContent();
        }

        return mView;
    }

    @Override
    protected void onRedisplay() {
        buildContent();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putBoolean(STATE_AUTO_LAUNCHED_ONLY_GROUP, mAutoLaunchedOnlyGroup);
    }

    @Override
    protected void onCreateOptionsMenuInternal(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.test_field_group_list, menu);

        if (Settings.getTestFieldGroupCount() < 2) {
            menu.removeItem(R.id.action_reorder_groups);
        }
    }

    static void openGroupPreference(PreferenceFragment currentFragment, int groupIndex) {
        Preference newPref = new IndividualTestFieldGroupPreference(currentFragment.getActivity(),
                groupIndex);
        // launch sub setting screen for the new field group preference
        launchPrefFragment(currentFragment, newPref);
    }

    static void launchPrefFragment(PreferenceFragment currentFragment, Preference pref) {
        ((OnPreferenceStartFragmentCallback)currentFragment.getActivity())
                .onPreferenceStartFragment(currentFragment, pref);
    }

    @Override
    protected boolean onOptionsItemSelectedInternal(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_add_group) {
            // add a preference for a new group
            Settings.addTestFieldGroup();

            openGroupPreference(this, Settings.getTestFieldGroupCount() - 1);
        } else if (itemId == R.id.action_reorder_groups) {
            showReorderGroupsDialog();
        }
        return super.onOptionsItemSelectedInternal(item);
    }

    private void showReorderGroupsDialog() {
        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        ListView listView = new ListView(getActivity());
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1));

        int dialogHorizontalPadding = ResourceUtils.getDimensionPixels(
                R.attr.dialogPreferredPaddingHorizontal, getActivity());
        listView.setPadding(dialogHorizontalPadding, 0, dialogHorizontalPadding, 0);
        listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        DraggableGroupedListAdapter<GroupEntry, FieldEntry> adapter =
                new DraggableGroupedListAdapter<>(getActivity(),
                        (view, group, field) -> {
                            TextView titleView = view.findViewById(R.id.title);
                            titleView.setText(field != null
                                    ? field.getDisplayName()
                                    : group.getDisplayName());
                        });
        for (int groupIndex = 0; groupIndex < Settings.getTestFieldGroupCount(); groupIndex++) {
            adapter.addGroup(new GroupEntry(getActivity(), groupIndex));
            int fieldCount = Settings.getTestFieldCount(groupIndex);
            for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                adapter.addItem(groupIndex,
                        new FieldEntry(getActivity(), groupIndex, fieldIndex));
            }
        }
        listView.setAdapter(adapter);
        layout.addView(listView);

        int negativeCheckboxIconPadding = ResourceUtils.getDimensionPixels(
                R.attr.checkboxIconPaddingNegation, getActivity());
        CheckBox checkBox = new CheckBox(getActivity());
        LinearLayout.LayoutParams checkBoxLayoutParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0);
        checkBoxLayoutParams.setMarginStart(dialogHorizontalPadding + negativeCheckboxIconPadding);
        checkBoxLayoutParams.setMarginEnd(dialogHorizontalPadding);
        checkBox.setLayoutParams(checkBoxLayoutParams);
        checkBox.setText(R.string.expand_groups);
        checkBox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> adapter.expandGroups(isChecked));
        layout.addView(checkBox);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.reorder_groups)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    FieldIdGroup[] testGroups = new FieldIdGroup[adapter.getGroupCount()];
                    for (int groupIndex = 0; groupIndex < testGroups.length; groupIndex++) {
                        int[] fieldIds = new int[adapter.getItemCount(groupIndex)];
                        for (int fieldIndex = 0; fieldIndex < fieldIds.length; fieldIndex++) {
                            fieldIds[fieldIndex] = adapter.getItem(groupIndex, fieldIndex).getId();
                        }
                        testGroups[groupIndex] = new FieldIdGroup(
                                adapter.getGroup(groupIndex).getId(),
                                fieldIds);
                    }
                    Settings.setTestGroupAndFieldIds(testGroups);
                    buildContent();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    private static class GroupEntry {
        private final int mGroupId;
        private final String mDisplayName;

        public GroupEntry(Context context, int groupIndex) {
            mGroupId = Settings.getTestGroupId(groupIndex);
            mDisplayName = getGroupDisplayName(context, groupIndex);
        }

        public int getId() {
            return mGroupId;
        }

        public String getDisplayName() {
            return mDisplayName;
        }
    }

    static class FieldEntry {
        private final int mFieldId;
        private final String mDisplayName;

        public FieldEntry(Context context, int groupIndex, int fieldIndex) {
            mFieldId = getTestFieldId(groupIndex, fieldIndex);
            mDisplayName = getFieldDisplayName(context, groupIndex, fieldIndex).toString();
        }

        public int getId() {
            return mFieldId;
        }

        public String getDisplayName() {
            return mDisplayName;
        }
    }

    /**
     * Build the preferences and them to this settings screen.
     */
    private void buildContent() {
        final Context context = getActivity();
        final PreferenceGroup group = getPreferenceScreen();
        group.removeAll();

        for (int i = 0; i < Settings.getTestFieldGroupCount(); i++) {
            group.addPreference(new IndividualTestFieldGroupPreference(context, i));
        }
    }

    /**
     * Preference to link to the main settings screen for a specific test field group.
     */
    private static class IndividualTestFieldGroupPreference extends PerTestGroupPreference {
        private boolean mAreGroupsUsed = true;

        /**
         * Create a new preference for a test field group.
         * @param context the context for this application.
         * @param groupIndex the index of the group in the UI.
         */
        public IndividualTestFieldGroupPreference(final Context context, final int groupIndex) {
            super(context, groupIndex);

            setFragment(TestFieldGroupSettingsFragment.class.getName());
        }

        @Override
        protected void updateDisplayText() {
            Context context = getContext();
            int groupIndex = getGroupIndex();
            setTitle(getGroupDisplayName(context, groupIndex));
            setSummary(context.getString(R.string.field_count,
                    Settings.getTestFieldCount(groupIndex)));
        }

        public void setAreGroupsUsed(boolean areGroupsUsed) {
            mAreGroupsUsed = areGroupsUsed;
        }

        @Override
        public Bundle getExtras() {
            Bundle extras = super.getExtras();
            if (extras == null) {
                extras = new Bundle();
            }
            extras.putBoolean(ARE_GROUPS_USED_BUNDLE_KEY, mAreGroupsUsed);
            return extras;
        }
    }
}
