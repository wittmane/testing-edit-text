/*
 * Copyright (C) 2022 Eli Wittman
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.settings.SharedPreferenceManager;
import com.wittmane.testingedittext.settings.preferences.SimpleEntryListPreference.SimpleReader;

import java.util.ArrayList;
import java.util.List;

/**
 * Preference for entering a list of items (all data tied to individual items)
 * @param <TRowData> Type for the items in the list
 * @param <TReader> Type for reading the preference data
 */
public abstract class SimpleEntryListPreference<TRowData, TReader extends SimpleReader<TRowData>>
        extends EntryListPreference<TRowData, TRowData[], TReader> {

    public SimpleEntryListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setExtraDataUI(TRowData[] data) {
    }

    @NonNull
    @Override
    protected TRowData[] getUIData() {
        List<TRowData> locales = new ArrayList<>();
        for (Row row : mRows) {
            if (canRemoveAsExtraLine(row.mContent)) {
                continue;
            }
            locales.add(getRowData(row.mContent));
        }
        return createArray(locales);
    }

    /**
     * Build a data object for the row based on the values entered in the UI.
     * @param rowContent The views that make up the row.
     * @return The data that should be saved from the row.
     */
    protected abstract TRowData getRowData(View[] rowContent);

    /**
     * Convert a list to an array.
     * @param list The list to convert.
     * @return The array equivalent of the list.
     */
    protected abstract TRowData[] createArray(List<TRowData> list);

    @Override
    protected TRowData[] getRowData(final TRowData[] fullData) {
        return fullData;
    }

    @NonNull
    @Override
    protected String[] getFlattenedExtraData(final TRowData[] fullData) {
        return new String[0];
    }

    public static abstract class SimpleReader<T> extends ReaderBase<T[]> {
        public SimpleReader(SharedPreferenceManager prefs, String key) {
            super(prefs, key);
        }

        @Override
        protected int getExtraDataLength() {
            return 0;
        }

        protected T[] buildFullData(String[] flatRowData, String[] extraData) {
            return buildRowData(flatRowData);
        }

        protected abstract T[] buildRowData(String[] flatRowData);
    }
}
