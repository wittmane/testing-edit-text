/*
 * Copyright (C) 2022-2023 Eli Wittman
 * Copyright (C) 2010 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.text;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.aosp.text.AutoGrowArray.ByteArray;
import com.wittmane.testingedittext.aosp.text.AutoGrowArray.FloatArray;
import com.wittmane.testingedittext.aosp.text.AutoGrowArray.IntArray;

import android.os.Build;
import android.text.Layout;
import com.wittmane.testingedittext.aosp.text.HiddenLayout.Directions;
import android.text.Spanned;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import androidx.core.util.Pools.SynchronizedPool;

import java.util.Arrays;

// (EW) the AOSP version of this is hidden from apps, so it had to be copied here in order to be
// used in other hidden classes, but only some if it was copied since not all of it is currently
// necessary, and it's not trivial to just copy over
/**
 * MeasuredParagraph provides text information for rendering purpose.
 *
 * The first motivation of this class is identify the text directions and retrieving individual
 * character widths. However retrieving character widths is slower than identifying text directions.
 * Thus, this class provides several builder methods for specific purposes.
 *
 * - buildForBidi:
 *   Compute only text directions.
 * - buildForMeasurement:
 *   Compute text direction and all character widths.
 * - buildForStaticLayout:
 *   This is bit special. StaticLayout also needs to know text direction and character widths for
 *   line breaking, but all things are done in native code. Similarly, text measurement is done
 *   in native code. So instead of storing result to Java array, this keeps the result in native
 *   code since there is no good reason to move the results to Java layer.
 *
 * In addition to the character widths, some additional information is computed for each purposes,
 * e.g. whole text length for measurement or font metrics for static layout.
 *
 * MeasuredParagraph is NOT a thread safe object.
 */
public class MeasuredParagraph {
    private static final char OBJECT_REPLACEMENT_CHARACTER = '\uFFFC';

    private MeasuredParagraph() {}  // Use build static functions instead.

    private static final SynchronizedPool<MeasuredParagraph> sPool = new SynchronizedPool<>(1);

    private static @NonNull MeasuredParagraph obtain() { // Use build static functions instead.
        final MeasuredParagraph mt = sPool.acquire();
        return mt != null ? mt : new MeasuredParagraph();
    }

    /**
     * Recycle the MeasuredParagraph.
     *
     * Do not call any methods after you call this method.
     */
    public void recycle() {
        release();
        sPool.release(this);
    }

    // The casted original text.
    //
    // This may be null if the passed text is not a Spanned.
    private @Nullable Spanned mSpanned;

    // The length of the target range in the original text.
    private @IntRange(from = 0) int mTextLength;

    // The copied character buffer for measuring text.
    //
    // The length of this array is mTextLength.
    private @Nullable char[] mCopiedBuffer;

    // The whole paragraph direction.
    private @HiddenLayout.Direction int mParaDir;

    // True if the text is LTR direction and doesn't contain any bidi characters.
    private boolean mLtrWithoutBidi;

    // The bidi level for individual characters.
    //
    // This is empty if mLtrWithoutBidi is true.
    private final @NonNull ByteArray mLevels = new ByteArray();

    // The whole width of the text.
    // See getWholeWidth comments.
    private @FloatRange(from = 0.0f) float mWholeWidth;

    // Individual characters' widths.
    // See getWidths comments.
    private final @NonNull FloatArray mWidths = new FloatArray();

    // The span end positions.
    // See getSpanEndCache comments.
    private final @NonNull IntArray mSpanEndCache = new IntArray(4);

    // The font metrics.
    // See getFontMetrics comments.
    private final @NonNull IntArray mFontMetrics = new IntArray(4 * 4);

    /**
     * Releases internal buffers.
     */
    public void release() {
        reset();
        mLevels.clearWithReleasingLargeArray();
        mWidths.clearWithReleasingLargeArray();
        mFontMetrics.clearWithReleasingLargeArray();
        mSpanEndCache.clearWithReleasingLargeArray();
    }

    /**
     * Resets the internal state for starting new text.
     */
    private void reset() {
        mSpanned = null;
        mCopiedBuffer = null;
        mWholeWidth = 0;
        mLevels.clear();
        mWidths.clear();
        mFontMetrics.clear();
        mSpanEndCache.clear();
    }

