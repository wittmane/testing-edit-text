/*
 * Copyright (C) 2022 Eli Wittman
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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.os.Build;

import com.wittmane.testingedittext.aosp.graphics.HiddenPaint;
import com.wittmane.testingedittext.aosp.text.HiddenLayout.Directions;
import com.wittmane.testingedittext.aosp.text.HiddenLayout.TabStops;

import android.text.Layout;
import android.text.PrecomputedText;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextShaper;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;
import android.util.Log;

import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// (EW) the AOSP version of this is hidden from apps, so it had to be copied here in order to be
// used in other hidden classes/methods, but only some if it was copied since not all of it is
// currently necessary, and it's not trivial to just copy over
/**
 * Represents a line of styled text, for measuring in visual order and
 * for rendering.
 *
 * <p>Get a new instance using obtain(), and when finished with it, return it
 * to the pool using recycle().
 *
 * <p>Call set to prepare the instance for use, then either draw, measure,
 * metrics, or caretToLeftRightOf.
 */
public class TextLine {
    private static final String TAG = TextLine.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final char TAB_CHAR = '\t';

    private TextPaint mPaint;
    private CharSequence mText;
    private int mStart;
    private int mLen;
    private int mDir;
    private Directions mDirections;
    private boolean mHasTabs;
    private TabStops mTabs;
    private char[] mChars;
    private boolean mCharsValid;
    private Spanned mSpanned;
    private PrecomputedText mComputed;

    // The start and end of a potentially existing ellipsis on this text line.
    // We use them to filter out replacement and metric affecting spans on ellipsized away chars.
    private int mEllipsisStart;
    private int mEllipsisEnd;

    // Additional width of whitespace for justification. This value is per whitespace, thus
    // the line width will increase by mAddedWidthForJustify x (number of stretchable whitespaces).
    private float mAddedWidthForJustify;
    private boolean mIsJustifying;

    private final TextPaint mWorkPaint = new TextPaint();
    private final TextPaint mActivePaint = new TextPaint();
    private final SpanSet<MetricAffectingSpan> mMetricAffectingSpanSpanSet =
            new SpanSet<MetricAffectingSpan>(MetricAffectingSpan.class);
    private final SpanSet<CharacterStyle> mCharacterStyleSpanSet =
            new SpanSet<CharacterStyle>(CharacterStyle.class);
    private final SpanSet<ReplacementSpan> mReplacementSpanSpanSet =
            new SpanSet<ReplacementSpan>(ReplacementSpan.class);

    /** Not allowed to access. If it's for memory leak workaround, it was already fixed M. */
    private static final TextLine[] sCached = new TextLine[3];

    /**
     * Returns a new TextLine from the shared pool.
     *
     * @return an uninitialized TextLine
     */
    public static TextLine obtain() {
        TextLine tl;
        synchronized (sCached) {
            for (int i = sCached.length; --i >= 0;) {
                if (sCached[i] != null) {
                    tl = sCached[i];
                    sCached[i] = null;
                    return tl;
                }
            }
        }
        tl = new TextLine();
        if (DEBUG) {
            Log.v("TLINE", "new: " + tl);
        }
        return tl;
    }

    /**
     * Puts a TextLine back into the shared pool. Do not use this TextLine once
     * it has been returned.
     * @param tl the textLine
     * @return null, as a convenience from clearing references to the provided
     * TextLine
     */
    public static TextLine recycle(TextLine tl) {
        tl.mText = null;
        tl.mPaint = null;
        tl.mDirections = null;
        tl.mSpanned = null;
        tl.mTabs = null;
        tl.mChars = null;
        tl.mComputed = null;

        tl.mMetricAffectingSpanSpanSet.recycle();
        tl.mCharacterStyleSpanSet.recycle();
        tl.mReplacementSpanSpanSet.recycle();

        synchronized(sCached) {
            for (int i = 0; i < sCached.length; ++i) {
                if (sCached[i] == null) {
                    sCached[i] = tl;
                    break;
                }
            }
        }
        return null;
    }

    /**
     * Initializes a TextLine and prepares it for use.
     *
     * @param paint the base paint for the line
     * @param text the text, can be Styled
     * @param start the start of the line relative to the text
     * @param limit the limit of the line relative to the text
     * @param dir the paragraph direction of this line
     * @param directions the directions information of this line
     * @param hasTabs true if the line might contain tabs
     * @param tabStops the tabStops. Can be null
     * @param ellipsisStart the start of the ellipsis relative to the line
     * @param ellipsisEnd the end of the ellipsis relative to the line. When there
     *                    is no ellipsis, this should be equal to ellipsisStart.
     */
    public void set(TextPaint paint, CharSequence text, int start, int limit, int dir,
                    Directions directions, boolean hasTabs, TabStops tabStops,
                    int ellipsisStart, int ellipsisEnd) {
        mPaint = paint;
        mText = text;
        mStart = start;
        mLen = limit - start;
        mDir = dir;
        mDirections = directions;
        if (mDirections == null) {
            throw new IllegalArgumentException("Directions cannot be null");
        }
        mHasTabs = hasTabs;
        mSpanned = null;

        boolean hasReplacement = false;
        if (text instanceof Spanned) {
            mSpanned = (Spanned) text;
            mReplacementSpanSpanSet.init(mSpanned, start, limit);
            hasReplacement = mReplacementSpanSpanSet.numberOfSpans > 0;
        }

        mComputed = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && text instanceof PrecomputedText) {
            // Here, no need to check line break strategy or hyphenation frequency since there is no
            // line break concept here.
            mComputed = (PrecomputedText) text;
            if (!mComputed.getParams().getTextPaint().equalsForTextMeasurement(paint)) {
                mComputed = null;
            }
        }

        mCharsValid = hasReplacement;

        if (mCharsValid) {
            if (mChars == null || mChars.length < mLen) {
                mChars = ArrayUtils.newUnpaddedCharArray(mLen);
            }
            TextUtils.getChars(text, start, limit, mChars, 0);
            if (hasReplacement) {
                // Handle these all at once so we don't have to do it as we go.
                // Replace the first character of each replacement run with the
                // object-replacement character and the remainder with zero width
                // non-break space aka BOM.  Cursor movement code skips these
                // zero-width characters.
                char[] chars = mChars;
                for (int i = start, inext; i < limit; i = inext) {
                    inext = mReplacementSpanSpanSet.getNextTransition(i, limit);
                    if (mReplacementSpanSpanSet.hasSpansIntersecting(i, inext)
                            && (i - start >= ellipsisEnd || inext - start <= ellipsisStart)) {
                        // transition into a span
                        chars[i - start] = '\ufffc';
                        for (int j = i - start + 1, e = inext - start; j < e; ++j) {
                            chars[j] = '\ufeff'; // used as ZWNBS, marks positions to skip
                        }
                    }
                }
            }
        }
        mTabs = tabStops;
        mAddedWidthForJustify = 0;
        mIsJustifying = false;

