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

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.datatype.TextList;
import com.wittmane.testingedittext.settings.SharedPreferenceManager;

public class StringTextListDataManager extends TextListDataManager<String> {
    private boolean mAllowDuplicates;

    public StringTextListDataManager(SharedPreferenceManager prefs, String key,
                                     boolean allowDuplicates) {
        super(prefs, key);
        mAllowDuplicates = allowDuplicates;
    }

    @Override
    protected @NonNull String[] buildDataArray(final @NonNull String[] data) {
        return data;
    }

    @NonNull
    @Override
    protected String[] getDefaultDataArray() {
        return new String[0];
    }

    @NonNull
    @Override
    protected String[] flattenRowData(@NonNull TextList<String> fullData) {
        String[] dataArray = fullData.getDataArray();
        return mAllowDuplicates ? dataArray : stripDuplicates(dataArray, new String[0]);
    }
}
