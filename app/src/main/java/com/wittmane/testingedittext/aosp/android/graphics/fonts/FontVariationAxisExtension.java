/*
 * Copyright (C) 2025 Eli Wittman
 * Copyright (C) 2017 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.android.graphics.fonts;

import android.graphics.fonts.FontVariationAxis;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * (EW) content from {@link FontVariationAxis} that is blocked from apps accessing
 */
public class FontVariationAxisExtension {

    // (EW) the AOSP version is marked as hidden. made static to allow calling on any
    // FontVariationAxis. the AOSP version just returned mTag, which is final and set from
    // makeTag(mTagString), so we can just remake the tag since mTagString is also final and
    // accessible from FontVariationAxis#getTag.
    /**
     * Returns the OpenType style tag value.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static int getOpenTypeTagValue(FontVariationAxis axis) {
        return makeTag(axis.getTag());
    }

    // (EW) the AOSP version is marked as hidden
    public static int makeTag(String tagString) {
        final char c1 = tagString.charAt(0);
        final char c2 = tagString.charAt(1);
        final char c3 = tagString.charAt(2);
        final char c4 = tagString.charAt(3);
        return (c1 << 24) | (c2 << 16) | (c3 << 8) | c4;
    }

    // (EW) the AOSP version is marked as hidden
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static @NonNull List<FontVariationAxis> fromFontVariationSettingsForList(
            @Nullable String settings) {
        if (settings == null || settings.isEmpty()) {
            return Collections.emptyList();
        }
        final ArrayList<FontVariationAxis> axisList = new ArrayList<>();
        final int length = settings.length();
        for (int i = 0; i < length; i++) {
            final char c = settings.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (!(c == '\'' || c == '"') || length < i + 6 || settings.charAt(i + 5) != c) {
                throw new IllegalArgumentException(
                        "Tag should be wrapped with double or single quote: " + settings);
            }
            final String tagString = settings.substring(i + 1, i + 5);

            i += 6;  // Move to end of tag.
            int endOfValueString = settings.indexOf(',', i);
            if (endOfValueString == -1) {
                endOfValueString = length;
            }
            final float value;
            try {
                // Float.parseFloat ignores leading/trailing whitespaces.
                value = Float.parseFloat(settings.substring(i, endOfValueString));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Failed to parse float string: " + e.getMessage());
            }
            axisList.add(new FontVariationAxis(tagString, value));
            i = endOfValueString;
        }
        if (axisList.isEmpty()) {
            return Collections.emptyList();
        }
        return axisList;
    }

    // (EW) the AOSP version is marked as hidden
    /**
     * Stringify the array of FontVariationAxis.
     */
    public static @NonNull String toFontVariationSettings(@Nullable List<FontVariationAxis> axes) {
        if (axes == null) {
            return "";
        }
        return TextUtils.join(",", axes);
    }

}
