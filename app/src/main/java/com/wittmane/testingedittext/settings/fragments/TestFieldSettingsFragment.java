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

import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.preferences.LocaleEntryListPreference;

public class TestFieldSettingsFragment extends TestFieldBaseSettingsFragment {
    private static final String TAG = TestFieldSettingsFragment.class.getSimpleName();

    private View mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_test_field);
        setHasOptionsMenu(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            Preference imeHintLocalesPref = findPreference(
                    Settings.PREF_TEST_FIELD_IME_HINT_LOCALES_PREFIX);
            preferenceScreen.removePreference(imeHintLocalesPref);

            LocaleEntryListPreference textLocalesPref =
                    (LocaleEntryListPreference)findPreference(
                            Settings.PREF_TEST_FIELD_TEXT_LOCALES_PREFIX);
            textLocalesPref.setMaxEntries(1);
        }
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);
        return mView;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.test_field, menu);

        MenuItem addFieldMenuItem = menu.findItem(R.id.action_remove_field);
        IconUtils.matchMenuIconColor(mView, addFieldMenuItem, getActivity().getActionBar());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_remove_field) {
            // remove the field and go back to the field list
            Settings.removeTestField(getFieldIndex());
            getFragmentManager().popBackStackImmediate();
        }
        return super.onOptionsItemSelected(item);
    }
}
