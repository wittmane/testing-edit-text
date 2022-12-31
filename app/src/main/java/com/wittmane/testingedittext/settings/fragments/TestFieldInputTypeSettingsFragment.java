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

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.ListPreferenceDependencyManager;
import com.wittmane.testingedittext.settings.ListPreferenceDependencyManager.OnPreferencesChangedListener;
import com.wittmane.testingedittext.settings.Settings;

public class TestFieldInputTypeSettingsFragment extends TestFieldBaseSettingsFragment {

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
        addPreferencesFromResource(R.xml.preference_screen_test_field_input_type);

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
    protected void registerPreferencesChangedListener(int fieldId) {
        new ListPreferenceDependencyManager(new String[]{
                Settings.PREF_TEST_FIELD_INPUT_TYPE_CLASS_PREFIX + fieldId
        }, this, new OnPreferencesChangedListener() {
            @Override
            public void onPreferencesChanged(CharSequence[] prefValues) {
                updateInputTypeFields(prefValues[0]);
            }
        });
    }

    private void updateInputTypeFields(CharSequence inputTypeClass) {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        switch (inputTypeClass.toString()) {
            case "TYPE_CLASS_TEXT":
                removeDateTimeFields();
                removeNumberFields();
                preferenceScreen.addPreference(mInputTypeTextVariationPref);
                preferenceScreen.addPreference(mInputTypeTextMultiLineFlagPref);
                preferenceScreen.addPreference(mInputTypeTextCapFlagPref);
                preferenceScreen.addPreference(mInputTypeTextAutoCompleteFlagPref);
                preferenceScreen.addPreference(mInputTypeTextAutoCorrectFlagPref);
                preferenceScreen.addPreference(mInputTypeTextNoSuggestionsFlagPref);
                break;
            case "TYPE_CLASS_NUMBER":
                removeTextFields();
                removeDateTimeFields();
                preferenceScreen.addPreference(mInputTypeNumberVariationPref);
                preferenceScreen.addPreference(mInputTypeNumberSignedFlagPref);
                preferenceScreen.addPreference(mInputTypeNumberDecimalFlagPref);
                break;
            case "TYPE_CLASS_DATETIME":
                removeTextFields();
                removeNumberFields();
                preferenceScreen.addPreference(mInputTypeDateTimeVariationPref);
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
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removePreference(mInputTypeTextVariationPref);
        preferenceScreen.removePreference(mInputTypeTextMultiLineFlagPref);
        preferenceScreen.removePreference(mInputTypeTextCapFlagPref);
        preferenceScreen.removePreference(mInputTypeTextAutoCompleteFlagPref);
        preferenceScreen.removePreference(mInputTypeTextAutoCorrectFlagPref);
        preferenceScreen.removePreference(mInputTypeTextNoSuggestionsFlagPref);
    }

    private void removeNumberFields() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removePreference(mInputTypeNumberVariationPref);
        preferenceScreen.removePreference(mInputTypeNumberSignedFlagPref);
        preferenceScreen.removePreference(mInputTypeNumberDecimalFlagPref);
    }

    private void removeDateTimeFields() {
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.removePreference(mInputTypeDateTimeVariationPref);
    }
}
