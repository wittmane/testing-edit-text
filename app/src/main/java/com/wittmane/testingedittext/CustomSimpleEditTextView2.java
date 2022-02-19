package com.wittmane.testingedittext;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.LocaleList;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.method.DateKeyListener;
import android.text.method.DateTimeKeyListener;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.MovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TimeKeyListener;
import android.text.method.TransformationMethod;
import android.text.style.LeadingMarginSpan;
import android.text.style.TabStopSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Scroller;

import androidx.annotation.Nullable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Locale;

// modified from https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release
public class CustomSimpleEditTextView2 extends View implements ICustomTextView {
    static final String TAG = CustomSimpleEditTextView2.class.getSimpleName();

    private int textColor = Color.BLACK;

    private int textSize = sp(/*14*/18);

    private TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private Paint mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String defaultText = "A";

    private Editable mText;
    private CharSequence mHint = "default hint";
    private Layout mLayout;
    private Layout mHintLayout;
    private TextUtils.TruncateAt mEllipsize = TextUtils.TruncateAt.END;
    private TextDirectionHeuristic mTextDir = TextDirectionHeuristics.LTR;
    private BoringLayout.Metrics mBoring;
    private BoringLayout.Metrics mHintBoring;
    private @Nullable Spannable mSpannable;
    private CharSequence mTransformed = "";
    private TransformationMethod mTransformation;
    private boolean mAllowTransformationLengthChange;
    private static final int LINES = 1;
    private static final int EMS = LINES;
    private static final int PIXELS = 2;
    private int mMaxWidth = Integer.MAX_VALUE;
    private int mMaxWidthMode = PIXELS;
    private int mMinWidth = 0;
    private int mMinWidthMode = PIXELS;
    private float mSpacingMult = 1.0f;//TextView_lineSpacingMultiplier
    private float mSpacingAdd = 0.0f;//TextView_lineSpacingExtra
    private boolean mHorizontallyScrolling = false;//TextView_scrollHorizontally or applySingleLine
    static final int VERY_WIDE = 1024 * 1024; // XXX should be much larger
    private int mMaximum = Integer.MAX_VALUE;
    private int mMaxMode = LINES;
    private int mMinimum = 0;
    private int mMinMode = LINES;
    private int mOldMaximum = mMaximum;
    private int mOldMaxMode = mMaxMode;
    private int mDesiredHeightAtMeasure = -1;
    private boolean mIncludePad = true;//TextView_includeFontPadding
    public static final BoringLayout.Metrics UNKNOWN_BORING = new BoringLayout.Metrics();
    private static final int DEFAULT_TYPEFACE = -1;
    private static final int SANS = 1;
    private static final int SERIF = 2;
    private static final int MONOSPACE = 3;
    // True if internationalized input should be used for numbers and date and time.
    private final boolean mUseInternationalizedInput;
    private boolean mSingleLine;
    private Scroller mScroller;
    private int mGravity = Gravity.TOP | Gravity.START;

    public CustomSimpleEditTextView2(Context context) {
        this(context, null);
    }
    public CustomSimpleEditTextView2(Context context, AttributeSet attrs) {
        this(context, attrs, /*com.android.internal.*/R.attr.editTextStyle);
//        this(context, attrs, 0);
    }
    public CustomSimpleEditTextView2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);

        int inputType = EditorInfo.TYPE_NULL;
        boolean singleLine = false;
//        final TextAppearanceAttributes attributes = new TextAppearanceAttributes();

        final Resources.Theme theme = context.getTheme();
        TypedArray typedArray = theme.obtainStyledAttributes(
                attrs, R.styleable.CustomSimpleTextView2, defStyleAttr, 0);

        for (int i = 0; i < typedArray.getIndexCount(); i++) {
            int attr = typedArray.getIndex(i);
            if (attr == R.styleable.CustomSimpleTextView2_android_text) {
                CharSequence text = typedArray.getText(attr);
                if (text != null) {
                    defaultText = text.toString();
                }
            } else if (attr == R.styleable.CustomSimpleTextView2_android_inputType) {
                inputType = typedArray.getInt(attr, EditorInfo.TYPE_NULL);
            } else if (attr == R.styleable.CustomSimpleTextView2_android_gravity) {
                setGravity(typedArray.getInt(attr, -1));
            }
        }

        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        final boolean passwordInputType = variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        final boolean webPasswordInputType = variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        final boolean numberPasswordInputType = variation
                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
        final int targetSdkVersion = context.getApplicationInfo().targetSdkVersion;
        mUseInternationalizedInput = targetSdkVersion >= Build.VERSION_CODES.O;
        if (inputType != EditorInfo.TYPE_NULL) {
            setInputType(inputType, true);
            // If set, the input type overrides what was set using the deprecated singleLine flag.
            singleLine = !isMultilineInputType(inputType);
        }
//        if (mEditor != null) {
//            mEditor.adjustInputType(/*password, */passwordInputType, webPasswordInputType,
//                    numberPasswordInputType);
//        }
        // Same as setSingleLine(), but make sure the transformation method and the maximum number
        // of lines of height are unchanged for multi-line TextViews.
        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, singleLine, singleLine);
        final boolean isPassword = /*password || */passwordInputType || webPasswordInputType
                || numberPasswordInputType;
//        final boolean isMonospaceEnforced = isPassword || (mEditor != null
//                && (mEditor.mInputType
//                & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION))
//                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD));
//        if (isMonospaceEnforced) {
//            attributes.mTypefaceIndex = MONOSPACE;
//        }
//        applyTextAppearance(attributes);
        if (isPassword) {
            setTransformationMethod(PasswordTransformationMethod.getInstance());
        }

        setText(defaultText);

        setEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.w(TAG, "getFocusable=" + getFocusable());
        }
        Log.w(TAG, "isClickable=" + isClickable());
    }

    private void init(Context context) {
        mTextPaint.setColor(textColor);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLayout == null) {
            assumeLayout();
        }
        mLayout.draw(canvas, null, mHighlightPaint, 0);
    }

    //TODO: (EW) this seems to get called in sets of 3 - see if I'm doing anything wrong
