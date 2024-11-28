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

package com.wittmane.testingedittext.settings.datamanager;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.settings.IntRange;
import com.wittmane.testingedittext.settings.SharedPreferenceManager;

public class IntRangeDataManager implements DataManager<IntRange> {
    private static final String TAG = IntRangeDataManager.class.getSimpleName();

    public static final IntRange DEFAULT_RANGE = null;

    private final SharedPreferenceManager mPrefs;
    private final String mKey;

    public IntRangeDataManager(SharedPreferenceManager prefs, String key) {
        mPrefs = prefs;
        mKey = key;
    }

    @Nullable
    @Override
    public IntRange readValue() {
        String rawValue = mPrefs != null ? mPrefs.getString(mKey, null) : null;
        if (TextUtils.isEmpty(rawValue)) {
            return null;
        }
        String[] pieces = rawValue.split("-");
        if (pieces.length != 2) {
            Log.e(TAG, "Unexpected number of codepoints in range preference: " + rawValue);
            return null;
        }
        try {
            return new IntRange(Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Unexpected codepoint in range preference: " + rawValue);
            return null;
        }
    }

    @Nullable
    @Override
    public IntRange readDefaultValue() {
        return DEFAULT_RANGE;
    }

    @Override
    public void writeValue(final @Nullable IntRange value) {
        mPrefs.setString(mKey,
                value == null ? null : value.getStart() + "-" + value.getEnd());
    }
}
