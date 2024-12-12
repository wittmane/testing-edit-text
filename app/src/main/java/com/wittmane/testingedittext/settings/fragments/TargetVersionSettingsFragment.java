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

import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.aosp.com.android.internal.inputmethod.EditableInputConnection;

public class TargetVersionSettingsFragment extends PerTestFieldSettingsFragment {
    private static final String TAG = TargetVersionSettingsFragment.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_target_version);

        manageOverrideToggle(PREF_OVERRIDE_TARGET_VERSION_SIMULATION_PREFIX);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && !EditableInputConnection.canSimulateMissingMethods(getContext())) {
            // these methods require lying to the framework about not implementing them to get the
            // appropriate return value to the IME for a valid test, so since we can't seem to fake
            // it that way, these aren't valid tests, so they shouldn't be allowed.

            SwitchPreference skipDeleteSurroundingTextInCodePointsPref =
                    (SwitchPreference)findPreference(getPrefKey(
                            PREF_SKIP_DELETESURROUNDINGTEXTINCODEPOINTS_PREFIX));
            skipDeleteSurroundingTextInCodePointsPref.setEnabled(false);
            skipDeleteSurroundingTextInCodePointsPref.setChecked(false);

            SwitchPreference skipSetComposingRegionPref =
                    (SwitchPreference)findPreference(getPrefKey(
                            PREF_SKIP_SETCOMPOSINGREGION_PREFIX));
            skipSetComposingRegionPref.setEnabled(false);
            skipSetComposingRegionPref.setChecked(false);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PreferenceCategory addedInApiLevel34Category =
                    (PreferenceCategory)findPreference("pref_key_added_in_api_level_34");
            getPreferenceScreen().removePreference(addedInApiLevel34Category);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PreferenceCategory addedInApiLevel33Category =
                    (PreferenceCategory)findPreference("pref_key_added_in_api_level_33");
            getPreferenceScreen().removePreference(addedInApiLevel33Category);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            PreferenceCategory addedInApiLevel31Category =
                    (PreferenceCategory)findPreference("pref_key_added_in_api_level_31");
            getPreferenceScreen().removePreference(addedInApiLevel31Category);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            PreferenceCategory addedInApiLevel25Category =
                    (PreferenceCategory)findPreference("pref_key_added_in_api_level_25");
            getPreferenceScreen().removePreference(addedInApiLevel25Category);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            PreferenceCategory addedInApiLevel24Category =
                    (PreferenceCategory)findPreference("pref_key_added_in_api_level_24");
            getPreferenceScreen().removePreference(addedInApiLevel24Category);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            PreferenceCategory addedInApiLevel21Category =
                    (PreferenceCategory)findPreference("pref_key_added_in_api_level_21");
            getPreferenceScreen().removePreference(addedInApiLevel21Category);
        }
    }
}