    /**
     * Returns the length of the paragraph.
     *
     * This is always available.
     */
    public int getTextLength() {
        return mTextLength;
    }

    /**
     * Returns the characters to be measured.
     *
     * This is always available.
     */
    public @NonNull char[] getChars() {
        return mCopiedBuffer;
    }

    /**
     * Returns the paragraph direction.
     *
     * This is always available.
     */
    public @HiddenLayout.Direction int getParagraphDir() {
        return mParaDir;
    }

    /**
     * Returns the directions.
     *
     * This is always available.
     */
    public Directions getDirections(@IntRange(from = 0) int start,  // inclusive
                                    @IntRange(from = 0) int end) {  // exclusive
        if (mLtrWithoutBidi) {
            return HiddenLayout.DIRS_ALL_LEFT_TO_RIGHT;
        }

        final int length = end - start;
        return AndroidBidi.directions(mParaDir, mLevels.getRawArray(), start, mCopiedBuffer, start,
                length);
    }

    /**
     * Returns the whole text width.
     *
     * This is available only if the MeasuredParagraph is computed with buildForMeasurement.
     * Returns 0 in other cases.
     */
    public @FloatRange(from = 0.0f) float getWholeWidth() {
        return mWholeWidth;
    }

    /**
     * Returns the individual character's width.
     *
     * This is available only if the MeasuredParagraph is computed with buildForMeasurement.
     * Returns empty array in other cases.
     */
    public @NonNull FloatArray getWidths() {
        return mWidths;
    }

    /**
     * Returns the MetricsAffectingSpan end indices.
     *
     * If the input text is not a spanned string, this has one value that is the length of the text.
     *
     * This is available only if the MeasuredParagraph is computed with buildForStaticLayout.
     * Returns empty array in other cases.
     */
    public @NonNull IntArray getSpanEndCache() {
        return mSpanEndCache;
    }

    /**
     * Returns the int array which holds FontMetrics.
     *
     * This array holds the repeat of top, bottom, ascent, descent of font metrics value.
     *
     * This is available only if the MeasuredParagraph is computed with buildForStaticLayout.
     * Returns empty array in other cases.
     */
    public @NonNull IntArray getFontMetrics() {
        return mFontMetrics;
    }

    // (EW) skipping #getMeasuredText, #getWidth, #getBounds, #getFontMetricsInt, and
    // #getCharWidthAt

    /**
     * Generates new MeasuredParagraph for Bidi computation.
     *
     * If recycle is null, this returns new instance. If recycle is not null, this fills computed
     * result to recycle and returns recycle.
     *
     * @param text the character sequence to be measured
     * @param start the inclusive start offset of the target region in the text
     * @param end the exclusive end offset of the target region in the text
     * @param textDir the text direction
     * @param recycle pass existing MeasuredParagraph if you want to recycle it.
     *
     * @return measured text
     */
    public static @NonNull MeasuredParagraph buildForBidi(@NonNull CharSequence text,
                                                          @IntRange(from = 0) int start,
                                                          @IntRange(from = 0) int end,
                                                          @NonNull TextDirectionHeuristic textDir,
                                                          @Nullable MeasuredParagraph recycle) {
        final MeasuredParagraph mt = recycle == null ? obtain() : recycle;
        mt.resetAndAnalyzeBidi(text, start, end, textDir);
        return mt;
    }

    // (EW) skipping #buildForMeasurement and #buildForStaticLayout

