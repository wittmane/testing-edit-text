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
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TableRow;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.SharedPreferenceManager;
import com.wittmane.testingedittext.settings.TextList;
import com.wittmane.testingedittext.settings.preferences.TextEntryListPreferenceBase.TextListReader;

public abstract class TextEntryListPreferenceBase<T, TReader extends TextListReader<T>>
        extends EntryListPreference<T, TextList<T>, TReader> {
    private static final String TAG = TextEntryListPreferenceBase.class.getSimpleName();

    private CheckBox mEscapeCharactersCheckBox;

    public TextEntryListPreferenceBase(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.text_entry_list_dialog);
    }

    @Override
    protected View onCreateDialogView() {
        final View view = super.onCreateDialogView();
        mEscapeCharactersCheckBox = view.findViewById(R.id.escape_characters);
        return view;
    }

    protected abstract View[] createRowContent(T data);

    @Override
    protected View[] createRowContent(T data, TableRow tableRow) {
        View[] rowContent = createRowContent(data);
        for (View view : rowContent) {
            tableRow.addView(view);
        }
        return rowContent;
    }

    @Override
    protected void setExtraDataUI(TextList<T> data) {
        mEscapeCharactersCheckBox.setChecked(data.escapeChars());
    }

    protected static abstract class TextListReader<T> extends ReaderBase<TextList<T>> {

        protected TextListReader(SharedPreferenceManager prefs, String key) {
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
        protected TextList<T> readDefaultValue() {
            return new TextList<T>(getDefaultDataArray(), false);
        }

        @NonNull
        protected abstract T[] buildDataArray(final @NonNull String[] data);

        @NonNull
        protected abstract T[] getDefaultDataArray();
    }

    @NonNull
    protected String[] getFlattenedExtraData(final TextList<T> fullData) {
        return new String[] { fullData.escapeChars() ? "1" : "0" };
    }

    @Override
    protected T[] getRowData(TextList<T> textList) {
        return textList.getDataArray();
    }

    protected abstract T[] getRowData();

    @NonNull
    @Override
    protected TextList<T> getUIData() {
        return new TextList<T>(getRowData(), mEscapeCharactersCheckBox.isChecked());
    }
}
