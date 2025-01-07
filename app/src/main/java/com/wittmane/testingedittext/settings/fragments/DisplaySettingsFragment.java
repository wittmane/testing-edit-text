/*
 * Copyright (C) 2024-2025 Eli Wittman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wittmane.testingedittext.settings.fragments;

import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import android.os.Build;
import android.os.Bundle;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.ThemedActivity;
import com.wittmane.testingedittext.aosp.com.android.internal.util.ArrayUtils;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.preferences.EnhancedListPreference;

public class DisplaySettingsFragment extends SettingsFragment {
    private static final String TAG = DisplaySettingsFragment.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_display);

        EnhancedListPreference themePref = (EnhancedListPreference) findPreference(PREF_THEME);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // remove the material themes as options since that's only available starting in
            // Lollipop
            CharSequence[] entries = themePref.getEntries();
            CharSequence[] entryValues = themePref.getEntryValues();
            // sanity check, but this should always be true
            if (entries.length == entryValues.length) {
                for (String theme : new String[] {
                        THEME_MATERIAL_DARK,
                        THEME_MATERIAL_LIGHT
                }) {
                    int index = ArrayUtils.indexOf(entryValues, theme);
                    if (index >= 0) {
                        entries = ArrayUtils.removeCharSequenceAt(entries, index);
                        entryValues = ArrayUtils.removeCharSequenceAt(entryValues, index);
                    }
                }

                themePref.setEntries(entries);
                themePref.setEntryValues(entryValues);
            }
        }

        // update the activity when the theme changes
        themePref.setOnPreferenceChangeListener((preference, newValue) -> {
            String oldValue = themePref.getValue();
            ThemedActivity.recreateActivityOnThemeChange(getActivity(),
                    Settings.getThemeId(oldValue, getActivity()),
                    Settings.getThemeId((String)newValue, getActivity()));
            return true;
        });
    }
}
