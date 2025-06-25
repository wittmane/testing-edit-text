/*
 * Copyright (C) 2022-2025 Eli Wittman
 * Copyright (C) 2007 The Android Open Source Project
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

import static com.wittmane.testingedittext.settings.fragments.PerTestFieldSettingsFragment.FIELD_INDEX_BUNDLE_KEY;
import static com.wittmane.testingedittext.settings.fragments.PerTestGroupSettingsFragment.GROUP_INDEX_BUNDLE_KEY;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.view.MenuItem;
import android.window.OnBackInvokedDispatcher;

import com.wittmane.testingedittext.util.EdgeToEdgeUtils;
import com.wittmane.testingedittext.settings.fragments.DisplaySettingsFragment;
import com.wittmane.testingedittext.settings.fragments.MainSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ModifyTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ReturningTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.SystemBehaviorSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TargetVersionSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ComposingTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ImeActionSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ImeOptionsSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.InputTypeSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldGroupListSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldGroupSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldSettingsFragment;

public class SettingsActivity extends PreferenceActivity {
    private static final String TAG = SettingsActivity.class.getSimpleName();

    public static final String FIELD_ID_BUNDLE_KEY = "FIELD_ID";

    @Override
    protected void onCreate(final Bundle savedState) {
        setTheme(Settings.getThemeId(this));
        super.onCreate(savedState);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        if (savedState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new MainSettingsFragment()).commit();
        }
        // handle the insets excluding the bottom to support showing the preference list behind the
        // navigation bar
        EdgeToEdgeUtils.addInsetHandling(this, true, true, true, false);

        // the back navigation bar button and gesture stopped calling #onBackPressed by default in
        // Android 13, so we have to add that back
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            int fieldId = extras.getInt(FIELD_ID_BUNDLE_KEY, -1);
            FieldPosition position = fieldId >= 0 ? Settings.getTestFieldPosition(fieldId) : null;
            if (position != null) {
                Bundle targetExtras = new Bundle();
                targetExtras.putString(GROUP_INDEX_BUNDLE_KEY, "" + position.groupIndex);
                targetExtras.putString(FIELD_INDEX_BUNDLE_KEY, "" + position.fieldIndex);
                Fragment f = new TestFieldSettingsFragment();
                f.setArguments(targetExtras);
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(android.R.id.content, f);
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                transaction.commitAllowingStateLoss();
            }
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        // (EW) based on PreferenceActivity#onPreferenceStartFragment and
        // PreferenceActivity#startPreferencePanel

        Fragment f = Fragment.instantiate(this, pref.getFragment(), pref.getExtras());
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(android.R.id.content, f);
        if (pref.getTitleRes() != 0) {
            transaction.setBreadCrumbTitle(pref.getTitleRes());
        } else if (pref.getTitle() != null) {
            transaction.setBreadCrumbTitle(pref.getTitle());
        }
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        transaction.addToBackStack(null);
        transaction.commitAllowingStateLoss();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean isValidFragment(final String fragmentName) {
        return MainSettingsFragment.class.getName().equals(fragmentName)
                || ModifyTextSettingsFragment.class.getName().equals(fragmentName)
                || SystemBehaviorSettingsFragment.class.getName().equals(fragmentName)
                || TargetVersionSettingsFragment.class.getName().equals(fragmentName)
                || ComposingTextSettingsFragment.class.getName().equals(fragmentName)
                || ReturningTextSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldGroupListSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldGroupSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldSettingsFragment.class.getName().equals(fragmentName)
                || InputTypeSettingsFragment.class.getName().equals(fragmentName)
                || ImeOptionsSettingsFragment.class.getName().equals(fragmentName)
                || ImeActionSettingsFragment.class.getName().equals(fragmentName)
                || DisplaySettingsFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        EdgeToEdgeUtils.removeInsetHandling(this);
        super.onDestroy();
    }
}
