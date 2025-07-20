/*
 * Copyright (C) 2022-2025 Eli Wittman
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

package com.wittmane.testingedittext.aosp.android.text;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.aosp.android.text.AutoGrowArray.ByteArray;
import com.wittmane.testingedittext.aosp.android.text.AutoGrowArray.FloatArray;
import com.wittmane.testingedittext.aosp.android.text.AutoGrowArray.IntArray;
import com.wittmane.testingedittext.aosp.android.text.LayoutExtension.Directions;

import android.icu.lang.UCharacter;
import android.icu.lang.UCharacterDirection;
import android.icu.text.Bidi;
import android.os.Build;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import com.wittmane.testingedittext.aosp.android.util.Pools.SynchronizedPool;

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
    private @LayoutExtension.Direction int mParaDir;

    // True if the text is LTR direction and doesn't contain any bidi characters.
    private boolean mLtrWithoutBidi;

    // The bidi level for individual characters.
    //
    // This is empty if mLtrWithoutBidi is true.
    private final @NonNull ByteArray mLevels = new ByteArray();

    private Bidi mBidi;

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
        mBidi = null;
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
    public @LayoutExtension.Direction int getParagraphDir() {
        // (EW) the ClientFlags#icuBidiMigration check was removed in Android 16, but given that our
        // fake version of that is just a version check enabling the functionality in an earlier
        // version, we'll leave it
        if (icuBidiMigrationClientFlag() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mBidi == null) {
                return Layout.DIR_LEFT_TO_RIGHT;
            }
            return (mBidi.getParaLevel() & 0x01) == 0
                    ? Layout.DIR_LEFT_TO_RIGHT : Layout.DIR_RIGHT_TO_LEFT;
        }
        return mParaDir;
    }

    /**
     * Returns the directions.
     *
     * This is always available.
     */
    public Directions getDirections(@IntRange(from = 0) int start,  // inclusive
                                    @IntRange(from = 0) int end) {  // exclusive
        // (EW) the ClientFlags#icuBidiMigration check was removed in Android 16, but given that our
        // fake version of that is just a version check enabling the functionality in an earlier
        // version, we'll leave it
        if (icuBidiMigrationClientFlag() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Easy case: mBidi == null means the text is all LTR and no bidi suppot is needed.
            if (mBidi == null) {
                return LayoutExtension.DIRS_ALL_LEFT_TO_RIGHT;
            }

            // Easy case: If the original text only contains single directionality run, the
            // substring is only single run.
            if (start == end) {
                if ((mBidi.getParaLevel() & 0x01) == 0) {
                    return LayoutExtension.DIRS_ALL_LEFT_TO_RIGHT;
                } else {
                    return LayoutExtension.DIRS_ALL_RIGHT_TO_LEFT;
                }
            }

            // Okay, now we need to generate the line instance.
            Bidi bidi = mBidi.createLineBidi(start, end);

            // Easy case: If the line instance only contains single directionality run, no need
            // to reorder visually.
            if (bidi.getRunCount() == 1) {
                if (bidi.getRunLevel(0) == 1) {
                    return LayoutExtension.DIRS_ALL_RIGHT_TO_LEFT;
                } else if (bidi.getRunLevel(0) == 0) {
                    return LayoutExtension.DIRS_ALL_LEFT_TO_RIGHT;
                } else {
                    return new Directions(new int[] {
                            0, bidi.getRunLevel(0) << LayoutExtension.RUN_LEVEL_SHIFT | (end - start)});
                }
            }

            // Reorder directionality run visually.
            byte[] levels = new byte[bidi.getRunCount()];
            for (int i = 0; i < bidi.getRunCount(); ++i) {
                levels[i] = (byte) bidi.getRunLevel(i);
            }
            int[] visualOrders = Bidi.reorderVisual(levels);

            int[] dirs = new int[bidi.getRunCount() * 2];
            for (int i = 0; i < bidi.getRunCount(); ++i) {
                int vIndex;
                if ((mBidi.getBaseLevel() & 0x01) == 1) {
                    // For the historical reasons, if the base directionality is RTL, the Android
                    // draws from the right, i.e. the visually reordered run needs to be reversed.
                    vIndex = visualOrders[bidi.getRunCount() - i - 1];
                } else {
                    vIndex = visualOrders[i];
                }

                // Special packing of dire
                dirs[i * 2] = bidi.getRunStart(vIndex);
                dirs[i * 2 + 1] = bidi.getRunLevel(vIndex) << LayoutExtension.RUN_LEVEL_SHIFT
                        | (bidi.getRunLimit(vIndex) - dirs[i * 2]);
            }

            return new Directions(dirs);
        }
        if (mLtrWithoutBidi) {
            return LayoutExtension.DIRS_ALL_LEFT_TO_RIGHT;
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

    // (EW) skipping #buildForMeasurement, StyleRunCallback, #buildForStaticLayout,
    // #buildForStaticLayoutTest, and #buildForStaticLayoutInternal

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

        // (EW) the ClientFlags#icuBidiMigration check was removed in Android 16, but given that our
        // fake version of that is just a version check enabling the functionality in an earlier
        // version, we'll leave it
        if (icuBidiMigrationClientFlag() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if ((textDir == TextDirectionHeuristics.LTR
                    || textDir == TextDirectionHeuristics.FIRSTSTRONG_LTR
                    || textDir == TextDirectionHeuristics.ANYRTL_LTR)
                    && TextUtilsExtension.doesNotNeedBidi(mCopiedBuffer, 0, mTextLength)) {
                mLevels.clear();
                mLtrWithoutBidi = true;
                return;
            }
            final int bidiRequest;
            if (textDir == TextDirectionHeuristics.LTR) {
                bidiRequest = Bidi.LTR;
            } else if (textDir == TextDirectionHeuristics.RTL) {
                bidiRequest = Bidi.RTL;
            } else if (textDir == TextDirectionHeuristics.FIRSTSTRONG_LTR) {
                bidiRequest = Bidi.LEVEL_DEFAULT_LTR;
            } else if (textDir == TextDirectionHeuristics.FIRSTSTRONG_RTL) {
                bidiRequest = Bidi.LEVEL_DEFAULT_RTL;
            } else {
                final boolean isRtl = textDir.isRtl(mCopiedBuffer, 0, mTextLength);
                bidiRequest = isRtl ? Bidi.RTL : Bidi.LTR;
            }
            mBidi = new Bidi(mCopiedBuffer, 0, null, 0, mCopiedBuffer.length, bidiRequest);

            if (mCopiedBuffer.length > 0
                    && mBidi.getParagraphIndex(mCopiedBuffer.length - 1) != 0) {
                // Historically, the MeasuredParagraph does not treat the CR letters as paragraph
                // breaker but ICU BiDi treats it as paragraph breaker. In the MeasureParagraph,
                // the given range always represents a single paragraph, so if the BiDi object has
                // multiple paragraph, it should contains a CR letters in the text. Using CR is not
                // common in Android and also it should not penalize the easy case, e.g. all LTR,
                // check the paragraph count here and replace the CR letters and re-calculate
                // BiDi again.
                for (int i = 0; i < mTextLength; ++i) {
                    if (Character.isSurrogate(mCopiedBuffer[i])) {
                        // All block separators are in BMP.
                        continue;
                    }
                    if (UCharacter.getDirection(mCopiedBuffer[i])
                            == UCharacterDirection.BLOCK_SEPARATOR) {
                        mCopiedBuffer[i] = OBJECT_REPLACEMENT_CHARACTER;
                    }
                }
                mBidi = new Bidi(mCopiedBuffer, 0, null, 0, mCopiedBuffer.length, bidiRequest);
            }
            mLevels.resize(mTextLength);
            byte[] rawArray = mLevels.getRawArray();
            for (int i = 0; i < mTextLength; ++i) {
                rawArray[i] = mBidi.getLevelAt(i);
            }
            mLtrWithoutBidi = false;
            return;
        }

        if ((textDir == TextDirectionHeuristics.LTR
                || textDir == TextDirectionHeuristics.FIRSTSTRONG_LTR
                || textDir == TextDirectionHeuristics.ANYRTL_LTR)
                && TextUtilsExtension.doesNotNeedBidi(mCopiedBuffer, 0, mTextLength)) {
            mLevels.clear();
            mParaDir = Layout.DIR_LEFT_TO_RIGHT;
            mLtrWithoutBidi = true;
        } else {
            final int bidiRequest;
            if (textDir == TextDirectionHeuristics.LTR) {
                bidiRequest = LayoutExtension.DIR_REQUEST_LTR;
            } else if (textDir == TextDirectionHeuristics.RTL) {
                bidiRequest = LayoutExtension.DIR_REQUEST_RTL;
            } else if (textDir == TextDirectionHeuristics.FIRSTSTRONG_LTR) {
                bidiRequest = LayoutExtension.DIR_REQUEST_DEFAULT_LTR;
            } else if (textDir == TextDirectionHeuristics.FIRSTSTRONG_RTL) {
                bidiRequest = LayoutExtension.DIR_REQUEST_DEFAULT_RTL;
            } else {
                final boolean isRtl = textDir.isRtl(mCopiedBuffer, 0, mTextLength);
                bidiRequest = isRtl
                        ? LayoutExtension.DIR_REQUEST_RTL
                        : LayoutExtension.DIR_REQUEST_LTR;
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

    // (EW) replacement for calls to ClientFlags#icuBidiMigration, which is hidden. that and what it
    // calls into (TextFlags#isFeatureEnabled and then AppGlobals#getIntCoreSetting) are all blocked
    // from reflection. I'm not certain how those flags/settings are supposed to work, but based on
    // my analysis in BoringLayoutExtension#isBoring, I'm guessing it's some sort of handling based
    // on the target version. I didn't see any info about this in the documented changes in
    // Android 15, but it seems like something that could go unmentioned. I'll just work off that
    // assumption and enable it on Android 15+.
    private boolean icuBidiMigrationClientFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }
}
