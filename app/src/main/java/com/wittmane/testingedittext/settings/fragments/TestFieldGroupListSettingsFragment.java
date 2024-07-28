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

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.ListView;
import android.widget.TextView;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.DraggableListAdapter;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.Settings.TestGroup;
import com.wittmane.testingedittext.settings.preferences.PerTestGroupPreference;

//TODO: (EW) reduce duplicate code with TestFieldListSettingsFragment
public class TestFieldGroupListSettingsFragment extends PreferenceFragment {
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
            ListView content = new ListView(getActivity());
            DraggableListAdapter<TestFieldGroup> adapter = new DraggableListAdapter<>(getActivity(),
                    new DraggableListAdapter.ListItemBuilder<TestFieldGroup>() {
                        @Override
                        public void populateView(View view, TestFieldGroup item) {
                            TextView titleView = view.findViewById(R.id.title);
                            titleView.setText(item.getDisplayName());
                        }
                    });
            for (int i = 0; i < Settings.getTestFieldGroupCount(); i++) {
                adapter.add(new TestFieldGroup(getActivity(), i));
            }
            content.setAdapter(adapter);

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reorder_groups)
                    .setView(content)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    TestGroup[] testGroups = new TestGroup[adapter.getCount()];
                                    for (int i = 0; i < adapter.getCount(); i++) {
                                        testGroups[i] = new TestGroup(adapter.getItem(i).getName(),
                                                adapter.getItem(i).getFieldIds());
                                    }
                                    Settings.setTestFieldGroups(testGroups);
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

    private static class TestFieldGroup {
        private String mName;
        private String mDisplayName;
        private int[] mFieldIds;

        public TestFieldGroup(Context context, int groupIndex) {
            mName = Settings.getTestFieldGroupName(groupIndex);
            mDisplayName = getGroupDisplayName(context, groupIndex, mName);
            mFieldIds = new int[Settings.getTestFieldCount(groupIndex)];
            for (int fieldIndex = 0; fieldIndex < Settings.getTestFieldCount(groupIndex); fieldIndex++) {
                mFieldIds[fieldIndex] = Settings.getTestFieldId(groupIndex, fieldIndex);
            }
        }

        public String getName() {
            return mName;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public int[] getFieldIds() {
            return mFieldIds;
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
        return getGroupDisplayName(context, groupIndex, Settings.getTestFieldGroupName(groupIndex));
    }

    private static String getGroupDisplayName(final Context context, final int groupIndex,
                                              final String groupName) {
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
