/*
 * Copyright (C) 2022-2023 Eli Wittman
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
import android.preference.PreferenceFragment;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager.OnPreferencesChangedListener;

public class ModifyTextSettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_modify_text);

        new SwitchPreferenceDependencyManager(new String[]{
                Settings.PREF_MODIFY_COMMITTED_TEXT,
                Settings.PREF_MODIFY_COMPOSED_TEXT
        }, this, new OnPreferencesChangedListener() {
            @Override
            public void onPreferencesChanged(boolean[] prefsChecked) {
                updateModifyTextEnabledState(prefsChecked[0], prefsChecked[1]);
            }
        });
    }

    private void updateModifyTextEnabledState(boolean modifyCommittedText,
                                              boolean modifyComposedText) {
        boolean enableModifierSettings = modifyCommittedText || modifyComposedText;

        String[] prefKeys = new String[] {
                Settings.PREF_RESTRICT_TO_INCLUDE,
                Settings.PREF_RESTRICT_SPECIFIC,
                Settings.PREF_RESTRICT_RANGE,
                Settings.PREF_TRANSLATE_SPECIFIC,
                Settings.PREF_TRANSLATE_FULL_MATCH_ONLY,
                Settings.PREF_SHIFT_CODEPOINT
        };
        for (String prefKey : prefKeys) {
            Preference pref = findPreference(prefKey);
            pref.setEnabled(enableModifierSettings);
        }
    }
}
