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
package com.wittmane.testingedittext.settings;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class TextTranslateListPreference
        extends TextListPreferenceBase<TextTranslateListPreference.TranslateText> {

    public TextTranslateListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected View[] createRowContent(TranslateText data) {
        TextView rangeIndicator = new TextView(getContext());
        // U+2190 (←) LEFTWARDS ARROW
        // U+2192 (→) RIGHTWARDS ARROW
        rangeIndicator.setText(getContext().getResources().getConfiguration()
                .getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                        ? "\u2190"
                        : "\u2192");
        return new View[] {
                createEditText(data == null ? null : data.mOriginal),
                rangeIndicator,
                createEditText(data == null ? null : data.mTranslation)
        };
    }

    @Override
    protected boolean isRowEmpty(TranslateText rowData) {
        return TextUtils.isEmpty(rowData.mOriginal) && TextUtils.isEmpty(rowData.mTranslation);
    }

    @Override
    protected boolean shouldHaveExtraRow(View[] rowContent) {
        // there needs to be the original text to translate, but it can translate to ""
        return !TextUtils.isEmpty(((EditText)rowContent[0]).getText());
    }

    @NonNull
    @Override
    protected TranslateText[] getData() {
        TranslateText[] translationArray = new TranslateText[mRows.size()];
        for (int i = 0; i < mRows.size(); i++) {
            translationArray[i] = new TranslateText();
            translationArray[i].mOriginal =
                    ((EditText)mRows.get(i).mContent[0]).getText().toString();
            translationArray[i].mTranslation =
                    ((EditText)mRows.get(i).mContent[2]).getText().toString();
        }
        return translationArray;
    }

    @Override
    protected String[] flattenDataArray(final @NonNull TranslateText[] dataArray) {
        String[] result = new String[dataArray.length * 2];
        for (int i = 0; i < dataArray.length; i++) {
            result[i * 2] = dataArray[i].mOriginal;
            result[i * 2 + 1] = dataArray[i].mTranslation;
        }
        return result;
    }

    @Override
    protected @NonNull TranslateText[] buildDataArray(final @NonNull String[] data) {
        // add 1 in case there is an odd number of pieces after the escaped characters flag (assume
        // the last translation is "")
        TranslateText[] translationArray = new TranslateText[(data.length + 1) / 2];
        // copy all of the pieces (alternating between original and translation) except for
        // piece 0 (escape characters flag) to the translation array
        for (int i = 0; i < data.length; i++) {
            int index = i / 2;
            if (i % 2 == 0) {
                translationArray[index] = new TranslateText();
                translationArray[index].mOriginal = data[i];
            } else {
                translationArray[index].mTranslation = data[i];
            }
        }

        return translationArray;
    }

    @NonNull
    @Override
    protected TranslateText[] getDefaultDataArray() {
        return new TranslateText[0];
    }

    @Override
    protected String getValueText(final @NonNull TranslateText[] dataArray) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < dataArray.length; i++) {
            if (isRowEmpty(dataArray[i])) {
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
            //TODO: (EW) should this change for RTL?
            sb.append("\"").append(dataArray[i].mOriginal).append("\", -> \"")
                    .append(dataArray[i].mTranslation).append("\"");
        }
        return sb.toString();
    }

    protected static class TranslateText {
        private String mOriginal;
        private String mTranslation;
    }
}
