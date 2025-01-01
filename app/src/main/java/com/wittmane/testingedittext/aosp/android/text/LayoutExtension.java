/*
 * Copyright (C) 2022-2024 Eli Wittman
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

import static android.text.Layout.DIR_LEFT_TO_RIGHT;
import static android.text.Layout.DIR_RIGHT_TO_LEFT;

import android.graphics.Path;
import android.os.Build;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextDirectionHeuristic;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.LeadingMarginSpan;
import android.text.style.ReplacementSpan;
import android.text.style.TabStopSpan;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.aosp.com.android.internal.util.ArrayUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * (EW) content from {@link Layout} that is blocked from apps accessing
 */
public class LayoutExtension {
    private static final String TAG = LayoutExtension.class.getSimpleName();

    @RequiresApi(api = Build.VERSION_CODES.M)
    @IntDef(value = {
            Layout.BREAK_STRATEGY_SIMPLE,
            Layout.BREAK_STRATEGY_HIGH_QUALITY,
            Layout.BREAK_STRATEGY_BALANCED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BreakStrategy {}

    @RequiresApi(api = Build.VERSION_CODES.M)
    @IntDef(value = {
            Layout.HYPHENATION_FREQUENCY_NORMAL,
            Layout.HYPHENATION_FREQUENCY_NORMAL_FAST,
            Layout.HYPHENATION_FREQUENCY_FULL,
            Layout.HYPHENATION_FREQUENCY_FULL_FAST,
            Layout.HYPHENATION_FREQUENCY_NONE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface HyphenationFrequency {}

    @RequiresApi(api = Build.VERSION_CODES.O)
    @IntDef(value = {
            Layout.JUSTIFICATION_MODE_NONE,
            Layout.JUSTIFICATION_MODE_INTER_WORD,
            Layout.JUSTIFICATION_MODE_INTER_CHARACTER,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JustificationMode {}

    /**
     * Return how wide a layout must be in order to display the
     * specified text slice with one line per paragraph.
     *
     * If the measured width exceeds given limit, returns limit value instead.
     */
    public static float getDesiredWidthWithLimit(CharSequence source, int start, int end,
                                                 TextPaint paint, TextDirectionHeuristic textDir,
                                                 float upperLimit, boolean useBoundsForWidth) {
        float need = 0;

        int next;
        for (int i = start; i <= end; i = next) {
            next = TextUtils.indexOf(source, '\n', i, end);

            if (next < 0)
                next = end;

            // note, omits trailing paragraph char
            float w = measurePara(paint, source, i, next, textDir, useBoundsForWidth);
            if (w > upperLimit) {
                return upperLimit;
            }

            if (w > need)
                need = w;

            next++;
        }

        return need;
    }

    // (EW) made static and added TextDirectionHeuristic parameter and custom logic to get a
    // Directions object that we can actually do something with
    /**
     * Returns the directional run information for the specified line.
     * The array alternates counts of characters in left-to-right
     * and right-to-left segments of the line.
     *
     * <p>NOTE: this is inadequate to support bidirectional text, and will change.
     */
    public static Directions getLineDirections(Layout layout, TextDirectionHeuristic textDir,
                                               int line) {
        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);

        MeasuredParagraph mt = null;
        try {
            mt = MeasuredParagraph.buildForBidi(layout.getText(), 0, layout.getText().length(),
                    textDir, mt);
            return mt.getDirections(lineStart, lineEnd);
        } finally {
            if (mt != null) {
                mt.recycle();
            }
        }
    }

    // (EW) made static and added TextDirectionHeuristic parameter
    /**
     * Returns true if the character at offset and the preceding character
     * are at different run levels (and thus there's a split caret).
     * @param offset the offset
     * @return true if at a level boundary
     */
    public static boolean isLevelBoundary(Layout layout, TextDirectionHeuristic textDir,
                                          int offset) {
        int line = layout.getLineForOffset(offset);
        Directions dirs = getLineDirections(layout, textDir, line);
        if (dirs == DIRS_ALL_LEFT_TO_RIGHT || dirs == DIRS_ALL_RIGHT_TO_LEFT) {
            return false;
        }

        int[] runs = dirs.mDirections;
        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);
        if (offset == lineStart || offset == lineEnd) {
            int paraLevel = layout.getParagraphDirection(line) == 1 ? 0 : 1;
            int runIndex = offset == lineStart ? 0 : runs.length - 2;
            return ((runs[runIndex + 1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK) != paraLevel;
        }

        offset -= lineStart;
        for (int i = 0; i < runs.length; i += 2) {
            if (offset == runs[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the range of the run that the character at offset belongs to.
     * @param offset the offset
     * @return The range of the run
     */
    public static long getRunRange(Layout layout, TextDirectionHeuristic textDir, int offset) {
        int line = layout.getLineForOffset(offset);
        Directions dirs = getLineDirections(layout, textDir, line);
        if (dirs == DIRS_ALL_LEFT_TO_RIGHT || dirs == DIRS_ALL_RIGHT_TO_LEFT) {
            return TextUtilsExtension.packRangeInLong(0, layout.getLineEnd(line));
        }
        int[] runs = dirs.mDirections;
        int lineStart = layout.getLineStart(line);
        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            int limit = start + (runs[i+1] & RUN_LENGTH_MASK);
            if (offset >= start && offset < limit) {
                return TextUtilsExtension.packRangeInLong(start, limit);
            }
        }
        // Should happen only if the offset is "out of bounds"
        return TextUtilsExtension.packRangeInLong(0, layout.getLineEnd(line));
    }

    // (EW) made static and added TextDirectionHeuristic parameter. although child classes could
    // override the implementation, as of Android 14, BoringLayout, DynamicLayout, and StaticLayout
    // are the only AOSP classes that extend from Layout, and nothing extends those.
    /**
     * Checks if the trailing BiDi level should be used for an offset
     *
     * This method is useful when the offset is at the BiDi level transition point and determine
     * which run need to be used. For example, let's think about following input: (L* denotes
     * Left-to-Right characters, R* denotes Right-to-Left characters.)
     * Input (Logical Order): L1 L2 L3 R1 R2 R3 L4 L5 L6
     * Input (Display Order): L1 L2 L3 R3 R2 R1 L4 L5 L6
     *
     * Then, think about selecting the range (3, 6). The offset=3 and offset=6 are ambiguous here
     * since they are at the BiDi transition point.  In Android, the offset is considered to be
     * associated with the trailing run if the BiDi level of the trailing run is higher than of the
     * previous run.  In this case, the BiDi level of the input text is as follows:
     *
     * Input (Logical Order): L1 L2 L3 R1 R2 R3 L4 L5 L6
     *              BiDi Run: [ Run 0 ][ Run 1 ][ Run 2 ]
     *            BiDi Level:  0  0  0  1  1  1  0  0  0
     *
     * Thus, offset = 3 is part of Run 1 and this method returns true for offset = 3, since the BiDi
     * level of Run 1 is higher than the level of Run 0.  Similarly, the offset = 6 is a part of Run
     * 1 and this method returns false for the offset = 6 since the BiDi level of Run 1 is higher
     * than the level of Run 2.
     *
     * @return true if offset is at the BiDi level transition point and trailing BiDi level is
     *         higher than previous BiDi level. See above for the detail.
     */
    public static boolean primaryIsTrailingPrevious(Layout layout, TextDirectionHeuristic textDir,
                                                    int offset) {
        int line = layout.getLineForOffset(offset);
        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);
        int[] runs = getLineDirections(layout, textDir, line).mDirections;

        int levelAt = -1;
        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            int limit = start + (runs[i+1] & RUN_LENGTH_MASK);
            if (limit > lineEnd) {
                limit = lineEnd;
            }
            if (offset >= start && offset < limit) {
                if (offset > start) {
                    // Previous character is at same level, so don't use trailing.
                    return false;
                }
                levelAt = (runs[i+1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK;
                break;
            }
        }
        if (levelAt == -1) {
            // Offset was limit of line.
            levelAt = layout.getParagraphDirection(line) == 1 ? 0 : 1;
        }

        // At level boundary, check previous level.
        int levelBefore = -1;
        if (offset == lineStart) {
            levelBefore = layout.getParagraphDirection(line) == 1 ? 0 : 1;
        } else {
            offset -= 1;
            for (int i = 0; i < runs.length; i += 2) {
                int start = lineStart + runs[i];
                int limit = start + (runs[i+1] & RUN_LENGTH_MASK);
                if (limit > lineEnd) {
                    limit = lineEnd;
                }
                if (offset >= start && offset < limit) {
                    levelBefore = (runs[i+1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK;
                    break;
                }
            }
        }

        return levelBefore < levelAt;
    }

    // (EW) as of S nothing seems to be overriding this, but if that changes, using this will cause
    // different behavior for those layout types
    // I suppose that's not really any worse than this code changing in future versions making a
    // change in functionality
    /**
     * Computes in linear time the results of calling
     * #primaryIsTrailingPrevious for all offsets on a line.
     * @param line The line giving the offsets we compute the information for
     * @return The array of results, indexed from 0, where 0 corresponds to the line start offset
     */
    public static boolean[] primaryIsTrailingPreviousAllLineOffsets(Layout layout,
                                                                    TextDirectionHeuristic textDir,
                                                                    int line) {
        int lineStart = layout.getLineStart(line);
        int lineEnd = layout.getLineEnd(line);
        int[] runs = getLineDirections(layout, textDir, line).mDirections;

        boolean[] trailing = new boolean[lineEnd - lineStart + 1];

        byte[] level = new byte[lineEnd - lineStart + 1];
        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            int limit = start + (runs[i + 1] & RUN_LENGTH_MASK);
            if (limit > lineEnd) {
                limit = lineEnd;
            }
            if (limit == start) {
                continue;
            }
            level[limit - lineStart - 1] =
                    (byte) ((runs[i + 1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK);
        }

        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            byte currentLevel = (byte) ((runs[i + 1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK);
            trailing[start - lineStart] = currentLevel > (start == lineStart
                    ? (layout.getParagraphDirection(line) == 1 ? 0 : 1)
                    : level[start - lineStart - 1]);
        }

        return trailing;
    }

    // (EW) made static and added TextDirectionHeuristic parameter (since
    // Layout#getTextDirectionHeuristic is hidden, so we can't get that from the layout). although
    // child classes could override the implementation, as of Android 14, BoringLayout,
    // DynamicLayout, and StaticLayout are the only AOSP classes that extend from Layout, and
    // nothing extends those.
    /**
     * Get the primary horizontal position for the specified text offset, but
     * optionally clamp it so that it doesn't exceed the width of the layout.
     */
    public static float getPrimaryHorizontal(Layout layout, TextDirectionHeuristic textDir,
                                             int offset, boolean clamped) {
        boolean trailing = primaryIsTrailingPrevious(layout, textDir, offset);
        return getHorizontal(layout, textDir, offset, trailing, clamped);
    }

    // (EW) made static and added TextDirectionHeuristic parameter
    private static float getHorizontal(Layout layout, TextDirectionHeuristic textDir,
                                       int offset, boolean trailing, boolean clamped) {
        int line = layout.getLineForOffset(offset);

        return getHorizontal(layout, textDir, offset, trailing, line, clamped);
    }

    // (EW) made static and added TextDirectionHeuristic parameter. added since it's private. to
    // avoid calling Layout#getLineStartPos (which is private and called into some methods that
    // could be overridden by child classes, which would be hard to replace accurately), we're
    // calling into Layout#getPrimaryHorizontal to have it call into the AOSP version of this and
    // then just modify the result for the trailing and clamped parameters we weren't able to pass
    // since they just shift the result of Layout#getLineStartPos, which is the result of this
    // method. we'll just match the rest of the method to be able to undo the base shift and shift
    // based on the passed parameters. note that Layout#getPrimaryHorizontal is public, so child
    // classes could override it, but at least as of Android 14, it isn't overridden in child
    // classes (BoringLayout,  DynamicLayout, and StaticLayout (none of which have children)).
    private static float getHorizontal(Layout layout, TextDirectionHeuristic textDir,
                                       int offset, boolean trailing, int line, boolean clamped) {
        float basePrimaryHorizontal = layout.getPrimaryHorizontal(offset);
        boolean baseTrailing = primaryIsTrailingPrevious(layout, textDir, offset);

        int start = layout.getLineStart(line);
        int end = layout.getLineEnd(line);
        int dir = layout.getParagraphDirection(line);
        boolean hasTab = layout.getLineContainsTab(line);
        Directions directions = getLineDirections(layout, textDir, line);

        TabStops tabStops = null;
        if (hasTab && layout.getText() instanceof Spanned) {
            // Just checking this line should be good enough, tabs should be
            // consistent across all lines in a paragraph.
            TabStopSpan[] tabs = getParagraphSpans((Spanned) layout.getText(), start, end,
                    TabStopSpan.class);
            if (tabs.length > 0) {
                tabStops = new TabStops(TAB_INCREMENT, tabs); // XXX should reuse
            }
        }

        TextLine tl = TextLine.obtain();
        tl.set(layout.getPaint(), layout.getText(), start, end, dir, directions, hasTab,
                tabStops, layout.getEllipsisStart(line),
                layout.getEllipsisStart(line) + layout.getEllipsisCount(line),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && layout.isFallbackLineSpacingEnabled());
        float baseWid = tl.measure(offset - start, baseTrailing, null, null);
        float wid = tl.measure(offset - start, trailing, null, null);
        TextLine.recycle(tl);

        if (clamped && wid > layout.getWidth()) {
            wid = layout.getWidth();
        }

        // remove the base wid that Layout#getPrimaryHorizontal (technically Layout#getHorizontal)
        // already added, and instead add the add the appropriate wid based on the trailing and
        // clamped parameters that we aren't allowed to provide it with.
        return basePrimaryHorizontal - baseWid + wid;
    }

    // (EW) wrapper for Layout#getLineBottom(int, boolean) since that wasn't available in older
    // versions
    public static int getLineBottom(Layout layout, int line, boolean includeLineSpacing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return layout.getLineBottom(line, includeLineSpacing);
        }
        //TODO: (EW) this method replaced getLineBottomWithoutSpacing, which wasn't added until Pie.
        // comparing where Pie used this in Editor to the alternative in Oreo MR1, 4/5 called
        // Layout#getLineBottom(line). the other called Layout#getLineTop(line + 1), but used the
        // result slightly different, so it may not have meant to be equivalent. simply using
        // Layout#getLineTop for older versions seems appropriate. we can't even use reflection to
        // access Layout#getLineBottomWithoutSpacing because it is a restricted API (warning logged
        // specifies "dark greylist"). at least as of S Layout#getLineBottomWithoutSpacing simply
        // returned getLineTop(line + 1) - getLineExtra(line), but Layout#getLineExtra is also a
        // restricted API (warning logged specifies "dark greylist"), just replicating that logic
        // with reflection isn't an option either. the logic from older versions won't always be
        // correct, but there doesn't seem to be much alternative. This probably should change, but
        // without knowing a specific issue this cause, I'm leaving it like this for now.
        return layout.getLineBottom(line);
    }

    private static int getOffsetAtStartOf(Layout layout, int offset) {
        // XXX this probably should skip local reorderings and
        // zero-width characters, look at callers
        if (offset == 0)
            return 0;

        CharSequence text = layout.getText();
        char c = text.charAt(offset);

        if (c >= '\uDC00' && c <= '\uDFFF') {
            char c1 = text.charAt(offset - 1);

            if (c1 >= '\uD800' && c1 <= '\uDBFF')
                offset -= 1;
        }

        if (text instanceof Spanned) {
            ReplacementSpan[] spans = ((Spanned) text).getSpans(offset, offset,
                                                       ReplacementSpan.class);

            for (int i = 0; i < spans.length; i++) {
                int start = ((Spanned) text).getSpanStart(spans[i]);
                int end = ((Spanned) text).getSpanEnd(spans[i]);

                if (start < offset && end > offset)
                    offset = start;
            }
        }

        return offset;
    }

    /**
     * Determine whether we should clamp cursor position. Currently it's
     * only robust for left-aligned displays.
     */
    public static boolean shouldClampCursor(Layout layout, int line) {
        // Only clamp cursor position in left-aligned displays.
        Layout.Alignment paragraphAlignment = layout.getParagraphAlignment(line);
        if (LayoutExtension.Alignment.isAlignLeft(paragraphAlignment)) {
            return true;
        }
        if (paragraphAlignment == Layout.Alignment.ALIGN_NORMAL) {
            return layout.getParagraphDirection(line) > 0;
        }
        return false;
    }

    // (EW) made static and added TextDirectionHeuristic parameter since it is hidden. it's private,
    // so there isn't risk of variable handling from child classes.
    private static void addSelection(Layout l, TextDirectionHeuristic textDir,
                                     int line, int start, int end, int top, int bottom,
                                     SelectionRectangleConsumer consumer) {
        int linestart = l.getLineStart(line);
        int lineend = l.getLineEnd(line);
        Directions dirs = getLineDirections(l, textDir, line);

        if (lineend > linestart && l.getText().charAt(lineend - 1) == '\n') {
            lineend--;
        }

        for (int i = 0; i < dirs.mDirections.length; i += 2) {
            int here = linestart + dirs.mDirections[i];
            int there = here + (dirs.mDirections[i + 1] & RUN_LENGTH_MASK);

            if (there > lineend) {
                there = lineend;
            }

            if (start <= there && end >= here) {
                int st = Math.max(start, here);
                int en = Math.min(end, there);

                if (st != en) {
                    float h1 = getHorizontal(l, textDir, st, false, line, false /* not clamped */);
                    float h2 = getHorizontal(l, textDir, en, true, line, false /* not clamped */);

                    float left = Math.min(h1, h2);
                    float right = Math.max(h1, h2);

                    final @TextSelectionLayout int layout =
                            ((dirs.mDirections[i + 1] & RUN_RTL_FLAG) != 0)
                                    ? TEXT_SELECTION_LAYOUT_RIGHT_TO_LEFT
                                    : TEXT_SELECTION_LAYOUT_LEFT_TO_RIGHT;

                    consumer.accept(left, top, right, bottom, layout);
                }
            }
        }
    }

    // (EW) made static and added TextDirectionHeuristic parameter since it is hidden. it was marked
    // final, so there isn't risk of variable handling from child classes.
    /**
     * Calculates the rectangles which should be highlighted to indicate a selection between start
     * and end and feeds them into the given {@link SelectionRectangleConsumer}.
     *
     * @param start    the starting index of the selection
     * @param end      the ending index of the selection
     * @param consumer the {@link SelectionRectangleConsumer} which will receive the generated
     *                 rectangles. It will be called every time a rectangle is generated.
     * @see Layout#getSelectionPath(int, int, Path)
     */
    public static void getSelection(Layout layout, TextDirectionHeuristic textDir,
                                    int start, int end, final SelectionRectangleConsumer consumer) {
        if (start == end) {
            return;
        }

        if (end < start) {
            int temp = end;
            end = start;
            start = temp;
        }

        final int startline = layout.getLineForOffset(start);
        final int endline = layout.getLineForOffset(end);

        int top = layout.getLineTop(startline);
        int bottom = getLineBottom(layout, endline, /* includeLineSpacing= */ false);

        if (startline == endline) {
            addSelection(layout, textDir, startline, start, end, top, bottom, consumer);
        } else {
            final float width = layout.getWidth();

            addSelection(layout, textDir, startline, start, layout.getLineEnd(startline),
                    top, layout.getLineBottom(startline), consumer);

            if (layout.getParagraphDirection(startline) == DIR_RIGHT_TO_LEFT) {
                consumer.accept(layout.getLineLeft(startline), top, 0,
                        layout.getLineBottom(startline), TEXT_SELECTION_LAYOUT_RIGHT_TO_LEFT);
            } else {
                consumer.accept(layout.getLineRight(startline), top, width,
                        layout.getLineBottom(startline), TEXT_SELECTION_LAYOUT_LEFT_TO_RIGHT);
            }

            for (int i = startline + 1; i < endline; i++) {
                top = layout.getLineTop(i);
                bottom = layout.getLineBottom(i);
                if (layout.getParagraphDirection(i) == DIR_RIGHT_TO_LEFT) {
                    consumer.accept(0, top, width, bottom, TEXT_SELECTION_LAYOUT_RIGHT_TO_LEFT);
                } else {
                    consumer.accept(0, top, width, bottom, TEXT_SELECTION_LAYOUT_LEFT_TO_RIGHT);
                }
            }

            top = layout.getLineTop(endline);
            bottom = getLineBottom(layout, endline, /* includeLineSpacing= */ false);

            addSelection(layout, textDir, endline, layout.getLineStart(endline), end, top, bottom,
                    consumer);

            if (layout.getParagraphDirection(endline) == DIR_RIGHT_TO_LEFT) {
                consumer.accept(width, top, layout.getLineRight(endline), bottom,
                        TEXT_SELECTION_LAYOUT_RIGHT_TO_LEFT);
            } else {
                consumer.accept(0, top, layout.getLineLeft(endline), bottom,
                        TEXT_SELECTION_LAYOUT_LEFT_TO_RIGHT);
            }
        }
    }

    private static float measurePara(TextPaint paint, CharSequence text, int start, int end,
            TextDirectionHeuristic textDir, boolean useBoundsForWidth) {
        MeasuredParagraph mt = null;
        TextLine tl = TextLine.obtain();
        try {
            mt = MeasuredParagraph.buildForBidi(text, start, end, textDir, mt);
            final char[] chars = mt.getChars();
            final int len = chars.length;
            final Directions directions = mt.getDirections(0, len);
            final int dir = mt.getParagraphDir();
            boolean hasTabs = false;
            TabStops tabStops = null;
            // leading margins should be taken into account when measuring a paragraph
            int margin = 0;
            if (text instanceof Spanned) {
                Spanned spanned = (Spanned) text;
                LeadingMarginSpan[] spans = getParagraphSpans(spanned, start, end,
                        LeadingMarginSpan.class);
                for (LeadingMarginSpan lms : spans) {
                    margin += lms.getLeadingMargin(true);
                }
            }
            for (int i = 0; i < len; ++i) {
                if (chars[i] == '\t') {
                    hasTabs = true;
                    if (text instanceof Spanned) {
                        Spanned spanned = (Spanned) text;
                        int spanEnd = spanned.nextSpanTransition(start, end,
                                TabStopSpan.class);
                        TabStopSpan[] spans = getParagraphSpans(spanned, start, spanEnd,
                                TabStopSpan.class);
                        if (spans.length > 0) {
                            tabStops = new TabStops(TAB_INCREMENT, spans);
                        }
                    }
                    break;
                }
            }
            tl.set(paint, text, start, end, dir, directions, hasTabs, tabStops,
                    0 /* ellipsisStart */, 0 /* ellipsisEnd */,
                    false /* use fallback line spacing. unused */);
            return margin + Math.abs(tl.metrics(null, null, useBoundsForWidth));
        } finally {
            TextLine.recycle(tl);
            if (mt != null) {
                mt.recycle();
            }
        }
    }

    public static class TabStops {
        private float[] mStops;
        private int mNumStops;
        private float mIncrement;

        public TabStops(float increment, Object[] spans) {
            reset(increment, spans);
        }

        void reset(float increment, Object[] spans) {
            this.mIncrement = increment;

            int ns = 0;
            if (spans != null) {
                float[] stops = this.mStops;
                for (Object o : spans) {
                    if (o instanceof TabStopSpan) {
                        if (stops == null) {
                            stops = new float[10];
                        } else if (ns == stops.length) {
                            float[] nstops = new float[ns * 2];
                            for (int i = 0; i < ns; ++i) {
                                nstops[i] = stops[i];
                            }
                            stops = nstops;
                        }
                        stops[ns++] = ((TabStopSpan) o).getTabStop();
                    }
                }
                if (ns > 1) {
                    Arrays.sort(stops, 0, ns);
                }
                if (stops != this.mStops) {
                    this.mStops = stops;
                }
            }
            this.mNumStops = ns;
        }

        float nextTab(float h) {
            int ns = this.mNumStops;
            if (ns > 0) {
                float[] stops = this.mStops;
                for (int i = 0; i < ns; ++i) {
                    float stop = stops[i];
                    if (stop > h) {
                        return stop;
                    }
                }
            }
            return nextDefaultStop(h, mIncrement);
        }

        /**
         * Returns the position of next tab stop.
         */
        public static float nextDefaultStop(float h, float inc) {
            return ((int) ((h + inc) / inc)) * inc;
        }
    }

    // (EW) copied since the AOSP version is package private
    /**
     * Returns the same as <code>text.getSpans()</code>, except where
     * <code>start</code> and <code>end</code> are the same and are not
     * at the very beginning of the text, in which case an empty array
     * is returned instead.
     * <p>
     * This is needed because of the special case that <code>getSpans()</code>
     * on an empty range returns the spans adjacent to that range, which is
     * primarily for the sake of <code>TextWatchers</code> so they will get
     * notifications when text goes from empty to non-empty.  But it also
     * has the unfortunate side effect that if the text ends with an empty
     * paragraph, that paragraph accidentally picks up the styles of the
     * preceding paragraph (even though those styles will not be picked up
     * by new text that is inserted into the empty paragraph).
     * <p>
     * The reason it just checks whether <code>start</code> and <code>end</code>
     * is the same is that the only time a line can contain 0 characters
     * is if it is the final paragraph of the Layout; otherwise any line will
     * contain at least one printing or newline character.  The reason for the
     * additional check if <code>start</code> is greater than 0 is that
     * if the empty paragraph is the entire content of the buffer, paragraph
     * styles that are already applied to the buffer will apply to text that
     * is inserted into it.
     */
    /* package */static <T> T[] getParagraphSpans(Spanned text, int start, int end, Class<T> type) {
        if (start == end && start > 0) {
            return ArrayUtils.emptyArray(type);
        }

        // (EW) starting in Nougat, the AOSP version called an overload on SpannableStringBuilder
        // with a sortByInsertionOrder (also added in Nougat) and passed false, but that overload is
        // hidden and regular one calls into the hidden one and passes true to it. prior to that,
        // the regular Spanned#getSpans(int, int, Class<T>) was called here the same for all types.
        // SpannableStringBuilder#getSpans(int, int, Class<T>) at that time didn't do the sorting,
        // so the change in Nougat didn't have a functional change here for the AOSP version, but
        // without access to that hidden overload there will be a functional change, but based on
        // how this function is used, I don't see a downstream impact from sorting. it might have
        // just skipped sorting for performance. we can try to fix this later if this turns out to
        // actually cause an issue from the sort.
        return text.getSpans(start, end, type);
    }

    /**
     * Stores information about bidirectional (left-to-right or right-to-left)
     * text within the layout of a line.
     */
    public static class Directions {
        /**
         * Directions represents directional runs within a line of text. Runs are pairs of ints
         * listed in visual order, starting from the leading margin.  The first int of each pair is
         * the offset from the first character of the line to the start of the run.  The second int
         * represents both the length and level of the run. The length is in the lower bits,
         * accessed by masking with RUN_LENGTH_MASK.  The level is in the higher bits, accessed by
         * shifting by RUN_LEVEL_SHIFT and masking by RUN_LEVEL_MASK. To simply test for an RTL
         * direction, test the bit using RUN_RTL_FLAG, if set then the direction is rtl.
         */
        public int[] mDirections;

        public Directions(int[] dirs) {
            mDirections = dirs;
        }

        /**
         * Returns number of BiDi runs.
         */
        public @IntRange(from = 0) int getRunCount() {
            return mDirections.length / 2;
        }

        /**
         * Returns the start offset of the BiDi run.
         *
         * @param runIndex the index of the BiDi run
         * @return the start offset of the BiDi run.
         */
        public @IntRange(from = 0) int getRunStart(@IntRange(from = 0) int runIndex) {
            return mDirections[runIndex * 2];
        }

        /**
         * Returns the length of the BiDi run.
         *
         * Note that this method may return too large number due to reducing the number of object
         * allocations. The too large number means the remaining part is assigned to this run. The
         * caller must clamp the returned value.
         *
         * @param runIndex the index of the BiDi run
         * @return the length of the BiDi run.
         */
        public @IntRange(from = 0) int getRunLength(@IntRange(from = 0) int runIndex) {
            return mDirections[runIndex * 2 + 1] & RUN_LENGTH_MASK;
        }

        /**
         * Returns the BiDi level of this run.
         *
         * @param runIndex the index of the BiDi run
         * @return the BiDi level of this run.
         */
        @IntRange(from = 0)
        public int getRunLevel(int runIndex) {
            return (mDirections[runIndex * 2 + 1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK;
        }

        /**
         * Returns true if the BiDi run is RTL.
         *
         * @param runIndex the index of the BiDi run
         * @return true if the BiDi run is RTL.
         */
        public boolean isRunRtl(int runIndex) {
            return (mDirections[runIndex * 2 + 1] & RUN_RTL_FLAG) != 0;
        }
    }

    @IntDef(value = {
            DIR_LEFT_TO_RIGHT,
            DIR_RIGHT_TO_LEFT
    })
    public @interface Direction {}

    /* package */ static final int DIR_REQUEST_LTR = 1;
    /* package */ static final int DIR_REQUEST_RTL = -1;
    /* package */ static final int DIR_REQUEST_DEFAULT_LTR = 2;
    /* package */ static final int DIR_REQUEST_DEFAULT_RTL = -2;

    /* package */ static final int RUN_LENGTH_MASK = 0x03ffffff;
    /* package */ static final int RUN_LEVEL_SHIFT = 26;
    /* package */ static final int RUN_LEVEL_MASK = 0x3f;
    /* package */ static final int RUN_RTL_FLAG = 1 << RUN_LEVEL_SHIFT;

    // (EW) custom wrapper to try to get the hidden Layout.Alignment enum values, but it's possible
    // that those values could change or be removed in future versions, so there is a fallback, but
    // it still probably isn't right, but it should at least not completely break. if that change
    // does happen, this will need to be updated.
    public static class Alignment {
        private static final int ALIGN_LEFT_INDEX = 3;
        private static final String ALIGN_LEFT_NAME = "ALIGN_LEFT";
        private static final int ALIGN_RIGHT_INDEX = 4;
        private static final String ALIGN_RIGHT_NAME = "ALIGN_RIGHT";

        /**
         * this should only be used for assignment since it's accessing a hidden enum value that
         * might not exist and falls back to returning ALIGN_NORMAL. to compare, check
         * {@link #isAlignLeft}.
         */
        public static final Layout.Alignment ALIGN_LEFT;
        /**
         * this should only be used for assignment since it's accessing a hidden enum value that
         * might not exist and falls back to returning ALIGN_OPPOSITE. to compare, check
         * {@link #isAlignRight}.
         */
        public static final Layout.Alignment ALIGN_RIGHT;
        private static final boolean CAN_USE_HIDDEN_ALIGNMENTS;
        static {
            final Layout.Alignment[] allEnums = Layout.Alignment.values();
            // I'm not sure if there's a way to dynamically get this at runtime since being hidden
            // seems to only be a compile time thing.
            Layout.Alignment[] expectedAccessibleEnums = new Layout.Alignment[] {
                    Layout.Alignment.ALIGN_NORMAL,
                    Layout.Alignment.ALIGN_CENTER,
                    Layout.Alignment.ALIGN_OPPOSITE
            };
            int[] expectedHiddenEnumIndices = new int[] { ALIGN_LEFT_INDEX, ALIGN_RIGHT_INDEX };
            String[] expectedHiddenEnumNames = new String[] { ALIGN_LEFT_NAME, ALIGN_RIGHT_NAME };
            Layout.Alignment[] expectedHiddenEnums =
                    new Layout.Alignment[expectedHiddenEnumIndices.length];
            if (allEnums != null) {
                final boolean[] allEnumsAccessibleFlag = new boolean[allEnums.length];
                for (Layout.Alignment accessibleEnum : expectedAccessibleEnums) {
                    if (accessibleEnum.ordinal() >= allEnums.length) {
                        // this probably shouldn't happen
                        continue;
                    }
                    allEnumsAccessibleFlag[accessibleEnum.ordinal()] = true;
                }
                for (int allEnumsIndex = 0; allEnumsIndex < allEnums.length; allEnumsIndex++) {
                    if (allEnumsAccessibleFlag[allEnumsIndex]) {
                        // skip accessible enums
                        continue;
                    }
                    for (int expectedHiddenEnumsIndex = 0;
                         expectedHiddenEnumsIndex < expectedHiddenEnumIndices.length;
                         expectedHiddenEnumsIndex++) {

                        boolean currentAllEnumNameSame =
                                expectedHiddenEnumNames[expectedHiddenEnumsIndex].equals(
                                        allEnums[allEnumsIndex].name());
                        boolean alreadyFoundEnum;
                        boolean alreadyFoundEnumNameSame;
                        if (expectedHiddenEnums[expectedHiddenEnumsIndex] != null) {
                            alreadyFoundEnum = true;
                            alreadyFoundEnumNameSame =
                                    expectedHiddenEnumNames[expectedHiddenEnumsIndex].equals(
                                            expectedHiddenEnums[expectedHiddenEnumsIndex].name());
                        } else {
                            alreadyFoundEnum = false;
                            alreadyFoundEnumNameSame = false;
                        }
                        // look for the matching enum based on the ordinal and name and prioritizing
                        // matching on the name. the name could easily change, so don't require that
                        // for matching, but it's unlikely for a new enum to take that same name.
                        if ((allEnumsIndex == expectedHiddenEnumIndices[expectedHiddenEnumsIndex]
                                        && (!alreadyFoundEnum || currentAllEnumNameSame))
                                || (currentAllEnumNameSame
                                        && (!alreadyFoundEnum || !alreadyFoundEnumNameSame))) {
                            expectedHiddenEnums[expectedHiddenEnumsIndex] = allEnums[allEnumsIndex];
                        }
                    }
                }
            }

            // only use the hidden enums if both were found. if only one was found something weird
            // happened, so we can't be sure what we found was actually right or how to handle one
            // if the pair doesn't exist.
            if (expectedHiddenEnums[0] != null && expectedHiddenEnums[1] != null
                    && expectedHiddenEnums[0] != expectedHiddenEnums[1]) {
                CAN_USE_HIDDEN_ALIGNMENTS = true;
                ALIGN_LEFT = expectedHiddenEnums[0];
                ALIGN_RIGHT = expectedHiddenEnums[1];
            } else {
                Log.w(TAG, "Alignment: ALIGN_LEFT and ALIGN_RIGHT couldn't be found");
                CAN_USE_HIDDEN_ALIGNMENTS = false;
                ALIGN_LEFT = Layout.Alignment.ALIGN_NORMAL;
                ALIGN_RIGHT = Layout.Alignment.ALIGN_OPPOSITE;
            }
        }

        public static boolean isAlignLeft(Layout.Alignment alignment) {
            if (!CAN_USE_HIDDEN_ALIGNMENTS) {
                return false;
            }
            return alignment == ALIGN_LEFT;
        }

        public static boolean isAlignRight(Layout.Alignment alignment) {
            if (!CAN_USE_HIDDEN_ALIGNMENTS) {
                return false;
            }
            return alignment == ALIGN_RIGHT;
        }
    }

    private static final float TAB_INCREMENT = 20;

    public static final Directions DIRS_ALL_LEFT_TO_RIGHT =
            new Directions(new int[] { 0, RUN_LENGTH_MASK });

    public static final Directions DIRS_ALL_RIGHT_TO_LEFT =
            new Directions(new int[] { 0, RUN_LENGTH_MASK | RUN_RTL_FLAG });

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            TEXT_SELECTION_LAYOUT_RIGHT_TO_LEFT,
            TEXT_SELECTION_LAYOUT_LEFT_TO_RIGHT
    })
    public @interface TextSelectionLayout {}

    /** @hide */
    public static final int TEXT_SELECTION_LAYOUT_RIGHT_TO_LEFT = 0;
    /** @hide */
    public static final int TEXT_SELECTION_LAYOUT_LEFT_TO_RIGHT = 1;

    /** @hide */
    @FunctionalInterface
    public interface SelectionRectangleConsumer {
        /**
         * Performs this operation on the given rectangle.
         *
         * @param left   the left edge of the rectangle
         * @param top    the top edge of the rectangle
         * @param right  the right edge of the rectangle
         * @param bottom the bottom edge of the rectangle
         * @param textSelectionLayout the layout (RTL or LTR) of the text covered by this
         *                            selection rectangle
         */
        void accept(float left, float top, float right, float bottom,
                @TextSelectionLayout int textSelectionLayout);
    }
}
