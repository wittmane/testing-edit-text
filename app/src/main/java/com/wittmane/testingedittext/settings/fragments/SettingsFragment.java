/*
 * Copyright (C) 2025 Eli Wittman
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

import android.app.Activity;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.settings.SettingsActivity;
import com.wittmane.testingedittext.util.EdgeToEdgeUtils;
import com.wittmane.testingedittext.util.IconUtils;

public abstract class SettingsFragment extends PreferenceFragment {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    protected View mView;
    private boolean mLifecycleHasReachedResume = false;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);
        return mView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        // handle the bottom insets in the preference list scrolling content since it isn't handled
        // on the activity level to allow showing content behind the navigation bar
        EdgeToEdgeUtils.addInsetHandling(getActivity(), mView.findViewById(android.R.id.list),
                false, false, false, true);
    }

    @Override
    public final void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (!isCurrentFragment()) {
            return;
        }
        onCreateOptionsMenuInternal(menu, inflater);

        IconUtils.matchMenuIconColor(mView, menu, getActivity().getActionBar());
    }

    protected void onCreateOptionsMenuInternal(final Menu menu, final MenuInflater inflater) { }

    @Override
    public final boolean onOptionsItemSelected(final MenuItem item) {
        if (!isCurrentFragment()) {
            return super.onOptionsItemSelected(item);
        }
        return onOptionsItemSelectedInternal(item);
    }

    protected boolean onOptionsItemSelectedInternal(final MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    protected boolean isCurrentFragment() {
        Activity activity = getActivity();
        if (activity instanceof SettingsActivity) {
            return ((SettingsActivity) getActivity()).getCurrentFragment() == this;
        }
        return false;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && mLifecycleHasReachedResume) {
            onRedisplay();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLifecycleHasReachedResume && !isHidden()) {
            onRedisplay();
        }
        mLifecycleHasReachedResume = true;
    }

    /**
     * Pseudo lifecycle event to manage updating the display after returning. This is called after
     * resuming (excluding the first resume after create since the initial content should still be
     * relevant) and unhiding (since the state is still resumed when the fragment is hidden).
     */
    protected void onRedisplay() {
        refreshPreferences(getPreferenceScreen());
    }

    private static void refreshPreferences(PreferenceGroup group) {
        // there doesn't seem to a way to directly tell a Preference to refresh based on saved
        // preference data, so we'll just rip everything off the screen and immediately add it back
        // to trigger it to refresh. this seems inefficient, and ideally we could just refresh
        // everything in place. some of our custom classes have methods that allow them to refresh
        // (PerTestGroupPreference#updateDisplayText, DialogPreferenceBase#updateValueSummary), but
        // we'd still need some other generic handling or specific handling for all of the known
        // Preference subclasses. we could manually check the preference and set the Preference
        // object to that value, but since we can't access the default set on the Preference, we
        // don't have a way to handle the case where the preference has no saved data.
        Preference[] prefs = new Preference[group.getPreferenceCount()];
        int index = 0;
        while (group.getPreferenceCount() > 0) {
            Preference pref = group.getPreference(0);
            prefs[index++] = pref;
            group.removePreference(pref);
        }
        for (int i = 0; i < prefs.length; i++) {
            Preference pref = prefs[i];
            if (pref instanceof PreferenceGroup) {
                refreshPreferences((PreferenceGroup) pref);
            }
            group.addPreference(pref);
        }
    }

    protected void navigateBack() {
        navigateBack(false);
    }

    protected void navigateBack(boolean isImmediatelyAddingNewFragment) {
        Activity activity = getActivity();
        if (activity instanceof SettingsActivity) {
            ((SettingsActivity) activity).navigateBack(isImmediatelyAddingNewFragment);
        } else {
            // this shouldn't ever happen
            getFragmentManager().popBackStack();
        }
    }

    protected void launchPrefFragment(Preference pref) {
        ((OnPreferenceStartFragmentCallback) getActivity())
                .onPreferenceStartFragment(this, pref);
    }

    @Override
    public void onDetach() {
        if (mView != null) {
            EdgeToEdgeUtils.removeInsetHandling(getActivity(),
                    mView.findViewById(android.R.id.list));
        }
        super.onDetach();
    }
}