//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        Log.w(TAG, "onMeasure: widthMeasureSpec: spec=" + widthMeasureSpec
//                + ", mode=" + MeasureSpec.getMode(widthMeasureSpec)
//                + ", size=" + MeasureSpec.getSize(widthMeasureSpec)
//                + ", string=" + MeasureSpec.toString(widthMeasureSpec));
//        Log.w(TAG, "onMeasure: heightMeasureSpec: spec=" + heightMeasureSpec
//                + ", mode=" + MeasureSpec.getMode(heightMeasureSpec)
//                + ", size=" + MeasureSpec.getSize(heightMeasureSpec)
//                + ", string=" + MeasureSpec.toString(heightMeasureSpec));
//        ViewGroup.LayoutParams layoutParams = getLayoutParams();
//        Log.w(TAG, "onMeasure: layoutParams.width=" + layoutParams.width
//                + ", layoutParams.height=" + layoutParams.height);
//        Log.w(TAG, "onMeasure: text=\"" + mText.toString() + "\"");
//
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
////        setMeasuredDimension(backgroundRectWidth + borderWidthLeft + borderWidthRight,
////                backgroundRectHeight + borderWidthTop + borderWidthBottom);
//
//
//        Log.w(TAG, "onMeasure: paddingLeft=" + getPaddingLeft());
//        final int measuredWidth;
//        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
//            measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
//        } else {
//            //TODO: need to handle splitting to multiple lines
//            int textWidth = (int)Math.ceil(textPaint.measureText(/*defaultText*/mText.toString())) + getPaddingLeft() + getPaddingRight();
//            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
//                measuredWidth = Math.min(textWidth, MeasureSpec.getSize(widthMeasureSpec));
//            } else {
//                measuredWidth = textWidth;
//            }
//        }
//        final int measuredHeight;
//        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
//            measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
//        } else {
//            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
//            //TODO: need to handle multiple lines
//            int textHeight = (int) Math.ceil(fontMetrics.descent - fontMetrics.ascent) + getPaddingTop() + getPaddingBottom();
//            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
//                measuredHeight = Math.min(textHeight, MeasureSpec.getSize(heightMeasureSpec));
//            } else {
//                measuredHeight = textHeight;
//            }
//        }
//        setMeasuredDimension(measuredWidth, measuredHeight);
//    }
    private MovementMethod mMovement;
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.w(TAG, "onMeasure: widthMeasureSpec: spec=" + widthMeasureSpec
                + ", mode=" + MeasureSpec.getMode(widthMeasureSpec)
                + ", size=" + MeasureSpec.getSize(widthMeasureSpec)
                + ", string=" + MeasureSpec.toString(widthMeasureSpec));
        Log.w(TAG, "onMeasure: heightMeasureSpec: spec=" + heightMeasureSpec
                + ", mode=" + MeasureSpec.getMode(heightMeasureSpec)
                + ", size=" + MeasureSpec.getSize(heightMeasureSpec)
                + ", string=" + MeasureSpec.toString(heightMeasureSpec));
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        Log.w(TAG, "onMeasure: layoutParams.width=" + layoutParams.width
                + ", layoutParams.height=" + layoutParams.height);
        Log.w(TAG, "onMeasure: text=\"" + mText.toString() + "\"");


        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        BoringLayout.Metrics boring = UNKNOWN_BORING;
        BoringLayout.Metrics hintBoring = UNKNOWN_BORING;

        if (mTextDir == null) {
            mTextDir = /*getTextDirectionHeuristic()*/TextDirectionHeuristics.LTR;
        }

        int des = -1;
        boolean fromexisting = false;
        final float widthLimit = (widthMode == MeasureSpec.AT_MOST)
                ?  (float) widthSize : Float.MAX_VALUE;

        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            width = widthSize;
        } else {
            if (mLayout != null && mEllipsize == null) {
                des = desired(mLayout);
            }

            if (des < 0) {
                boring = BoringLayout.isBoring(/*mTransformed*/mText, mTextPaint/*, mTextDir*/, mBoring);
                if (boring != null) {
                    mBoring = boring;
                }
            } else {
                fromexisting = true;
            }

            if (boring == null || boring == UNKNOWN_BORING) {
                if (des < 0) {
//                    des = (int) Math.ceil(/*Layout*/HiddenLayoutInfo.getDesiredWidthWithLimit(mTransformed, 0,
//                            mTransformed.length(), mTextPaint, mTextDir, widthLimit));
                    des = (int) /*FloatMath*/Math.ceil(Layout.getDesiredWidth(/*mTransformed*/mText, mTextPaint));
                }
                width = des;
            } else {
                width = boring.width;
            }

//            final Drawables dr = mDrawables;
//            if (dr != null) {
//                width = Math.max(width, dr.mDrawableWidthTop);
//                width = Math.max(width, dr.mDrawableWidthBottom);
//            }

            if (mHint != null) {
                int hintDes = -1;
                int hintWidth;

                if (mHintLayout != null && mEllipsize == null) {
                    hintDes = desired(mHintLayout);
                }

                if (hintDes < 0) {
                    hintBoring = BoringLayout.isBoring(mHint, mTextPaint/*, mTextDir*/, mHintBoring);
                    if (hintBoring != null) {
                        mHintBoring = hintBoring;
                    }
                }

                if (hintBoring == null || hintBoring == UNKNOWN_BORING) {
                    if (hintDes < 0) {
//                        hintDes = (int) Math.ceil(/*Layout*/HiddenLayoutInfo.getDesiredWidthWithLimit(mHint, 0,
//                                mHint.length(), mTextPaint, mTextDir, widthLimit));
                        hintDes = (int) /*FloatMath*/Math.ceil(Layout.getDesiredWidth(mHint, mTextPaint));
                    }
                    hintWidth = hintDes;
                } else {
                    hintWidth = hintBoring.width;
                }

                if (hintWidth > width) {
                    width = hintWidth;
                }
            }

            width += getCompoundPaddingLeft() + getCompoundPaddingRight();

            if (mMaxWidthMode == EMS) {
                width = Math.min(width, mMaxWidth * getLineHeight());
            } else {
                width = Math.min(width, mMaxWidth);
            }

            if (mMinWidthMode == EMS) {
                width = Math.max(width, mMinWidth * getLineHeight());
            } else {
                width = Math.max(width, mMinWidth);
            }

            // Check against our minimum width
            width = Math.max(width, getSuggestedMinimumWidth());

            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(widthSize, width);
            }
        }

        int want = width - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int unpaddedWidth = want;

        if (mHorizontallyScrolling) want = VERY_WIDE;

        int hintWant = want;
        int hintWidth = (mHintLayout == null) ? hintWant : mHintLayout.getWidth();

        if (mLayout == null) {
            makeNewLayout(want, hintWant, boring, hintBoring,
                    width - getCompoundPaddingLeft() - getCompoundPaddingRight(), false);
        } else {
            final boolean layoutChanged = (mLayout.getWidth() != want) || (hintWidth != hintWant)
                    || (mLayout.getEllipsizedWidth()
                    != width - getCompoundPaddingLeft() - getCompoundPaddingRight());

            final boolean widthChanged = (mHint == null) && (mEllipsize == null)
                    && (want > mLayout.getWidth())
                    && (mLayout instanceof BoringLayout
                    || (fromexisting && des >= 0 && des <= want));

            final boolean maximumChanged = (mMaxMode != mOldMaxMode) || (mMaximum != mOldMaximum);

            if (layoutChanged || maximumChanged) {
                if (!maximumChanged && widthChanged) {
                    mLayout.increaseWidthTo(want);
                } else {
                    makeNewLayout(want, hintWant, boring, hintBoring,
                            width - getCompoundPaddingLeft() - getCompoundPaddingRight(), false);
                }
            } else {
                // Nothing has changed
            }
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            height = heightSize;
            mDesiredHeightAtMeasure = -1;
        } else {
            int desired = getDesiredHeight();

            height = desired;
            mDesiredHeightAtMeasure = desired;

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(desired, heightSize);
            }
        }

        int unpaddedHeight = height - getCompoundPaddingTop() - getCompoundPaddingBottom();
        if (mMaxMode == LINES && mLayout.getLineCount() > mMaximum) {
            unpaddedHeight = Math.min(unpaddedHeight, mLayout.getLineTop(mMaximum));
        }

        /*
         * We didn't let makeNewLayout() register to bring the cursor into view,
         * so do it here if there is any possibility that it is needed.
         */
        if (mMovement != null
                || mLayout.getWidth() > unpaddedWidth
                || mLayout.getHeight() > unpaddedHeight) {
//            registerForPreDraw();
        } else {
//            scrollTo(0, 0);
        }

        setMeasuredDimension(width, height);
        Log.w(TAG, "onMeasure: setMeasuredDimension: width=" + width + ", height=" + height);
    }
    private static int desired(Layout layout) {
        int n = layout.getLineCount();
        CharSequence text = layout.getText();
        float max = 0;

        // if any line was wrapped, we can't use it.
        // but it's ok for the last line not to have a newline

        for (int i = 0; i < n - 1; i++) {
            if (text.charAt(layout.getLineEnd(i) - 1) != '\n') {
                return -1;
            }
        }

        for (int i = 0; i < n; i++) {
            max = Math.max(max, layout.getLineWidth(i));
        }

        return (int) Math.ceil(max);
    }
    /**
     * Returns the top padding of the view, plus space for the top
     * Drawable if any.
     */
    public int getCompoundPaddingTop() {
//        final Drawables dr = mDrawables;
//        if (dr == null || dr.mShowing[Drawables.TOP] == null) {
//            return mPaddingTop;
//        } else {
//            return mPaddingTop + dr.mDrawablePadding + dr.mDrawableSizeTop;
//        }
        return getPaddingTop();
    }
    /**
     * Returns the bottom padding of the view, plus space for the bottom
     * Drawable if any.
     */
    public int getCompoundPaddingBottom() {
//        final Drawables dr = mDrawables;
//        if (dr == null || dr.mShowing[Drawables.BOTTOM] == null) {
//            return mPaddingBottom;
//        } else {
//            return mPaddingBottom + dr.mDrawablePadding + dr.mDrawableSizeBottom;
//        }
        return getPaddingBottom();
    }
    /**
     * Returns the left padding of the view, plus space for the left
     * Drawable if any.
     */
    public int getCompoundPaddingLeft() {
//        final Drawables dr = mDrawables;
//        if (dr == null || dr.mShowing[Drawables.LEFT] == null) {
//            return mPaddingLeft;
//        } else {
//            return mPaddingLeft + dr.mDrawablePadding + dr.mDrawableSizeLeft;
//        }
        return getPaddingLeft();
    }
    /**
     * Returns the right padding of the view, plus space for the right
     * Drawable if any.
     */
    public int getCompoundPaddingRight() {
//        final Drawables dr = mDrawables;
//        if (dr == null || dr.mShowing[Drawables.RIGHT] == null) {
//            return mPaddingRight;
//        } else {
//            return mPaddingRight + dr.mDrawablePadding + dr.mDrawableSizeRight;
//        }
        return getPaddingRight();
    }
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
//    private static final float TAB_INCREMENT = 20;
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
//    static <T> T[] getParagraphSpans(Spanned text, int start, int end, Class<T> type) {
//        if (start == end && start > 0) {
////            return ArrayUtils.emptyArray(type);
//            return (T[]) Array.newInstance(type, 0);
//        }
//
////        if(text instanceof SpannableStringBuilder) {
////            return ((SpannableStringBuilder) text).getSpans(start, end, type, false);
////        } else {
//            return text.getSpans(start, end, type);
////        }
//    }
    public int getLineHeight() {
        return Math.round(mTextPaint.getFontMetricsInt(null) * mSpacingMult + mSpacingAdd);
    }
    private int getDesiredHeight() {
        return Math.max(
                getDesiredHeight(mLayout, true),
                getDesiredHeight(mHintLayout, mEllipsize != null));
    }
    private int getDesiredHeight(Layout layout, boolean cap) {
        if (layout == null) {
            return 0;
        }

        /*
         * Don't cap the hint to a certain number of lines.
         * (Do cap it, though, if we have a maximum pixel height.)
         */
        int desired = layout.getHeight(/*cap*/);

//        final Drawables dr = mDrawables;
//        if (dr != null) {
//            desired = Math.max(desired, dr.mDrawableHeightLeft);
//            desired = Math.max(desired, dr.mDrawableHeightRight);
//        }

        int linecount = layout.getLineCount();
        final int padding = getCompoundPaddingTop() + getCompoundPaddingBottom();
        desired += padding;

        if (mMaxMode != LINES) {
            desired = Math.min(desired, mMaximum);
        } else if (cap && linecount > mMaximum && (layout instanceof DynamicLayout
                || layout instanceof BoringLayout)) {
            desired = layout.getLineTop(mMaximum);

//            if (dr != null) {
//                desired = Math.max(desired, dr.mDrawableHeightLeft);
//                desired = Math.max(desired, dr.mDrawableHeightRight);
//            }

            desired += padding;
            linecount = mMaximum;
        }

        if (mMinMode == LINES) {
            if (linecount < mMinimum) {
                desired += getLineHeight() * (mMinimum - linecount);
            }
        } else {
            desired = Math.max(desired, mMinimum);
        }

        // Check against our minimum height
        desired = Math.max(desired, getSuggestedMinimumHeight());

        return desired;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private int sp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }






    /**
     * Return the text that TextView is displaying as an Editable object. If the text is not
     * editable, null is returned.
     *
     * @see #getText
     */
    public Editable getEditableText() {
        return (mText instanceof Editable) ? (Editable) mText : null;
    }
    private void setText(CharSequence text) {
        if (text == null) {
            text = "";
        }
        mText = Editable.Factory.getInstance().newEditable(text);
        mSpannable = (text instanceof Spannable) ? (Spannable) text : null;
        InputMethodManager imm = getInputMethodManager();
        if (imm != null) {
            imm.restartInput(this);
        }

        if (mTransformation == null) {
            mTransformed = text;
        } else {
            mTransformed = mTransformation.getTransformation(text, this);
        }
        if (mTransformed == null) {
            // Should not happen if the transformation method follows the non-null postcondition.
            mTransformed = "";
        }
    }

    public boolean onCheckIsTextEditor() {
        //return mEditor != null && mEditor.mInputType != EditorInfo.TYPE_NULL;
        return true;
    }

    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (onCheckIsTextEditor() && isEnabled()) {
            return new CustomInputConnection2(this);
        }

        return null;
    }

    private InputMethodManager getInputMethodManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getContext().getSystemService(InputMethodManager.class);
        }
        //TODO: verify this works
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    private static final int NO_POINTER_ID = -1;
    /**
     * The prime (the 1st finger) pointer id which is used as a lock to prevent multi touch among
     * TextView and the handle views which are rendered on popup windows.
     */
    private int mPrimePointerId = NO_POINTER_ID;
    /**
     * Whether the prime pointer is from the event delivered to selection handle or insertion
     * handle.
     */
    private boolean mIsPrimePointerFromHandleView;
    /**
     * Called from onTouchEvent() to prevent the touches by secondary fingers.
     * Dragging on handles can revise cursor/selection, so can dragging on the text view.
     * This method is a lock to avoid processing multiple fingers on both text view and handles.
     * Note: multiple fingers on handles (e.g. 2 fingers on the 2 selection handles) should work.
     *
     * @param event The motion event that is being handled and carries the pointer info.
     * @param fromHandleView true if the event is delivered to selection handle or insertion
     * handle; false if this event is delivered to TextView.
     * @return Returns true to indicate that onTouchEvent() can continue processing the motion
     * event, otherwise false.
     *  - Always returns true for the first finger.
     *  - For secondary fingers, if the first or current finger is from TextView, returns false.
     *    This is to make touch mutually exclusive between the TextView and the handles, but
     *    not among the handles.
     */
    boolean isFromPrimePointer(MotionEvent event, boolean fromHandleView) {
        boolean res = true;
        if (mPrimePointerId == NO_POINTER_ID)  {
            mPrimePointerId = event.getPointerId(0);
            mIsPrimePointerFromHandleView = fromHandleView;
        } else if (mPrimePointerId != event.getPointerId(0)) {
            res = mIsPrimePointerFromHandleView && fromHandleView;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            mPrimePointerId = -1;
        }
        return res;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        if (DEBUG_CURSOR) {
//            logCursor("onTouchEvent", "%d: %s (%f,%f)",
//                    event.getSequenceNumber(),
//                    MotionEvent.actionToString(event.getActionMasked()),
//                    event.getX(), event.getY());
//        }

        Log.w(TAG, String.format("onTouchEvent: %s (%f,%f)",
                MotionEvent.actionToString(event.getActionMasked()),
                event.getX(), event.getY()));
        if (!isFromPrimePointer(event, false)) {
            return true;
        }

        final int action = event.getActionMasked();
//        if (mEditor != null) {
//            mEditor.onTouchEvent(event);
//
//            if (mEditor.mInsertionPointCursorController != null
//                    && mEditor.mInsertionPointCursorController.isCursorBeingModified()) {
//                return true;
//            }
//            if (mEditor.mSelectionModifierCursorController != null
//                    && mEditor.mSelectionModifierCursorController.isDragAcceleratorActive()) {
//                return true;
//            }
//        }
//
        final boolean superResult = super.onTouchEvent(event);
//        if (DEBUG_CURSOR) {
//            logCursor("onTouchEvent", "superResult=%s", superResult);
//        }
//
//        /*
//         * Don't handle the release after a long press, because it will move the selection away from
//         * whatever the menu action was trying to affect. If the long press should have triggered an
//         * insertion action mode, we can now actually show it.
//         */
//        if (mEditor != null && mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
//            mEditor.mDiscardNextActionUp = false;
//            if (DEBUG_CURSOR) {
//                logCursor("onTouchEvent", "release after long press detected");
//            }
//            if (mEditor.mIsInsertionActionModeStartPending) {
//                mEditor.startInsertionActionMode();
//                mEditor.mIsInsertionActionModeStartPending = false;
//            }
//            return superResult;
//        }
//
        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP)
                /*&& (mEditor == null || !mEditor.mIgnoreActionUpEvent)*/ && isFocused();
//
//        if ((mMovement != null || onCheckIsTextEditor()) && isEnabled()
//                && mText instanceof Spannable && mLayout != null) {
        boolean handled = false;
//
//            if (mMovement != null) {
//                handled |= mMovement.onTouchEvent(this, mSpannable, event);
//            }
//
        final boolean textIsSelectable = /*isTextSelectable()*/true;
//            if (touchIsFinished && mLinksClickable && mAutoLinkMask != 0 && textIsSelectable) {
//                // The LinkMovementMethod which should handle taps on links has not been installed
//                // on non editable text that support text selection.
//                // We reproduce its behavior here to open links for these.
//                ClickableSpan[] links = mSpannable.getSpans(getSelectionStart(),
//                        getSelectionEnd(), ClickableSpan.class);
//
//                if (links.length > 0) {
//                    links[0].onClick(this);
//                    handled = true;
//                }
//            }
//
        if (touchIsFinished && (/*isTextEditable()*/true || textIsSelectable)) {
            // Show the IME, except when selecting in read-only text.
            final InputMethodManager imm = getInputMethodManager();
            viewClicked(imm);
            if (/*isTextEditable()*/true && /*mEditor.mShowSoftInputOnFocus*/true && imm != null) {
                imm.showSoftInput(this, 0);
            }

//                // The above condition ensures that the mEditor is not null
//                mEditor.onTouchUpEvent(event);

            handled = true;
        }

        if (handled) {
            return true;
        }
//        }
//
        Log.w(TAG, "onTouchEvent: isFocused=" + isFocused());

        return superResult;
    }

    protected void viewClicked(InputMethodManager imm) {
        if (imm != null) {
            imm.viewClicked(this);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        Log.w(TAG, "setEnabled " + enabled);
        if (enabled == isEnabled()) {
            return;
        }

        if (!enabled) {
            // Hide the soft input if the currently active TextView is disabled
            InputMethodManager imm = getInputMethodManager();
            if (imm != null && imm.isActive(this)) {
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        }

        super.setEnabled(enabled);

        if (enabled) {
            // Make sure IME is updated with current editor info.
            InputMethodManager imm = getInputMethodManager();
            if (imm != null) imm.restartInput(this);
        }

//        // Will change text color
//        if (mEditor != null) {
////            mEditor.invalidateTextDisplayList();
//            mEditor.prepareCursorControllers();
//
//            // start or stop the cursor blinking as appropriate
//            mEditor.makeBlink();
//        }
    }

//    void invalidateCursor() {
//        int where = getSelectionEnd();
//
//        invalidateCursor(where, where, where);
//    }
//
//    private void invalidateCursor(int a, int b, int c) {
//        if (a >= 0 || b >= 0 || c >= 0) {
//            int start = Math.min(Math.min(a, b), c);
//            int end = Math.max(Math.max(a, b), c);
//            invalidateRegion(start, end, true /* Also invalidates blinking cursor */);
//        }
//    }
//
//    /**
//     * Invalidates the region of text enclosed between the start and end text offsets.
//     */
//    void invalidateRegion(int start, int end, boolean invalidateCursor) {
//        if (mLayout == null) {
//            invalidate();
//        } else {
//            int lineStart = mLayout.getLineForOffset(start);
//            int top = mLayout.getLineTop(lineStart);
//
//            // This is ridiculous, but the descent from the line above
//            // can hang down into the line we really want to redraw,
//            // so we have to invalidate part of the line above to make
//            // sure everything that needs to be redrawn really is.
//            // (But not the whole line above, because that would cause
//            // the same problem with the descenders on the line above it!)
//            if (lineStart > 0) {
//                top -= mLayout.getLineDescent(lineStart - 1);
//            }
//
//            int lineEnd;
//
//            if (start == end) {
//                lineEnd = lineStart;
//            } else {
//                lineEnd = mLayout.getLineForOffset(end);
//            }
//
//            int bottom = mLayout.getLineBottom(lineEnd);
//
//            // mEditor can be null in case selection is set programmatically.
//            if (invalidateCursor && mEditor != null && mEditor.mDrawableForCursor != null) {
//                final Rect bounds = mEditor.mDrawableForCursor.getBounds();
//                top = Math.min(top, bounds.top);
//                bottom = Math.max(bottom, bounds.bottom);
//            }
//
//            final int compoundPaddingLeft = getCompoundPaddingLeft();
//            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);
//
//            int left, right;
//            if (lineStart == lineEnd && !invalidateCursor) {
//                left = (int) mLayout.getPrimaryHorizontal(start);
//                right = (int) (mLayout.getPrimaryHorizontal(end) + 1.0);
//                left += compoundPaddingLeft;
//                right += compoundPaddingLeft;
//            } else {
//                // Rectangle bounding box when the region spans several lines
//                left = compoundPaddingLeft;
//                right = getWidth() - getCompoundPaddingRight();
//            }
//
//            invalidate(mScrollX + left, verticalPadding + top,
//                    mScrollX + right, verticalPadding + bottom);
//        }
//    }

//    /**
//     * Set whether the cursor is visible. The default is true. Note that this property only
//     * makes sense for editable TextView.
//     *
//     * @see #isCursorVisible()
//     *
//     * @attr ref android.R.styleable#TextView_cursorVisible
//     */
//    public void setCursorVisible(boolean visible) {
//        if (visible && mEditor == null) return; // visible is the default value with no edit data
//        createEditorIfNeeded();
//        if (mEditor.mCursorVisible != visible) {
//            mEditor.mCursorVisible = visible;
//            invalidate();
//
//            mEditor.makeBlink();
//
//            // InsertionPointCursorController depends on mCursorVisible
//            mEditor.prepareCursorControllers();
//        }
//    }

//    /**
//     * @return whether or not the cursor is visible (assuming this TextView is editable)
//     *
//     * @see #setCursorVisible(boolean)
//     *
//     * @attr ref android.R.styleable#TextView_cursorVisible
//     */
//    public boolean isCursorVisible() {
//        // true is the default value
//        return mEditor == null ? true : mEditor.mCursorVisible;
//    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.w(TAG, "onKeyUp: keyCode=" + keyCode + ", event=" + event);
//        if (!isEnabled()) {
//            return super.onKeyUp(keyCode, event);
//        }
//
//        if (!KeyEvent.isModifierKey(keyCode)) {
//            mPreventDefaultMovement = false;
//        }
//
//        switch (keyCode) {
//            case KeyEvent.KEYCODE_DPAD_CENTER:
//                if (event.hasNoModifiers()) {
//                    /*
//                     * If there is a click listener, just call through to
//                     * super, which will invoke it.
//                     *
//                     * If there isn't a click listener, try to show the soft
//                     * input method.  (It will also
//                     * call performClick(), but that won't do anything in
//                     * this case.)
//                     */
//                    if (!hasOnClickListeners()) {
//                        if (mMovement != null && mText instanceof Editable
//                                && mLayout != null && onCheckIsTextEditor()) {
//                            InputMethodManager imm = getInputMethodManager();
//                            viewClicked(imm);
//                            if (imm != null && getShowSoftInputOnFocus()) {
//                                imm.showSoftInput(this, 0);
//                            }
//                        }
//                    }
//                }
//                return super.onKeyUp(keyCode, event);
//
//            case KeyEvent.KEYCODE_ENTER:
//            case KeyEvent.KEYCODE_NUMPAD_ENTER:
//                if (event.hasNoModifiers()) {
//                    if (mEditor != null && mEditor.mInputContentType != null
//                            && mEditor.mInputContentType.onEditorActionListener != null
//                            && mEditor.mInputContentType.enterDown) {
//                        mEditor.mInputContentType.enterDown = false;
//                        if (mEditor.mInputContentType.onEditorActionListener.onEditorAction(
//                                this, EditorInfo.IME_NULL, event)) {
//                            return true;
//                        }
//                    }
//
//                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
//                            || shouldAdvanceFocusOnEnter()) {
//                        /*
//                         * If there is a click listener, just call through to
//                         * super, which will invoke it.
//                         *
//                         * If there isn't a click listener, try to advance focus,
//                         * but still call through to super, which will reset the
//                         * pressed state and longpress state.  (It will also
//                         * call performClick(), but that won't do anything in
//                         * this case.)
//                         */
//                        if (!hasOnClickListeners()) {
//                            View v = focusSearch(FOCUS_DOWN);
//
//                            if (v != null) {
//                                if (!v.requestFocus(FOCUS_DOWN)) {
//                                    throw new IllegalStateException("focus search returned a view "
//                                            + "that wasn't able to take focus!");
//                                }
//
//                                /*
//                                 * Return true because we handled the key; super
//                                 * will return false because there was no click
//                                 * listener.
//                                 */
//                                super.onKeyUp(keyCode, event);
//                                return true;
//                            } else if ((event.getFlags()
//                                    & KeyEvent.FLAG_EDITOR_ACTION) != 0) {
//                                // No target for next focus, but make sure the IME
//                                // if this came from it.
//                                InputMethodManager imm = getInputMethodManager();
//                                if (imm != null && imm.isActive(this)) {
//                                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
//                                }
//                            }
//                        }
//                    }
//                    return super.onKeyUp(keyCode, event);
//                }
//                break;
//        }
//
//        if (mEditor != null && mEditor.mKeyListener != null) {
//            if (mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, event)) {
//                return true;
//            }
//        }
//
//        if (mMovement != null && mLayout != null) {
//            if (mMovement.onKeyUp(this, mSpannable, keyCode, event)) {
//                return true;
//            }
//        }

        return super.onKeyUp(keyCode, event);
    }

    public void beginBatchEdit() {
        Log.w(TAG, "beginBatchEdit");
//        if (mEditor != null) mEditor.beginBatchEdit();
    }

    public void endBatchEdit() {
        Log.w(TAG, "endBatchEdit");
//        if (mEditor != null) mEditor.endBatchEdit();
        //TODO: (EW) probably should only call requestLayout based on certain text changes
        requestLayout();
        invalidate();
    }

//    void updateAfterEdit() {
//        invalidate();
//        int curs = getSelectionStart();
//
//        if (curs >= 0 || (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
//            registerForPreDraw();
//        }
//
//        checkForResize();
//
//        if (curs >= 0) {
//            mHighlightPathBogus = true;
//            if (mEditor != null) mEditor.makeBlink();
//            bringPointIntoView(curs);
//        }
//    }
//
//
//    private boolean mPreDrawRegistered;
////    private boolean mPreDrawListenerDetached;
//    private void registerForPreDraw() {
//        if (!mPreDrawRegistered) {
//            getViewTreeObserver().addOnPreDrawListener(this);
//            mPreDrawRegistered = true;
//        }
//    }
//    private void unregisterForPreDraw() {
//        getViewTreeObserver().removeOnPreDrawListener(this);
//        mPreDrawRegistered = false;
////        mPreDrawListenerDetached = false;
//    }
//    /**
//     * {@inheritDoc}
//     */
//    @Override
//    public boolean onPreDraw() {
//        if (mLayout == null) {
//            assumeLayout();
//        }
//
//        if (mMovement != null) {
//            /* This code also provides auto-scrolling when a cursor is moved using a
//             * CursorController (insertion point or selection limits).
//             * For selection, ensure start or end is visible depending on controller's state.
//             */
//            int curs = getSelectionEnd();
//            // Do not create the controller if it is not already created.
//            if (mEditor != null && mEditor.mSelectionModifierCursorController != null
//                    && mEditor.mSelectionModifierCursorController.isSelectionStartDragged()) {
//                curs = getSelectionStart();
//            }
//
//            /*
//             * TODO: This should really only keep the end in view if
//             * it already was before the text changed.  I'm not sure
//             * of a good way to tell from here if it was.
//             */
//            if (curs < 0 && (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
//                curs = mText.length();
//            }
//
//            if (curs >= 0) {
//                bringPointIntoView(curs);
//            }
//        } else {
//            bringTextIntoView();
//        }
//
//        // This has to be checked here since:
//        // - onFocusChanged cannot start it when focus is given to a view with selected text (after
//        //   a screen rotation) since layout is not yet initialized at that point.
//        if (mEditor != null && mEditor.mCreatedWithASelection) {
//            mEditor.refreshTextActionMode();
//            mEditor.mCreatedWithASelection = false;
//        }
//
//        unregisterForPreDraw();
//
//        return true;
//    }

    /**
     * Convenience for {@link Selection#getSelectionStart}.
     */
    public int getSelectionStart() {
        return Selection.getSelectionStart(mText);
    }

    /**
     * Convenience for {@link Selection#getSelectionEnd}.
     */
    public int getSelectionEnd() {
        return Selection.getSelectionEnd(mText);
    }

    public void nullLayouts() {
//        if (mLayout instanceof BoringLayout && mSavedLayout == null) {
//            mSavedLayout = (BoringLayout) mLayout;
//        }
//        if (mHintLayout instanceof BoringLayout && mSavedHintLayout == null) {
//            mSavedHintLayout = (BoringLayout) mHintLayout;
//        }
//
        /*mSavedMarqueeModeLayout = */mLayout = mHintLayout = null;

        mBoring = mHintBoring = null;

//        // Since it depends on the value of mLayout
//        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    private void assumeLayout() {
//        int width = getRight() - getLeft() - /*getCompoundPaddingLeft()*/getPaddingLeft() - /*getCompoundPaddingRight()*/getPaddingRight();
        int width = getRight() - getLeft();

        if (width < 1) {
            width = 0;
        }

        int physicalWidth = width;

        if (mHorizontallyScrolling) {
            width = VERY_WIDE;
        }

        makeNewLayout(width, physicalWidth, UNKNOWN_BORING, UNKNOWN_BORING,
                physicalWidth, false);
    }
    public void makeNewLayout(int wantWidth, int hintWidth,
                              BoringLayout.Metrics boring,
                              BoringLayout.Metrics hintBoring,
                              int ellipsisWidth, boolean bringIntoView) {
//        stopMarquee();

        // Update "old" cached values
        mOldMaximum = mMaximum;
        mOldMaxMode = mMaxMode;

//        mHighlightPathBogus = true;

        if (wantWidth < 0) {
            wantWidth = 0;
        }
        if (hintWidth < 0) {
            hintWidth = 0;
        }

        Layout.Alignment alignment = /*getLayoutAlignment()*/Layout.Alignment.ALIGN_NORMAL;
//        final boolean testDirChange = mSingleLine && mLayout != null
//                && (alignment == Layout.Alignment.ALIGN_NORMAL
//                || alignment == Layout.Alignment.ALIGN_OPPOSITE);
//        int oldDir = 0;
//        if (testDirChange) oldDir = mLayout.getParagraphDirection(0);
        boolean shouldEllipsize = mEllipsize != null /*&& getKeyListener() == null*/;
//        final boolean switchEllipsize = mEllipsize == TextUtils.TruncateAt.MARQUEE
//                && mMarqueeFadeMode != MARQUEE_FADE_NORMAL;
        TextUtils.TruncateAt effectiveEllipsize = mEllipsize;
//        if (mEllipsize == TextUtils.TruncateAt.MARQUEE
//                && mMarqueeFadeMode == MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS) {
//            effectiveEllipsize = TextUtils.TruncateAt.END_SMALL;
//        }
//
//        if (mTextDir == null) {
//            mTextDir = getTextDirectionHeuristic();
//        }

        mLayout = makeSingleLayout(wantWidth, boring, ellipsisWidth, alignment, shouldEllipsize/*,
                effectiveEllipsize, effectiveEllipsize == mEllipsize*/);
//        if (switchEllipsize) {
//            TextUtils.TruncateAt oppositeEllipsize = effectiveEllipsize == TextUtils.TruncateAt.MARQUEE
//                    ? TextUtils.TruncateAt.END : TextUtils.TruncateAt.MARQUEE;
//            mSavedMarqueeModeLayout = makeSingleLayout(wantWidth, boring, ellipsisWidth, alignment,
//                    shouldEllipsize, oppositeEllipsize, effectiveEllipsize != mEllipsize);
//        }

        shouldEllipsize = mEllipsize != null;
        mHintLayout = null;

        if (mHint != null) {
            if (shouldEllipsize) hintWidth = wantWidth;

            if (hintBoring == UNKNOWN_BORING) {
                hintBoring = BoringLayout.isBoring(mHint, mTextPaint/*, mTextDir*/,
                        mHintBoring);
                if (hintBoring != null) {
                    mHintBoring = hintBoring;
                }
            }

            if (hintBoring != null) {
                if (hintBoring.width <= hintWidth
                        && (!shouldEllipsize || hintBoring.width <= ellipsisWidth)) {
//                    if (mSavedHintLayout != null) {
//                        mHintLayout = mSavedHintLayout.replaceOrMake(mHint, mTextPaint,
//                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
//                                hintBoring, mIncludePad);
//                    } else {
                        mHintLayout = BoringLayout.make(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
                                hintBoring, mIncludePad);
//                    }

//                    mSavedHintLayout = (BoringLayout) mHintLayout;
                } else if (shouldEllipsize && hintBoring.width <= hintWidth) {
//                    if (mSavedHintLayout != null) {
//                        mHintLayout = mSavedHintLayout.replaceOrMake(mHint, mTextPaint,
//                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
//                                hintBoring, mIncludePad, mEllipsize,
//                                ellipsisWidth);
//                    } else {
                        mHintLayout = BoringLayout.make(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
                                hintBoring, mIncludePad, mEllipsize,
                                ellipsisWidth);
//                    }
                }
            }
            // TODO: code duplication with makeSingleLayout()
            if (mHintLayout == null) {
//                StaticLayout.Builder builder = StaticLayout.Builder.obtain(mHint, 0,
//                        mHint.length(), mTextPaint, hintWidth)
//                        .setAlignment(alignment)
//                        .setTextDirection(mTextDir)
//                        .setLineSpacing(mSpacingAdd, mSpacingMult)
//                        .setIncludePad(mIncludePad)
//                        .setUseLineSpacingFromFallbacks(mUseFallbackLineSpacing)
//                        .setBreakStrategy(mBreakStrategy)
//                        .setHyphenationFrequency(mHyphenationFrequency)
//                        .setJustificationMode(mJustificationMode)
//                        .setMaxLines(mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
//                if (shouldEllipsize) {
//                    builder.setEllipsize(mEllipsize)
//                            .setEllipsizedWidth(ellipsisWidth);
//                }
//                mHintLayout = builder.build();
                mHintLayout = new StaticLayout(mHint, mTextPaint, hintWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);
            }
        }
//
//        if (bringIntoView || (testDirChange && oldDir != mLayout.getParagraphDirection(0))) {
//            registerForPreDraw();
//        }
//
//        if (mEllipsize == TextUtils.TruncateAt.MARQUEE) {
//            if (!compressText(ellipsisWidth)) {
//                final int height = mLayoutParams.height;
//                // If the size of the view does not depend on the size of the text, try to
//                // start the marquee immediately
//                if (height != ViewGroup.LayoutParams.WRAP_CONTENT && height != ViewGroup.LayoutParams.MATCH_PARENT) {
//                    startMarquee();
//                } else {
//                    // Defer the start of the marquee until we know our width (see setFrame())
//                    mRestartMarquee = true;
//                }
//            }
//        }
//
//        // CursorControllers need a non-null mLayout
//        if (mEditor != null) mEditor.prepareCursorControllers();
    }
    protected Layout makeSingleLayout(int wantWidth, BoringLayout.Metrics boring, int ellipsisWidth,
                                      Layout.Alignment alignment, boolean shouldEllipsize/*, TextUtils.TruncateAt effectiveEllipsize,
                                      boolean useSaved*/) {
        Layout result = null;
//        if (useDynamicLayout()) {
//            final DynamicLayout.Builder builder = DynamicLayout.Builder.obtain(mText, textPaint,
//                    wantWidth)
//                    .setDisplayText(mTransformed)
//                    .setAlignment(alignment)
//                    .setTextDirection(mTextDir)
//                    .setLineSpacing(mSpacingAdd, mSpacingMult)
//                    .setIncludePad(mIncludePad)
//                    .setUseLineSpacingFromFallbacks(mUseFallbackLineSpacing)
//                    .setBreakStrategy(mBreakStrategy)
//                    .setHyphenationFrequency(mHyphenationFrequency)
//                    .setJustificationMode(mJustificationMode)
//                    .setEllipsize(getKeyListener() == null ? effectiveEllipsize : null)
//                    .setEllipsizedWidth(ellipsisWidth);
//            result = builder.build();
        result = new DynamicLayout(mText, mTextPaint, wantWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);
//        result = new DynamicLayout(mText, mTextPaint, wantWidth, alignment, 0, 0, true);
//        } else {
//            if (boring == UNKNOWN_BORING) {
//                boring = BoringLayout.isBoring(mTransformed, mTextPaint, mTextDir, mBoring);
//                if (boring != null) {
//                    mBoring = boring;
//                }
//            }
//
//            if (boring != null) {
//                if (boring.width <= wantWidth
//                        && (effectiveEllipsize == null || boring.width <= ellipsisWidth)) {
//                    if (useSaved && mSavedLayout != null) {
//                        result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad);
//                    } else {
//                        result = BoringLayout.make(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad);
//                    }
//
//                    if (useSaved) {
//                        mSavedLayout = (BoringLayout) result;
//                    }
//                } else if (shouldEllipsize && boring.width <= wantWidth) {
//                    if (useSaved && mSavedLayout != null) {
//                        result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad, effectiveEllipsize,
//                                ellipsisWidth);
//                    } else {
//                        result = BoringLayout.make(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad, effectiveEllipsize,
//                                ellipsisWidth);
//                    }
//                }
//            }
//        }
//        if (result == null) {
//            StaticLayout.Builder builder = StaticLayout.Builder.obtain(mTransformed,
//                    0, mTransformed.length(), mTextPaint, wantWidth)
//                    .setAlignment(alignment)
//                    .setTextDirection(mTextDir)
//                    .setLineSpacing(mSpacingAdd, mSpacingMult)
//                    .setIncludePad(mIncludePad)
//                    .setUseLineSpacingFromFallbacks(mUseFallbackLineSpacing)
//                    .setBreakStrategy(mBreakStrategy)
//                    .setHyphenationFrequency(mHyphenationFrequency)
//                    .setJustificationMode(mJustificationMode)
//                    .setMaxLines(mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
//            if (shouldEllipsize) {
//                builder.setEllipsize(effectiveEllipsize)
//                        .setEllipsizedWidth(ellipsisWidth);
//            }
//            result = builder.build();
//        }
        return result;
    }
    private Layout makeSingleLayout(int wantWidth, BoringLayout.Metrics boring, int ellipsisWidth,
                                    Layout.Alignment alignment, boolean shouldEllipsize, TruncateAt effectiveEllipsize/*,
                                    boolean useSaved*/) {
        Layout result = null;
//        if (mText instanceof Spannable) {
            result = new DynamicLayout(mText, mTransformed, mTextPaint, wantWidth,
                    alignment, /*mTextDir, */mSpacingMult,
                    mSpacingAdd, mIncludePad, /*getKeyListener() == null*/true ? effectiveEllipsize : null,
                    ellipsisWidth);
//        } else {
//            if (boring == UNKNOWN_BORING) {
//                boring = BoringLayout.isBoring(mTransformed, mTextPaint, mTextDir, mBoring);
//                if (boring != null) {
//                    mBoring = boring;
//                }
//            }
//
//            if (boring != null) {
//                if (boring.width <= wantWidth &&
//                        (effectiveEllipsize == null || boring.width <= ellipsisWidth)) {
//                    if (useSaved && mSavedLayout != null) {
//                        result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad);
//                    } else {
//                        result = BoringLayout.make(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad);
//                    }
//
//                    if (useSaved) {
//                        mSavedLayout = (BoringLayout) result;
//                    }
//                } else if (shouldEllipsize && boring.width <= wantWidth) {
//                    if (useSaved && mSavedLayout != null) {
//                        result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad, effectiveEllipsize,
//                                ellipsisWidth);
//                    } else {
//                        result = BoringLayout.make(mTransformed, mTextPaint,
//                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
//                                boring, mIncludePad, effectiveEllipsize,
//                                ellipsisWidth);
//                    }
//                } else if (shouldEllipsize) {
//                    result = new StaticLayout(mTransformed,
//                            0, mTransformed.length(),
//                            mTextPaint, wantWidth, alignment, mTextDir, mSpacingMult,
//                            mSpacingAdd, mIncludePad, effectiveEllipsize,
//                            ellipsisWidth, mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
//                } else {
//                    result = new StaticLayout(mTransformed, mTextPaint,
//                            wantWidth, alignment, mTextDir, mSpacingMult, mSpacingAdd,
//                            mIncludePad);
//                }
//            } else if (shouldEllipsize) {
//                result = new StaticLayout(mTransformed,
//                        0, mTransformed.length(),
//                        mTextPaint, wantWidth, alignment, mTextDir, mSpacingMult,
//                        mSpacingAdd, mIncludePad, effectiveEllipsize,
//                        ellipsisWidth, mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
//            } else {
//                result = new StaticLayout(mTransformed, mTextPaint,
//                        wantWidth, alignment, mTextDir, mSpacingMult, mSpacingAdd,
//                        mIncludePad);
//            }
//        }
        return result;
    }

    private static boolean isMultilineInputType(int type) {
        return (type & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE))
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    }

    private void setInputType(int type, boolean direct) {
        final int cls = type & EditorInfo.TYPE_MASK_CLASS;
        KeyListener input;
        if (cls == EditorInfo.TYPE_CLASS_TEXT) {
            boolean autotext = (type & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0;
            TextKeyListener.Capitalize cap;
            if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
                cap = TextKeyListener.Capitalize.CHARACTERS;
            } else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
                cap = TextKeyListener.Capitalize.WORDS;
            } else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                cap = TextKeyListener.Capitalize.SENTENCES;
            } else {
                cap = TextKeyListener.Capitalize.NONE;
            }
            input = TextKeyListener.getInstance(autotext, cap);
        } else if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                final Locale locale = getCustomLocaleForKeyListenerOrNull();
                input = DigitsKeyListener.getInstance(
                        locale,
                        (type & EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0,
                        (type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0);
                if (locale != null) {
                    // Override type, if necessary for i18n.
                    int newType = input.getInputType();
                    final int newClass = newType & EditorInfo.TYPE_MASK_CLASS;
                    if (newClass != EditorInfo.TYPE_CLASS_NUMBER) {
                        // The class is different from the original class. So we need to override
                        // 'type'. But we want to keep the password flag if it's there.
                        if ((type & EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD) != 0) {
                            newType |= EditorInfo.TYPE_TEXT_VARIATION_PASSWORD;
                        }
                        type = newType;
                    }
                }
            } else {
                //from AOSP21
                input = DigitsKeyListener.getInstance(
                        (type & EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0,
                        (type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0);
            }
        } else if (cls == EditorInfo.TYPE_CLASS_DATETIME) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                final Locale locale = getCustomLocaleForKeyListenerOrNull();
                switch (type & EditorInfo.TYPE_MASK_VARIATION) {
                    case EditorInfo.TYPE_DATETIME_VARIATION_DATE:
                        input = DateKeyListener.getInstance(locale);
                        break;
                    case EditorInfo.TYPE_DATETIME_VARIATION_TIME:
                        input = TimeKeyListener.getInstance(locale);
                        break;
                    default:
                        input = DateTimeKeyListener.getInstance(locale);
                        break;
                }
            } else {
                //from AOSP21
                switch (type & EditorInfo.TYPE_MASK_VARIATION) {
                    case EditorInfo.TYPE_DATETIME_VARIATION_DATE:
                        input = DateKeyListener.getInstance();
                        break;
                    case EditorInfo.TYPE_DATETIME_VARIATION_TIME:
                        input = TimeKeyListener.getInstance();
                        break;
                    default:
                        input = DateTimeKeyListener.getInstance();
                        break;
                }
            }
            if (mUseInternationalizedInput) {
                type = input.getInputType(); // Override type, if necessary for i18n.
            }
        } else if (cls == EditorInfo.TYPE_CLASS_PHONE) {
            input = DialerKeyListener.getInstance();
        } else {
            input = TextKeyListener.getInstance();
        }
        setRawInputType(type);
