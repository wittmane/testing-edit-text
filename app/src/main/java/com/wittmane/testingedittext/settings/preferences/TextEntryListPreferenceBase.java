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

import java.util.ArrayList;
import java.util.List;

/**
 * Preference for entering a list of raw text items with a flag for handling of special characters
 * @param <T> Type for the items in the list
 * @param <TReader> Type for reading the preference data
 */
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

    /**
     * Build a data object for the row based on the values entered in the UI.
     * @param rowContent The views that make up the row.
     * @return The data that should be saved from the row.
     */
    protected abstract T getRowData(View[] rowContent);

    /**
     * Convert a list to an array.
     * @param list The list to convert.
     * @return The array equivalent of the list.
     */
    protected abstract T[] createArray(List<T> list);

    @NonNull
    @Override
    protected TextList<T> getUIData() {
        List<T> rowData = new ArrayList<>();
        for (Row row : mRows) {
            if (canRemoveAsExtraLine(row.mContent)) {
                continue;
            }
            rowData.add(getRowData(row.mContent));
        }
        return new TextList<T>(createArray(rowData), mEscapeCharactersCheckBox.isChecked());
    }
}
