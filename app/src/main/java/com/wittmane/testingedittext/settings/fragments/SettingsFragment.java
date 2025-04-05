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

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.util.EdgeToEdgeUtils;

public abstract class SettingsFragment extends PreferenceFragment {
    private static final String TAG = SettingsFragment.class.getSimpleName();

    protected View mView;

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
    public void onDetach() {
        if (mView != null) {
            EdgeToEdgeUtils.removeInsetHandling(getActivity(),
                    mView.findViewById(android.R.id.list));
        }
        super.onDetach();
    }
}
