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

package com.wittmane.testingedittext.settings;

import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;

//TODO: (EW) see if there is a good way to reduce duplicate code with
// SwitchPreferenceDependencyManager
public class ListPreferenceDependencyManager implements OnPreferenceChangeListener {
    public interface OnPreferencesChangedListener {
        void onPreferencesChanged(CharSequence[] prefValues);
    }

    private final ListPreference[] mPrefs;
    private final OnPreferencesChangedListener mListener;

    public ListPreferenceDependencyManager(String[] prefListenerKeys, PreferenceFragment fragment,
                                           OnPreferencesChangedListener listener) {
        mPrefs = new ListPreference[prefListenerKeys.length];
        mListener = listener;
        CharSequence[] prefsValues = new CharSequence[prefListenerKeys.length];
        for (int i = 0; i < prefListenerKeys.length; i++) {
            ListPreference pref = (ListPreference) fragment.findPreference(prefListenerKeys[i]);
            mPrefs[i] = pref;
            pref.setOnPreferenceChangeListener(this);
            prefsValues[i] = pref.getValue();
        }

        // call right away so the initial state is correct
        mListener.onPreferencesChanged(prefsValues);
    }

    @Override
    public boolean onPreferenceChange(Preference changingPreference, Object newValue) {
        CharSequence[] prefsValues = new CharSequence[mPrefs.length];
        for (int i = 0; i < mPrefs.length; i++) {
            ListPreference pref = mPrefs[i];
            if (changingPreference == pref) {
                // use the new value (getValue is outdated)
                prefsValues[i] = (CharSequence) newValue;
            } else {
                prefsValues[i] = pref.getValue();
            }
        }
        mListener.onPreferencesChanged(prefsValues);

        return true;
    }
}
