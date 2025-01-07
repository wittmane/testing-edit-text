/*
 * Copyright (C) 2024-2025 Eli Wittman
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

import static com.wittmane.testingedittext.settings.PreferenceKeys.*;

import android.os.Bundle;
import android.util.Log;

import com.wittmane.testingedittext.settings.Settings;

public abstract class PerTestGroupSettingsFragment extends SettingsFragment {
    private static final String TAG = PerTestGroupSettingsFragment.class.getSimpleName();

    public static final String GROUP_INDEX_BUNDLE_KEY = "GROUP_INDEX";

    private int mGroupIndex = Integer.MIN_VALUE;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGroupIndex = getIndexFromArgs(GROUP_INDEX_BUNDLE_KEY, Settings.getTestFieldGroupCount(),
                BASE_GROUP_INDEX);
    }

    protected int getIndexFromArgs(String key, int length, int baseIndex) {
        int index;
        final Bundle args = getArguments();
        if (args != null) {
            String groupIndex = args.getString(key);
            if (groupIndex != null) {
                try {
                    index = Integer.parseInt(groupIndex);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse the index: " + e.getMessage());
                    getFragmentManager().popBackStack();
                    // this value doesn't really matter
                    return baseIndex;
                }
                if (index < 0 || index >= length) {
                    Log.e(TAG, "Invalid index: " + index);
                    getFragmentManager().popBackStack();
                }
            } else {
                index = baseIndex;
            }
        } else {
            Log.e(TAG, "No bundle for the index");
            getFragmentManager().popBackStack();
            // this value doesn't really matter
            return baseIndex;
        }
        return index;
    }

    protected int getGroupIndex() {
        return mGroupIndex;
    }
}