    /**
     * Reset internal state and analyzes text for bidirectional runs.
     *
     * @param text the character sequence to be measured
     * @param start the inclusive start offset of the target region in the text
     * @param end the exclusive end offset of the target region in the text
     * @param textDir the text direction
     */
    private void resetAndAnalyzeBidi(@NonNull CharSequence text,
                                     @IntRange(from = 0) int start,  // inclusive
                                     @IntRange(from = 0) int end,  // exclusive
                                     @NonNull TextDirectionHeuristic textDir) {
        reset();
        mSpanned = text instanceof Spanned ? (Spanned) text : null;
        mTextLength = end - start;

        if (mCopiedBuffer == null || mCopiedBuffer.length != mTextLength) {
            mCopiedBuffer = new char[mTextLength];
        }
        TextUtils.getChars(text, start, end, mCopiedBuffer, 0);

        // Replace characters associated with ReplacementSpan to U+FFFC.
        if (mSpanned != null) {
            ReplacementSpan[] spans = mSpanned.getSpans(start, end, ReplacementSpan.class);

            for (int i = 0; i < spans.length; i++) {
                int startInPara = mSpanned.getSpanStart(spans[i]) - start;
                int endInPara = mSpanned.getSpanEnd(spans[i]) - start;
                // The span interval may be larger and must be restricted to [start, end)
                if (startInPara < 0) startInPara = 0;
                if (endInPara > mTextLength) endInPara = mTextLength;
                Arrays.fill(mCopiedBuffer, startInPara, endInPara, OBJECT_REPLACEMENT_CHARACTER);
            }
        }

        if ((textDir == TextDirectionHeuristics.LTR
                || textDir == TextDirectionHeuristics.FIRSTSTRONG_LTR
                || textDir == TextDirectionHeuristics.ANYRTL_LTR)
                && HiddenTextUtils.doesNotNeedBidi(mCopiedBuffer, 0, mTextLength)) {
            mLevels.clear();
            mParaDir = Layout.DIR_LEFT_TO_RIGHT;
            mLtrWithoutBidi = true;
        } else {
            final int bidiRequest;
            if (textDir == TextDirectionHeuristics.LTR) {
                bidiRequest = HiddenLayout.DIR_REQUEST_LTR;
            } else if (textDir == TextDirectionHeuristics.RTL) {
                bidiRequest = HiddenLayout.DIR_REQUEST_RTL;
            } else if (textDir == TextDirectionHeuristics.FIRSTSTRONG_LTR) {
                bidiRequest = HiddenLayout.DIR_REQUEST_DEFAULT_LTR;
            } else if (textDir == TextDirectionHeuristics.FIRSTSTRONG_RTL) {
                bidiRequest = HiddenLayout.DIR_REQUEST_DEFAULT_RTL;
            } else {
                final boolean isRtl = textDir.isRtl(mCopiedBuffer, 0, mTextLength);
                bidiRequest = isRtl ? HiddenLayout.DIR_REQUEST_RTL : HiddenLayout.DIR_REQUEST_LTR;
            }
            mLevels.resize(mTextLength);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mParaDir = AndroidBidi.bidi(bidiRequest, mCopiedBuffer, mLevels.getRawArray());
            } else {
                // (EW) based on MeasuredText#setPara, which seems to be the rough alternative in
                // older versions
                mParaDir = AndroidBidi.bidi(bidiRequest, mCopiedBuffer, mLevels.getRawArray(),
                        mTextLength, false);
            }
            mLtrWithoutBidi = false;
        }
    }

    // (EW) skipping #applyReplacementRun, #applyStyleRun, and #applyMetricsAffectingSpan

    /**
     * Returns the maximum index that the accumulated width not exceeds the width.
     *
     * If forward=false is passed, returns the minimum index from the end instead.
     *
     * This only works if the MeasuredParagraph is computed with buildForMeasurement.
     * Undefined behavior in other case.
     */
    @IntRange(from = 0) int breakText(int limit, boolean forwards, float width) {
        float[] w = mWidths.getRawArray();
        if (forwards) {
            int i = 0;
            while (i < limit) {
                width -= w[i];
                if (width < 0.0f) break;
                i++;
            }
            while (i > 0 && mCopiedBuffer[i - 1] == ' ') i--;
            return i;
        } else {
            int i = limit - 1;
            while (i >= 0) {
                width -= w[i];
                if (width < 0.0f) break;
                i--;
            }
            while (i < limit - 1 && (mCopiedBuffer[i + 1] == ' ' || w[i + 1] == 0.0f)) {
                i++;
            }
            return limit - i - 1;
        }
    }

    /**
     * Returns the length of the substring.
     *
     * This only works if the MeasuredParagraph is computed with buildForMeasurement.
     * Undefined behavior in other case.
     */
    @FloatRange(from = 0.0f) float measure(int start, int limit) {
        float width = 0;
        float[] w = mWidths.getRawArray();
        for (int i = start; i < limit; ++i) {
            width += w[i];
        }
        return width;
    }

    // (EW) skipping #getMemoryUsage
}
