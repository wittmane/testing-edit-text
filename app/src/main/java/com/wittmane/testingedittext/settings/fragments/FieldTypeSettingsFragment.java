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
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;

import com.wittmane.testingedittext.BuildConfig;
import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.ListPreferenceDependencyManager;
import com.wittmane.testingedittext.settings.ListPreferenceDependencyManager.OnPreferencesChangedListener;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.preferences.EnhancedListPreference;
import com.wittmane.testingedittext.settings.preferences.LongTextSwitchPreference;

public class FieldTypeSettingsFragment extends PreferenceFragment {

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
        addPreferencesFromResource(R.xml.preference_screen_field_type);

        LongTextSwitchPreference useDebugScreenPref =
                (LongTextSwitchPreference)findPreference(Settings.PREF_USE_DEBUG_SCREEN);
        if (!BuildConfig.DEBUG) {
            useDebugScreenPref.setChecked(false);
            getPreferenceScreen().removePreference(useDebugScreenPref);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            PreferenceCategory imeOptionsCategory = (PreferenceCategory)findPreference(
                    "pref_key_ime_options_category");
            Preference noPersonalizedLearningFlagPref = (PreferenceCategory)findPreference(
                    Settings.PREF_IME_OPTIONS_FLAG_NO_PERSONALIZED_LEARNING);
            imeOptionsCategory.removePreference(noPersonalizedLearningFlagPref);
        }

        mInputTypeCategory = (PreferenceCategory)findPreference("pref_key_input_type_category");

        mInputTypeTextVariationPref =
                (ListPreference)findPreference(Settings.PREF_INPUT_TYPE_TEXT_VARIATION);
        mInputTypeTextMultiLineFlagPref =
                (ListPreference)findPreference(Settings.PREF_INPUT_TYPE_TEXT_FLAG_MULTI_LINE);
        mInputTypeTextCapFlagPref =
                (ListPreference)findPreference(Settings.PREF_INPUT_TYPE_TEXT_FLAG_CAP);
        mInputTypeTextAutoCompleteFlagPref =
                (SwitchPreference)findPreference(Settings.PREF_INPUT_TYPE_TEXT_FLAG_AUTO_COMPLETE);
        mInputTypeTextAutoCorrectFlagPref =
                (SwitchPreference)findPreference(Settings.PREF_INPUT_TYPE_TEXT_FLAG_AUTO_CORRECT);
        mInputTypeTextNoSuggestionsFlagPref =
                (SwitchPreference)findPreference(Settings.PREF_INPUT_TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        mInputTypeNumberVariationPref =
                (ListPreference)findPreference(Settings.PREF_INPUT_TYPE_NUMBER_VARIATION);
        mInputTypeNumberSignedFlagPref =
                (SwitchPreference)findPreference(Settings.PREF_INPUT_TYPE_NUMBER_FLAG_SIGNED);
        mInputTypeNumberDecimalFlagPref =
                (SwitchPreference)findPreference(Settings.PREF_INPUT_TYPE_NUMBER_FLAG_DECIMAL);

        mInputTypeDateTimeVariationPref =
                (ListPreference)findPreference(Settings.PREF_INPUT_TYPE_DATETIME_VARIATION);

        EnhancedListPreference inputTypeClassPref =
                (EnhancedListPreference)findPreference(Settings.PREF_INPUT_TYPE_CLASS);
        inputTypeClassPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                return false;
            }
        });
        new ListPreferenceDependencyManager(new String[]{
                Settings.PREF_INPUT_TYPE_CLASS
        }, this, new OnPreferencesChangedListener() {
            @Override
            public void onPreferencesChanged(CharSequence[] prefValues) {
                updateInputTypeFields(prefValues[0]);
            }
        });
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
}
