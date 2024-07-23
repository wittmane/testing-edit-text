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

import static com.wittmane.testingedittext.settings.Settings.BASE_FIELD_INDEX;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager.OnPreferencesChangedListener;

public class ReturningTextSettingsFragment extends PerTestFieldSettingsFragment {
    private static final String TAG = ReturningTextSettingsFragment.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_returning_text);

        new SwitchPreferenceDependencyManager(new String[]{
                getPrefKey(Settings.PREF_OVERRIDE_TEXT_RETURN_PREFIX),
                getPrefKey(Settings.PREF_SKIP_EXTRACTING_TEXT_PREFIX),
                getPrefKey(Settings.PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX),
                getPrefKey(Settings.PREF_EXTRACT_FULL_TEXT_PREFIX)
        }, this, new OnPreferencesChangedListener() {
            @Override
            public void onPreferencesChanged(boolean[] prefsChecked) {
                updateEnabledState(prefsChecked[0], prefsChecked[1], prefsChecked[2],
                        prefsChecked[3]);
            }
        });
        if (getFieldIndex() == BASE_FIELD_INDEX) {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            Preference pref = findPreference(getPrefKey(Settings.PREF_OVERRIDE_TEXT_RETURN_PREFIX));
            preferenceScreen.removePreference(pref);
        }
    }

    private void updateEnabledState(boolean overrideDefaults,
                                    boolean skipExtractingText,
                                    boolean ignoreExtractedTextMonitor,
                                    boolean extractFullText) {
        boolean enableUpdateSelectionBeforeExtractedText;
        boolean enableExtractFullText;
        boolean enableLimitExtractMonitorText;
        boolean enableOthers;
        if (overrideDefaults || getFieldIndex() < 0) {
            enableUpdateSelectionBeforeExtractedText = !ignoreExtractedTextMonitor;
            enableExtractFullText = !ignoreExtractedTextMonitor;
            enableLimitExtractMonitorText = !skipExtractingText
                    || (!ignoreExtractedTextMonitor && extractFullText);
            enableOthers = true;
        } else {
            enableUpdateSelectionBeforeExtractedText = false;
            enableExtractFullText = false;
            enableLimitExtractMonitorText = false;
            enableOthers = false;
        }

        Preference updateSelectionBeforeExtractedTextPref = findPreference(getPrefKey(
                Settings.PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT_PREFIX));
        updateSelectionBeforeExtractedTextPref.setEnabled(enableUpdateSelectionBeforeExtractedText);

        Preference extractFullTextPref = findPreference(getPrefKey(
                Settings.PREF_EXTRACT_FULL_TEXT_PREFIX));
        extractFullTextPref.setEnabled(enableExtractFullText);

        Preference limitExtractMonitorTextPref = findPreference(getPrefKey(
                Settings.PREF_LIMIT_EXTRACT_MONITOR_TEXT_PREFIX));
        limitExtractMonitorTextPref.setEnabled(enableLimitExtractMonitorText);

        String[] otherPrefKeyPrefixes = new String[] {
                Settings.PREF_SKIP_EXTRACTING_TEXT_PREFIX,
                Settings.PREF_IGNORE_EXTRACTED_TEXT_MONITOR_PREFIX,
                Settings.PREF_UPDATE_EXTRACTED_TEXT_ONLY_ON_NET_CHANGES_PREFIX,
                Settings.PREF_LIMIT_RETURNED_TEXT_PREFIX
        };
        for (String prefKeyPrefix : otherPrefKeyPrefixes) {
            Preference pref = findPreference(getPrefKey(prefKeyPrefix));
            pref.setEnabled(enableOthers);
        }
    }
}
