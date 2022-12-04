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
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.settings.preferences.SimpleEntryListPreference.SimpleReader;

public abstract class SimpleEntryListPreference<TRowData, TReader extends SimpleReader<TRowData>>
        extends EntryListPreference<TRowData, TRowData[], TReader> {

    public SimpleEntryListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setExtraDataUI(TRowData[] data) {
    }

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
        public SimpleReader(SharedPreferences prefs, String key) {
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