//        mListenerChanged = false;
//        if (direct) {
//            createEditorIfNeeded();
//            mEditor.mKeyListener = input;
//        } else {
//            setKeyListenerOnly(input);
//        }
    }

    /**
     * @return {@code null} if the key listener should use pre-O (locale-independent). Otherwise
     *         a {@code Locale} object that can be used to customize key various listeners.
     * @see DateKeyListener#getInstance(Locale)
     * @see DateTimeKeyListener#getInstance(Locale)
     * @see DigitsKeyListener#getInstance(Locale)
     * @see TimeKeyListener#getInstance(Locale)
     */
    @Nullable
    private Locale getCustomLocaleForKeyListenerOrNull() {
        if (!mUseInternationalizedInput) {
            // If the application does not target O, stick to the previous behavior.
            return null;
        }
//        final LocaleList locales = getImeHintLocales();
//        if (locales == null) {
//            // If the application does not explicitly specify IME hint locale, also stick to the
//            // previous behavior.
            return null;
//        }
//        return locales.get(0);
    }
    /**
     * If true, sets the properties of this field (number of lines, horizontally scrolling,
     * transformation method) to be for a single-line input; if false, restores these to the default
     * conditions.
     *
     * Note that the default conditions are not necessarily those that were in effect prior this
     * method, and you may want to reset these properties to your custom values.
     *
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public void setSingleLine(boolean singleLine) {
        // Could be used, but may break backward compatibility.
        // if (mSingleLine == singleLine) return;
        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, true, true);
    }

    /**
     * Adds or remove the EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE on the mInputType.
     * @param singleLine
     */
    private void setInputTypeSingleLine(boolean singleLine) {
//        if (mEditor != null
//                && (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS)
//                == EditorInfo.TYPE_CLASS_TEXT) {
//            if (singleLine) {
//                mEditor.mInputType &= ~EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
//            } else {
//                mEditor.mInputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
//            }
//        }
    }

    private void applySingleLine(boolean singleLine, boolean applyTransformation,
                                 boolean changeMaxLines) {
        mSingleLine = singleLine;
        if (singleLine) {
            setLines(1);
            setHorizontallyScrolling(true);
            if (applyTransformation) {
                setTransformationMethod(SingleLineTransformationMethod.getInstance());
            }
        } else {
            if (changeMaxLines) {
                setMaxLines(Integer.MAX_VALUE);
            }
            setHorizontallyScrolling(false);
            if (applyTransformation) {
                setTransformationMethod(null);
            }
        }
    }

    /**
     * Sets the height of the TextView to be exactly {@code lines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides previous minimum/maximum height configurations
     * such as {@link #setMinLines(int)} or {@link #setMaxLines(int)}. {@link #setSingleLine()} will
     * set this value to 1.
     *
     * @param lines the exact height of the TextView in terms of lines
     *
     * @see #setHeight(int)
     *
     * @attr ref android.R.styleable#TextView_lines
     */
    public void setLines(int lines) {
        mMaximum = mMinimum = lines;
        mMaxMode = mMinMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * Sets whether the text should be allowed to be wider than the
     * View is.  If false, it will be wrapped to the width of the View.
     *
     * @attr ref android.R.styleable#TextView_scrollHorizontally
     */
    public void setHorizontallyScrolling(boolean whether) {
        if (mHorizontallyScrolling != whether) {
            mHorizontallyScrolling = whether;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets the height of the TextView to be at most {@code maxLines} tall.
     * <p>
     * This value is used for height calculation if LayoutParams does not force TextView to have an
     * exact height. Setting this value overrides previous maximum height configurations such as
     * {@link #setMaxHeight(int)} or {@link #setLines(int)}.
     *
     * @param maxLines the maximum height of TextView in terms of number of lines
     *
     * @see #getMaxLines()
     * @see #setLines(int)
     *
     * @attr ref android.R.styleable#TextView_maxLines
     */
    public void setMaxLines(int maxLines) {
        mMaximum = maxLines;
        mMaxMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * Sets the transformation that is applied to the text that this
     * TextView is displaying.
     *
     * @attr ref android.R.styleable#TextView_password
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public final void setTransformationMethod(TransformationMethod method) {
        if (method == mTransformation) {
            // Avoid the setText() below if the transformation is
            // the same.
            return;
        }
        if (mTransformation != null) {
            if (mSpannable != null) {
                mSpannable.removeSpan(mTransformation);
            }
        }

        mTransformation = method;

//        if (method instanceof TransformationMethod2) {
//            TransformationMethod2 method2 = (TransformationMethod2) method;
//            mAllowTransformationLengthChange = !isTextSelectable() && !(mText instanceof Editable);
//            method2.setLengthChangesAllowed(mAllowTransformationLengthChange);
//        } else {
            mAllowTransformationLengthChange = false;
//        }

        setText(mText);

//        if (hasPasswordTransformationMethod()) {
//            notifyViewAccessibilityStateChangedIfNeeded(
//                    AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
//        }

//        // PasswordTransformationMethod always have LTR text direction heuristics returned by
//        // getTextDirectionHeuristic, needs reset
//        mTextDir = getTextDirectionHeuristic();
    }

    /**
     * It would be better to rely on the input type for everything. A password inputType should have
     * a password transformation. We should hence use isPasswordInputType instead of this method.
     *
     * We should:
     * - Call setInputType in setKeyListener instead of changing the input type directly (which
     * would install the correct transformation).
     * - Refuse the installation of a non-password transformation in setTransformation if the input
     * type is password.
     *
     * However, this is like this for legacy reasons and we cannot break existing apps. This method
     * is useful since it matches what the user can see (obfuscated text or not).
     *
     * @return true if the current transformation method is of the password type.
     */
    boolean hasPasswordTransformationMethod() {
        return mTransformation instanceof PasswordTransformationMethod;
    }

    /**
     * Directly change the content type integer of the text view, without
     * modifying any other state.
     * @see #setInputType(int)
     * @see android.text.InputType
     * @attr ref android.R.styleable#TextView_inputType
     */
    public void setRawInputType(int type) {
//        if (type == InputType.TYPE_NULL && mEditor == null) return; //TYPE_NULL is the default value
//        createEditorIfNeeded();
//        mEditor.mInputType = type;
    }


    @Override
    protected int computeHorizontalScrollRange() {
        if (mLayout != null) {
            int result = mSingleLine && (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT
                    ? (int) mLayout.getLineWidth(0) : mLayout.getWidth();
            Log.w(TAG, "computeHorizontalScrollRange: " + result);
            return result;
        }

        return super.computeHorizontalScrollRange();
    }

    @Override
    protected int computeVerticalScrollRange() {
        if (mLayout != null) {
            Log.w(TAG, "computeVerticalScrollRange: " + mLayout.getHeight());
            return mLayout.getHeight();
        }
        return super.computeVerticalScrollRange();
    }


    @Override
    public void computeScroll() {
        Log.w(TAG, "computeScroll " + mScroller);
        if (mScroller != null) {
            if (mScroller.computeScrollOffset()) {
//                mScrollX = mScroller.getCurrX();
//                mScrollY = mScroller.getCurrY();
                scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
//                invalidateParentCaches();
                postInvalidate();  // So we draw again
            }
        }
    }

    /**
     * Sets the Scroller used for producing a scrolling animation
     *
     * @param s A Scroller instance
     */
    public void setScroller(Scroller s) {
        mScroller = s;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        Log.w(TAG, "onScrollChanged: horiz=" + horiz + ", vert=" + vert);
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
//        if (mEditor != null) {
//            mEditor.onScrollChanged();
//        }
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        Log.w(TAG, "onDragEvent: event=" + event);
        switch (event.getAction()) {
//            case DragEvent.ACTION_DRAG_STARTED:
//                return mEditor != null && mEditor.hasInsertionController();
//
//            case DragEvent.ACTION_DRAG_ENTERED:
//                TextView.this.requestFocus();
//                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                if (mText instanceof Spannable) {
                    final int offset = getOffsetForPosition(event.getX(), event.getY());
                    Selection.setSelection(mSpannable, offset);
                }
                return true;

//            case DragEvent.ACTION_DROP:
//                if (mEditor != null) mEditor.onDrop(event);
//                return true;

            case DragEvent.ACTION_DRAG_ENDED:
            case DragEvent.ACTION_DRAG_EXITED:
            default:
                return true;
        }
    }

    /**
     * Get the character offset closest to the specified absolute position. A typical use case is to
     * pass the result of {@link MotionEvent#getX()} and {@link MotionEvent#getY()} to this method.
     *
     * @param x The horizontal absolute position of a point on screen
     * @param y The vertical absolute position of a point on screen
     * @return the character offset for the character whose position is closest to the specified
     *  position. Returns -1 if there is no layout.
     */
    public int getOffsetForPosition(float x, float y) {
        if (getLayout() == null) return -1;
        final int line = getLineAtCoordinate(y);
        final int offset = getOffsetAtCoordinate(line, x);
        return offset;
    }

    /**
     * Gets the {@link android.text.Layout} that is currently being used to display the text.
     * This value can be null if the text or width has recently changed.
     * @return The Layout that is currently being used to display the text.
     */
    public final Layout getLayout() {
        return mLayout;
    }

    float convertToLocalHorizontalCoordinate(float x) {
        x -= getTotalPaddingLeft();
        // Clamp the position to inside of the view.
        x = Math.max(0.0f, x);
        x = Math.min(getWidth() - getTotalPaddingRight() - 1, x);
        x += getScrollX();
        return x;
    }

    int getLineAtCoordinate(float y) {
        y -= getTotalPaddingTop();
        // Clamp the position to inside of the view.
        y = Math.max(0.0f, y);
        y = Math.min(getHeight() - getTotalPaddingBottom() - 1, y);
        y += getScrollY();
        return getLayout().getLineForVertical((int) y);
    }

    int getOffsetAtCoordinate(int line, float x) {
        x = convertToLocalHorizontalCoordinate(x);
        return getLayout().getOffsetForHorizontal(line, x);
    }

    /**
     * Returns the total left padding of the view, including the left
     * Drawable if any.
     */
    public int getTotalPaddingLeft() {
        return getCompoundPaddingLeft();
    }

    /**
     * Returns the total right padding of the view, including the right
     * Drawable if any.
     */
    public int getTotalPaddingRight() {
        return getCompoundPaddingRight();
    }

    /**
     * Returns the total start padding of the view, including the start
     * Drawable if any.
     */
    public int getTotalPaddingStart() {
        return getCompoundPaddingStart();
    }

    /**
     * Returns the total end padding of the view, including the end
     * Drawable if any.
     */
    public int getTotalPaddingEnd() {
        return getCompoundPaddingEnd();
    }

    /**
     * Returns the total top padding of the view, including the top
     * Drawable if any, the extra space to keep more than maxLines
     * from showing, and the vertical offset for gravity, if any.
     */
    public int getTotalPaddingTop() {
        return getExtendedPaddingTop() + getVerticalOffset(true);
    }

    /**
     * Returns the total bottom padding of the view, including the bottom
     * Drawable if any, the extra space to keep more than maxLines
     * from showing, and the vertical offset for gravity, if any.
     */
    public int getTotalPaddingBottom() {
        return getExtendedPaddingBottom() + getBottomVerticalOffset(true);
    }

    /**
     * Returns the start padding of the view, plus space for the start
     * Drawable if any.
     */
    public int getCompoundPaddingStart() {
//        resolveDrawables();
        switch(getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingLeft();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingRight();
        }
    }

    /**
     * Returns the end padding of the view, plus space for the end
     * Drawable if any.
     */
    public int getCompoundPaddingEnd() {
//        resolveDrawables();
        switch(getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingRight();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingLeft();
        }
    }

    /**
     * Returns the extended top padding of the view, including both the
     * top Drawable if any and any extra space to keep more than maxLines
     * of text from showing.  It is only valid to call this after measuring.
     */
    public int getExtendedPaddingTop() {
        if (mMaxMode != LINES) {
            return getCompoundPaddingTop();
        }

        if (mLayout == null) {
            assumeLayout();
        }

        if (mLayout.getLineCount() <= mMaximum) {
            return getCompoundPaddingTop();
        }

        int top = getCompoundPaddingTop();
        int bottom = getCompoundPaddingBottom();
        int viewht = getHeight() - top - bottom;
        int layoutht = mLayout.getLineTop(mMaximum);

        if (layoutht >= viewht) {
            return top;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return top;
        } else if (gravity == Gravity.BOTTOM) {
            return top + viewht - layoutht;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return top + (viewht - layoutht) / 2;
        }
    }

    /**
     * Returns the extended bottom padding of the view, including both the
     * bottom Drawable if any and any extra space to keep more than maxLines
     * of text from showing.  It is only valid to call this after measuring.
     */
    public int getExtendedPaddingBottom() {
        if (mMaxMode != LINES) {
            return getCompoundPaddingBottom();
        }

        if (mLayout == null) {
            assumeLayout();
        }

        if (mLayout.getLineCount() <= mMaximum) {
            return getCompoundPaddingBottom();
        }

        int top = getCompoundPaddingTop();
        int bottom = getCompoundPaddingBottom();
        int viewht = getHeight() - top - bottom;
        int layoutht = mLayout.getLineTop(mMaximum);

        if (layoutht >= viewht) {
            return bottom;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return bottom + viewht - layoutht;
        } else if (gravity == Gravity.BOTTOM) {
            return bottom;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return bottom + (viewht - layoutht) / 2;
        }
    }
    /**
     * Sets the horizontal alignment of the text and the
     * vertical gravity that will be used when there is extra space
     * in the TextView beyond what is required for the text itself.
     *
     * @see android.view.Gravity
     * @attr ref android.R.styleable#TextView_gravity
     */
    public void setGravity(int gravity) {
        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.START;
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.TOP;
        }

        boolean newLayout = false;

        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
                != (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)) {
            newLayout = true;
        }

        if (gravity != mGravity) {
            invalidate();
        }

        mGravity = gravity;

        if (mLayout != null && newLayout) {
            // XXX this is heavy-handed because no actual content changes.
            int want = mLayout.getWidth();
            int hintWant = mHintLayout == null ? 0 : mHintLayout.getWidth();

            makeNewLayout(want, hintWant, UNKNOWN_BORING, UNKNOWN_BORING,
                    /*mRight*/getRight() - /*mLeft*/getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight(), true);
        }
    }

    int getVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.TOP) {
            int boxht = getBoxHeight(l);
            int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.BOTTOM) {
                    voffset = boxht - textht;
                } else { // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
                }
            }
        }
        return voffset;
    }

    private int getBottomVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.BOTTOM) {
            int boxht = getBoxHeight(l);
            int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.TOP) {
                    voffset = boxht - textht;
                } else { // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
                }
            }
        }
        return voffset;
    }

    private int getBoxHeight(Layout l) {
//        Insets opticalInsets = isLayoutModeOptical(mParent) ? getOpticalInsets() : Insets.NONE;
        int padding = (l == mHintLayout)
                ? getCompoundPaddingTop() + getCompoundPaddingBottom()
                : getExtendedPaddingTop() + getExtendedPaddingBottom();
        return getMeasuredHeight() - padding/* + opticalInsets.top + opticalInsets.bottom*/;
    }
}
