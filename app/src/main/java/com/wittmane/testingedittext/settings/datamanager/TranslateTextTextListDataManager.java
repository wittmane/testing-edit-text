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

import com.wittmane.testingedittext.datatype.TextList;
import com.wittmane.testingedittext.datatype.TranslateText;
import com.wittmane.testingedittext.settings.SharedPreferenceManager;

public class TranslateTextTextListDataManager extends TextListDataManager<TranslateText> {
    public TranslateTextTextListDataManager(SharedPreferenceManager prefs, String key) {
        super(prefs, key);
    }

    @Override
    protected @NonNull TranslateText[] buildDataArray(final @NonNull String[] data) {
        // add 1 in case there is an odd number of pieces after the escaped characters flag (assume
        // the last translation is "")
        TranslateText[] translationArray = new TranslateText[(data.length + 1) / 2];
        // copy all of the pieces (alternating between original and translation) except for piece 0
        // (escape characters flag) to the translation array
        for (int i = 0; i < data.length; i++) {
            int index = i / 2;
            if (i % 2 == 0) {
                translationArray[index] = new TranslateText();
                translationArray[index].setOriginal(data[i]);
            } else {
                translationArray[index].setTranslation(data[i]);
            }
        }

        return translationArray;
    }

    @NonNull
    @Override
    protected TranslateText[] getDefaultDataArray() {
        return new TranslateText[0];
    }

    @NonNull
    @Override
    protected String[] flattenRowData(@NonNull TextList<TranslateText> fullData) {
        TranslateText[] dataArray = fullData.getDataArray();
        String[] result = new String[dataArray.length * 2];
        for (int i = 0; i < dataArray.length; i++) {
            result[i * 2] = dataArray[i].getOriginal();
            result[i * 2 + 1] = dataArray[i].getTranslation();
        }
        return result;
    }
}
