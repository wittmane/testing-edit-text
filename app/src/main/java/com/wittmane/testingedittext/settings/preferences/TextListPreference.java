/*
 * Copyright (C) 2022-2024 Eli Wittman
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

import static com.wittmane.testingedittext.settings.PreferenceReader.DEFAULT_RESTRICT_SPECIFIC;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.settings.SharedPreferenceManager;
import com.wittmane.testingedittext.settings.TextList;
import com.wittmane.testingedittext.settings.preferences.TextListPreference.DataManager;

import java.util.List;

public class TextListPreference extends TextEntryListPreferenceBase<String, DataManager> {

    public TextListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View[] createRowContent(String data) {
        return new View[] {
                createEditText(data)
        };
    }

    @Override
    protected boolean isRowEmpty(String rowData) {
        return TextUtils.isEmpty(rowData);
    }

    @Override
    protected String getRowData(View[] rowContent) {
        return ((EditText)rowContent[0]).getText().toString();
    }

    @Override
    protected String[] createArray(List<String> list) {
        return list.toArray(new String[0]);
    }

    @Override
    protected DataManager createDataManager(SharedPreferenceManager prefs, String key) {
        return new DataManager(prefs, key);
    }

    public static class DataManager extends TextListDataManager<String> {
        public DataManager(SharedPreferenceManager prefs, String key) {
            super(prefs, key);
        }

        @Override
        protected @NonNull String[] buildDataArray(final @NonNull String[] data) {
            return data;
        }

        @NonNull
        @Override
        protected String[] getDefaultDataArray() {
            return DEFAULT_RESTRICT_SPECIFIC;
        }

        @NonNull
        @Override
        protected String[] flattenRowData(@NonNull TextList<String> fullData) {
            return fullData.getDataArray();
        }

    }

    @Override
    protected String getValueText(final @NonNull String[] dataArray) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < dataArray.length; i++) {
            if (TextUtils.isEmpty(dataArray[i])) {
                continue;
            }
            if (sb.length() != 0) {
                sb.append(", ");
            }
            // the text may contain a quote, but there isn't really a good way to handle this. we
            // could use angled quotes, but it could contain both. there may be other quote-like
            // characters that we could go through, but all of them could be included, so we can't
            // fully fix this, and the extra logic and slightly weird behavior of changing quote
            // types doesn't seem worth it for this minor visual bug.
            sb.append("\"").append(dataArray[i]).append("\"");
        }
        return sb.toString();
    }
}