        mEllipsisStart = ellipsisStart != ellipsisEnd ? ellipsisStart : 0;
        mEllipsisEnd = ellipsisStart != ellipsisEnd ? ellipsisEnd : 0;
    }

    private char charAt(int i) {
        return mCharsValid ? mChars[i] : mText.charAt(i + mStart);
    }

    /**
     * Justify the line to the given width.
     */
    public void justify(float justifyWidth) {
        int end = mLen;
        while (end > 0 && isLineEndSpace(mText.charAt(mStart + end - 1))) {
            end--;
        }
        final int spaces = countStretchableSpaces(0, end);
        if (spaces == 0) {
            // There are no stretchable spaces, so we can't help the justification by adding any
            // width.
            return;
        }
        final float width = Math.abs(measure(end, false, null));
        mAddedWidthForJustify = (justifyWidth - width) / spaces;
        mIsJustifying = true;
    }

    // (EW) skipping #draw

    /**
     * Returns metrics information for the entire line.
     *
     * @param fmi receives font metrics information, can be null
     * @return the signed width of the line
     */
    public float metrics(FontMetricsInt fmi) {
        return measure(mLen, false, fmi);
    }

    // (EW) skipping #shape

    /**
     * Returns the signed graphical offset from the leading margin.
     *
     * Following examples are all for measuring offset=3. LX(e.g. L0, L1, ...) denotes a
     * character which has LTR BiDi property. On the other hand, RX(e.g. R0, R1, ...) denotes a
     * character which has RTL BiDi property. Assuming all character has 1em width.
     *
     * Example 1: All LTR chars within LTR context
     *   Input Text (logical)  :   L0 L1 L2 L3 L4 L5 L6 L7 L8
     *   Input Text (visual)   :   L0 L1 L2 L3 L4 L5 L6 L7 L8
     *   Output(trailing=true) :  |--------| (Returns 3em)
     *   Output(trailing=false):  |--------| (Returns 3em)
     *
     * Example 2: All RTL chars within RTL context.
     *   Input Text (logical)  :   R0 R1 R2 R3 R4 R5 R6 R7 R8
     *   Input Text (visual)   :   R8 R7 R6 R5 R4 R3 R2 R1 R0
     *   Output(trailing=true) :                    |--------| (Returns -3em)
     *   Output(trailing=false):                    |--------| (Returns -3em)
     *
     * Example 3: BiDi chars within LTR context.
     *   Input Text (logical)  :   L0 L1 L2 R3 R4 R5 L6 L7 L8
     *   Input Text (visual)   :   L0 L1 L2 R5 R4 R3 L6 L7 L8
     *   Output(trailing=true) :  |-----------------| (Returns 6em)
     *   Output(trailing=false):  |--------| (Returns 3em)
     *
     * Example 4: BiDi chars within RTL context.
     *   Input Text (logical)  :   L0 L1 L2 R3 R4 R5 L6 L7 L8
     *   Input Text (visual)   :   L6 L7 L8 R5 R4 R3 L0 L1 L2
     *   Output(trailing=true) :           |-----------------| (Returns -6em)
     *   Output(trailing=false):                    |--------| (Returns -3em)
     *
     * @param offset the line-relative character offset, between 0 and the line length, inclusive
     * @param trailing no effect if the offset is not on the BiDi transition offset. If the offset
     *                 is on the BiDi transition offset and true is passed, the offset is regarded
     *                 as the edge of the trailing run's edge. If false, the offset is regarded as
     *                 the edge of the preceding run's edge. See example above.
     * @param fmi receives metrics information about the requested character, can be null
     * @return the signed graphical offset from the leading margin to the requested character edge.
     *         The positive value means the offset is right from the leading edge. The negative
     *         value means the offset is left from the leading edge.
     */
    public float measure(@IntRange(from = 0) int offset, boolean trailing,
                         @Nullable FontMetricsInt fmi) {
        if (offset > mLen) {
            throw new IndexOutOfBoundsException(
                    "offset(" + offset + ") should be less than line limit(" + mLen + ")");
        }
        final int target = trailing ? offset - 1 : offset;
        if (target < 0) {
            return 0;
        }

        float h = 0;
        for (int runIndex = 0; runIndex < mDirections.getRunCount(); runIndex++) {
            final int runStart = mDirections.getRunStart(runIndex);
            if (runStart > mLen) break;
            final int runLimit = Math.min(runStart + mDirections.getRunLength(runIndex), mLen);
            final boolean runIsRtl = mDirections.isRunRtl(runIndex);

            int segStart = runStart;
            for (int j = mHasTabs ? runStart : runLimit; j <= runLimit; j++) {
                if (j == runLimit || charAt(j) == TAB_CHAR) {
                    final boolean targetIsInThisSegment = target >= segStart && target < j;
                    final boolean sameDirection = (mDir == Layout.DIR_RIGHT_TO_LEFT) == runIsRtl;

                    if (targetIsInThisSegment && sameDirection) {
                        return h + measureRun(segStart, offset, j, runIsRtl, fmi);
                    }

                    final float segmentWidth = measureRun(segStart, j, j, runIsRtl, fmi);
                    h += sameDirection ? segmentWidth : -segmentWidth;

                    if (targetIsInThisSegment) {
                        return h + measureRun(segStart, offset, j, runIsRtl, null);
                    }

                    if (j != runLimit) {  // charAt(j) == TAB_CHAR
                        if (offset == j) {
                            return h;
                        }
                        h = mDir * nextTab(h * mDir);
                        if (target == j) {
                            return h;
                        }
                    }

                    segStart = j + 1;
                }
            }
        }

        return h;
    }

    /**
     * @see #measure(int, boolean, FontMetricsInt)
     * @return The measure results for all possible offsets
     */
    public float[] measureAllOffsets(boolean[] trailing, FontMetricsInt fmi) {
        float[] measurement = new float[mLen + 1];

        int[] target = new int[mLen + 1];
        for (int offset = 0; offset < target.length; ++offset) {
            target[offset] = trailing[offset] ? offset - 1 : offset;
        }
        if (target[0] < 0) {
            measurement[0] = 0;
        }

        float h = 0;
        for (int runIndex = 0; runIndex < mDirections.getRunCount(); runIndex++) {
            final int runStart = mDirections.getRunStart(runIndex);
            if (runStart > mLen) break;
            final int runLimit = Math.min(runStart + mDirections.getRunLength(runIndex), mLen);
            final boolean runIsRtl = mDirections.isRunRtl(runIndex);

            int segStart = runStart;
            for (int j = mHasTabs ? runStart : runLimit; j <= runLimit; ++j) {
                if (j == runLimit || charAt(j) == TAB_CHAR) {
                    final  float oldH = h;
                    final boolean advance = (mDir == Layout.DIR_RIGHT_TO_LEFT) == runIsRtl;
                    final float w = measureRun(segStart, j, j, runIsRtl, fmi);
                    h += advance ? w : -w;

                    final float baseH = advance ? oldH : h;
                    FontMetricsInt crtFmi = advance ? fmi : null;
                    for (int offset = segStart; offset <= j && offset <= mLen; ++offset) {
                        if (target[offset] >= segStart && target[offset] < j) {
                            measurement[offset] =
                                    baseH + measureRun(segStart, offset, j, runIsRtl, crtFmi);
                        }
                    }

                    if (j != runLimit) {  // charAt(j) == TAB_CHAR
                        if (target[j] == j) {
                            measurement[j] = h;
                        }
                        h = mDir * nextTab(h * mDir);
                        if (target[j + 1] == j) {
                            measurement[j + 1] =  h;
                        }
                    }

                    segStart = j + 1;
                }
            }
        }
        if (target[mLen] == mLen) {
            measurement[mLen] = h;
        }

        return measurement;
    }

    // (EW) skipping #drawRun

    /**
     * Measures a unidirectional (but possibly multi-styled) run of text.
     *
     *
     * @param start the line-relative start of the run
     * @param offset the offset to measure to, between start and limit inclusive
     * @param limit the line-relative limit of the run
     * @param runIsRtl true if the run is right-to-left
     * @param fmi receives metrics information about the requested
     * run, can be null.
     * @return the signed width from the start of the run to the leading edge
     * of the character at offset, based on the run (not paragraph) direction
     */
    private float measureRun(int start, int offset, int limit, boolean runIsRtl,
                             FontMetricsInt fmi) {
        return handleRun(start, offset, limit, runIsRtl, fmi);
    }

    // (EW) skipping #shapeRun

    /**
     * Walk the cursor through this line, skipping conjuncts and
     * zero-width characters.
     *
     * <p>This function cannot properly walk the cursor off the ends of the line
     * since it does not know about any shaping on the previous/following line
     * that might affect the cursor position. Callers must either avoid these
     * situations or handle the result specially.
     *
     * @param cursor the starting position of the cursor, between 0 and the
     * length of the line, inclusive
     * @param toLeft true if the caret is moving to the left.
     * @return the new offset.  If it is less than 0 or greater than the length
     * of the line, the previous/following line should be examined to get the
     * actual offset.
     */
    int getOffsetToLeftRightOf(int cursor, boolean toLeft) {
        // 1) The caret marks the leading edge of a character. The character
        // logically before it might be on a different level, and the active caret
        // position is on the character at the lower level. If that character
        // was the previous character, the caret is on its trailing edge.
        // 2) Take this character/edge and move it in the indicated direction.
        // This gives you a new character and a new edge.
        // 3) This position is between two visually adjacent characters.  One of
        // these might be at a lower level.  The active position is on the
        // character at the lower level.
        // 4) If the active position is on the trailing edge of the character,
        // the new caret position is the following logical character, else it
        // is the character.

        int lineStart = 0;
        int lineEnd = mLen;
        boolean paraIsRtl = mDir == -1;
        int[] runs = mDirections.mDirections;

        int runIndex, runLevel = 0, runStart = lineStart, runLimit = lineEnd, newCaret = -1;
        boolean trailing = false;

        if (cursor == lineStart) {
            runIndex = -2;
        } else if (cursor == lineEnd) {
            runIndex = runs.length;
        } else {
            // First, get information about the run containing the character with
            // the active caret.
            for (runIndex = 0; runIndex < runs.length; runIndex += 2) {
                runStart = lineStart + runs[runIndex];
                if (cursor >= runStart) {
                    runLimit = runStart + (runs[runIndex+1] & HiddenLayout.RUN_LENGTH_MASK);
                    if (runLimit > lineEnd) {
                        runLimit = lineEnd;
                    }
                    if (cursor < runLimit) {
                        runLevel = (runs[runIndex+1] >>> HiddenLayout.RUN_LEVEL_SHIFT) &
                                HiddenLayout.RUN_LEVEL_MASK;
                        if (cursor == runStart) {
                            // The caret is on a run boundary, see if we should
                            // use the position on the trailing edge of the previous
                            // logical character instead.
                            int prevRunIndex, prevRunLevel, prevRunStart, prevRunLimit;
                            int pos = cursor - 1;
                            for (prevRunIndex = 0; prevRunIndex < runs.length; prevRunIndex += 2) {
                                prevRunStart = lineStart + runs[prevRunIndex];
                                if (pos >= prevRunStart) {
                                    prevRunLimit = prevRunStart +
                                            (runs[prevRunIndex+1] & HiddenLayout.RUN_LENGTH_MASK);
                                    if (prevRunLimit > lineEnd) {
                                        prevRunLimit = lineEnd;
                                    }
                                    if (pos < prevRunLimit) {
                                        prevRunLevel = (runs[prevRunIndex+1] >>> HiddenLayout.RUN_LEVEL_SHIFT)
                                                & HiddenLayout.RUN_LEVEL_MASK;
                                        if (prevRunLevel < runLevel) {
                                            // Start from logically previous character.
                                            runIndex = prevRunIndex;
                                            runLevel = prevRunLevel;
                                            runStart = prevRunStart;
                                            runLimit = prevRunLimit;
                                            trailing = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
            }

            // caret might be == lineEnd.  This is generally a space or paragraph
            // separator and has an associated run, but might be the end of
            // text, in which case it doesn't.  If that happens, we ran off the
            // end of the run list, and runIndex == runs.length.  In this case,
            // we are at a run boundary so we skip the below test.
            if (runIndex != runs.length) {
                boolean runIsRtl = (runLevel & 0x1) != 0;
                boolean advance = toLeft == runIsRtl;
                if (cursor != (advance ? runLimit : runStart) || advance != trailing) {
                    // Moving within or into the run, so we can move logically.
                    newCaret = getOffsetBeforeAfter(runIndex, runStart, runLimit,
                            runIsRtl, cursor, advance);
                    // If the new position is internal to the run, we're at the strong
                    // position already so we're finished.
                    if (newCaret != (advance ? runLimit : runStart)) {
                        return newCaret;
                    }
                }
            }
        }

        // If newCaret is -1, we're starting at a run boundary and crossing
        // into another run. Otherwise we've arrived at a run boundary, and
        // need to figure out which character to attach to.  Note we might
        // need to run this twice, if we cross a run boundary and end up at
        // another run boundary.
        while (true) {
            boolean advance = toLeft == paraIsRtl;
            int otherRunIndex = runIndex + (advance ? 2 : -2);
            if (otherRunIndex >= 0 && otherRunIndex < runs.length) {
                int otherRunStart = lineStart + runs[otherRunIndex];
                int otherRunLimit = otherRunStart +
                        (runs[otherRunIndex+1] & HiddenLayout.RUN_LENGTH_MASK);
                if (otherRunLimit > lineEnd) {
                    otherRunLimit = lineEnd;
                }
                int otherRunLevel = (runs[otherRunIndex+1] >>> HiddenLayout.RUN_LEVEL_SHIFT) &
                        HiddenLayout.RUN_LEVEL_MASK;
                boolean otherRunIsRtl = (otherRunLevel & 1) != 0;

                advance = toLeft == otherRunIsRtl;
                if (newCaret == -1) {
                    newCaret = getOffsetBeforeAfter(otherRunIndex, otherRunStart,
                            otherRunLimit, otherRunIsRtl,
                            advance ? otherRunStart : otherRunLimit, advance);
                    if (newCaret == (advance ? otherRunLimit : otherRunStart)) {
                        // Crossed and ended up at a new boundary,
                        // repeat a second and final time.
                        runIndex = otherRunIndex;
                        runLevel = otherRunLevel;
                        continue;
                    }
                    break;
                }

                // The new caret is at a boundary.
                if (otherRunLevel < runLevel) {
                    // The strong character is in the other run.
                    newCaret = advance ? otherRunStart : otherRunLimit;
                }
                break;
            }

            if (newCaret == -1) {
                // We're walking off the end of the line.  The paragraph
                // level is always equal to or lower than any internal level, so
                // the boundaries get the strong caret.
                newCaret = advance ? mLen + 1 : -1;
                break;
            }

            // Else we've arrived at the end of the line.  That's a strong position.
            // We might have arrived here by crossing over a run with no internal
            // breaks and dropping out of the above loop before advancing one final
            // time, so reset the caret.
            // Note, we use '<=' below to handle a situation where the only run
            // on the line is a counter-directional run.  If we're not advancing,
            // we can end up at the 'lineEnd' position but the caret we want is at
            // the lineStart.
            if (newCaret <= lineEnd) {
                newCaret = advance ? lineEnd : lineStart;
            }
            break;
        }

        return newCaret;
    }

    /**
     * Returns the next valid offset within this directional run, skipping
     * conjuncts and zero-width characters.  This should not be called to walk
     * off the end of the line, since the returned values might not be valid
     * on neighboring lines.  If the returned offset is less than zero or
     * greater than the line length, the offset should be recomputed on the
     * preceding or following line, respectively.
     *
     * @param runIndex the run index
     * @param runStart the start of the run
     * @param runLimit the limit of the run
     * @param runIsRtl true if the run is right-to-left
     * @param offset the offset
     * @param after true if the new offset should logically follow the provided
     * offset
     * @return the new offset
     */
    private int getOffsetBeforeAfter(int runIndex, int runStart, int runLimit,
                                     boolean runIsRtl, int offset, boolean after) {

        if (runIndex < 0 || offset == (after ? mLen : 0)) {
            // Walking off end of line.  Since we don't know
            // what cursor positions are available on other lines, we can't
            // return accurate values.  These are a guess.
            if (after) {
                return TextUtils.getOffsetAfter(mText, offset + mStart) - mStart;
            }
            return TextUtils.getOffsetBefore(mText, offset + mStart) - mStart;
        }

        TextPaint wp = mWorkPaint;
        wp.set(mPaint);
        if (mIsJustifying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // (EW) despite not actually getting called, on Pie, simply having code here causes this
            // warning to be logged:
            //Accessing hidden method Landroid/graphics/Paint;->setWordSpacing(F)V (dark greylist, linking)
            // (EW) the AOSP version started calling this in Oreo, but on Pie, this was marked as a
            // restricted API (warning logged specifies "dark greylist"), so it can't even be called
            // with reflection. I couldn't find a good alternative, and rather than calling it
            // starting in Oreo and just skipping it on Pie, until there is a real need for it on
            // older version, it will just be skipped to be consistent between older versions.
            wp.setWordSpacing(mAddedWidthForJustify);
        }

        int spanStart = runStart;
        int spanLimit;
        if (mSpanned == null) {
            spanLimit = runLimit;
        } else {
            int target = after ? offset + 1 : offset;
            int limit = mStart + runLimit;
            while (true) {
                spanLimit = mSpanned.nextSpanTransition(mStart + spanStart, limit,
                        MetricAffectingSpan.class) - mStart;
                if (spanLimit >= target) {
                    break;
                }
                spanStart = spanLimit;
            }

            MetricAffectingSpan[] spans = mSpanned.getSpans(mStart + spanStart,
                    mStart + spanLimit, MetricAffectingSpan.class);
            spans = HiddenTextUtils.removeEmptySpans(spans, mSpanned, MetricAffectingSpan.class);

            if (spans.length > 0) {
                ReplacementSpan replacement = null;
                for (int j = 0; j < spans.length; j++) {
                    MetricAffectingSpan span = spans[j];
                    if (span instanceof ReplacementSpan) {
                        replacement = (ReplacementSpan)span;
                    } else {
                        span.updateMeasureState(wp);
                    }
                }

                if (replacement != null) {
                    // If we have a replacement span, we're moving either to
                    // the start or end of this span.
                    return after ? spanLimit : spanStart;
                }
            }
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int cursorOpt = after ? Paint.CURSOR_AFTER : Paint.CURSOR_BEFORE;
            if (mCharsValid) {
                return wp.getTextRunCursor(mChars, spanStart, spanLimit - spanStart,
                        runIsRtl, offset, cursorOpt);
            } else {
                return wp.getTextRunCursor(mText, mStart + spanStart,
                        mStart + spanLimit, runIsRtl, mStart + offset, cursorOpt) - mStart;
            }
        } else {
            // (EW) Paint#getTextRunCursor was called at least since Kitkat, but it only became
            // available for apps to call in Q (some changed parameters). this isn't great, but this
            // use of reflection is at least relatively safe since it's only done on old versions so
            // it shouldn't just stop working at some point in the future.
            int dir = runIsRtl ? HiddenPaint.DIRECTION_RTL : HiddenPaint.DIRECTION_LTR;
            int cursorOpt = after ? HiddenPaint.CURSOR_AFTER : HiddenPaint.CURSOR_BEFORE;
            try {
                if (mCharsValid) {
                    Method getTextRunCursorMethod = TextPaint.class.getMethod("getTextRunCursor",
                            char[].class, int.class, int.class, int.class, int.class, int.class);
                    return (int) getTextRunCursorMethod.invoke(wp, mChars, spanStart,
                            spanLimit - spanStart, dir, offset, cursorOpt);
                } else {
                    Method getTextRunCursorMethod = TextPaint.class.getMethod("getTextRunCursor",
                            CharSequence.class, int.class, int.class, int.class, int.class,
                            int.class);
                    return (int) getTextRunCursorMethod.invoke(wp, mText, mStart + spanStart,
                            mStart + spanLimit, dir, mStart + offset, cursorOpt) - mStart;
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.e(TAG, "getOffsetBeforeAfter: Reflection failed on getTextRunCursor: "
                        + e.getMessage());
                return -1;
            }
        }
    }

    /**
     * @param wp
     */
    private static void expandMetricsFromPaint(FontMetricsInt fmi, TextPaint wp) {
        final int previousTop     = fmi.top;
        final int previousAscent  = fmi.ascent;
        final int previousDescent = fmi.descent;
        final int previousBottom  = fmi.bottom;
        final int previousLeading = fmi.leading;

        wp.getFontMetricsInt(fmi);

        updateMetrics(fmi, previousTop, previousAscent, previousDescent, previousBottom,
                previousLeading);
    }

    static void updateMetrics(FontMetricsInt fmi, int previousTop, int previousAscent,
                              int previousDescent, int previousBottom, int previousLeading) {
        fmi.top     = Math.min(fmi.top,     previousTop);
        fmi.ascent  = Math.min(fmi.ascent,  previousAscent);
        fmi.descent = Math.max(fmi.descent, previousDescent);
        fmi.bottom  = Math.max(fmi.bottom,  previousBottom);
        fmi.leading = Math.max(fmi.leading, previousLeading);
    }

    private static void drawStroke(TextPaint wp, Canvas c, int color, float position,
                                   float thickness, float xLeft, float xRight, float baseline) {
        final float strokeTop = baseline + wp.baselineShift + position;

        final int previousColor = wp.getColor();
        final Paint.Style previousStyle = wp.getStyle();
        final boolean previousAntiAlias = wp.isAntiAlias();

        wp.setStyle(Paint.Style.FILL);
        wp.setAntiAlias(true);

        wp.setColor(color);
        c.drawRect(xLeft, strokeTop, xRight, strokeTop + thickness, wp);

        wp.setStyle(previousStyle);
        wp.setColor(previousColor);
        wp.setAntiAlias(previousAntiAlias);
    }

    private float getRunAdvance(TextPaint wp, int start, int end, int contextStart, int contextEnd,
                                boolean runIsRtl, int offset) {
        if (mCharsValid) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return wp.getRunAdvance(mChars, start, end, contextStart, contextEnd, runIsRtl,
                        offset);
            } else {
                // (EW) Paint#getTextRunAdvances was called at least since Kitkat (although with
                // some varying parameters), but it only became available for apps to call in
                // Marshmallow. this isn't great, but this use of reflection is at least  relatively
                // safe since it's only done on old versions so it shouldn't just stop working at
                // some point in the future.
                try {
                    int runLen = end - start;
                    int contextLen = contextEnd - contextStart;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Method getTextRunAdvancesMethod = TextPaint.class.getMethod(
                                "getTextRunAdvances", char[].class, int.class, int.class,
                                int.class, int.class, boolean.class, float[].class, int.class);
                        return (float) getTextRunAdvancesMethod.invoke(wp, mChars, start, runLen,
                                contextStart, contextLen, runIsRtl, null, 0);
                    } else {
                        int flags = runIsRtl
                                ? HiddenPaint.DIRECTION_RTL : HiddenPaint.DIRECTION_LTR;
                        Method getTextRunAdvancesMethod = TextPaint.class.getMethod(
                                "getTextRunAdvances", char[].class, int.class, int.class,
                                int.class, int.class, int.class, float[].class, int.class);
                        return (float) getTextRunAdvancesMethod.invoke(wp, mChars, start, runLen,
                                contextStart, contextLen, flags, null, 0);
                    }
                } catch (NoSuchMethodException | IllegalAccessException
                        | InvocationTargetException e) {
                    Log.e(TAG, "getRunAdvance: Reflection failed on getTextRunAdvances: "
                            + e.getMessage());
                    return 0;
                }
            }
        } else {
            final int delta = mStart;
            if (mComputed == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    return wp.getRunAdvance(mText, delta + start, delta + end,
                            delta + contextStart, delta + contextEnd, runIsRtl, delta + offset);
                } else {
                    // (EW) Paint#getTextRunAdvances was called at least since Kitkat (although with
                    // some varying parameters), but it only became available for apps to call in
                    // Marshmallow. this isn't great, but this use of reflection is at least
                    // relatively safe since it's only done on old versions so it shouldn't just
                    // stop working at some point in the future.
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Method getTextRunAdvancesMethod = TextPaint.class.getMethod(
                                    "getTextRunAdvances", CharSequence.class, int.class, int.class,
                                    int.class, int.class, boolean.class, float[].class, int.class);
                            return (float) getTextRunAdvancesMethod.invoke(wp, mText, delta + start,
                                    delta + end, delta + contextStart, delta + contextEnd,
                                    runIsRtl, null, 0);
                        } else {
                            int flags = runIsRtl
                                    ? HiddenPaint.DIRECTION_RTL : HiddenPaint.DIRECTION_LTR;
                            Method getTextRunAdvancesMethod = TextPaint.class.getMethod(
                                    "getTextRunAdvances", CharSequence.class, int.class, int.class,
                                    int.class, int.class, int.class, float[].class, int.class);
                            return (float) getTextRunAdvancesMethod.invoke(wp, mText, delta + start,
                                    delta + end, delta + contextStart, delta + contextEnd,
                                    flags, null, 0);
                        }
                    } catch (NoSuchMethodException | IllegalAccessException
                            | InvocationTargetException e) {
                        Log.e(TAG, "getRunAdvance: Reflection failed on getTextRunAdvances: "
                                + e.getMessage());
                        return 0;
                    }
                }
            } else {
                return mComputed.getWidth(start + delta, end + delta);
            }
        }
    }

    // (EW) due to the simplified parameters in #handleRun (the only caller of this), it would
    // always pass null for the Canvas and TextShaper.GlyphsConsumer, 0 for top, y, and bottom, and
    // true for needWidth, so these parameters were removed to simplify, which also made decorations
    // no longer used, so it was removed too.
    /**
     * Utility function for measuring and rendering text.  The text must
     * not include a tab.
     *
     * @param wp the working paint
     * @param start the start of the text
     * @param end the end of the text
     * @param runIsRtl true if the run is right-to-left
     * @param x the edge of the run closest to the leading margin
     * @param fmi receives metrics information, can be null
     * @param offset the offset for the purpose of measuring
     * @return the signed width of the run based on the run direction; only
     * valid if needWidth is true
     */
    private float handleText(TextPaint wp, int start, int end,
                             int contextStart, int contextEnd, boolean runIsRtl,
                             float x,
                             FontMetricsInt fmi, int offset) {
        if (mIsJustifying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // (EW) the AOSP version started calling this in Oreo, but on Pie, this was marked as a
            // restricted API (warning logged specifies "dark greylist"), so it can't even be called
            // with reflection. I couldn't find a good alternative, and rather than calling it
            // starting in Oreo and just skipping it on Pie, until there is a real need for it on
            // older version, it will just be skipped to be consistent between older versions.
            wp.setWordSpacing(mAddedWidthForJustify);
        }

        // Get metrics first (even for empty strings or "0" width runs)
        if (fmi != null) {
            expandMetricsFromPaint(fmi, wp);
        }

        // No need to do anything if the run width is "0"
        if (end == start) {
            return 0f;
        }

        float totalWidth =
                getRunAdvance(wp, start, end, contextStart, contextEnd, runIsRtl, offset);

        return runIsRtl ? -totalWidth : totalWidth;
    }

    // (EW) due to the simplified parameters in #handleRun (the only caller of this), it would
    // always pass null for the Canvas, 0 for top, y, and bottom, and true for needWidth, so these
    // parameters were removed to simplify, which also made x no longer used, so it was removed too.
    /**
     * Utility function for measuring and rendering a replacement.
     *
     * @param replacement the replacement
     * @param wp the work paint
     * @param start the start of the run
     * @param limit the limit of the run
     * @param runIsRtl true if the run is right-to-left
     * @param fmi receives metrics information, can be null
     * @return the signed width of the run based on the run direction; only
     * valid if needWidth is true
     */
    private float handleReplacement(ReplacementSpan replacement, TextPaint wp, int start, int limit,
                                    boolean runIsRtl, FontMetricsInt fmi) {
        int textStart = mStart + start;
        int textLimit = mStart + limit;

        int previousTop = 0;
        int previousAscent = 0;
        int previousDescent = 0;
        int previousBottom = 0;
        int previousLeading = 0;

        boolean needUpdateMetrics = (fmi != null);

        if (needUpdateMetrics) {
            previousTop     = fmi.top;
            previousAscent  = fmi.ascent;
            previousDescent = fmi.descent;
            previousBottom  = fmi.bottom;
            previousLeading = fmi.leading;
        }

        float ret = replacement.getSize(wp, mText, textStart, textLimit, fmi);

        if (needUpdateMetrics) {
            updateMetrics(fmi, previousTop, previousAscent, previousDescent, previousBottom,
                    previousLeading);
        }

        return runIsRtl ? -ret : ret;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private int adjustStartHyphenEdit(int start, @HiddenPaint.StartHyphenEdit int startHyphenEdit) {
        // Only draw hyphens on first in line. Disable them otherwise.
        return start > 0 ? Paint.START_HYPHEN_EDIT_NO_EDIT : startHyphenEdit;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private int adjustEndHyphenEdit(int limit, @HiddenPaint.EndHyphenEdit int endHyphenEdit) {
        // Only draw hyphens on last run in line. Disable them otherwise.
        return limit < mLen ? Paint.END_HYPHEN_EDIT_NO_EDIT : endHyphenEdit;
    }

    // (EW) from Pie
    private int adjustHyphenEdit(int start, int limit, int hyphenEdit) {
        int result = hyphenEdit;
        // Only draw hyphens on first or last run in line. Disable them otherwise.
        if (start > 0) { // not the first run
            result &= ~HiddenPaint.HYPHENEDIT_MASK_START_OF_LINE;
        }
        if (limit < mLen) { // not the last run
            result &= ~HiddenPaint.HYPHENEDIT_MASK_END_OF_LINE;
        }
        return result;
    }

    private static final class DecorationInfo {
        public boolean isStrikeThroughText;
        public boolean isUnderlineText;
        public int underlineColor;
        public float underlineThickness;
        public int start = -1;
        public int end = -1;

        public boolean hasDecoration() {
            return isStrikeThroughText || isUnderlineText || underlineColor != 0;
        }

        // Copies the info, but not the start and end range.
        public DecorationInfo copyInfo() {
            final DecorationInfo copy = new DecorationInfo();
            copy.isStrikeThroughText = isStrikeThroughText;
            copy.isUnderlineText = isUnderlineText;
            copy.underlineColor = underlineColor;
            copy.underlineThickness = underlineThickness;
            return copy;
        }
    }

    private void clearDecorationInfo(@NonNull TextPaint paint) {
        if (paint.isStrikeThruText()) {
            paint.setStrikeThruText(false);
        }
        if (paint.isUnderlineText()) {
            paint.setUnderlineText(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // (EW) despite not actually getting called, on Pie, simply having this code here (or
            // the other 2 direct uses of underlineColor below and in #equalAttributes) causes this
            // warning to be logged:
            // Accessing hidden field Landroid/text/TextPaint;->underlineColor:I (light greylist, linking)
            paint.underlineColor = 0;
            // (EW) despite not actually getting called, on Pie, simply having this code here (or
            // the other 2 direct uses of underlineThickness below and in #equalAttributes) causes
            // this warning to be logged:
            // Accessing hidden field Landroid/text/TextPaint;->underlineThickness:F (light greylist, linking)
            paint.underlineThickness = 0.0f;
        } else {
            // (EW) TextPaint#underlineColor and TextPaint#underlineThickness have existed since at
            // least kitkat, but were hidden. this isn't great, but this use of reflection is at
            // least relatively safe since it's only done on old versions so it shouldn't just stop
            // working at some point in the future.
            try {
                Field underlineColorField = TextPaint.class.getDeclaredField("underlineColor");
                underlineColorField.set(paint, 0);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                Log.e(TAG, "extractDecorationInfo: failed to use underlineColor: " + e);
            }
            try {
                Field underlineThicknessField =
                        TextPaint.class.getDeclaredField("underlineThickness");
                underlineThicknessField.set(paint, 0);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                Log.e(TAG, "extractDecorationInfo: failed to use underlineThickness: " + e);
            }
        }
    }

    // (EW) #drawRun and #shapeRun were skipped because they weren't necessary, and since the only
    // caller, #measureRun, always passed null for the Canvas and TextShaper.GlyphsConsumer, 0 for
    // x, top, y, and bottom, and true for needWidth, those parameters were removed to simplify.
    /**
     * Utility function for handling a unidirectional run.  The run must not
     * contain tabs but can contain styles.
     *
     * @param start the line-relative start of the run
     * @param measureLimit the offset to measure to, between start and limit inclusive
     * @param limit the limit of the run
     * @param runIsRtl true if the run is right-to-left
     * @param fmi receives metrics information, can be null
     * @return the signed width of the run based on the run direction; only
     * valid if needWidth is true
     */
    private float handleRun(int start, int measureLimit, int limit, boolean runIsRtl,
                            FontMetricsInt fmi) {
        float x = 0;

        if (measureLimit < start || measureLimit > limit) {
            throw new IndexOutOfBoundsException("measureLimit (" + measureLimit + ") is out of "
                    + "start (" + start + ") and limit (" + limit + ") bounds");
        }

        // Case of an empty line, make sure we update fmi according to mPaint
        if (start == measureLimit) {
            final TextPaint wp = mWorkPaint;
            wp.set(mPaint);
            if (fmi != null) {
                expandMetricsFromPaint(fmi, wp);
            }
            return 0f;
        }

        final boolean needsSpanMeasurement;
        if (mSpanned == null) {
            needsSpanMeasurement = false;
        } else {
            mMetricAffectingSpanSpanSet.init(mSpanned, mStart + start, mStart + limit);
            mCharacterStyleSpanSet.init(mSpanned, mStart + start, mStart + limit);
            needsSpanMeasurement = mMetricAffectingSpanSpanSet.numberOfSpans != 0
                    || mCharacterStyleSpanSet.numberOfSpans != 0;
        }

        if (!needsSpanMeasurement) {
            final TextPaint wp = mWorkPaint;
            wp.set(mPaint);
            setHyphenEdit(wp, wp, start, limit);
            return handleText(wp, start, limit, start, limit, runIsRtl, x, fmi, measureLimit);
        }

        // Shaping needs to take into account context up to metric boundaries,
        // but rendering needs to take into account character style boundaries.
        // So we iterate through metric runs to get metric bounds,
        // then within each metric run iterate through character style runs
        // for the run bounds.
        for (int i = start, iNext; i < measureLimit; i = iNext) {
            final TextPaint wp = mWorkPaint;
            wp.set(mPaint);

            iNext = mMetricAffectingSpanSpanSet.getNextTransition(mStart + i, mStart + limit) -
                    mStart;
            int mlimit = Math.min(iNext, measureLimit);

            ReplacementSpan replacement = null;

            for (int j = 0; j < mMetricAffectingSpanSpanSet.numberOfSpans; j++) {
                // Both intervals [spanStarts..spanEnds] and [mStart + i..mStart + mlimit] are NOT
                // empty by construction. This special case in getSpans() explains the >= & <= tests
                if ((mMetricAffectingSpanSpanSet.spanStarts[j] >= mStart + mlimit)
                        || (mMetricAffectingSpanSpanSet.spanEnds[j] <= mStart + i)) {
                    continue;
                }

                boolean insideEllipsis =
                        mStart + mEllipsisStart <= mMetricAffectingSpanSpanSet.spanStarts[j]
                                && mMetricAffectingSpanSpanSet.spanEnds[j] <= mStart + mEllipsisEnd;
                final MetricAffectingSpan span = mMetricAffectingSpanSpanSet.spans[j];
                if (span instanceof ReplacementSpan) {
                    replacement = !insideEllipsis ? (ReplacementSpan) span : null;
                } else {
                    // We might have a replacement that uses the draw
                    // state, otherwise measure state would suffice.
                    span.updateDrawState(wp);
                }
            }

            if (replacement != null) {
                x += handleReplacement(replacement, wp, i, mlimit, runIsRtl, fmi);
                continue;
            }

            final TextPaint activePaint = mActivePaint;
            activePaint.set(mPaint);
            int activeStart = i;
            int activeEnd = mlimit;
            for (int j = i, jNext; j < mlimit; j = jNext) {
                jNext = mCharacterStyleSpanSet.getNextTransition(mStart + j, mStart + iNext) -
                        mStart;

                final int offset = Math.min(jNext, mlimit);
                wp.set(mPaint);
                for (int k = 0; k < mCharacterStyleSpanSet.numberOfSpans; k++) {
                    // Intentionally using >= and <= as explained above
                    if ((mCharacterStyleSpanSet.spanStarts[k] >= mStart + offset) ||
                            (mCharacterStyleSpanSet.spanEnds[k] <= mStart + j)) {
                        continue;
                    }

                    final CharacterStyle span = mCharacterStyleSpanSet.spans[k];
                    span.updateDrawState(wp);
                }

                // (EW) since #handleText doesn't need the list of DecorationInfo, that
                // functionality was stripped, making it simply clear the decoration properties from
                // the paint, rather than extracting them to the DecorationInfo
                clearDecorationInfo(wp);

                if (j == i) {
                    // First chunk of text. We can't handle it yet, since we may need to merge it
                    // with the next chunk. So we just save the TextPaint for future comparisons
                    // and use.
                    activePaint.set(wp);
                } else if (!equalAttributes(wp, activePaint)) {
                    // The style of the present chunk of text is substantially different from the
                    // style of the previous chunk. We need to handle the active piece of text
                    // and restart with the present chunk.
                    setHyphenEdit(activePaint, mPaint, activeStart, activeEnd);
                    x += handleText(activePaint, activeStart, activeEnd, i, iNext, runIsRtl,
                            x, fmi,
                            Math.min(activeEnd, mlimit));
                    activeStart = j;
                    activePaint.set(wp);
                } else {
                    // The present TextPaint is substantially equal to the last TextPaint except
                    // perhaps for decorations. We just need to expand the active piece of text to
                    // include the present chunk, which we always do anyway. We don't need to save
                    // wp to activePaint, since they are already equal.
                }

                activeEnd = jNext;
            }
            // Handle the final piece of text.
            setHyphenEdit(activePaint, mPaint, activeStart, activeEnd);
            x += handleText(activePaint, activeStart, activeEnd, i, iNext, runIsRtl, x, fmi,
                    Math.min(activeEnd, mlimit));
        }

        return x;
    }

    // (EW) wrapper for TextPaint#setStartHyphenEdit called with #adjustStartHyphenEdit and
    // TextPaint#setEndHyphenEdit called with #adjustEndHyphenEdit to handle versions
    private void setHyphenEdit(final TextPaint wp, final TextPaint sourcePaint, int start,
                               int limit) {
        // (EW) Paint#setHyphenEdit(int) and Paint#getHyphenEdit() existed since Marshmallow,
        // although they were only called in Oreo through Pie. prior to Oreo the documentation
        // stated that setHyphenEdit only takes a 1 or 0, so the handling in adjustHyphenEdit
        // wouldn't work. I was going to just use reflection to also call them (only starting in
        // Oreo) since reflection is relatively safe if it's only used until a certain version, but
        // on Pie, due to being a restricted API, Paint#getHyphenEdit throws a NoSuchMethodException
        // and logs a warning indicating it is on the dark greylist, although Paint#setHyphenEdit
        // still works (it does still log a warning indicating it is on the light greylist). I
        // couldn't find a good alternative, and rather than just skipping it on Pie, until there is
        // a real need for it on older version, it will just be skipped to be consistent between
        // older versions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wp.setStartHyphenEdit(adjustStartHyphenEdit(start, sourcePaint.getStartHyphenEdit()));
            wp.setEndHyphenEdit(adjustEndHyphenEdit(limit, sourcePaint.getEndHyphenEdit()));
        }
    }

    /**
     * Render a text run with the set-up paint.
     *
     * @param c the canvas
     * @param wp the paint used to render the text
     * @param start the start of the run
     * @param end the end of the run
     * @param contextStart the start of context for the run
     * @param contextEnd the end of the context for the run
     * @param runIsRtl true if the run is right-to-left
     * @param x the x position of the left edge of the run
     * @param y the baseline of the run
     */
    private void drawTextRun(Canvas c, TextPaint wp, int start, int end,
                             int contextStart, int contextEnd, boolean runIsRtl, float x, int y) {

        if (mCharsValid) {
            int count = end - start;
            int contextCount = contextEnd - contextStart;
            tryDrawTextRun(c, mChars, start, count, contextStart, contextCount,
                    x, y, runIsRtl, wp);
        } else {
            int delta = mStart;
            tryDrawTextRun(c, mText, delta + start, delta + end,
                    delta + contextStart, delta + contextEnd, x, y, runIsRtl, wp);
        }
    }

    // (EW) Canvas#drawTextRun was made available in Marshmallow, but it was actually added with the
    // current signature in Lollipop, so it should be safe to call on these older versions, but to
    // be extra safe we'll wrap it in a try/catch. even older versions still have it, but has an int
    // dir parameter instead of boolean isRtl, so we'll need to use reflection for that.
    @SuppressLint("NewApi")
    private static boolean tryDrawTextRun(@NonNull Canvas c, @NonNull char[] text, int index,
                                          int count, int contextIndex, int contextCount, float x,
                                          float y, boolean isRtl, @NonNull Paint paint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                c.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint);
            } catch (Exception e) {
                Log.w(TAG, "Canvas#drawTextRun couldn't be called: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                return false;
            }
        } else {
            int dir = isRtl ? /*DIRECTION_RTL*/1 : /*DIRECTION_LTR*/0;
            try {
                Method drawTextRunMethod = Canvas.class.getMethod("drawTextRun",
                        char[].class, int.class, int.class, int.class, int.class, float.class,
                        float.class, int.class, Paint.class);
                drawTextRunMethod.invoke(c,
                        text, index, count, contextIndex, contextCount, x, y, dir, paint);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.e(TAG, "tryDrawTextRun: Reflection failed on drawTextRun: "
                        + e.getMessage());
                return false;
            }
        }
        return true;
    }

    // (EW) Canvas#drawTextRun was made available in Marshmallow, but it was actually added with the
    // current signature in Lollipop, so it should be safe to call on these older versions, but to
    // be extra safe we'll wrap it in a try/catch. even older versions still have it, but has an int
    // dir parameter instead of boolean isRtl, so we'll need to use reflection for that.
    @SuppressLint("NewApi")
    private static boolean tryDrawTextRun(@NonNull Canvas c, @NonNull CharSequence text, int start,
                                          int end, int contextStart, int contextEnd, float x,
                                          float y, boolean isRtl, @NonNull Paint paint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                c.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
            } catch (Exception e) {
                Log.w(TAG, "Canvas#drawTextRun couldn't be called: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
                return false;
            }
        } else {
            int dir = isRtl ? /*DIRECTION_RTL*/1 : /*DIRECTION_LTR*/0;
            try {
                Method drawTextRunMethod = Canvas.class.getMethod("drawTextRun",
                        CharSequence.class, int.class, int.class, int.class, int.class, float.class,
                        float.class, int.class, Paint.class);
                drawTextRunMethod.invoke(c,
                        text, start, end, contextStart, contextEnd, x, y, dir, paint);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                Log.e(TAG, "tryDrawTextRun: Reflection failed on drawTextRun: "
                        + e.getMessage());
                return false;
            }
        }
        return true;
    }

    // (EW) this was added in S and TextRunShaper#shapeTextRun is only available starting in that
    // version, so for now, I'm marking this as requiring that version, at least until there is a
    // need for it on older versions.
    /**
     * Shape a text run with the set-up paint.
     *
     * @param consumer the output positioned glyphs list
     * @param paint the paint used to render the text
     * @param start the start of the run
     * @param end the end of the run
     * @param contextStart the start of context for the run
     * @param contextEnd the end of the context for the run
     * @param runIsRtl true if the run is right-to-left
     * @param x the x position of the left edge of the run
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private void shapeTextRun(TextShaper.GlyphsConsumer consumer, TextPaint paint,
                              int start, int end, int contextStart, int contextEnd,
                              boolean runIsRtl, float x) {

        int count = end - start;
        int contextCount = contextEnd - contextStart;
        PositionedGlyphs glyphs;
        if (mCharsValid) {
            glyphs = TextRunShaper.shapeTextRun(
                    mChars,
                    start, count,
                    contextStart, contextCount,
                    x, 0f,
                    runIsRtl,
                    paint
            );
        } else {
            glyphs = TextRunShaper.shapeTextRun(
                    mText,
                    mStart + start, count,
                    mStart + contextStart, contextCount,
                    x, 0f,
                    runIsRtl,
                    paint
            );
        }
        consumer.accept(start, count, glyphs, paint);
    }

    /**
     * Returns the next tab position.
     *
     * @param h the (unsigned) offset from the leading margin
     * @return the (unsigned) tab position after this offset
     */
    float nextTab(float h) {
        if (mTabs != null) {
            return mTabs.nextTab(h);
        }
        return TabStops.nextDefaultStop(h, TAB_INCREMENT);
    }

    private boolean isStretchableWhitespace(int ch) {
        // TODO: Support NBSP and other stretchable whitespace (b/34013491 and b/68204709).
        return ch == 0x0020;
    }

    /* Return the number of spaces in the text line, for the purpose of justification */
    private int countStretchableSpaces(int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            final char c = mCharsValid ? mChars[i] : mText.charAt(i + mStart);
            if (isStretchableWhitespace(c)) {
                count++;
            }
        }
        return count;
    }

    // Note: keep this in sync with Minikin LineBreaker::isLineEndSpace()
    public static boolean isLineEndSpace(char ch) {
        return ch == ' ' || ch == '\t' || ch == 0x1680
                || (0x2000 <= ch && ch <= 0x200A && ch != 0x2007)
                || ch == 0x205F || ch == 0x3000;
    }

    private static final int TAB_INCREMENT = 20;

    private static boolean equalAttributes(@NonNull TextPaint lp, @NonNull TextPaint rp) {
        return lp.getColorFilter() == rp.getColorFilter()
                && lp.getMaskFilter() == rp.getMaskFilter()
                && lp.getShader() == rp.getShader()
                && lp.getTypeface() == rp.getTypeface()
                && lp.getXfermode() == rp.getXfermode()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                        || lp.getTextLocales().equals(rp.getTextLocales()))
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                        || TextUtils.equals(lp.getFontFeatureSettings(),
                                rp.getFontFeatureSettings()))
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                        || TextUtils.equals(lp.getFontVariationSettings(),
                                rp.getFontVariationSettings()))
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || lp.getShadowLayerRadius() == rp.getShadowLayerRadius())
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || lp.getShadowLayerDx() == rp.getShadowLayerDx())
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || lp.getShadowLayerDy() == rp.getShadowLayerDy())
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || lp.getShadowLayerColor() == rp.getShadowLayerColor())
                && lp.getFlags() == rp.getFlags()
                && lp.getHinting() == rp.getHinting()
                && lp.getStyle() == rp.getStyle()
                && lp.getColor() == rp.getColor()
                && lp.getStrokeWidth() == rp.getStrokeWidth()
                && lp.getStrokeMiter() == rp.getStrokeMiter()
                && lp.getStrokeCap() == rp.getStrokeCap()
                && lp.getStrokeJoin() == rp.getStrokeJoin()
                && lp.getTextAlign() == rp.getTextAlign()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                        || lp.isElegantTextHeight() == rp.isElegantTextHeight())
                && lp.getTextSize() == rp.getTextSize()
                && lp.getTextScaleX() == rp.getTextScaleX()
                && lp.getTextSkewX() == rp.getTextSkewX()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                        || lp.getLetterSpacing() == rp.getLetterSpacing())
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        // (EW) despite not actually getting called, on Pie, simply having this code
                        // here causes this warning to be logged:
                        // Accessing hidden method Landroid/graphics/Paint;->getWordSpacing()F (dark greylist, linking)
                        || lp.getWordSpacing() == rp.getWordSpacing())
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || lp.getStartHyphenEdit() == rp.getStartHyphenEdit())
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || lp.getEndHyphenEdit() == rp.getEndHyphenEdit())
                && lp.bgColor == rp.bgColor
                && lp.baselineShift == rp.baselineShift
                && lp.linkColor == rp.linkColor
                && lp.drawableState == rp.drawableState
                && lp.density == rp.density
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || lp.underlineColor == rp.underlineColor)
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || lp.underlineThickness == rp.underlineThickness);
    }
}
