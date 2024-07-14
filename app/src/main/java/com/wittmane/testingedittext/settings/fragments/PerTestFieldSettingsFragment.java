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

import static com.wittmane.testingedittext.settings.Settings.BASE_SUFFIX;
import static com.wittmane.testingedittext.settings.Settings.FIELD_INFIX;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager;
import com.wittmane.testingedittext.settings.SwitchPreferenceDependencyManager.OnPreferencesChangedListener;
import com.wittmane.testingedittext.settings.preferences.PerTestFieldPreference;

public abstract class PerTestFieldSettingsFragment extends PreferenceFragment {
    private static final String TAG = PerTestFieldSettingsFragment.class.getSimpleName();

    public static final String FIELD_INDEX_BUNDLE_KEY = "FIELD_INDEX";

    public static final int NO_FIELD_INDEX = -1;
    private static final int BASE_FIELD_ID = -1;

    private int mFieldIndex = -1;

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        // note that this is done here, rather than in onCreate because the preference screen isn't
        // available yet
        final Bundle args = getArguments();
        if (args != null) {
            String fieldIndex = args.getString(FIELD_INDEX_BUNDLE_KEY);
            if (fieldIndex != null) {
                try {
                    mFieldIndex = Integer.parseInt(fieldIndex);
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
                mFieldIndex = NO_FIELD_INDEX;
                updatePrefsForSpecificTestField(getPreferenceScreen(), BASE_FIELD_ID);

                registerPreferencesChangedListener(BASE_FIELD_ID);
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
                if (key != null && key.length() > 1) {
                    // add the suffix to the preference keys
                    pref.setKey(getPrefKey(key, fieldId));
                }
                if (pref instanceof PerTestFieldPreference) {
                    // set the index for launching sub preference screens
                    ((PerTestFieldPreference)pref).setFieldIndex(mFieldIndex);
                }
            }
            prefGroup.addPreference(pref);
        }
    }

    protected String getPrefKey(String prefKeyPrefix) {
        if (mFieldIndex == NO_FIELD_INDEX) {
            return getPrefKey(prefKeyPrefix, BASE_FIELD_ID);
        }
        int fieldId = Settings.getTestFieldId(mFieldIndex);
        return prefKeyPrefix + FIELD_INFIX + fieldId;
    }

    private static String getPrefKey(String prefKeyPrefix, int fieldId) {
        if (fieldId == BASE_FIELD_ID) {
            return prefKeyPrefix + BASE_SUFFIX;
        }
        return prefKeyPrefix + FIELD_INFIX + fieldId;
    }

    protected void registerPreferencesChangedListener(int fieldId) {
        // default does nothing, but this can be overridden for listening for changes if necessary
    }

    protected int getFieldIndex() {
        return mFieldIndex;
    }

    protected void manageOverrideToggle(String overridePrefKeyPrefix) {
        String overridePrefKey = getPrefKey(overridePrefKeyPrefix);

        new SwitchPreferenceDependencyManager(new String[]{
                overridePrefKey
        }, this, new OnPreferencesChangedListener() {
            @Override
            public void onPreferencesChanged(boolean[] prefsChecked) {
                updateEnabledState(prefsChecked[0], overridePrefKey);
            }
        });
        if (getFieldIndex() == NO_FIELD_INDEX) {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            Preference pref = findPreference(overridePrefKey);
            preferenceScreen.removePreference(pref);
        }
    }

    private void updateEnabledState(boolean overrideDefaults, String overridePrefKey) {
        boolean enableSettings;
        if (overrideDefaults || getFieldIndex() < 0) {
            enableSettings = true;
        } else {
            enableSettings = false;
        }

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
            Preference pref = preferenceScreen.getPreference(i);
            if (overridePrefKey.equals(pref.getKey())) {
                continue;
            }
            pref.setEnabled(enableSettings);
        }
    }
}
