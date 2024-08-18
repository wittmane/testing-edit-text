/*
 * Copyright (C) 2024 Eli Wittman
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

import static com.wittmane.testingedittext.settings.Settings.getTestFieldId;
import static com.wittmane.testingedittext.settings.fragments.TestFieldListSettingsFragment.getFieldTitle;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.DraggableGroupedListAdapter;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.Settings.FieldIdGroup;
import com.wittmane.testingedittext.settings.preferences.PerTestGroupPreference;

//TODO: (EW) reduce duplicate code with TestFieldListSettingsFragment
public class TestFieldGroupListSettingsFragment extends PreferenceFragment {
    private static final String TAG = TestFieldGroupListSettingsFragment.class.getSimpleName();
    private View mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_empty);
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
        inflater.inflate(R.menu.test_field_group_list, menu);

        ActionBar actionBar = getActivity().getActionBar();
        MenuItem addGroupMenuItem = menu.findItem(R.id.action_add_group);
        IconUtils.matchMenuIconColor(mView, addGroupMenuItem, actionBar);
        MenuItem reorderGroupsMenuItem = menu.findItem(R.id.action_reorder_groups);
        IconUtils.matchMenuIconColor(mView, reorderGroupsMenuItem, actionBar);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_add_group) {
            // add a preference for a new group
            Settings.addTestFieldGroup();
            final PreferenceGroup group = getPreferenceScreen();
            Preference newPref = new IndividualTestFieldGroupPreference(getActivity(),
                    group.getPreferenceCount());
            group.addPreference(newPref);
            // launch sub setting screen for the new field group preference
            ((OnPreferenceStartFragmentCallback)getActivity()).onPreferenceStartFragment(
                    this, newPref);
        } else if (itemId == R.id.action_reorder_groups) {
            LinearLayout layout = new LinearLayout(getActivity());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            ListView listView = new ListView(getActivity());
            listView.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, 0, 1));
            DraggableGroupedListAdapter<GroupEntry, FieldEntry> adapter = new DraggableGroupedListAdapter<>(getActivity(),
                    new DraggableGroupedListAdapter.ListItemBuilder<GroupEntry, FieldEntry>() {
                        @Override
                        public void populateView(View view, GroupEntry group, FieldEntry field) {
                            TextView titleView = view.findViewById(R.id.title);
                            titleView.setText(field != null
                                    ? field.getDisplayName()
                                    : group.getDisplayName());
                        }
                    });
            for (int groupIndex = 0; groupIndex < Settings.getTestFieldGroupCount(); groupIndex++) {
                adapter.addGroup(new GroupEntry(getActivity(), groupIndex));
                for (int fieldIndex = 0; fieldIndex < Settings.getTestFieldCount(groupIndex); fieldIndex++) {
                    adapter.addItem(groupIndex,
                            new FieldEntry(getActivity(), groupIndex, fieldIndex));
                }
            }
            listView.setAdapter(adapter);
            layout.addView(listView);

            CheckBox checkBox = new CheckBox(getActivity());
            checkBox.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0));
            checkBox.setText(R.string.expand_groups);
            checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    adapter.expandGroups(isChecked);
                }
            });
            layout.addView(checkBox);

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reorder_groups)
                    .setView(layout)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    FieldIdGroup[] testGroups =
                                            new FieldIdGroup[adapter.getGroupCount()];
                                    for (int i = 0; i < adapter.getGroupCount(); i++) {
                                        int[] fieldIds = new int[adapter.getItemCount(i)];
                                        for (int j = 0; j < adapter.getItemCount(i); j++) {
                                            fieldIds[j] = adapter.getItem(i, j).getId();
                                        }
                                        testGroups[i] = new FieldIdGroup(
                                                adapter.getGroup(i).getId(),
                                                fieldIds);
                                    }
                                    Settings.setTestGroupAndFieldIds(testGroups);
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
        }
        return super.onOptionsItemSelected(item);
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

    private static class FieldEntry {
        int mFieldId;
        private final String mDisplayName;

        public FieldEntry(Context context, int groupIndex, int fieldIndex) {
            mFieldId = getTestFieldId(groupIndex, fieldIndex);
            mDisplayName = getFieldTitle(context, groupIndex, fieldIndex).toString();
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public int getId() {
            return mFieldId;
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

    public static String getGroupDisplayName(final Context context, final int groupIndex) {
        final String groupName = Settings.getTestFieldGroupName(groupIndex);
        if (groupName == null) {
            return context.getString(R.string.test_group_default_name, (groupIndex + 1));
        }
        return groupName;
    }

    /**
     * Preference to link to the main settings screen for a specific test field group.
     */
    private static class IndividualTestFieldGroupPreference extends PerTestGroupPreference {

        /**
         * Create a new preference for a test field group.
         * @param context the context for this application.
         * @param groupIndex the index of the group in the UI.
         */
        public IndividualTestFieldGroupPreference(final Context context, final int groupIndex) {
            super(context, groupIndex);

            setFragment(TestFieldListSettingsFragment.class.getName());
        }

        @Override
        protected void updateSummary() {
            Context context = getContext();
            int groupIndex = getGroupIndex();
            setTitle(getGroupDisplayName(context, groupIndex));
            setSummary(context.getString(R.string.field_count,
                    Settings.getTestFieldCount(groupIndex)));
        }
    }
}
