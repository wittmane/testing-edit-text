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

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.settings.SharedPreferenceManager;

public abstract class ListDataManager<T> implements DataManager<T> {
    protected final SharedPreferenceManager mPrefs;
    protected String mKey;

    protected ListDataManager(SharedPreferenceManager prefs, String key) {
        mPrefs = prefs;
        mKey = key;
    }

    protected abstract int getExtraDataLength();

    @NonNull
    @Override
    public T readValue() {
        String[] pieces = mPrefs != null ? mPrefs.getStringArray(mKey, null) : null;
        if (pieces == null) {
            return readDefaultValue();
        }

        String[] extraData = new String[getExtraDataLength()];
        if (extraData.length > 0) {
            System.arraycopy(pieces, 0, extraData, 0, extraData.length);
        }

        // create a new array excluding any extra data
        String[] rowData = new String[pieces.length - extraData.length];
        if (pieces.length > extraData.length) {
            System.arraycopy(pieces, extraData.length, rowData, 0,
                    pieces.length - extraData.length);
        }

        return buildFullData(rowData, extraData);
    }

    protected abstract T buildFullData(String[] rowData, String[] extraData);

    @NonNull
    @Override
    public abstract T readDefaultValue();

    @Override
    public void writeValue(@NonNull T fullData) {
        String[] rowData = flattenRowData(fullData);
        String[] extraData = flattenExtraData(fullData);
        String[] dataForSave = new String[rowData.length + extraData.length];
        System.arraycopy(extraData, 0, dataForSave, 0, extraData.length);
        System.arraycopy(rowData, 0, dataForSave, extraData.length, rowData.length);

        mPrefs.setStringArray(mKey, dataForSave);
    }

    @NonNull
    protected abstract String[] flattenRowData(final @NonNull T fullData);

    @NonNull
    protected abstract String[] flattenExtraData(final @NonNull T fullData);
}
