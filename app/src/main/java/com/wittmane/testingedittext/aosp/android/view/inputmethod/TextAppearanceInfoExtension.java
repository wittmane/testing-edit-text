/*
 * Copyright (C) 2024 Eli Wittman
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

package com.wittmane.testingedittext.aosp.android.view.inputmethod;

import android.graphics.Typeface;
import android.graphics.fonts.FontStyle;
import android.os.Build;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.view.inputmethod.TextAppearanceInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.aosp.android.widget.EditText;

// (EW) AOSP added this in Android 14
/**
 * (EW) content from {@link TextAppearanceInfo} that is blocked from apps accessing
 */
public class TextAppearanceInfoExtension {

    // (EW) the AOSP version is hidden
    /**
     * Creates a new instance of {@link TextAppearanceInfo} by extracting text appearance from the
     * character before cursor in the target {@link EditText}.
     * @param textView the target {@link EditText}.
     * @return the new instance of {@link TextAppearanceInfo}.
     */
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @NonNull
    public static TextAppearanceInfo createFromTextView(@NonNull EditText textView) {
        final int selectionStart = textView.getSelectionStart();
        final CharSequence text = textView.getText();
        TextPaint textPaint = new TextPaint();
        textPaint.set(textView.getPaint());    // Copy from textView
        if (text instanceof Spanned && text.length() > 0 && selectionStart > 0) {
            // Extract the CharacterStyle spans that changes text appearance in the character before
            // cursor.
            Spanned spannedText = (Spanned) text;
            int lastCh = selectionStart - 1;
            CharacterStyle[] spans = spannedText.getSpans(lastCh, lastCh, CharacterStyle.class);
            if (spans != null) {
                for (CharacterStyle span: spans) {
                    // Exclude spans that end at lastCh
                    if (spannedText.getSpanStart(span) <= lastCh
                            && lastCh < spannedText.getSpanEnd(span)) {
                        span.updateDrawState(textPaint); // Override the TextPaint
                    }
                }
            }
        }
        Typeface typeface = textPaint.getTypeface();
        String systemFontFamilyName = null;
        int textWeight = FontStyle.FONT_WEIGHT_UNSPECIFIED;
        int textStyle = Typeface.NORMAL;
        if (typeface != null) {
            systemFontFamilyName = typeface.getSystemFontFamilyName();
            // (EW) there seems to be a bug with this lint. it's complaining that
            // "Value must be ≥ 0 (was -1)", which makes no sense as there is no restriction on the
            // range of the variable. the restriction is only coming from #getWeight and there's no
            // reason it shouldn't be allowed to be set to a variable with a wider range or none at
            // all. even explicitly adding the wider range to the variable doesn't satisfy this
            // buggy lint.
            //noinspection Range
            textWeight = typeface.getWeight();
            textStyle = typeface.getStyle();
        }
        TextAppearanceInfo.Builder builder = new TextAppearanceInfo.Builder();
        builder.setTextSize(textPaint.getTextSize())
                .setTextLocales(textPaint.getTextLocales())
                .setSystemFontFamilyName(systemFontFamilyName)
                .setTextFontWeight(textWeight)
                .setTextStyle(textStyle)
                .setShadowDx(textPaint.getShadowLayerDx())
                .setShadowDy(textPaint.getShadowLayerDy())
                .setShadowRadius(textPaint.getShadowLayerRadius())
                .setShadowColor(textPaint.getShadowLayerColor())
                .setElegantTextHeight(textPaint.isElegantTextHeight())
                .setLetterSpacing(textPaint.getLetterSpacing())
                .setFontFeatureSettings(textPaint.getFontFeatureSettings())
                .setFontVariationSettings(textPaint.getFontVariationSettings())
                .setTextScaleX(textPaint.getTextScaleX())
                // When there is a hint text (text length is 0), the text color should be the normal
                // text color rather than hint text color.
                .setTextColor(text.length() == 0
                        ? textView.getCurrentTextColor() : textPaint.getColor())
                .setLinkTextColor(textPaint.linkColor)
                // (EW) the AOSP version checked TextView#isAllCaps, but that was skipped because it
                // isn't for editable fields (see comment in Edit#readTextAppearance)
                .setAllCaps(false)
                .setFallbackLineSpacing(textView.isFallbackLineSpacing())
                .setLineBreakStyle(textView.getLineBreakStyle())
                .setLineBreakWordStyle(textView.getLineBreakWordStyle())
                .setHighlightTextColor(textView.getHighlightColor())
                .setHintTextColor(textView.getCurrentHintTextColor());
        return builder.build();
    }
}
