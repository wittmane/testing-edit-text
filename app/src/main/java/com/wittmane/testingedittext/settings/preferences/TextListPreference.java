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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.settings.SharedPreferenceManager;
import com.wittmane.testingedittext.settings.preferences.TextListPreference.Reader;

public class TextListPreference extends TextEntryListPreferenceBase<String, Reader> {

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

    @NonNull
    @Override
    protected String[] getRowData() {
        String[] textArray = new String[mRows.size()];
        for (int i = 0; i < mRows.size(); i++) {
            textArray[i] = ((EditText)mRows.get(i).mContent[0]).getText().toString();
        }
        return textArray;
    }

    @Override
    protected String[] flattenDataArray(final @NonNull String[] dataArray) {
        return dataArray;
    }

    @Override
    protected Reader createReader(SharedPreferenceManager prefs, String key) {
        return new Reader(prefs, key);
    }

    public static class Reader extends TextEntryListPreferenceBase.TextListReader<String> {
        public Reader(SharedPreferenceManager prefs, String key) {
            super(prefs, key);
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
