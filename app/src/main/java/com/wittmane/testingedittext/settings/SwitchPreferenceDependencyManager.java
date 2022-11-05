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

import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;

public class SwitchPreferenceDependencyManager implements OnPreferenceChangeListener {
    public interface OnPreferencesChangedListener {
        void onPreferencesChanged(boolean[] prefsChecked);
    }

    private final SwitchPreference[] mPrefs;
    private final OnPreferencesChangedListener mListener;

    public SwitchPreferenceDependencyManager(String[] switchPrefListenerKeys,
                                             PreferenceFragment fragment,
                                             OnPreferencesChangedListener listener) {
        mPrefs = new SwitchPreference[switchPrefListenerKeys.length];
        mListener = listener;
        boolean[] prefsChecked = new boolean[switchPrefListenerKeys.length];
        for (int i = 0; i < switchPrefListenerKeys.length; i++) {
            SwitchPreference pref =
                    (SwitchPreference)fragment.findPreference(switchPrefListenerKeys[i]);
            mPrefs[i] = pref;
            pref.setOnPreferenceChangeListener(this);
            prefsChecked[i] = pref.isChecked();
        }

        // call right away so the initial state is correct
        mListener.onPreferencesChanged(prefsChecked);
    }

    @Override
    public boolean onPreferenceChange(Preference changingPreference, Object newValue) {
        boolean[] prefsChecked = new boolean[mPrefs.length];
        for (int i = 0; i < mPrefs.length; i++) {
            SwitchPreference pref = mPrefs[i];
            if (changingPreference == pref) {
                // use the new value (isChecked is outdated)
                prefsChecked[i] = (boolean) newValue;
            } else {
                prefsChecked[i] = pref.isChecked();
            }
        }
        mListener.onPreferencesChanged(prefsChecked);

        return true;
    }
}
