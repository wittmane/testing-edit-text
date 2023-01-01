/*
 * Copyright (C) 2022 Eli Wittman
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

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.view.MenuItem;

import com.wittmane.testingedittext.settings.fragments.MainSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ModifyTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ReturningTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.SystemBehaviorSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TargetVersionSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.ComposingTextSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldImeActionSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldImeOptionsSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldInputTypeSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldListSettingsFragment;
import com.wittmane.testingedittext.settings.fragments.TestFieldSettingsFragment;

public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(final Bundle savedState) {
        super.onCreate(savedState);
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        if (savedState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new MainSettingsFragment()).commit();
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
            super.onBackPressed();
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
                || TestFieldListSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldInputTypeSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldImeOptionsSettingsFragment.class.getName().equals(fragmentName)
                || TestFieldImeActionSettingsFragment.class.getName().equals(fragmentName);
    }
}
