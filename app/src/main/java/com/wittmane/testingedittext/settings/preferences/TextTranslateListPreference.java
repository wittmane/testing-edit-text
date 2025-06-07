/*
 * Copyright (C) 2022-2025 Eli Wittman
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
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.datatype.TranslateText;
import com.wittmane.testingedittext.settings.datamanager.TranslateTextTextListDataManager;
import com.wittmane.testingedittext.settings.SharedPreferenceManager;

import java.util.HashSet;
import java.util.List;

public class TextTranslateListPreference
        extends TextEntryListPreferenceBase<TranslateText, TranslateTextTextListDataManager> {

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
                createEditText(data == null ? null : data.getOriginal(), false, true, true),
                rangeIndicator,
                createEditText(data == null ? null : data.getTranslation(), true, false, true)
        };
    }

    private static EditText getOriginalEditText(View[] rowContent) {
        return (EditText) rowContent[0];
    }

    private static EditText getTranslationEditText(View[] rowContent) {
        return (EditText) rowContent[2];
    }

    @Override
    protected boolean isDataValid() {
        HashSet<String> originalTexts = new HashSet<>();
        HashSet<String> duplicateOriginalTexts = new HashSet<>();
        boolean isValid = true;
        for (TranslateText translateText : getUIData().getDataArray()) {
            String original = translateText.getOriginal();
            if (TextUtils.isEmpty(original)) {
                isValid = false;
                continue;
            }
            if (originalTexts.contains(original)) {
                isValid = false;
                duplicateOriginalTexts.add(original);
                continue;
            }
            originalTexts.add(original);
        }

        // update errors
        for (Row row : mRows) {
            if (canRemoveAsExtraLine(row.mContent)) {
                continue;
            }
            EditText originalEditText = (EditText) getOriginalEditText(row.mContent);
            CharSequence originalText = originalEditText.getText();
            if (TextUtils.isEmpty(originalText)) {
                originalEditText.setError(
                        getContext().getString(R.string.text_cant_be_blank_error));
            } else if (duplicateOriginalTexts.contains(originalText.toString())) {
                originalEditText.setError(
                        getContext().getString(R.string.duplicates_not_allowed_error));
            } else {
                originalEditText.setError(null);
            }
        }

        return isValid;
    }

    @Override
    protected boolean isRowEmpty(TranslateText rowData) {
        return TextUtils.isEmpty(rowData.getOriginal())
                && TextUtils.isEmpty(rowData.getTranslation());
    }

    @Override
    protected boolean shouldHaveExtraRow(View[] rowContent) {
        // there needs to be the original text to translate, but it can translate to ""
        return !TextUtils.isEmpty(getOriginalEditText(rowContent).getText());
    }

    @Override
    protected TranslateText getUIRowData(View[] rowContent) {
        TranslateText translation = new TranslateText();
        translation.setOriginal(
                getOriginalEditText(rowContent).getText().toString());
        translation.setTranslation(
                getTranslationEditText(rowContent).getText().toString());
        return translation;
    }

    @Override
    protected TranslateText[] createArray(List<TranslateText> list) {
        return list.toArray(new TranslateText[0]);
    }

    @Override
    protected TranslateTextTextListDataManager createDataManager(SharedPreferenceManager prefs,
                                                                 String key) {
        return new TranslateTextTextListDataManager(prefs, key);
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
            sb.append("\"").append(dataArray[i].getOriginal()).append("\", -> \"")
                    .append(dataArray[i].getTranslation()).append("\"");
        }
        return sb.toString();
    }
}
