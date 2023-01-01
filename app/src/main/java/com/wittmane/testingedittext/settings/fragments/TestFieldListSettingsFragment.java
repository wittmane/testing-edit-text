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

package com.wittmane.testingedittext.settings.fragments;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
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
import com.wittmane.testingedittext.settings.preferences.SingleFieldPreference;
import com.wittmane.testingedittext.settings.preferences.TestFieldImeActionPreference;
import com.wittmane.testingedittext.settings.preferences.TestFieldImeOptionsPreference;
import com.wittmane.testingedittext.settings.preferences.TestFieldInputTypePreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TestFieldListSettingsFragment extends PreferenceFragment {
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
        inflater.inflate(R.menu.test_field_list, menu);

        ActionBar actionBar = getActivity().getActionBar();
        MenuItem addFieldMenuItem = menu.findItem(R.id.action_add_field);
        IconUtils.matchMenuIconColor(mView, addFieldMenuItem, actionBar);
        MenuItem reorderFieldsMenuItem = menu.findItem(R.id.action_reorder_fields);
        IconUtils.matchMenuIconColor(mView, reorderFieldsMenuItem, actionBar);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_add_field) {
            // add a preference for a new field
            Settings.addTestField();
            final PreferenceGroup group = getPreferenceScreen();
            Preference newPref =
                    new MainSingleFieldPreference(getActivity(), group.getPreferenceCount());
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
            for (int i = 0; i < Settings.getTestFieldCount(); i++) {
                adapter.add(new TestField(getActivity(), i));
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
                                    int[] testFields = new int[adapter.getCount()];
                                    for (int i = 0; i < adapter.getCount(); i++) {
                                        testFields[i] = adapter.getItem(i).getId();
                                    }
                                    Settings.setTestFieldIds(testFields);
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

    private static class TestField {
        private CharSequence mTitle;
        private int mId;

        public TestField(Context context, int index) {
            mTitle = getFieldTitle(context, index);
            mId = Settings.getTestFieldId(index);
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

        for (int i = 0; i < Settings.getTestFieldCount(); i++) {
            group.addPreference(new MainSingleFieldPreference(context, i));
        }
    }

    private static CharSequence getFieldTitle(final Context context, final int fieldIndex) {
        CharSequence defaultText = Settings.getTestFieldDefaultText(fieldIndex);
        if (!TextUtils.isEmpty(defaultText)) {
            return defaultText;
        } else {
            CharSequence hintText = Settings.getTestFieldHintText(fieldIndex);
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
    private static class MainSingleFieldPreference extends SingleFieldPreference {

        /**
         * Create a new preference for a test field.
         * @param context the context for this application.
         * @param fieldIndex the index of the field in the UI.
         */
        public MainSingleFieldPreference(final Context context, final int fieldIndex) {
            super(context, fieldIndex);

            setFragment(TestFieldSettingsFragment.class.getName());
        }

        @Override
        protected void updateSummary() {
            Context context = getContext();
            int fieldIndex = getFieldIndex();
            setTitle(getFieldTitle(context, fieldIndex));
            String[] summaryInfo = new String[] {
                    getLabeledProperty(R.string.input_type,
                            TestFieldInputTypePreference.getInputTypeDescription(
                                    Settings.getTestFieldInputType(fieldIndex), context), context),
                    getLabeledProperty(R.string.ime_options,
                            TestFieldImeOptionsPreference.getImeOptionsDescription(
                                    Settings.getTestFieldImeOptions(fieldIndex), context), context),
                    getLabeledProperty(R.string.ime_action,
                            TestFieldImeActionPreference.getImeActionDescription(
                                    Settings.getTestFieldImeActionId(fieldIndex),
                                    Settings.getTestFieldImeActionLabel(fieldIndex),
                                    context), context),
                    getLabeledPrivateImeOptions(
                            Settings.getTestFieldPrivateImeOptions(fieldIndex), context),
                    Settings.shouldTestFieldSelectAllOnFocus(fieldIndex)
                            ? context.getString(R.string.select_all_on_focus) : null,
                    getLabeledMaxLength(Settings.getTestFieldMaxLength(fieldIndex), context),
                    Settings.shouldTestFieldAllowUndo(fieldIndex)
                            ? context.getString(R.string.allow_undo) : null,
                    getLabeledTextLocales(
                            Settings.getTestFieldTextLocales(fieldIndex), context),
                    getLabeledImeHintLocales(
                            Settings.getTestFieldImeHintLocales(fieldIndex), context)
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
