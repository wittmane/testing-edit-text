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
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;

public class ModifyTextSettingsFragment extends PreferenceFragment
        implements OnPreferenceChangeListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_modify_text);

        SwitchPreference modifyCommittedTextPref
                = (SwitchPreference)findPreference(Settings.PREF_MODIFY_COMMITTED_TEXT);
        modifyCommittedTextPref.setOnPreferenceChangeListener(this);
        SwitchPreference modifyComposedTextPref
                = (SwitchPreference)findPreference(Settings.PREF_MODIFY_COMPOSED_TEXT);
        modifyComposedTextPref.setOnPreferenceChangeListener(this);

        updateModifyTextEnabledState(modifyCommittedTextPref.isChecked(),
                modifyComposedTextPref.isChecked());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        SwitchPreference modifyCommittedTextPref
                = (SwitchPreference)findPreference(Settings.PREF_MODIFY_COMMITTED_TEXT);
        SwitchPreference modifyComposedTextPref
                = (SwitchPreference)findPreference(Settings.PREF_MODIFY_COMPOSED_TEXT);

        boolean modifyCommittedText;
        if (preference == modifyCommittedTextPref) {
            // use the new value (isChecked is outdated)
            modifyCommittedText = (boolean) newValue;
        } else {
            modifyCommittedText = modifyCommittedTextPref.isChecked();
        }
        boolean modifyComposedText;
        if (preference == modifyComposedTextPref) {
            // use the new value (isChecked is outdated)
            modifyComposedText = (boolean) newValue;
        } else {
            modifyComposedText = modifyComposedTextPref.isChecked();
        }
        updateModifyTextEnabledState(modifyCommittedText, modifyComposedText);

        return true;
    }

    private void updateModifyTextEnabledState(boolean modifyCommittedText,
                                              boolean modifyComposedText) {
        boolean enableModifierSettings = modifyCommittedText || modifyComposedText;

        String[] prefKeys = new String[] {
                Settings.PREF_RESTRICT_TO_INCLUDE,
                Settings.PREF_RESTRICT_SPECIFIC,
                Settings.PREF_RESTRICT_RANGE,
                Settings.PREF_TRANSLATE_SPECIFIC,
                Settings.PREF_SHIFT_CODEPOINT
        };
        for (String prefKey : prefKeys) {
            Preference pref = findPreference(prefKey);
            pref.setEnabled(enableModifierSettings);
        }
    }
}
