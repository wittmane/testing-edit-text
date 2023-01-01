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
import android.preference.PreferenceGroup;
import android.util.Log;

import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.preferences.SingleFieldPreference;

public abstract class TestFieldBaseSettingsFragment extends PreferenceFragment {
    private static final String TAG = TestFieldBaseSettingsFragment.class.getSimpleName();

    public static final String FIELD_INDEX_BUNDLE_KEY = "FIELD_INDEX";

    private int mFieldIndex = -1;

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        if (args != null) {
            try {
                mFieldIndex = Integer.parseInt(args.getString(FIELD_INDEX_BUNDLE_KEY));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse the index: " + e.getMessage());
                getFragmentManager().popBackStack();
            }
            if (mFieldIndex >= 0 && mFieldIndex < Settings.getTestFieldCount()) {
                int fieldId = Settings.getTestFieldId(mFieldIndex);
                updatePrefsForSpecificTestField(getPreferenceScreen(), fieldId);

                registerPreferencesChangedListener(fieldId);
            } else {
                Log.e(TAG, "Invalid index: " + mFieldIndex);
                getFragmentManager().popBackStack();
            }
        } else {
            Log.e(TAG, "No bundle for the index");
            getFragmentManager().popBackStack();
        }

        super.onActivityCreated(savedInstanceState);
    }

    private void updatePrefsForSpecificTestField(PreferenceGroup prefGroup, int fieldId) {
        Preference[] prefs = new Preference[prefGroup.getPreferenceCount()];
        for (int i = 0; i < prefGroup.getPreferenceCount(); i++) {
            prefs[i] = prefGroup.getPreference(i);
        }
        // simply updating the key doesn't update the UI with the actual stored preference value for
        // the new key, so remove the preferences and add them back with the updated key to get the
        // appropriate value displayed
        prefGroup.removeAll();
        for (Preference pref : prefs) {
            String key = pref.getKey();
            if (pref instanceof PreferenceGroup) {
                updatePrefsForSpecificTestField((PreferenceGroup) pref, fieldId);
            } else {
                if (key != null && key.length() > 1 && key.charAt(key.length() - 1) == '_') {
                    // add the suffix to the preference keys
                    pref.setKey(key + fieldId);
                }
                if (pref instanceof SingleFieldPreference) {
                    // set the index for launching sub preference screens
                    ((SingleFieldPreference)pref).setFieldIndex(mFieldIndex);
                }
            }
            prefGroup.addPreference(pref);
        }
    }

    protected void registerPreferencesChangedListener(int fieldId) {
        // default does nothing, but this can be overridden for listening for changes if necessary
    }

    protected int getFieldIndex() {
        return mFieldIndex;
    }
}
