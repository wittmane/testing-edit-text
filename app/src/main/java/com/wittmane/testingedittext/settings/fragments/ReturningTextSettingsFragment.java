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
import android.preference.PreferenceFragment;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager.OnPreferencesChangedListener;

public class ReturningTextSettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_returning_text);

        new SwitchPreferenceDependencyManager(new String[]{
                Settings.PREF_SKIP_EXTRACTING_TEXT,
                Settings.PREF_IGNORE_EXTRACTED_TEXT_MONITOR,
                Settings.PREF_EXTRACT_FULL_TEXT
        }, this, new OnPreferencesChangedListener() {
            @Override
            public void onPreferencesChanged(boolean[] prefsChecked) {
                updateExtractTextEnabledState(prefsChecked[0], prefsChecked[1], prefsChecked[2]);
            }
        });
    }

    private void updateExtractTextEnabledState(boolean skipExtractingText,
                                               boolean ignoreExtractedTextMonitor,
                                               boolean extractFullText) {
        Preference updateSelectionBeforeExtractedTextPref =
                findPreference(Settings.PREF_UPDATE_SELECTION_BEFORE_EXTRACTED_TEXT);
        updateSelectionBeforeExtractedTextPref.setEnabled(!ignoreExtractedTextMonitor);

        Preference extractFullTextPref = findPreference(Settings.PREF_EXTRACT_FULL_TEXT);
        extractFullTextPref.setEnabled(!ignoreExtractedTextMonitor);

        Preference limitExtractMonitorTextPref =
                findPreference(Settings.PREF_LIMIT_EXTRACT_MONITOR_TEXT);
        limitExtractMonitorTextPref.setEnabled(!skipExtractingText
                || (!ignoreExtractedTextMonitor && extractFullText));
    }
}
