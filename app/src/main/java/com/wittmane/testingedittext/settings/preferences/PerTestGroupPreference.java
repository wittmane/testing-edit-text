/*
 * Copyright (C) 2024 Eli Wittman
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

package com.wittmane.testingedittext.settings.preferences;

import static com.wittmane.testingedittext.settings.Settings.BASE_GROUP_INDEX;
import static com.wittmane.testingedittext.settings.fragments.PerTestGroupSettingsFragment.GROUP_INDEX_BUNDLE_KEY;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;

/**
 * Preference to link to a test field group specific settings screen.
 */
public abstract class PerTestGroupPreference extends Preference {
    private static final String TAG = PerTestGroupPreference.class.getSimpleName();

    private int mGroupIndex = -1;
    private Bundle mExtras;

    public PerTestGroupPreference(Context context) {
        super(context);
    }

    public PerTestGroupPreference(Context context, int groupIndex) {
        this(context);
        setGroupIndex(groupIndex);
    }

    public PerTestGroupPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PerTestGroupPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PerTestGroupPreference(Context context, AttributeSet attrs, int defStyleAttr,
                                  int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);

        if (mGroupIndex >= 0) {
            updateSummary();
        }
    }

    protected void setGroupIndex(int groupIndex) {
        if (mGroupIndex == groupIndex) {
            return;
        }
        mGroupIndex = groupIndex;
        mExtras = null;
    }

    public int getGroupIndex() {
        return mGroupIndex;
    }

    @Override
    public Bundle getExtras() {
        if (mGroupIndex == BASE_GROUP_INDEX) {
            return super.getExtras();
        }
        if (mExtras == null) {
            mExtras = new Bundle();
            mExtras.putString(GROUP_INDEX_BUNDLE_KEY, "" + mGroupIndex);
        }
        return mExtras;
    }

    @Override
    public Bundle peekExtras() {
        if (mGroupIndex < 0) {
            Log.e(TAG, "No group index for extras");
            return super.getExtras();
        }
        return mExtras;
    }

    //TODO: (EW) consider renaming since implementations update both the title and summary
    protected abstract void updateSummary();
}
