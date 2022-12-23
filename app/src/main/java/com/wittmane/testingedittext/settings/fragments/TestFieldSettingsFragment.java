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

import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.ListPreferenceDependencyManager;
import com.wittmane.testingedittext.settings.ListPreferenceDependencyManager.OnPreferencesChangedListener;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;

public class TestFieldSettingsFragment extends PreferenceFragment {
    private static final String TAG = TestFieldSettingsFragment.class.getSimpleName();

    public static final String FIELD_INDEX_BUNDLE_KEY = "FIELD_INDEX";

    private int mFieldIndex = -1;

    private View mView;

    private PreferenceCategory mInputTypeCategory;

    private ListPreference mInputTypeTextVariationPref;
    private ListPreference mInputTypeTextMultiLineFlagPref;
    private ListPreference mInputTypeTextCapFlagPref;
    private SwitchPreference mInputTypeTextAutoCompleteFlagPref;
    private SwitchPreference mInputTypeTextAutoCorrectFlagPref;
    private SwitchPreference mInputTypeTextNoSuggestionsFlagPref;

    private ListPreference mInputTypeNumberVariationPref;
    private SwitchPreference mInputTypeNumberSignedFlagPref;
    private SwitchPreference mInputTypeNumberDecimalFlagPref;

    private ListPreference mInputTypeDateTimeVariationPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_test_field);
        setHasOptionsMenu(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            PreferenceCategory imeOptionsCategory = (PreferenceCategory)findPreference(
                    "pref_key_ime_options_category");
            SwitchPreference noPersonalizedLearningFlagPref = (SwitchPreference)findPreference(
                    Settings.PREF_TEST_FIELD_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING_PREFIX);
            imeOptionsCategory.removePreference(noPersonalizedLearningFlagPref);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            PreferenceCategory imeOptionsCategory = (PreferenceCategory)findPreference(
                    "pref_key_misc_category");
            Preference imeHintLocalesPref = findPreference(
                    Settings.PREF_TEST_FIELD_IME_HINT_LOCALES_PREFIX);
            imeOptionsCategory.removePreference(imeHintLocalesPref);

            LocaleEntryListPreference textLocalesPref =
                    (LocaleEntryListPreference)findPreference(
                            Settings.PREF_TEST_FIELD_TEXT_LOCALES_PREFIX);
            textLocalesPref.setMaxEntries(1);
        }

        mInputTypeCategory = (PreferenceCategory)findPreference("pref_key_input_type_category");

        mInputTypeTextVariationPref = (ListPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_TEXT_VARIATION_PREFIX);
        mInputTypeTextMultiLineFlagPref = (ListPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_MULTI_LINE_PREFIX);
        mInputTypeTextCapFlagPref = (ListPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_CAP_PREFIX);
        mInputTypeTextAutoCompleteFlagPref = (SwitchPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE_PREFIX);
        mInputTypeTextAutoCorrectFlagPref = (SwitchPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT_PREFIX);
        mInputTypeTextNoSuggestionsFlagPref = (SwitchPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS_PREFIX);

        mInputTypeNumberVariationPref = (ListPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_NUMBER_VARIATION_PREFIX);
        mInputTypeNumberSignedFlagPref = (SwitchPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_SIGNED_PREFIX);
        mInputTypeNumberDecimalFlagPref = (SwitchPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_NUMBER_FLAG_DECIMAL_PREFIX);

        mInputTypeDateTimeVariationPref = (ListPreference)findPreference(
                Settings.PREF_TEST_FIELD_INPUT_TYPE_DATETIME_VARIATION_PREFIX);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);
        return mView;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.test_field, menu);

        MenuItem addFieldMenuItem = menu.findItem(R.id.action_remove_field);
        IconUtils.matchMenuIconColor(mView, addFieldMenuItem, getActivity().getActionBar());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_remove_field) {
            // remove the field and go back to the field list
            Settings.removeTestField(mFieldIndex);
            getFragmentManager().popBackStackImmediate();
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateInputTypeFields(CharSequence inputTypeClass) {
        switch (inputTypeClass.toString()) {
            case "TYPE_CLASS_TEXT":
                removeDateTimeFields();
                removeNumberFields();
                mInputTypeCategory.addPreference(mInputTypeTextVariationPref);
                mInputTypeCategory.addPreference(mInputTypeTextMultiLineFlagPref);
                mInputTypeCategory.addPreference(mInputTypeTextCapFlagPref);
                mInputTypeCategory.addPreference(mInputTypeTextAutoCompleteFlagPref);
                mInputTypeCategory.addPreference(mInputTypeTextAutoCorrectFlagPref);
                mInputTypeCategory.addPreference(mInputTypeTextNoSuggestionsFlagPref);
                break;
            case "TYPE_CLASS_NUMBER":
                removeTextFields();
                removeDateTimeFields();
                mInputTypeCategory.addPreference(mInputTypeNumberVariationPref);
                mInputTypeCategory.addPreference(mInputTypeNumberSignedFlagPref);
                mInputTypeCategory.addPreference(mInputTypeNumberDecimalFlagPref);
                break;
            case "TYPE_CLASS_DATETIME":
                removeTextFields();
                removeNumberFields();
                mInputTypeCategory.addPreference(mInputTypeDateTimeVariationPref);
                break;
            case "TYPE_CLASS_PHONE":
            default:
                removeTextFields();
                removeNumberFields();
                removeDateTimeFields();
                break;
        }
    }

    private void removeTextFields() {
        mInputTypeCategory.removePreference(mInputTypeTextVariationPref);
        mInputTypeCategory.removePreference(mInputTypeTextMultiLineFlagPref);
        mInputTypeCategory.removePreference(mInputTypeTextCapFlagPref);
        mInputTypeCategory.removePreference(mInputTypeTextAutoCompleteFlagPref);
        mInputTypeCategory.removePreference(mInputTypeTextAutoCorrectFlagPref);
        mInputTypeCategory.removePreference(mInputTypeTextNoSuggestionsFlagPref);
    }

    private void removeNumberFields() {
        mInputTypeCategory.removePreference(mInputTypeNumberVariationPref);
        mInputTypeCategory.removePreference(mInputTypeNumberSignedFlagPref);
        mInputTypeCategory.removePreference(mInputTypeNumberDecimalFlagPref);
    }

    private void removeDateTimeFields() {
        mInputTypeCategory.removePreference(mInputTypeDateTimeVariationPref);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {

        final Bundle args = getArguments();
        if (args != null) {
            try {
                mFieldIndex = Integer.parseInt(args.getString(FIELD_INDEX_BUNDLE_KEY));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse the index: " + e.getMessage());
                getFragmentManager().popBackStack();
            }
            if (mFieldIndex >= 0 && mFieldIndex < Settings.getTestFieldCount()) {
                int fieldId = Settings.getTestFieldId(mFieldIndex);
                setFieldIdToKeys(getPreferenceScreen(), fieldId);


                new ListPreferenceDependencyManager(new String[]{
                        Settings.PREF_TEST_FIELD_INPUT_TYPE_CLASS_PREFIX + fieldId
                }, this, new OnPreferencesChangedListener() {
                    @Override
                    public void onPreferencesChanged(CharSequence[] prefValues) {
                        updateInputTypeFields(prefValues[0]);
                    }
                });
            } else {
                Log.e(TAG, "Invalid index: " + mFieldIndex);
                getFragmentManager().popBackStack();
            }
        } else {
            Log.e(TAG, "No bundle for the index");
            getFragmentManager().popBackStack();
        }

        super.onActivityCreated(savedInstanceState);
    }

    private void setFieldIdToKeys(PreferenceGroup prefGroup, int fieldId) {
        Preference[] prefs = new Preference[prefGroup.getPreferenceCount()];
        for (int i = 0; i < prefGroup.getPreferenceCount(); i++) {
            prefs[i] = prefGroup.getPreference(i);
        }
        // simply updating the key doesn't update the UI with the actual stored preference value for
        // the new key, so remove the preferences and add them back with the updated key to get the
        // appropriate value displayed
        prefGroup.removeAll();
        for (Preference pref : prefs) {
            String key = pref.getKey();
            if (pref instanceof PreferenceGroup) {
                setFieldIdToKeys((PreferenceGroup) pref, fieldId);
            } else {
                if (key != null && key.length() > 1 && key.charAt(key.length() - 1) == '_') {
                    pref.setKey(key + fieldId);
                }
            }
            prefGroup.addPreference(pref);
        }
    }
}
