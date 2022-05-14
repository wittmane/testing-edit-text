package com.wittmane.testingedittext.aosp.text;

import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextDirectionHeuristic;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.LeadingMarginSpan;
import android.text.style.TabStopSpan;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;

import com.wittmane.testingedittext.aosp.internal.util.ArrayUtils;

import java.util.Arrays;

/**
 * (EW) content from Layout that is blocked for app developers to access
 */
public class HiddenLayout {
    /**
     * Return how wide a layout must be in order to display the
     * specified text slice with one line per paragraph.
     *
     * If the measured width exceeds given limit, returns limit value instead.
     * @hide
     */
    public static float getDesiredWidthWithLimit(CharSequence source, int start, int end,
                                                 TextPaint paint, TextDirectionHeuristic textDir,
                                                 float upperLimit) {
        float need = 0;

        int next;
        for (int i = start; i <= end; i = next) {
            next = TextUtils.indexOf(source, '\n', i, end);

            if (next < 0)
                next = end;

            // note, omits trailing paragraph char
            float w = measurePara(paint, source, i, next, textDir);
            if (w > upperLimit) {
                return upperLimit;
            }

            if (w > need)
                need = w;

            next++;
        }

        return need;
    }

    private static float measurePara(TextPaint paint, CharSequence text, int start, int end,
            TextDirectionHeuristic textDir) {
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
                    0 /* ellipsisStart */, 0 /* ellipsisEnd */);
            return margin + Math.abs(tl.metrics(null));
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

        if(text instanceof SpannableStringBuilder) {
            //TODO: (EW) the overload with sortByInsertionOrder is hidden, and the alternative
            // passes true to it. I'm not sure if sorting will cause problems. verify
            return ((SpannableStringBuilder) text).getSpans(start, end, type/*, false*/);
        } else {
            return text.getSpans(start, end, type);
        }
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
         * @hide
         */
        public int[] mDirections;

        /**
         * @hide
         */
        public Directions(int[] dirs) {
            mDirections = dirs;
        }

        /**
         * Returns number of BiDi runs.
         *
         * @hide
         */
        public @IntRange(from = 0) int getRunCount() {
            return mDirections.length / 2;
        }

        /**
         * Returns the start offset of the BiDi run.
         *
         * @param runIndex the index of the BiDi run
         * @return the start offset of the BiDi run.
         * @hide
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
         * @hide
         */
        public @IntRange(from = 0) int getRunLength(@IntRange(from = 0) int runIndex) {
            return mDirections[runIndex * 2 + 1] & RUN_LENGTH_MASK;
        }

        /**
         * Returns true if the BiDi run is RTL.
         *
         * @param runIndex the index of the BiDi run
         * @return true if the BiDi run is RTL.
         * @hide
         */
        public boolean isRunRtl(int runIndex) {
            return (mDirections[runIndex * 2 + 1] & RUN_RTL_FLAG) != 0;
        }
    }

    @IntDef(/*prefix = { "DIR_" }, */value = {
            Layout.DIR_LEFT_TO_RIGHT,
            Layout.DIR_RIGHT_TO_LEFT
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

    public enum Alignment {
        ALIGN_NORMAL,
        ALIGN_OPPOSITE,
        ALIGN_CENTER,
        /** @hide */
        ALIGN_LEFT,
        /** @hide */
        ALIGN_RIGHT,
    }
    //TODO: (EW) this is rather hacky and fragile. is there something that could be done instead?
    // if we do keep this, this might be fine for assignment but comparisons might be a problem if
    // these hidden values ever get removed (make sure to check the fallback enum first). maybe try
    // something more safe in case new values are added before these hidden ones.
    // also, maybe put these in an Alignment wrapper class so the calls look more similar
    public static final Layout.Alignment ALIGNMENT_ALIGN_LEFT =
            getAlignment(3, Layout.Alignment.ALIGN_NORMAL);
    public static final Layout.Alignment ALIGNMENT_ALIGN_RIGHT =
            getAlignment(4, Layout.Alignment.ALIGN_OPPOSITE);
    private static Layout.Alignment getAlignment(final int index, final Layout.Alignment fallback) {
        final Layout.Alignment[] enums = Layout.Alignment.class.getEnumConstants();
        return enums != null && enums.length > index ? enums[index] : fallback;
    }

    private static final float TAB_INCREMENT = 20;

    /** @hide */
    public static final Directions DIRS_ALL_LEFT_TO_RIGHT =
            new Directions(new int[] { 0, RUN_LENGTH_MASK });

    /** @hide */
    public static final Directions DIRS_ALL_RIGHT_TO_LEFT =
            new Directions(new int[] { 0, RUN_LENGTH_MASK | RUN_RTL_FLAG });
}
