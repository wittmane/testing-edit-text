/*
 * Copyright (C) 2024-2025 Eli Wittman
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
            // adjust the result if com.android.text.flags.Flags#fixLineHeightForLocale (ClientFlags
            // prior to Android 16) returns true. I can't find the source for
            // com.android.text.flags.Flags, so I have no way to verify what that does now, but
            // ClientFlags#fixLineHeightForLocale and what it called into
            // (TextFlags#isFeatureEnabled and then AppGlobals#getIntCoreSetting) were all blocked
            // from reflection. I'm not certain how those flags/settings were supposed to work, but
            // based on the documentation for locale-aware default line height for EditText
            // indicating the new option and default when targeting Android 15 (API level 35), my
            // best guess is that it was managing that. since we're targeting that Android 15, I
            // think that flag should always be true on Android 15+, so we'll just use a version
            // check to mimic that, and then copy in most of the AOSP code, since we can't just
            // modify the result because the minimum needs to be set before some other calculations.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
                    && minimumFontMetrics != null) {
                // (EW) use the framework method to handle the determination if this is boring since
                // it has some checks that we don't have access to
                Metrics baseMetrics = BoringLayout.isBoring(text, paint, textDir,
                        useFallbackLineSpacing, null);
                if (baseMetrics == null) {
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
                // (EW) the AOSP version passed fm's drawing bounding box as an output parameter,
                // but our version of TextLine can't support that (see the comment in
                // TextLine#getRunAdvance)
                fm.width = (int) Math.ceil(line.metrics(fm));
                // (EW) from some brief testing with the drawing bounding box output parameter
                // accessed with reflection when enabling access to non-SDK interfaces with the adb
                // command, it seems that the drawing bounding box isn't impacted by adjustments
                // from the minimum font metrics, so we'll just copy the bounding box from the
                // framework call
                fm.getDrawingBoundingBox().set(baseMetrics.getDrawingBoundingBox());

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
