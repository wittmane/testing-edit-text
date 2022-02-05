//package com.wittmane.testingedittext;
//
//import android.text.Layout;
//import android.text.Spanned;
//import android.text.TextDirectionHeuristic;
//import android.text.TextPaint;
//import android.text.TextUtils;
//import android.text.style.LeadingMarginSpan;
//import android.text.style.TabStopSpan;
//
//import androidx.annotation.IntRange;
//
//import java.lang.annotation.Retention;
//import java.lang.annotation.RetentionPolicy;
//import java.lang.reflect.Array;
//import java.util.Arrays;
//
//public class HiddenLayoutInfo {
//    public static final int DIR_LEFT_TO_RIGHT = 1;
//    public static final int DIR_RIGHT_TO_LEFT = -1;
//
//    /* package */ static final int DIR_REQUEST_LTR = 1;
//    /* package */ static final int DIR_REQUEST_RTL = -1;
//    /* package */ static final int DIR_REQUEST_DEFAULT_LTR = 2;
//    /* package */ static final int DIR_REQUEST_DEFAULT_RTL = -2;
//    /* package */ static final int RUN_LENGTH_MASK = 0x03ffffff;
//    /* package */ static final int RUN_LEVEL_SHIFT = 26;
//    /* package */ static final int RUN_LEVEL_MASK = 0x3f;
//    /* package */ static final int RUN_RTL_FLAG = 1 << RUN_LEVEL_SHIFT;
//
//    public static final Directions DIRS_ALL_LEFT_TO_RIGHT =
//            new Directions(new int[] { 0, RUN_LENGTH_MASK });
//
//    public static final Directions DIRS_ALL_RIGHT_TO_LEFT =
//            new Directions(new int[] { 0, RUN_LENGTH_MASK | RUN_RTL_FLAG });
//
//    @Retention(RetentionPolicy.SOURCE)
//    public @interface Direction {}
//
//    public static float getDesiredWidthWithLimit(CharSequence source, int start, int end,
//                                                 TextPaint paint, TextDirectionHeuristic textDir, float upperLimit) {
//        float need = 0;
//
//        int next;
//        for (int i = start; i <= end; i = next) {
//            next = TextUtils.indexOf(source, '\n', i, end);
//
//            if (next < 0)
//                next = end;
//
//            // note, omits trailing paragraph char
//            float w = measurePara(paint, source, i, next, textDir);
//            if (w > upperLimit) {
//                return upperLimit;
//            }
//
//            if (w > need)
//                need = w;
//
//            next++;
//        }
//
//        return need;
//    }
//
//
//    private static final float TAB_INCREMENT = 20;
//
//    private static float measurePara(TextPaint paint, CharSequence text, int start, int end,
//                                     TextDirectionHeuristic textDir) {
//        MeasuredParagraph mt = null;
//        TextLine tl = TextLine.obtain();
//        try {
//            mt = MeasuredParagraph.buildForBidi(text, start, end, textDir, mt);
//            final char[] chars = mt.getChars();
//            final int len = chars.length;
//            final Layout.Directions directions = mt.getDirections(0, len);
//            final int dir = mt.getParagraphDir();
//            boolean hasTabs = false;
//            TabStops tabStops = null;
//            // leading margins should be taken into account when measuring a paragraph
//            int margin = 0;
//            if (text instanceof Spanned) {
//                Spanned spanned = (Spanned) text;
//                LeadingMarginSpan[] spans = getParagraphSpans(spanned, start, end,
//                        LeadingMarginSpan.class);
//                for (LeadingMarginSpan lms : spans) {
//                    margin += lms.getLeadingMargin(true);
//                }
//            }
//            for (int i = 0; i < len; ++i) {
//                if (chars[i] == '\t') {
//                    hasTabs = true;
//                    if (text instanceof Spanned) {
//                        Spanned spanned = (Spanned) text;
//                        int spanEnd = spanned.nextSpanTransition(start, end,
//                                TabStopSpan.class);
//                        TabStopSpan[] spans = getParagraphSpans(spanned, start, spanEnd,
//                                TabStopSpan.class);
//                        if (spans.length > 0) {
//                            tabStops = new TabStops(TAB_INCREMENT, spans);
//                        }
//                    }
//                    break;
//                }
//            }
//            tl.set(paint, text, start, end, dir, directions, hasTabs, tabStops,
//                    0 /* ellipsisStart */, 0 /* ellipsisEnd */);
//            return margin + Math.abs(tl.metrics(null));
//        } finally {
//            TextLine.recycle(tl);
//            if (mt != null) {
//                mt.recycle();
//            }
//        }
//    }
//
//    static <T> T[] getParagraphSpans(Spanned text, int start, int end, Class<T> type) {
//        if (start == end && start > 0) {
////            return ArrayUtils.emptyArray(type);
//            return (T[]) Array.newInstance(type, 0);
//        }
//
////        if(text instanceof SpannableStringBuilder) {
////            return ((SpannableStringBuilder) text).getSpans(start, end, type, false);
////        } else {
//        return text.getSpans(start, end, type);
////        }
//    }
//
//    public static class TabStops {
//        private float[] mStops;
//        private int mNumStops;
//        private float mIncrement;
//
//        public TabStops(float increment, Object[] spans) {
//            reset(increment, spans);
//        }
//
//        void reset(float increment, Object[] spans) {
//            this.mIncrement = increment;
//
//            int ns = 0;
//            if (spans != null) {
//                float[] stops = this.mStops;
//                for (Object o : spans) {
//                    if (o instanceof TabStopSpan) {
//                        if (stops == null) {
//                            stops = new float[10];
//                        } else if (ns == stops.length) {
//                            float[] nstops = new float[ns * 2];
//                            for (int i = 0; i < ns; ++i) {
//                                nstops[i] = stops[i];
//                            }
//                            stops = nstops;
//                        }
//                        stops[ns++] = ((TabStopSpan) o).getTabStop();
//                    }
//                }
//                if (ns > 1) {
//                    Arrays.sort(stops, 0, ns);
//                }
//                if (stops != this.mStops) {
//                    this.mStops = stops;
//                }
//            }
//            this.mNumStops = ns;
//        }
//
//        float nextTab(float h) {
//            int ns = this.mNumStops;
//            if (ns > 0) {
//                float[] stops = this.mStops;
//                for (int i = 0; i < ns; ++i) {
//                    float stop = stops[i];
//                    if (stop > h) {
//                        return stop;
//                    }
//                }
//            }
//            return nextDefaultStop(h, mIncrement);
//        }
//
//        /**
//         * Returns the position of next tab stop.
//         */
//        public static float nextDefaultStop(float h, float inc) {
//            return ((int) ((h + inc) / inc)) * inc;
//        }
//    }
//
//    /**
//     * Stores information about bidirectional (left-to-right or right-to-left)
//     * text within the layout of a line.
//     */
//    public static class Directions {
//        /**
//         * Directions represents directional runs within a line of text. Runs are pairs of ints
//         * listed in visual order, starting from the leading margin.  The first int of each pair is
//         * the offset from the first character of the line to the start of the run.  The second int
//         * represents both the length and level of the run. The length is in the lower bits,
//         * accessed by masking with RUN_LENGTH_MASK.  The level is in the higher bits, accessed by
//         * shifting by RUN_LEVEL_SHIFT and masking by RUN_LEVEL_MASK. To simply test for an RTL
//         * direction, test the bit using RUN_RTL_FLAG, if set then the direction is rtl.
//         * @hide
//         */
////        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
//        public int[] mDirections;
//
//        /**
//         * @hide
//         */
////        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
//        public Directions(int[] dirs) {
//            mDirections = dirs;
//        }
//
//        /**
//         * Returns number of BiDi runs.
//         *
//         * @hide
//         */
//        public @IntRange(from = 0) int getRunCount() {
//            return mDirections.length / 2;
//        }
//
//        /**
//         * Returns the start offset of the BiDi run.
//         *
//         * @param runIndex the index of the BiDi run
//         * @return the start offset of the BiDi run.
//         * @hide
//         */
//        public @IntRange(from = 0) int getRunStart(@IntRange(from = 0) int runIndex) {
//            return mDirections[runIndex * 2];
//        }
//
//        /**
//         * Returns the length of the BiDi run.
//         *
//         * Note that this method may return too large number due to reducing the number of object
//         * allocations. The too large number means the remaining part is assigned to this run. The
//         * caller must clamp the returned value.
//         *
//         * @param runIndex the index of the BiDi run
//         * @return the length of the BiDi run.
//         * @hide
//         */
//        public @IntRange(from = 0) int getRunLength(@IntRange(from = 0) int runIndex) {
//            return mDirections[runIndex * 2 + 1] & RUN_LENGTH_MASK;
//        }
//
//        /**
//         * Returns true if the BiDi run is RTL.
//         *
//         * @param runIndex the index of the BiDi run
//         * @return true if the BiDi run is RTL.
//         * @hide
//         */
//        public boolean isRunRtl(int runIndex) {
//            return (mDirections[runIndex * 2 + 1] & RUN_RTL_FLAG) != 0;
//        }
//    }
//}
