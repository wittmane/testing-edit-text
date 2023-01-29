/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.graphics.text;

import android.graphics.text.LineBreakConfig;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * (EW) content from LineBreakConfig that is blocked from apps accessing
 */
public class HiddenLineBreakConfig {

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @IntDef(value = {
            LineBreakConfig.LINE_BREAK_STYLE_NONE, LineBreakConfig.LINE_BREAK_STYLE_LOOSE,
            LineBreakConfig.LINE_BREAK_STYLE_NORMAL, LineBreakConfig.LINE_BREAK_STYLE_STRICT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LineBreakStyle {}

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @IntDef(value = {
            LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE, LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LineBreakWordStyle {}

    /**
     * Create the LineBreakConfig instance.
     *
     * @param lineBreakStyle the line break style for text wrapping.
     * @param lineBreakWordStyle the line break word style for text wrapping.
     * @return the {@link LineBreakConfig} instance.
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @NonNull
    public static LineBreakConfig getLineBreakConfig(@LineBreakStyle int lineBreakStyle,
                                                     @LineBreakWordStyle int lineBreakWordStyle) {
        LineBreakConfig.Builder builder = new LineBreakConfig.Builder();
        return builder.setLineBreakStyle(lineBreakStyle)
                .setLineBreakWordStyle(lineBreakWordStyle)
                .build();
    }
}
