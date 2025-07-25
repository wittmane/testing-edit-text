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
import java.util.Arrays;
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

    // (EW) the AOSP version (added in Android 16) is marked as hidden
    @RequiresApi(api = Build.VERSION_CODES.O)
    public static @NonNull List<FontVariationAxis> fromFontVariationSettingsForList(
            @Nullable String settings) {
        // FontVariationAxis#fromFontVariationSettings just calls into
        // FontVariationAxis#fromFontVariationSettingsForList and converts the result into an array,
        // so we'll just do the inverse
        FontVariationAxis[] axisArray = FontVariationAxis.fromFontVariationSettings(settings);
        if (axisArray == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(axisArray));
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
