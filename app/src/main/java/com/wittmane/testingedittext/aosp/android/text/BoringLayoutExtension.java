/*
 * Copyright (C) 2024 Eli Wittman
 * Copyright (C) 2006 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.android.text;

import android.graphics.Paint;
import android.os.Build;
import android.text.BoringLayout;
import android.text.BoringLayout.Metrics;
import android.text.Layout;
import android.text.TextDirectionHeuristic;
import android.text.TextPaint;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * (EW) content from {@link BoringLayoutExtension} that is blocked from apps accessing
 */
public class BoringLayoutExtension {
    private static final String TAG = BoringLayoutExtension.class.getSimpleName();

    // (EW) wrapper for calling the accessible overloads along with handling for the parameters that
    // can't be passed to those overloads
    public static @Nullable Metrics isBoring(@NonNull CharSequence text, @NonNull TextPaint paint,
                                             @NonNull TextDirectionHeuristic textDir,
                                             boolean useFallbackLineSpacing,
                                             @Nullable Paint.FontMetrics minimumFontMetrics,
                                             @Nullable Metrics metrics) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // (EW) the BoringLayout#isBoring overload that takes a Paint.FontMetrics (added in
            // Android 15) is hidden and blocked from reflection. that parameter is just used to
            // adjust the result if ClientFlags#fixLineHeightForLocale returns true. that and what
            // it calls into (TextFlags#isFeatureEnabled and then AppGlobals#getIntCoreSetting) are
            // all blocked from reflection. I'm not certain how those flags/settings are supposed to
            // work, but based on the documentation for locale-aware default line height for
            // EditText indicating the new option and default when targeting Android 15 (API level
            // 35), my best guess is that it's managing that. since we're targeting that version, I
            // think that should always be true on Android 15+, so we'll just use a version check to
            // mimic that, and then copy in most of the AOSP code, since we can't just modify the
            // result because the minimum needs to be set before some other calculations.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                    && minimumFontMetrics != null) {
                // (EW) use the framework method to handle the determination if this is boring since
                // it has some checks that we don't have access to
                Metrics metricsCheck = BoringLayout.isBoring(text, paint, textDir,
                        useFallbackLineSpacing, null);
                if (metricsCheck == null) {
                    return null;
                }

                Metrics fm = metrics;
                if (fm == null) {
                    fm = new Metrics();
                } else {
                    MetricsExtension.reset(fm);
                }

                fm.set(minimumFontMetrics);
                // Because the font metrics is provided by public APIs, adjust the top/bottom with
                // ascent/descent: top must be smaller than ascent, bottom must be larger than
                // descent.
                fm.top = Math.min(fm.top, fm.ascent);
                fm.bottom = Math.max(fm.bottom, fm.descent);

                TextLine line = TextLine.obtain();
                line.set(paint, text, 0, text.length(), Layout.DIR_LEFT_TO_RIGHT,
                        LayoutExtension.DIRS_ALL_LEFT_TO_RIGHT, false, null,
                        0 /* ellipsisStart, 0 since text has not been ellipsized at this point */,
                        0 /* ellipsisEnd, 0 since text has not been ellipsized at this point */,
                        useFallbackLineSpacing);
                fm.width = (int) Math.ceil(line.metrics(fm, fm.getDrawingBoundingBox(), false));
                TextLine.recycle(line);

                return fm;
            }

            return BoringLayout.isBoring(text, paint, textDir, useFallbackLineSpacing, metrics);
        } else {
            // (EW) the BoringLayout#isBoring override with a TextDirectionHeuristic parameter was
            // hidden in older versions, so we'll just add the handling from that here before
            // calling the available overload.
            final int textLength = text.length();
            if (textDir != null && textDir.isRtl(text, 0, textLength)) {
                return null;  // The heuristic considers the whole text RTL. Not boring.
            }
            return BoringLayout.isBoring(text, paint, metrics);
        }
    }

    public static class MetricsExtension {
        public static void reset(@NonNull Metrics fm) {
            fm.top = 0;
            fm.bottom = 0;
            fm.ascent = 0;
            fm.descent = 0;
            fm.width = 0;
            fm.leading = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                fm.getDrawingBoundingBox().setEmpty();
            }
        }
    }
}
