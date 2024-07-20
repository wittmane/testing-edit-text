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

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager.OnPreferencesChangedListener;

public class ModifyTextSettingsFragment extends PerTestFieldSettingsFragment {
    private static final String TAG = ModifyTextSettingsFragment.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_modify_text);

        findPreference(getPrefKey(Settings.PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX))
                .setDependency(getPrefKey(Settings.PREF_MODIFY_COMPOSED_TEXT_PREFIX));
        findPreference(getPrefKey(Settings.PREF_CONSIDER_COMPOSED_CHANGES_FROM_END_PREFIX))
                .setDependency(getPrefKey(Settings.PREF_MODIFY_COMPOSED_CHANGES_ONLY_PREFIX));

        new SwitchPreferenceDependencyManager(new String[]{
                getPrefKey(Settings.PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX),
                getPrefKey(Settings.PREF_MODIFY_COMMITTED_TEXT_PREFIX),
                getPrefKey(Settings.PREF_MODIFY_COMPOSED_TEXT_PREFIX)
        }, this, new OnPreferencesChangedListener() {
            @Override
            public void onPreferencesChanged(boolean[] prefsChecked) {
                updateEnabledState(prefsChecked[0], prefsChecked[1], prefsChecked[2]);
            }
        });
        if (getFieldIndex() == NO_FIELD_INDEX) {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            Preference pref = findPreference(getPrefKey(
                    Settings.PREF_OVERRIDE_TEXT_INPUT_MODIFICATION_PREFIX));
            preferenceScreen.removePreference(pref);
        }
    }

    private void updateEnabledState(boolean overrideDefaults,
                                    boolean modifyCommittedText,
                                    boolean modifyComposedText) {
        boolean enableEntryTypeSettings;
        boolean enableModifierSettings;
        if (overrideDefaults || getFieldIndex() < 0) {
            enableEntryTypeSettings = true;
            enableModifierSettings = modifyCommittedText || modifyComposedText;
        } else {
            enableEntryTypeSettings = false;
            enableModifierSettings = false;
        }

        String[] modifyEntryTypePrefKeyPrefixes = new String[] {
                Settings.PREF_MODIFY_COMMITTED_TEXT_PREFIX,
                Settings.PREF_MODIFY_COMPOSED_TEXT_PREFIX
        };
        for (String prefKey : modifyEntryTypePrefKeyPrefixes) {
            Preference pref = findPreference(getPrefKey(prefKey));
            pref.setEnabled(enableEntryTypeSettings);
        }

        String[] modifierPrefKeyPrefixes = new String[] {
                Settings.PREF_RESTRICT_TO_INCLUDE_PREFIX,
                Settings.PREF_RESTRICT_SPECIFIC_PREFIX,
                Settings.PREF_RESTRICT_RANGE_PREFIX,
                Settings.PREF_TRANSLATE_SPECIFIC_PREFIX,
                Settings.PREF_TRANSLATE_FULL_MATCH_ONLY_PREFIX,
                Settings.PREF_SHIFT_CODEPOINT_PREFIX
        };
        for (String prefKey : modifierPrefKeyPrefixes) {
            Preference pref = findPreference(getPrefKey(prefKey));
            pref.setEnabled(enableModifierSettings);
        }
    }
}
