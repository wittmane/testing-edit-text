/*
 * Copyright (C) 2024-2025 Eli Wittman
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

import android.util.Log;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.datatype.TextList;
import com.wittmane.testingedittext.settings.SharedPreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public abstract class TextListDataManager<T> extends ListDataManager<TextList<T>> {
    private static final String TAG = TextListDataManager.class.getSimpleName();

    protected TextListDataManager(SharedPreferenceManager prefs, String key) {
        super(prefs, key);
    }

    @Override
    protected int getExtraDataLength() {
        return 1;
    }

    @Override
    protected TextList<T> buildFullData(String[] rowData, String[] extraData) {
        boolean escapeChars;
        if (extraData[0].equals("1")) {
            escapeChars = true;
        } else if (extraData[0].equals("0")) {
            escapeChars = false;
        } else {
            Log.e(TAG, "Invalid escape character flag (" + extraData[0] + ") from preference "
                    + mKey);
            escapeChars = false;
        }

        return new TextList<T>(buildDataArray(rowData), escapeChars);
    }

    @NonNull
    @Override
    public TextList<T> readDefaultValue() {
        return new TextList<T>(getDefaultDataArray(), false);
    }

    @NonNull
    protected abstract T[] buildDataArray(final @NonNull String[] data);

    @NonNull
    protected abstract T[] getDefaultDataArray();

    @NonNull
    protected String[] flattenExtraData(final @NonNull TextList<T> fullData) {
        return new String[] { fullData.escapeChars() ? "1" : "0" };
    }

    protected T[] stripDuplicates(T[] fullData, T[] emptyArray) {
        List<T> deduplicatedData = new ArrayList<>();
        HashSet<T> usedData = new HashSet<>();
        for (T entry : fullData) {
            if (usedData.contains(entry)) {
                continue;
            }
            deduplicatedData.add(entry);
            usedData.add(entry);
        }
        return deduplicatedData.toArray(emptyArray);
    }
}
