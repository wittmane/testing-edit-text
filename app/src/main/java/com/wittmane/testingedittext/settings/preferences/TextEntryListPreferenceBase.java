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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TableRow;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.datatype.TextList;
import com.wittmane.testingedittext.settings.datamanager.TextListDataManager;

import java.util.List;

/**
 * Preference for entering a list of raw text items with a flag for handling of special characters
 * @param <T> Type for the items in the list
 * @param <TDataManager> Type for reading and writing the preference data
 */
public abstract class TextEntryListPreferenceBase<T, TDataManager extends TextListDataManager<T>>
        extends EntryListPreference<T, TextList<T>, TDataManager> {
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

    @Override
    protected T[] getRowData(TextList<T> textList) {
        return textList.getDataArray();
    }

    /**
     * Convert a list to an array.
     * @param list The list to convert.
     * @return The array equivalent of the list.
     */
    protected abstract T[] createArray(List<T> list);

    @NonNull
    @Override
    protected TextList<T> getUIData() {
        return new TextList<T>(createArray(getUIRowDataList()),
                mEscapeCharactersCheckBox.isChecked());
    }
}
