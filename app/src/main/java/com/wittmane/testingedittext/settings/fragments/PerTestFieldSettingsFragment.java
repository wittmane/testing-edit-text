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

import static com.wittmane.testingedittext.settings.Settings.BASE_FIELD_ID;
import static com.wittmane.testingedittext.settings.Settings.BASE_FIELD_INDEX;
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

    private int mFieldIndex = Integer.MIN_VALUE;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                if (mFieldIndex < 0 || mFieldIndex >= Settings.getTestFieldCount()) {
                    Log.e(TAG, "Invalid index: " + mFieldIndex);
                    getFragmentManager().popBackStack();
                }
            } else {
                mFieldIndex = BASE_FIELD_INDEX;
            }
        } else {
            Log.e(TAG, "No bundle for the index");
            getFragmentManager().popBackStack();
        }
    }

    @Override
    public void addPreferencesFromResource(int preferencesResId) {
        // make sure we already got a valid field index from onCreate
        if (mFieldIndex != BASE_FIELD_INDEX
                && (mFieldIndex < 0 || mFieldIndex >= Settings.getTestFieldCount())) {
            Log.e(TAG, "Invalid index: " + mFieldIndex);
            getFragmentManager().popBackStack();
        }

        // in case there are any preferences with a key matching the prefix specified in the
        // resource file, clear out the shared preferences name because changing the key doesn't
        // reset the preference to the default value in case there isn't a value set for the new
        // preference key, and there doesn't seem to be way to force it to reset to default. this
        // will force it to start at the default value so we don't need to try to reset it.
        String sharedPreferencesName = getPreferenceManager().getSharedPreferencesName();
        getPreferenceManager().setSharedPreferencesName(null);

        super.addPreferencesFromResource(preferencesResId);
        PreferenceScreen preferenceScreen = getPreferenceScreen();

        // add the appropriate suffix to the preferences
        int fieldId = getFieldId();
        updatePrefsForSpecificTestField(preferenceScreen, fieldId);

        // add the original shared preferences name back now that the correct keys are set, and
        // force the preferences to update to use the value from the shared preferences
        getPreferenceManager().setSharedPreferencesName(sharedPreferencesName);
        refreshPrefs(preferenceScreen);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        registerPreferencesChangedListener(getFieldId());
    }

    private void updatePrefsForSpecificTestField(PreferenceGroup prefGroup, int fieldId) {
        Preference[] prefs = new Preference[prefGroup.getPreferenceCount()];
        for (int i = 0; i < prefGroup.getPreferenceCount(); i++) {
            prefs[i] = prefGroup.getPreference(i);
        }
        for (Preference pref : prefs) {
            String key = pref.getKey();
            if (pref instanceof PreferenceGroup) {
                updatePrefsForSpecificTestField((PreferenceGroup) pref, fieldId);
            } else {
                String suffix = getPrefKeySuffix(fieldId);
                if (key != null && key.length() > 1 && !key.endsWith(suffix)) {
                    // add the suffix to the preference keys
                    pref.setKey(key + suffix);
                }
                if (pref instanceof PerTestFieldPreference) {
                    // set the index for launching sub preference screens
                    ((PerTestFieldPreference)pref).setFieldIndex(mFieldIndex);
                }
            }
        }
    }

    private void refreshPrefs(PreferenceGroup prefGroup) {
        // simply updating the key or the shared preference name doesn't update the UI with the
        // actual stored preference value for, so we need to remove the preferences and add them
        // back to get the appropriate value displayed
        Preference[] prefs = new Preference[prefGroup.getPreferenceCount()];
        for (int i = 0; i < prefGroup.getPreferenceCount(); i++) {
            prefs[i] = prefGroup.getPreference(i);
        }
        prefGroup.removeAll();
        for (Preference pref : prefs) {
            if (pref instanceof PreferenceGroup) {
                refreshPrefs((PreferenceGroup) pref);
            }
            prefGroup.addPreference(pref);
        }
    }

    private int getFieldId() {
        return mFieldIndex == BASE_FIELD_INDEX
                ? BASE_FIELD_ID
                : Settings.getTestFieldId(mFieldIndex);
    }

    protected String getPrefKey(String prefKeyPrefix) {
        return prefKeyPrefix + getPrefKeySuffix(getFieldId());
    }

    private static String getPrefKeySuffix(int fieldId) {
        if (fieldId == BASE_FIELD_ID) {
            return BASE_SUFFIX;
        }
        return FIELD_INFIX + fieldId;
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
        if (getFieldIndex() == BASE_FIELD_INDEX) {
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
