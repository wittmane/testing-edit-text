package com.wittmane.testingedittext;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.KeyListener;
import android.text.method.MetaKeyKeyListener;
import android.text.method.MovementMethod;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TransformationMethod;
import android.text.style.CharacterStyle;
import android.text.style.ParagraphStyle;
import android.text.style.SuggestionSpan;
import android.text.style.UpdateAppearance;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.wittmane.testingedittext.method.CustomArrowKeyMovementMethod;
import com.wittmane.testingedittext.method.CustomMovementMethod;

import java.util.ArrayList;
import java.util.Collections;

public class CustomEditTextView extends View implements ICustomTextView {
    static final String TAG = CustomEditTextView.class.getSimpleName();

    private int textColor = Color.BLACK;
    private int textSize = sp(/*14*/18);
    private float mSpacingMult = 1.0f;//TextView_lineSpacingMultiplier - this adds space between lines (relative to base spacing of font)
    private float mSpacingAdd = 0.0f;//TextView_lineSpacingExtra - this adds space between lines (in addition to base, possibly in px)
    private boolean mIncludePad = true;//TextView_includeFontPadding
    private int mMaxWidth = Integer.MAX_VALUE;
    private int mMaxWidthMode = PIXELS;
    private int mMinWidth = 0;
    private int mMinWidthMode = PIXELS;
    private int mMaximum = Integer.MAX_VALUE;
    private int mMaxMode = LINES;
    private int mMinimum = 0;
    private int mMinMode = LINES;
    private int mOldMaximum = mMaximum;
    private int mOldMaxMode = mMaxMode;
    private int mGravity = Gravity.TOP | Gravity.START;

    private static final int LINES = 1;
    private static final int EMS = LINES;
    private static final int PIXELS = 2;
    static final int VERY_WIDE = 1024 * 1024; // XXX should be much larger
    private static final int ANIMATED_SCROLL_GAP = 250;
    static final int BLINK = 500;
    private static final int UNSET_X_VALUE = -1;
    private static final int UNSET_LINE = -1;
    private static final int CHANGE_WATCHER_PRIORITY = 100;

    private TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    //TODO: find a better way to set the color (and allow changes)
    private int mCurTextColor = Color.BLACK;
    private int mCurHintTextColor = Color.GRAY;

    private Editable mText;
    private @Nullable Spannable mSpannable;
    private CharSequence mTransformed;
    private TransformationMethod mTransformation;
    private CharSequence mHint;
    private Layout mLayout;
    private Layout mHintLayout;
    private int mCursorDrawableRes;
    private Drawable mDrawableForCursor = null;
    private Rect mTempRect;

    private ArrayList<TextWatcher> mListeners;

    private ChangeWatcher mChangeWatcher;

    //TODO: I think the scroller is null by default, so it probably can be removed
    private Scroller mScroller;
    private long mLastScroll;

    private boolean mSingleLine;
    private CustomMovementMethod mMovement;

    private boolean mHorizontallyScrolling = false;//TextView_scrollHorizontally or applySingleLine

    private long mShowCursor;
    private boolean mRenderCursorRegardlessTiming;
    private Blink mBlink;
    private int mHighlightColor = 0x6633B5E5;//TODO: probably also load from somewhere
    private Path mHighlightPath;
    private boolean mHighlightPathBogus = true;

    private int mDesiredHeightAtMeasure = -1;
    private int mDeferScroll = -1;

    private static final RectF TEMP_RECTF = new RectF();

    private boolean mCursorVisible = true;
    private boolean mSelectAllOnFocus;
    private boolean mFrozenWithFocus;
    private boolean mSelectionMoved;
    private boolean mTouchFocusSelected;
    // Set when this TextView gained focus with some text selected. Will start selection mode.
    private boolean mCreatedWithASelection;

    // The button state as of the last time #onTouchEvent is called.
    private int mLastButtonState;

    boolean mDiscardNextActionUp;
    boolean mIgnoreActionUpEvent;

    private final EditorTouchState mTouchState = new EditorTouchState();
    private final SimpleTouchManager mTouchManager = new SimpleTouchManager(this);

    /**
     *  Return code of {@link #doKeyDown}.
     */
    private static final int KEY_EVENT_NOT_HANDLED = 0;
    private static final int KEY_EVENT_HANDLED = -1;
    private static final int KEY_DOWN_HANDLED_BY_KEY_LISTENER = 1;
    private static final int KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD = 2;

    // A flag to prevent repeated movements from escaping the enclosing text view. The idea here is
    // that if a user is holding down a movement key to traverse text, we shouldn't also traverse
    // the view hierarchy. On the other hand, if the user is using the movement key to traverse
    // views (i.e. the first movement was to traverse out of this view, or this view was traversed
    // into by the user holding the movement key down) then we shouldn't prevent the focus from
    // changing.
    private boolean mPreventDefaultMovement;

    // Cursor Controllers.
//    InsertionPointCursorController mInsertionPointCursorController;
//    SelectionModifierCursorController mSelectionModifierCursorController;

    private final Editor mEditor = new Editor(this);

    public CustomEditTextView(Context context) {
        this(context, null);
    }
    public CustomEditTextView(Context context, AttributeSet attrs) {
        //R.attr.editTextStyle pulls in the underline in the DynamicLayout and allows this view to be focusable by default
        this(context, attrs, R.attr.editTextStyle);
    }
    public CustomEditTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        EditText referenceEditText = new EditText(context);
        Log.w(TAG, "Reference EditText: totalPaddingTop=" + referenceEditText.getTotalPaddingTop()//30
                + ", extendedPaddingTop=" + referenceEditText.getExtendedPaddingTop()//30
                + ", compoundPaddingTop=" + referenceEditText.getCompoundPaddingTop()//30
                + ", paddingTop=" + referenceEditText.getPaddingTop());//30
        Log.w(TAG, "Reference EditText: totalPaddingBottom=" + referenceEditText.getTotalPaddingBottom()//33
                + ", extendedPaddingBottom=" + referenceEditText.getExtendedPaddingBottom()//33
                + ", compoundPaddingBottom=" + referenceEditText.getCompoundPaddingBottom()//33
                + ", paddingBottom=" + referenceEditText.getPaddingBottom());//33
        Log.w(TAG, "Reference EditText: totalPaddingRight=" + referenceEditText.getTotalPaddingRight()//12
//                + ", extendedPaddingRight=" + referenceEditText.getExtendedPaddingRight()//
                + ", compoundPaddingRight=" + referenceEditText.getCompoundPaddingRight()//12
                + ", paddingRight=" + referenceEditText.getPaddingRight());//12

        Log.w(TAG, "Reference EditText: lineSpacingExtra=" + referenceEditText.getLineSpacingExtra()//0.0
                + ", lineSpacingMultiplier=" + referenceEditText.getLineSpacingMultiplier()//1.0
                + ", lineHeight=" + referenceEditText.getLineHeight()//63
                + ", textSize=" + referenceEditText.getTextSize());//54.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.w(TAG, "Reference EditText: firstBaselineToTopHeight=" + referenceEditText.getFirstBaselineToTopHeight()//88
                    + ", lastBaselineToBottomHeight=" + referenceEditText.getLastBaselineToBottomHeight());//48
        }
        Log.w(TAG, "Reference EditText: includeFontPadding=" + referenceEditText.getIncludeFontPadding()//true
                + ", fontFeatureSettings=" + referenceEditText.getFontFeatureSettings()//null
                + ", fontVariationSettings=" + referenceEditText.getFontVariationSettings());//null
        //mTextPaint.getFontMetricsInt(null)//(63-0.0)/1.0

        //android:textSize - impacts the text size and spacing before and after all lines
        //android:lineHeight - impacts spacing between lines of text
        //android:lineSpacingExtra - impacts spacing between lines of text
        //android:lineSpacingMultiplier - impacts spacing between lines of text
        //android:firstBaselineToTopHeight - impacts vertical spacing before the first line of text
        //android:lastBaselineToBottomHeight - impacts vertical spacing after the last line of text


//        mTextPaint.density = getResources().getDisplayMetrics().density;
        mTextPaint.setColor(textColor);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTextSize(textSize);
        //this gives better spacing, but it doesn't seem to be what the default edit text uses (off by default)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            mTextPaint.setElegantTextHeight(true);
//        }
        //R.styleable.TextView_textScaleX -> mTextPaint.setTextScaleX
        //R.styleable.TextAppearance_fontFamily, R.styleable.TextAppearance_typeface, R.styleable.TextAppearance_textStyle, R.styleable.TextAppearance_textFontWeight -> mTextPaint.setTypeface
        //R.styleable.TextAppearance_elegantTextHeight -> mTextPaint.setElegantTextHeight - impacts vertical padding, but it isn't the default that I'm looking for
        //R.styleable.TextAppearance_letterSpacing -> mTextPaint.setLetterSpacing - doesn't seem to impact vertical padding
        //R.styleable.TextAppearance_fontFeatureSettings -> mTextPaint.setFontFeatureSettings - doesn't seem to impact vertical padding
        //R.styleable.TextAppearance_fontVariationSettings -> mTextPaint.setFontVariationSettings - doesn't seem to do anything
//        mTextPaint.setTextScaleX(1.0f);
        Log.w(TAG, "getFontMetricsInt=" + mTextPaint.getFontMetricsInt(null));

        Log.w(TAG, "paddingTop=" + getPaddingTop()//
                + ", paddingBottom=" + getPaddingBottom()//
                + ", paddingRight=" + getPaddingRight());//


        mMovement = CustomArrowKeyMovementMethod.getInstance();

        int inputType = EditorInfo.TYPE_NULL;
        boolean singleLine = false;

        final Resources.Theme theme = context.getTheme();
        TypedArray typedArray = theme.obtainStyledAttributes(
                attrs, R.styleable.CustomSimpleTextView2, defStyleAttr, 0);

        CharSequence initialText = "";
        for (int i = 0; i < typedArray.getIndexCount(); i++) {
            int attr = typedArray.getIndex(i);
            if (attr == R.styleable.CustomSimpleTextView2_android_text) {
                CharSequence text = typedArray.getText(attr);
                if (text != null) {
                    initialText = text;
                }
            } else if (attr == R.styleable.CustomSimpleTextView2_android_inputType) {
                inputType = typedArray.getInt(attr, EditorInfo.TYPE_NULL);
            } else if (attr == R.styleable.CustomSimpleTextView2_android_gravity) {
//                setGravity(typedArray.getInt(attr, -1));
            } else if (attr == R.styleable.CustomSimpleTextView2_android_hint) {
                CharSequence text = typedArray.getText(attr);
                if (text != null) {
                    mHint = text;
                }
            } else if (attr == R.styleable.CustomSimpleTextView2_android_textCursorDrawable) {
                mCursorDrawableRes = typedArray.getResourceId(attr, 0);
                Log.w(TAG, "mCursorDrawableRes=" + mCursorDrawableRes);
            }
        }

        if (inputType != EditorInfo.TYPE_NULL) {
            // If set, the input type overrides what was set using the deprecated singleLine flag.
            singleLine = !isMultilineInputType(inputType);
        }
        // Same as setSingleLine(), but make sure the transformation method and the maximum number
        // of lines of height are unchanged for multi-line TextViews.
        applySingleLine(singleLine, singleLine, singleLine);

        setText(initialText);



////        setEnabled(true);
//        setClickable(true);
////        setFocusable(true);
//        setFocusableInTouchMode(true);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.w(TAG, "getFocusable=" + getFocusable());
        }
        Log.w(TAG, "isClickable=" + isClickable());
    }

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

        BoringLayout.Metrics boring = /*UNKNOWN_BORING*/null;
        BoringLayout.Metrics hintBoring = /*UNKNOWN_BORING*/null;

//        if (mTextDir == null) {
//            mTextDir = /*getTextDirectionHeuristic()*/TextDirectionHeuristics.LTR;
//        }

        int des = -1;
        boolean fromexisting = false;

        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            width = widthSize;
        } else {
//                    des = (int) Math.ceil(/*Layout*/HiddenLayoutInfo.getDesiredWidthWithLimit(mTransformed, 0,
//                            mTransformed.length(), mTextPaint, mTextDir, widthLimit));
            width = (int) /*FloatMath*/Math.ceil(Layout.getDesiredWidth(/*mTransformed*/mText, mTextPaint));

            if (mHint != null) {
                int hintWidth;

//                        hintDes = (int) Math.ceil(/*Layout*/HiddenLayoutInfo.getDesiredWidthWithLimit(mHint, 0,
//                                mHint.length(), mTextPaint, mTextDir, widthLimit));
                hintWidth = (int) /*FloatMath*/Math.ceil(Layout.getDesiredWidth(mHint, mTextPaint));

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
            makeNewLayout(want, hintWant);
        } else {
            final boolean layoutChanged = (mLayout.getWidth() != want) || (hintWidth != hintWant)
                    || (mLayout.getEllipsizedWidth()
                    != width - getCompoundPaddingLeft() - getCompoundPaddingRight());

//            final boolean widthChanged = (mHint == null) && (mEllipsize == null)
//                    && (want > mLayout.getWidth())
//                    && (mLayout instanceof BoringLayout);

            final boolean maximumChanged = (mMaxMode != mOldMaxMode) || (mMaximum != mOldMaximum);

            if (layoutChanged || maximumChanged) {
//                if (!maximumChanged && widthChanged) {
//                    mLayout.increaseWidthTo(want);
//                } else {
                    makeNewLayout(want, hintWant);
//                }
            } else {
                // Nothing has changed
            }
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            height = heightSize;
            mDesiredHeightAtMeasure = -1;
        } else {
            height = getDesiredHeight();
            mDesiredHeightAtMeasure = height;
            Log.w(TAG, "onMeasure: desiredHeight=" + height);// 97, 160 (px)
            //30px top padding, 21px bottom padding
            //46px 1 line, 109px 2 lines -> 17px space between lines
            //should be: 85px 1 line, 148px 2 lines -> -22px space between lines
            //overall height should be: 136, 199

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(height, heightSize);
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
//            scrollTo(50, 0);

        setMeasuredDimension(width, height);
        Log.w(TAG, "onMeasure: setMeasuredDimension: width=" + width + ", height=" + height);
    }

    public int getLineHeight() {
        return Math.round(mTextPaint.getFontMetricsInt(null) * mSpacingMult + mSpacingAdd);
    }
    private int getDesiredHeight() {
        return Math.max(
                getDesiredHeight(mLayout, true),
                getDesiredHeight(mHintLayout, /*mEllipsize != null*/false));
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

        int linecount = layout.getLineCount();
        final int padding = getCompoundPaddingTop() + getCompoundPaddingBottom();
        Log.w(TAG, "getDesiredHeight: layout.getHeight=" + desired + ", padding=" + padding + ", linecount=" + linecount);
        desired += padding;

        if (mMaxMode != LINES) {
            desired = Math.min(desired, mMaximum);
        } else if (cap && linecount > mMaximum && (layout instanceof DynamicLayout
                || layout instanceof BoringLayout)) {
            desired = layout.getLineTop(mMaximum);

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
        int suggested = getSuggestedMinimumHeight();
        Log.w(TAG, "getDesiredHeight: desired=" + desired + ", suggested=" + suggested);
        desired = Math.max(desired, suggested);

        return desired;
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

    public void setLines(int lines) {
        mMaximum = mMinimum = lines;
        mMaxMode = mMinMode = LINES;

        requestLayout();
        invalidate();
    }
    public void setMaxLines(int maxLines) {
        mMaximum = maxLines;
        mMaxMode = LINES;

        requestLayout();
        invalidate();
    }

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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLayout == null) {
            assumeLayout();
        }

        int color = mCurTextColor;
        Layout layout = mLayout;

        if (mHint != null && mText.length() == 0) {
//            if (mHintTextColor != null) {
                color = mCurHintTextColor;
//            }

            layout = mHintLayout;
        }
        mTextPaint.setColor(color);

        canvas.save();

        final int compoundPaddingLeft = getCompoundPaddingLeft();
        final int compoundPaddingTop = getCompoundPaddingTop();
        final int compoundPaddingRight = getCompoundPaddingRight();
        final int compoundPaddingBottom = getCompoundPaddingBottom();
        final int scrollX = getScrollX();
        final int scrollY = getScrollY();
//        Log.w(TAG, "onDraw: scrollX=" + scrollX + ", scrollY=" + scrollY);
        final int right = getRight();
        final int left = getLeft();
        final int bottom = getBottom();
        final int top = getTop();
//        final boolean isLayoutRtl = isLayoutRtl();
//        final int offset = getHorizontalOffsetForDrawables();
//        final int leftOffset = isLayoutRtl ? 0 : offset;
//        final int rightOffset = isLayoutRtl ? offset : 0;

        int extendedPaddingTop = getExtendedPaddingTop();
        int extendedPaddingBottom = getExtendedPaddingBottom();

        final int vspace = bottom - top - compoundPaddingBottom - compoundPaddingTop;
        final int maxScrollY = mLayout.getHeight() - vspace;

        float clipLeft = compoundPaddingLeft + scrollX;
        float clipTop = (scrollY == 0) ? 0 : extendedPaddingTop + scrollY;
        float clipRight = right - left - compoundPaddingRight + scrollX;
        float clipBottom = bottom - top + scrollY
                - ((scrollY == maxScrollY) ? 0 : extendedPaddingBottom);

        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);

        int voffsetText = 0;
        int voffsetCursor = 0;

        // translate in by our padding
        /* shortcircuit calling getVerticaOffset() */
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffsetText = getVerticalOffset(false);
            voffsetCursor = getVerticalOffset(true);
        }
        canvas.translate(compoundPaddingLeft, extendedPaddingTop + voffsetText);

        final int layoutDirection = getLayoutDirection();
        final int absoluteGravity = Gravity.getAbsoluteGravity(mGravity, layoutDirection);

        final int cursorOffsetVertical = voffsetCursor - voffsetText;

        Path highlight = getUpdatedHighlightPath();
//        if (mEditor != null) {
        final int selectionStart = getSelectionStart();
        final int selectionEnd = getSelectionEnd();
        if (highlight != null && selectionStart == selectionEnd && mDrawableForCursor != null) {
            drawCursor(canvas, cursorOffsetVertical);
            // Rely on the drawable entirely, do not draw the cursor line.
            // Has to be done after the IMM related code above which relies on the highlight.
            highlight = null;
        }
//            mEditor.onDraw(canvas, layout, highlight, mHighlightPaint, cursorOffsetVertical);
//        } else {
            layout.draw(canvas, highlight, mHighlightPaint, cursorOffsetVertical);
//        }

        canvas.restore();
    }
    private void drawCursor(Canvas canvas, int cursorOffsetVertical) {
        Log.w(TAG, "drawCursor: selectionStart=" + getSelectionStart() + ", selectionEnd=" + getSelectionEnd());
        final boolean translate = cursorOffsetVertical != 0;
        if (translate) canvas.translate(0, cursorOffsetVertical);
        if (mDrawableForCursor != null) {
            mDrawableForCursor.draw(canvas);
        }
        if (translate) canvas.translate(0, -cursorOffsetVertical);
    }

    private void assumeLayout() {
        int width = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();

        if (width < 1) {
            width = 0;
        }

        int physicalWidth = width;

        if (mHorizontallyScrolling) {
            width = VERY_WIDE;
        }

        makeNewLayout(width, physicalWidth);
    }
    public void makeNewLayout(int wantWidth, int hintWidth) {
        mHighlightPathBogus = true;

        if (wantWidth < 0) {
            wantWidth = 0;
        }
        if (hintWidth < 0) {
            hintWidth = 0;
        }

        Layout.Alignment alignment = Layout.Alignment.ALIGN_NORMAL;

        mLayout = new DynamicLayout(mText, /*mTransformed*/mText, mTextPaint, wantWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);

        if (mHint != null) {
            mHintLayout = new StaticLayout(mHint, mTextPaint, hintWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);
        } else {
            mHintLayout = null;
        }
    }
    public void nullLayouts() {
        mLayout = mHintLayout = null;
    }

    public int getCompoundPaddingTop() {
        return getPaddingTop();
    }
    public int getCompoundPaddingBottom() {
        return getPaddingBottom();
    }
    public int getCompoundPaddingLeft() {
        return getPaddingLeft();
    }
    public int getCompoundPaddingRight() {
        return getPaddingRight();
    }
    public int getCompoundPaddingStart() {
        switch(getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingLeft();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingRight();
        }
    }
    public int getCompoundPaddingEnd() {
        switch(getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingRight();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingLeft();
        }
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

    private int getBoxHeight(Layout l) {
//        Insets opticalInsets = isLayoutModeOptical(mParent) ? getOpticalInsets() : Insets.NONE;
        int padding = (l == mHintLayout)
                ? getCompoundPaddingTop() + getCompoundPaddingBottom()
                : getExtendedPaddingTop() + getExtendedPaddingBottom();
        // tested creating an EditText and compared getTotalPaddingTop and getExtendedPaddingTop, which were the same, so this optical inset doesn't seem to apply
        return getMeasuredHeight() - padding/* + opticalInsets.top + opticalInsets.bottom*/;
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
                if (gravity == Gravity.TOP)
                    voffset = boxht - textht;
                else // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
            }
        }
        return voffset;
    }

    private void setText(CharSequence text) {
        if (text == null) {
            text = "";
        }
        Editable t = Editable.Factory.getInstance().newEditable(text);
        text = t;

        InputMethodManager imm = getInputMethodManager();
        if (imm != null) {
            imm.restartInput(this);
        }

        mText = t;
        mSpannable = (t instanceof Spannable) ? (Spannable) t : null;

        if (mTransformation == null) {
            mTransformed = text;
        } else {
            mTransformed = mTransformation.getTransformation(text, this);
        }
        if (mTransformed == null) {
            // Should not happen if the transformation method follows the non-null postcondition.
            mTransformed = "";
        }

        final int textLength = text.length();

        if (text instanceof Spannable) {
            Spannable sp = (Spannable) text;

            // Remove any ChangeWatchers that might have come from other TextViews.
            final ChangeWatcher[] watchers = sp.getSpans(0, sp.length(), ChangeWatcher.class);
            final int count = watchers.length;
            for (int i = 0; i < count; i++) {
                sp.removeSpan(watchers[i]);
            }

            if (mChangeWatcher == null) mChangeWatcher = new ChangeWatcher();

            sp.setSpan(mChangeWatcher, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE |
                    (CHANGE_WATCHER_PRIORITY << Spanned.SPAN_PRIORITY_SHIFT));

            if (mEditor != null) mEditor.addSpanWatchers(sp);

            if (mTransformation != null) {
                sp.setSpan(mTransformation, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            if (mMovement != null) {
                mMovement.initialize(this, (Spannable) text);

                /*
                 * Initializing the movement method will have set the
                 * selection, so reset mSelectionMoved to keep that from
                 * interfering with the normal on-focus selection-setting.
                 */
                /*if (mEditor != null) mEditor.*/mSelectionMoved = false;
            }
        }

        if (mLayout != null) {
//            checkForRelayout();
        }

        sendOnTextChanged(text, 0, /*oldlen*/0, textLength);
        onTextChanged(text, 0, /*oldlen*/0, textLength);

//        notifyViewAccessibilityStateChangedIfNeeded(AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT);
//
//        if (needEditableForNotification) {
//            sendAfterTextChanged((Editable) text);
//        } else {
//            notifyListeningManagersAfterTextChanged();
//        }
//
//        // SelectionModifierCursorController depends on textCanBeSelected, which depends on text
//        if (mEditor != null) mEditor.prepareCursorControllers();
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
//        mAllowTransformationLengthChange = false;
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

    private static boolean isMultilineInputType(int type) {
        return (type & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE))
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
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

        // Will change text color
//        if (mEditor != null) {
////            mEditor.invalidateTextDisplayList();
//            mEditor.prepareCursorControllers();

            // start or stop the cursor blinking as appropriate
            /*mEditor.*/makeBlink();
//        }
    }

    private int sp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
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
//        if (!isFromPrimePointer(event, false)) {
//            return true;
//        }
//
        final int action = event.getActionMasked();
////        if (mEditor != null) {
////            mEditor.onTouchEvent(event);
//        editorOnTouchEvent(event);
////
////            if (mEditor.mInsertionPointCursorController != null
////                    && mEditor.mInsertionPointCursorController.isCursorBeingModified()) {
////                return true;
////            }
//            if (/*mEditor.*/mSelectionModifierCursorController != null
//                    && /*mEditor.*/mSelectionModifierCursorController.isDragAcceleratorActive()) {
//                return true;
//            }
////        }
////
        if (mTouchManager.onTouchEventPre(event)) {
            return true;
        }

        final boolean superResult = super.onTouchEvent(event);
////        if (DEBUG_CURSOR) {
////            logCursor("onTouchEvent", "superResult=%s", superResult);
////        }
////
////        /*
////         * Don't handle the release after a long press, because it will move the selection away from
////         * whatever the menu action was trying to affect. If the long press should have triggered an
////         * insertion action mode, we can now actually show it.
////         */
////        if (mEditor != null && mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
////            mEditor.mDiscardNextActionUp = false;
////            if (DEBUG_CURSOR) {
////                logCursor("onTouchEvent", "release after long press detected");
////            }
////            if (mEditor.mIsInsertionActionModeStartPending) {
////                mEditor.startInsertionActionMode();
////                mEditor.mIsInsertionActionModeStartPending = false;
////            }
////            return superResult;
////        }
////
//        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP)
//                /*&& (mEditor == null || !mEditor.mIgnoreActionUpEvent)*/ && isFocused();
////
////        if ((mMovement != null || onCheckIsTextEditor()) && isEnabled()
////                && mText instanceof Spannable && mLayout != null) {
//        boolean handled = false;
////
////            if (mMovement != null) {
////                handled |= mMovement.onTouchEvent(this, mSpannable, event);
////            }
////
//        final boolean textIsSelectable = /*isTextSelectable()*/true;
////            if (touchIsFinished && mLinksClickable && mAutoLinkMask != 0 && textIsSelectable) {
////                // The LinkMovementMethod which should handle taps on links has not been installed
////                // on non editable text that support text selection.
////                // We reproduce its behavior here to open links for these.
////                ClickableSpan[] links = mSpannable.getSpans(getSelectionStart(),
////                        getSelectionEnd(), ClickableSpan.class);
////
////                if (links.length > 0) {
////                    links[0].onClick(this);
////                    handled = true;
////                }
////            }
////
//        if (touchIsFinished && (/*isTextEditable()*/true || textIsSelectable)) {
//            // Show the IME, except when selecting in read-only text.
////            final InputMethodManager imm = getInputMethodManager();
////            viewClicked(imm);
////            if (/*isTextEditable()*/true && /*mEditor.mShowSoftInputOnFocus*/true && imm != null) {
////                imm.showSoftInput(this, 0);
////            }
////
////                // The above condition ensures that the mEditor is not null
////                editorOnTouchUpEvent(event);
////
////            handled = true;
//        }
//
//        if (handled) {
//            return true;
//        }
////        }
////
        Log.w(TAG, "onTouchEvent: isFocused=" + isFocused());

//        return superResult;
        return mTouchManager.onTouchEventPost(event, superResult);
    }

    public void onTouchFinished() {
        final InputMethodManager imm = getInputMethodManager();
        viewClicked(imm);
        if (/*isTextEditable()*/true && /*mEditor.mShowSoftInputOnFocus*/true && imm != null) {
            imm.showSoftInput(this, 0);
        }
    }

    /**
     * Handles touch events on an editable text view, implementing cursor movement, selection, etc.
     */
    public void editorOnTouchEvent(MotionEvent event) {
        final boolean filterOutEvent = shouldFilterOutTouchEvent(event);
        mLastButtonState = event.getButtonState();
        if (filterOutEvent) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                mDiscardNextActionUp = true;
            }
            return;
        }
        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
        mTouchState.update(event, viewConfiguration);
//        updateFloatingToolbarVisibility(event);
//
//        if (hasInsertionController()) {
//            getInsertionController().onTouchEvent(event);
//        }
//        if (hasSelectionController()) {
//            getSelectionController().onTouchEvent(event);
//        }
//
//        if (mShowSuggestionRunnable != null) {
//            mTextView.removeCallbacks(mShowSuggestionRunnable);
//            mShowSuggestionRunnable = null;
//        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Reset this state; it will be re-set if super.onTouchEvent
            // causes focus to move to the view.
            mTouchFocusSelected = false;
            mIgnoreActionUpEvent = false;
        }
    }
//    void editorOnTouchUpEvent(MotionEvent event) {
////        if (TextView.DEBUG_CURSOR) {
////            logCursor("onTouchUpEvent", null);
////        }
//        if (getSelectionActionModeHelper().resetSelection(
//                /*getTextView().*/getOffsetForPosition(event.getX(), event.getY()))) {
//            return;
//        }
//
//        boolean selectAllGotFocus = mSelectAllOnFocus && /*mTextView.*/didTouchFocusSelect();
//        hideCursorAndSpanControllers();
//        stopTextActionMode();
//        CharSequence text = mText;
//        if (!selectAllGotFocus && text.length() > 0) {
//            // Move cursor
//            final int offset = getOffsetForPosition(event.getX(), event.getY());
//
//            final boolean shouldInsertCursor = !mRequestingLinkActionMode;
//            if (shouldInsertCursor) {
//                Selection.setSelection((Spannable) text, offset);
////                if (mSpellChecker != null) {
////                    // When the cursor moves, the word that was typed may need spell check
////                    mSpellChecker.onSelectionChanged();
////                }
//            }
//
////            if (!extractedTextModeWillBeStarted()) {
////                if (isCursorInsideEasyCorrectionSpan()) {
////                    // Cancel the single tap delayed runnable.
////                    if (mInsertionActionModeRunnable != null) {
////                        removeCallbacks(mInsertionActionModeRunnable);
////                    }
////
////                    mShowSuggestionRunnable = this::replace;
////
////                    // removeCallbacks is performed on every touch
////                    postDelayed(mShowSuggestionRunnable,
////                            ViewConfiguration.getDoubleTapTimeout());
////                } else if (hasInsertionController()) {
////                    if (shouldInsertCursor) {
////                        getInsertionController().show();
////                    } else {
////                        getInsertionController().hide();
////                    }
////                }
////            }
//        }
//    }


//    private SelectionActionModeHelper getSelectionActionModeHelper() {
//        if (mSelectionActionModeHelper == null) {
//            mSelectionActionModeHelper = new SelectionActionModeHelper(this);
//        }
//        return mSelectionActionModeHelper;
//    }

//    /** Returns the controller for selection. */
//    public @Nullable SelectionModifierCursorController getSelectionController() {
////        if (!mSelectionControllerEnabled) {
////            return null;
////        }
//
//        if (mSelectionModifierCursorController == null) {
//            mSelectionModifierCursorController = new SelectionModifierCursorController(this);
//
//            final ViewTreeObserver observer = getViewTreeObserver();
//            observer.addOnTouchModeChangeListener(mSelectionModifierCursorController);
//        }
//
//        return mSelectionModifierCursorController;
//    }

    private boolean shouldFilterOutTouchEvent(MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return false;
        }
        final boolean primaryButtonStateChanged =
                ((mLastButtonState ^ event.getButtonState()) & MotionEvent.BUTTON_PRIMARY) != 0;
        final int action = event.getActionMasked();
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP)
                && !primaryButtonStateChanged) {
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE
                && !event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
            return true;
        }
        return false;
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

    protected void viewClicked(InputMethodManager imm) {
        if (imm != null) {
            imm.viewClicked(this);
        }
    }

    private int getLastTapPosition() {
        // No need to create the controller at that point, no last tap position saved
//        if (mSelectionModifierCursorController != null) {
//            int lastTapPosition = mSelectionModifierCursorController.getMinTouchOffset();
//            if (lastTapPosition >= 0) {
//                // Safety check, should not be possible.
//                if (lastTapPosition > mText.length()) {
//                    lastTapPosition = mText.length();
//                }
//                return lastTapPosition;
//            }
//        }
        int lastTapPosition = mTouchManager.getMinTouchOffset();
        if (lastTapPosition >= 0) {
            // Safety check, should not be possible.
            if (lastTapPosition > mText.length()) {
                lastTapPosition = mText.length();
            }
            return lastTapPosition;
        }

        return -1;
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

    private int getOffsetAtCoordinate(int line, float x) {
        x = convertToLocalHorizontalCoordinate(x);
        return getLayout().getOffsetForHorizontal(line, x);
    }


    /**
     * Returns true if the screen coordinates position (x,y) corresponds to a character displayed
     * in the view. Returns false when the position is in the empty space of left/right of text.
     */
    public boolean isPositionOnText(float x, float y) {
        Layout layout = mLayout;
        if (layout == null) return false;

        final int line = getLineAtCoordinate(y);
        x = convertToLocalHorizontalCoordinate(x);

        if (x < layout.getLineLeft(line)) return false;
        if (x > layout.getLineRight(line)) return false;
        return true;
    }


    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        Log.w(TAG, "onFocusChanged: focused=" + focused + ", direction=" + direction);
//        if (mTemporaryDetach) {
//            // If we are temporarily in the detach state, then do nothing.
//            super.onFocusChanged(focused, direction, previouslyFocusedRect);
//            return;
//        }

//        if (mEditor != null) mEditor.onFocusChanged(focused, direction);
        mShowCursor = SystemClock.uptimeMillis();
        mEditor.ensureEndedBatchEdit();
        if (focused) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            // SelectAllOnFocus fields are highlighted and not selected. Do not start text selection
            // mode for these, unless there was a specific selection already started.
            final boolean isFocusHighlighted = mSelectAllOnFocus && selStart == 0
                    && selEnd == mText.length();

            mCreatedWithASelection = mFrozenWithFocus && hasSelection()
                    && !isFocusHighlighted;

            if (!mFrozenWithFocus || (selStart < 0 || selEnd < 0)) {
                // If a tap was used to give focus to that view, move cursor at tap position.
                // Has to be done before onTakeFocus, which can be overloaded.
                final int lastTapPosition = getLastTapPosition();
                if (lastTapPosition >= 0) {
                    Log.w(TAG, String.format("onFocusChanged: setting cursor position: %d", lastTapPosition));
                    Selection.setSelection((Spannable) mText, lastTapPosition);
                }

                // Note this may have to be moved out of the Editor class
//                MovementMethod mMovement = mTextView.getMovementMethod();
                if (mMovement != null) {
                    mMovement.onTakeFocus(this, (Spannable) mText, direction);
                }

                // The DecorView does not have focus when the 'Done' ExtractEditText button is
                // pressed. Since it is the ViewAncestor's mView, it requests focus before
                // ExtractEditText clears focus, which gives focus to the ExtractEditText.
                // This special case ensure that we keep current selection in that case.
                // It would be better to know why the DecorView does not have focus at that time.
                if (((isInExtractedMode()) || mSelectionMoved)
                        && selStart >= 0 && selEnd >= 0) {
                    /*
                     * Someone intentionally set the selection, so let them
                     * do whatever it is that they wanted to do instead of
                     * the default on-focus behavior.  We reset the selection
                     * here instead of just skipping the onTakeFocus() call
                     * because some movement methods do something other than
                     * just setting the selection in theirs and we still
                     * need to go through that path.
                     */
                    Selection.setSelection((Spannable) mText, selStart, selEnd);
                }

                if (mSelectAllOnFocus) {
                    selectAllText();
                }

                mTouchFocusSelected = true;
            }

            mFrozenWithFocus = false;
            mSelectionMoved = false;
//
//            if (mError != null) {
//                showError();
//            }
//
            makeBlink();
        } else {
//            if (mError != null) {
//                hideError();
//            }
            // Don't leave us in the middle of a batch edit.
            onEndBatchEdit();

//            if (mTextView.isInExtractedMode()) {
//                hideCursorAndSpanControllers();
//                stopTextActionModeWithPreservingSelection();
//            } else {
//                hideCursorAndSpanControllers();
//                if (mTextView.isTemporarilyDetached()) {
//                    stopTextActionModeWithPreservingSelection();
//                } else {
//                    stopTextActionMode();
//                }
//                downgradeEasyCorrectionSpans();
//            }
//            // No need to create the controller
//            if (mSelectionModifierCursorController != null) {
//                mSelectionModifierCursorController.resetTouchOffsets();
//            }
//
//            ensureNoSelectionIfNonSelectable();
        }

        if (focused) {
            if (mText instanceof Spannable) {
                Spannable sp = (Spannable) mText;
                MetaKeyKeyListener.resetMetaState(sp);
            }
        }

        if (mTransformation != null) {
            mTransformation.onFocusChanged(this, mText, focused, direction, previouslyFocusedRect);
        }

        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * @return True when the TextView isFocused and has a valid zero-length selection (cursor).
     */
    private boolean shouldBlink() {
        if (!isCursorVisible() || !isFocused()) return false;

        final int start = getSelectionStart();
        if (start < 0) return false;

        final int end = getSelectionEnd();
        if (end < 0) return false;

        return start == end;
    }

    private void makeBlink() {
        if (shouldBlink()) {
//            Log.w(TAG, "makeBlink shouldBlink");
            mShowCursor = SystemClock.uptimeMillis();
            if (mBlink == null) mBlink = new Blink();
            removeCallbacks(mBlink);
            postDelayed(mBlink, BLINK);
        } else {
            Log.w(TAG, "makeBlink not shouldBlink");
            if (mBlink != null) removeCallbacks(mBlink);
        }
    }

    private void suspendBlink() {
        if (mBlink != null) {
            mBlink.cancel();
        }
    }

    private void resumeBlink() {
        if (mBlink != null) {
            mBlink.uncancel();
            makeBlink();
        }
    }

    private boolean isCursorVisible() {
        // The default value is true, even when there is no associated Editor
//        return mCursorVisible && isTextEditable();
        return true;
    }

    boolean shouldRenderCursor() {
        if (!isCursorVisible()) {
            return false;
        }
        if (mRenderCursorRegardlessTiming) {
            return true;
        }
        final long showCursorDelta = SystemClock.uptimeMillis() - mShowCursor;
        return showCursorDelta % (2 * BLINK) < BLINK;
    }

    private class Blink implements Runnable {
        private boolean mCancelled;

        public void run() {
            if (mCancelled) {
                return;
            }

            removeCallbacks(this);

            if (shouldBlink()) {
                if (mLayout != null) {
//                    Log.w(TAG, "Blink invalidateCursorPath");
                    invalidateCursorPath();
                }

                postDelayed(this, BLINK);
            }
        }

        void cancel() {
            if (!mCancelled) {
                removeCallbacks(this);
                mCancelled = true;
            }
        }

        void uncancel() {
            mCancelled = false;
        }
    }

    private Path getUpdatedHighlightPath() {
        Path highlight = null;
        Paint highlightPaint = mHighlightPaint;

        final int selStart = getSelectionStart();
        final int selEnd = getSelectionEnd();
        if (mMovement != null && (isFocused() || isPressed()) && selStart >= 0) {
            if (selStart == selEnd) {
                if (/*mEditor != null && mEditor.*/shouldRenderCursor()) {
                    if (mHighlightPathBogus) {
                        if (mHighlightPath == null) mHighlightPath = new Path();
                        mHighlightPath.reset();
                        mLayout.getCursorPath(selStart, mHighlightPath, mText);
                        /*mEditor.*/updateCursorPosition();
                        mHighlightPathBogus = false;
                    }

                    // XXX should pass to skin instead of drawing directly
                    highlightPaint.setColor(mCurTextColor);
                    highlightPaint.setStyle(Paint.Style.STROKE);
                    highlight = mHighlightPath;
                }
            } else {
                if (mHighlightPathBogus) {
                    if (mHighlightPath == null) mHighlightPath = new Path();
                    mHighlightPath.reset();
                    mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
                    mHighlightPathBogus = false;
                }

                // XXX should pass to skin instead of drawing directly
                highlightPaint.setColor(mHighlightColor);
                highlightPaint.setStyle(Paint.Style.FILL);

                highlight = mHighlightPath;
            }
        }
        return highlight;
    }

    void invalidateCursorPath() {
        if (mHighlightPathBogus) {
            invalidateCursor();
        } else {
            final int horizontalPadding = getCompoundPaddingLeft();
            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

            if (/*mEditor.*/mDrawableForCursor == null) {
                synchronized (TEMP_RECTF) {
                    /*
                     * The reason for this concern about the thickness of the
                     * cursor and doing the floor/ceil on the coordinates is that
                     * some EditTexts (notably textfields in the Browser) have
                     * anti-aliased text where not all the characters are
                     * necessarily at integer-multiple locations.  This should
                     * make sure the entire cursor gets invalidated instead of
                     * sometimes missing half a pixel.
                     */
                    float thick = (float) Math.ceil(mTextPaint.getStrokeWidth());
                    if (thick < 1.0f) {
                        thick = 1.0f;
                    }

                    thick /= 2.0f;

                    // mHighlightPath is guaranteed to be non null at that point.
                    mHighlightPath.computeBounds(TEMP_RECTF, false);

                    invalidate((int) Math.floor(horizontalPadding + TEMP_RECTF.left - thick),
                            (int) Math.floor(verticalPadding + TEMP_RECTF.top - thick),
                            (int) Math.ceil(horizontalPadding + TEMP_RECTF.right + thick),
                            (int) Math.ceil(verticalPadding + TEMP_RECTF.bottom + thick));
                }
            } else {
                final Rect bounds = /*mEditor.*/mDrawableForCursor.getBounds();
                invalidate(bounds.left + horizontalPadding, bounds.top + verticalPadding,
                        bounds.right + horizontalPadding, bounds.bottom + verticalPadding);
            }
        }
    }

    void invalidateCursor() {
        int where = getSelectionEnd();

        invalidateCursor(where, where, where);
    }

    private void invalidateCursor(int a, int b, int c) {
        if (a >= 0 || b >= 0 || c >= 0) {
            int start = Math.min(Math.min(a, b), c);
            int end = Math.max(Math.max(a, b), c);
            invalidateRegion(start, end, true /* Also invalidates blinking cursor */);
        }
    }

    /**
     * Invalidates the region of text enclosed between the start and end text offsets.
     */
    void invalidateRegion(int start, int end, boolean invalidateCursor) {
        if (mLayout == null) {
            invalidate();
        } else {
            int lineStart = mLayout.getLineForOffset(start);
            int top = mLayout.getLineTop(lineStart);

            // This is ridiculous, but the descent from the line above
            // can hang down into the line we really want to redraw,
            // so we have to invalidate part of the line above to make
            // sure everything that needs to be redrawn really is.
            // (But not the whole line above, because that would cause
            // the same problem with the descenders on the line above it!)
            if (lineStart > 0) {
                top -= mLayout.getLineDescent(lineStart - 1);
            }

            int lineEnd;

            if (start == end) {
                lineEnd = lineStart;
            } else {
                lineEnd = mLayout.getLineForOffset(end);
            }

            int bottom = mLayout.getLineBottom(lineEnd);

            // mEditor can be null in case selection is set programmatically.
            if (invalidateCursor && /*mEditor != null && mEditor.*/mDrawableForCursor != null) {
                final Rect bounds = /*mEditor.*/mDrawableForCursor.getBounds();
                top = Math.min(top, bounds.top);
                bottom = Math.max(bottom, bounds.bottom);
            }

            final int compoundPaddingLeft = getCompoundPaddingLeft();
            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

            int left, right;
            if (lineStart == lineEnd && !invalidateCursor) {
                left = (int) mLayout.getPrimaryHorizontal(start);
                right = (int) (mLayout.getPrimaryHorizontal(end) + 1.0);
                left += compoundPaddingLeft;
                right += compoundPaddingLeft;
            } else {
                // Rectangle bounding box when the region spans several lines
                left = compoundPaddingLeft;
                right = getWidth() - getCompoundPaddingRight();
            }

            invalidate(getScrollX() + left, verticalPadding + top,
                    getScrollX() + right, verticalPadding + bottom);
        }
    }






    void loadCursorDrawable() {
        if (mDrawableForCursor == null && mCursorDrawableRes != 0) {
//            mDrawableForCursor = getContext().getDrawable(mCursorDrawableRes);
            mDrawableForCursor = ContextCompat.getDrawable(getContext(), mCursorDrawableRes);
            Log.w(TAG, "mDrawableForCursor=" + mDrawableForCursor);
        }
    }

    private void updateCursorPosition(int top, int bottom, float horizontal) {
        loadCursorDrawable();
        final int left = clampHorizontalPosition(mDrawableForCursor, horizontal);
        final int width = mDrawableForCursor.getIntrinsicWidth();
        Log.w(TAG, String.format("updateCursorPosition: left=%s, top=%s", left, (top - mTempRect.top)));
        mDrawableForCursor.setBounds(left, top - mTempRect.top, left + width,
                bottom + mTempRect.bottom);
    }
    void updateCursorPosition() {
        loadCursorDrawable();
        if (mDrawableForCursor == null) {
            return;
        }

        final Layout layout = mLayout;
        final int offset = getSelectionStart();
        final int line = layout.getLineForOffset(offset);
        final int top = layout.getLineTop(line);
        final int bottom = /*layout.getLineBottomWithoutSpacing(line)*/layout.getLineBottom(line);//hidden - hopefully this is good enough

//        final boolean clamped = layout.shouldClampCursor(line);
        updateCursorPosition(top, bottom, layout.getPrimaryHorizontal(offset/*, clamped*/));
    }

    /**
     * Return clamped position for the drawable. If the drawable is within the boundaries of the
     * view, then it is offset with the left padding of the cursor drawable. If the drawable is at
     * the beginning or the end of the text then its drawable edge is aligned with left or right of
     * the view boundary. If the drawable is null, horizontal parameter is aligned to left or right
     * of the view.
     *
     * @param drawable Drawable. Can be null.
     * @param horizontal Horizontal position for the drawable.
     * @return The clamped horizontal position for the drawable.
     */
    private int clampHorizontalPosition(@Nullable final Drawable drawable, float horizontal) {
        horizontal = Math.max(0.5f, horizontal - 0.5f);
        if (mTempRect == null) mTempRect = new Rect();

        int drawableWidth = 0;
        if (drawable != null) {
            drawable.getPadding(mTempRect);
            drawableWidth = drawable.getIntrinsicWidth();
        } else {
            mTempRect.setEmpty();
        }

        int scrollX = getScrollX();
        float horizontalDiff = horizontal - scrollX;
        int viewClippedWidth = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();

        final int left;
        if (horizontalDiff >= (viewClippedWidth - 1f)) {
            // at the rightmost position
            left = viewClippedWidth + scrollX - (drawableWidth - mTempRect.right);
        } else if (Math.abs(horizontalDiff) <= 1f
                || (TextUtils.isEmpty(mText)
                && (VERY_WIDE - scrollX) <= (viewClippedWidth + 1f)
                && horizontal <= 1f)) {
            // at the leftmost position
            left = scrollX - mTempRect.left;
        } else {
            left = (int) horizontal - mTempRect.left;
        }
        return left;
    }

    /**
     * Convenience for {@link Selection#getSelectionStart}.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public int getSelectionStart() {
        return Selection.getSelectionStart(mText);
    }

    /**
     * Convenience for {@link Selection#getSelectionEnd}.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public int getSelectionEnd() {
        return Selection.getSelectionEnd(mText);
    }

    /**
     * Return true iff there is a selection of nonzero length inside this text view.
     */
    public boolean hasSelection() {
        final int selectionStart = getSelectionStart();
        final int selectionEnd = getSelectionEnd();

        return selectionStart >= 0 && selectionEnd > 0 && selectionStart != selectionEnd;
    }

    boolean canSelectAllText() {
        return /*canSelectText() &&*//* !hasPasswordTransformationMethod()
                &&*/ !(getSelectionStart() == 0 && getSelectionEnd() == mText.length());
    }

    boolean selectAllText() {
//        if (mEditor != null) {
//            // Hide the toolbar before changing the selection to avoid flickering.
//            hideFloatingToolbar(FLOATING_TOOLBAR_SELECT_ALL_REFRESH_DELAY);
//        }
        final int length = mText.length();
        Selection.setSelection(mSpannable, 0, length);
        return length > 0;
    }

    /**
     * @return {@code true} if this TextView is specialized for showing and interacting with the
     * extracted text in a full-screen input method.
     * @hide
     */
    public boolean isInExtractedMode() {
        return false;
    }





    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int which = doKeyDown(keyCode, event, null);
        if (which == KEY_EVENT_NOT_HANDLED) {
            return super.onKeyDown(keyCode, event);
        }

        return true;
    }

    private int doKeyDown(int keyCode, KeyEvent event, KeyEvent otherEvent) {
        if (!isEnabled()) {
            return KEY_EVENT_NOT_HANDLED;
        }

        // If this is the initial keydown, we don't want to prevent a movement away from this view.
        // While this shouldn't be necessary because any time we're preventing default movement we
        // should be restricting the focus to remain within this view, thus we'll also receive
        // the key up event, occasionally key up events will get dropped and we don't want to
        // prevent the user from traversing out of this on the next key down.
        if (event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(keyCode)) {
            mPreventDefaultMovement = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.hasNoModifiers()) {
//                    // When mInputContentType is set, we know that we are
//                    // running in a "modern" cupcake environment, so don't need
//                    // to worry about the application trying to capture
//                    // enter key events.
//                    if (mEditor != null && mEditor.mInputContentType != null) {
//                        // If there is an action listener, given them a
//                        // chance to consume the event.
//                        if (mEditor.mInputContentType.onEditorActionListener != null
//                                && mEditor.mInputContentType.onEditorActionListener.onEditorAction(
//                                this, EditorInfo.IME_NULL, event)) {
//                            mEditor.mInputContentType.enterDown = true;
//                            // We are consuming the enter key for them.
//                            return KEY_EVENT_HANDLED;
//                        }
//                    }

                    // If our editor should move focus when enter is pressed, or
                    // this is a generated event from an IME action button, then
                    // don't let it be inserted into the text.
                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
                            || shouldAdvanceFocusOnEnter()) {
                        if (hasOnClickListeners()) {
                            return KEY_EVENT_NOT_HANDLED;
                        }
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    if (shouldAdvanceFocusOnEnter()) {
                        return KEY_EVENT_NOT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers() || event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                    // Tab is used to move focus.
                    return KEY_EVENT_NOT_HANDLED;
                }
                break;

//            // Has to be done on key down (and not on key up) to correctly be intercepted.
//            case KeyEvent.KEYCODE_BACK:
//                if (mEditor != null && mEditor.getTextActionMode() != null) {
//                    stopTextActionMode();
//                    return KEY_EVENT_HANDLED;
//                }
//                break;
//
//            case KeyEvent.KEYCODE_CUT:
//                if (event.hasNoModifiers() && canCut()) {
//                    if (onTextContextMenuItem(ID_CUT)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
//
//            case KeyEvent.KEYCODE_COPY:
//                if (event.hasNoModifiers() && canCopy()) {
//                    if (onTextContextMenuItem(ID_COPY)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
//
//            case KeyEvent.KEYCODE_PASTE:
//                if (event.hasNoModifiers() && canPaste()) {
//                    if (onTextContextMenuItem(ID_PASTE)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
//
//            case KeyEvent.KEYCODE_FORWARD_DEL:
//                if (event.hasModifiers(KeyEvent.META_SHIFT_ON) && canCut()) {
//                    if (onTextContextMenuItem(ID_CUT)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
//
//            case KeyEvent.KEYCODE_INSERT:
//                if (event.hasModifiers(KeyEvent.META_CTRL_ON) && canCopy()) {
//                    if (onTextContextMenuItem(ID_COPY)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                } else if (event.hasModifiers(KeyEvent.META_SHIFT_ON) && canPaste()) {
//                    if (onTextContextMenuItem(ID_PASTE)) {
//                        return KEY_EVENT_HANDLED;
//                    }
//                }
//                break;
        }

        if (/*mEditor != null &&*/ mEditor.mKeyListener != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    beginBatchEdit();
                    final boolean handled = mEditor.mKeyListener.onKeyOther(this, (Editable) mText,
                            otherEvent);
//                    hideErrorIfUnchanged();
                    doDown = false;
                    if (handled) {
                        return KEY_EVENT_HANDLED;
                    }
                } catch (AbstractMethodError e) {
                    // onKeyOther was added after 1.0, so if it isn't
                    // implemented we need to try to dispatch as a regular down.
                } finally {
                    endBatchEdit();
                }
            }

            if (doDown) {
                beginBatchEdit();
                final boolean handled = mEditor.mKeyListener.onKeyDown(this, (Editable) mText,
                        keyCode, event);
                endBatchEdit();
//                hideErrorIfUnchanged();
                if (handled) return KEY_DOWN_HANDLED_BY_KEY_LISTENER;
            }
        }

        // bug 650865: sometimes we get a key event before a layout.
        // don't try to move around if we don't know the layout.

        if (mMovement != null && mLayout != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    boolean handled = mMovement.onKeyOther(this, mSpannable, otherEvent);
                    doDown = false;
                    if (handled) {
                        return KEY_EVENT_HANDLED;
                    }
                } catch (AbstractMethodError e) {
                    // onKeyOther was added after 1.0, so if it isn't
                    // implemented we need to try to dispatch as a regular down.
                }
            }
            if (doDown) {
                if (mMovement.onKeyDown(this, mSpannable, keyCode, event)) {
                    if (event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(keyCode)) {
                        mPreventDefaultMovement = true;
                    }
                    return KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD;
                }
            }
            // Consume arrows from keyboard devices to prevent focus leaving the editor.
            // DPAD/JOY devices (Gamepads, TV remotes) often lack a TAB key so allow those
            // to move focus with arrows.
            if (event.getSource() == InputDevice.SOURCE_KEYBOARD
                    && isDirectionalNavigationKey(keyCode)) {
                return KEY_EVENT_HANDLED;
            }
        }

        return mPreventDefaultMovement && !KeyEvent.isModifierKey(keyCode)
                ? KEY_EVENT_HANDLED : KEY_EVENT_NOT_HANDLED;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!isEnabled()) {
            return super.onKeyUp(keyCode, event);
        }

        if (!KeyEvent.isModifierKey(keyCode)) {
            mPreventDefaultMovement = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    /*
                     * If there is a click listener, just call through to
                     * super, which will invoke it.
                     *
                     * If there isn't a click listener, try to show the soft
                     * input method.  (It will also
                     * call performClick(), but that won't do anything in
                     * this case.)
                     */
                    if (!hasOnClickListeners()) {
                        if (mMovement != null && mText instanceof Editable
                                && mLayout != null && onCheckIsTextEditor()) {
                            InputMethodManager imm = getInputMethodManager();
                            viewClicked(imm);
                            if (imm != null/* && getShowSoftInputOnFocus()*/) {
                                imm.showSoftInput(this, 0);
                            }
                        }
                    }
                }
                return super.onKeyUp(keyCode, event);

            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.hasNoModifiers()) {
//                    if (mEditor != null && mEditor.mInputContentType != null
//                            && mEditor.mInputContentType.onEditorActionListener != null
//                            && mEditor.mInputContentType.enterDown) {
//                        mEditor.mInputContentType.enterDown = false;
//                        if (mEditor.mInputContentType.onEditorActionListener.onEditorAction(
//                                this, EditorInfo.IME_NULL, event)) {
//                            return true;
//                        }
//                    }

                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
                            || shouldAdvanceFocusOnEnter()) {
                        /*
                         * If there is a click listener, just call through to
                         * super, which will invoke it.
                         *
                         * If there isn't a click listener, try to advance focus,
                         * but still call through to super, which will reset the
                         * pressed state and longpress state.  (It will also
                         * call performClick(), but that won't do anything in
                         * this case.)
                         */
                        if (!hasOnClickListeners()) {
                            View v = focusSearch(FOCUS_DOWN);

                            if (v != null) {
                                if (!v.requestFocus(FOCUS_DOWN)) {
                                    throw new IllegalStateException("focus search returned a view "
                                            + "that wasn't able to take focus!");
                                }

                                /*
                                 * Return true because we handled the key; super
                                 * will return false because there was no click
                                 * listener.
                                 */
                                super.onKeyUp(keyCode, event);
                                return true;
                            } else if ((event.getFlags()
                                    & KeyEvent.FLAG_EDITOR_ACTION) != 0) {
                                // No target for next focus, but make sure the IME
                                // if this came from it.
                                InputMethodManager imm = getInputMethodManager();
                                if (imm != null && imm.isActive(this)) {
                                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
                                }
                            }
                        }
                    }
                    return super.onKeyUp(keyCode, event);
                }
                break;
        }

        if (/*mEditor != null && */mEditor.mKeyListener != null) {
            if (mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, event)) {
                return true;
            }
        }

        if (mMovement != null && mLayout != null) {
            if (mMovement.onKeyUp(this, mSpannable, keyCode, event)) {
                return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    private boolean isDirectionalNavigationKey(int keyCode) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
        }
        return false;
    }

    /**
     * Returns true if pressing ENTER in this field advances focus instead
     * of inserting the character.  This is true mostly in single-line fields,
     * but also in mail addresses and subjects which will display on multiple
     * lines but where it doesn't make sense to insert newlines.
     */
    private boolean shouldAdvanceFocusOnEnter() {
//        if (getKeyListener() == null) {
//            return false;
//        }
//
//        if (mSingleLine) {
//            return true;
//        }
//
//        if (mEditor != null
//                && (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS)
//                == EditorInfo.TYPE_CLASS_TEXT) {
//            int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
//            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
//                    || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
//                return true;
//            }
//        }

        return false;
    }





    //TODO: copy over onLayout?

    private boolean isShowingHint() {
        return TextUtils.isEmpty(mText) && !TextUtils.isEmpty(mHint);
    }

    /**
     * Move the point, specified by the offset, into the view if it is needed.
     * This has to be called after layout. Returns true if anything changed.
     */
    public boolean bringPointIntoView(int offset) {
        if (isLayoutRequested()) {
            mDeferScroll = offset;
            return false;
        }
        boolean changed = false;

        Layout layout = isShowingHint() ? mHintLayout : mLayout;

        if (layout == null) return changed;

        int line = layout.getLineForOffset(offset);

        int grav;

        switch (layout.getParagraphAlignment(line)) {
//            case ALIGN_LEFT:
//                grav = 1;
//                break;
//            case ALIGN_RIGHT:
//                grav = -1;
//                break;
            case ALIGN_NORMAL:
                grav = layout.getParagraphDirection(line);
                break;
            case ALIGN_OPPOSITE:
                grav = -layout.getParagraphDirection(line);
                break;
            case ALIGN_CENTER:
            default:
                grav = 0;
                break;
        }

        // We only want to clamp the cursor to fit within the layout width
        // in left-to-right modes, because in a right to left alignment,
        // we want to scroll to keep the line-right on the screen, as other
        // lines are likely to have text flush with the right margin, which
        // we want to keep visible.
        // A better long-term solution would probably be to measure both
        // the full line and a blank-trimmed version, and, for example, use
        // the latter measurement for centering and right alignment, but for
        // the time being we only implement the cursor clamping in left to
        // right where it is most likely to be annoying.
        final boolean clamped = grav > 0;
        // FIXME: Is it okay to truncate this, or should we round?
        final int x = (int) layout.getPrimaryHorizontal(offset/*, clamped*/);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineTop(line + 1);

        int left = (int) Math.floor(layout.getLineLeft(line));
        int right = (int) Math.ceil(layout.getLineRight(line));
        int ht = layout.getHeight();

        int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        if (!mHorizontallyScrolling && right - left > hspace && right > x) {
            // If cursor has been clamped, make sure we don't scroll.
            right = Math.max(x, left + hspace);
        }

        int hslack = (bottom - top) / 2;
        int vslack = hslack;

        if (vslack > vspace / 4) {
            vslack = vspace / 4;
        }
        if (hslack > hspace / 4) {
            hslack = hspace / 4;
        }

        int hs = getScrollX();
        int vs = getScrollY();

        if (top - vs < vslack) {
            vs = top - vslack;
        }
        if (bottom - vs > vspace - vslack) {
            vs = bottom - (vspace - vslack);
        }
        if (ht - vs < vspace) {
            vs = ht - vspace;
        }
        if (0 - vs > 0) {
            vs = 0;
        }

        if (grav != 0) {
            if (x - hs < hslack) {
                hs = x - hslack;
            }
            if (x - hs > hspace - hslack) {
                hs = x - (hspace - hslack);
            }
        }

        if (grav < 0) {
            if (left - hs > 0) {
                hs = left;
            }
            if (right - hs < hspace) {
                hs = right - hspace;
            }
        } else if (grav > 0) {
            if (right - hs < hspace) {
                hs = right - hspace;
            }
            if (left - hs > 0) {
                hs = left;
            }
        } else /* grav == 0 */ {
            if (right - left <= hspace) {
                /*
                 * If the entire text fits, center it exactly.
                 */
                hs = left - (hspace - (right - left)) / 2;
            } else if (x > right - hslack) {
                /*
                 * If we are near the right edge, keep the right edge
                 * at the edge of the view.
                 */
                hs = right - hspace;
            } else if (x < left + hslack) {
                /*
                 * If we are near the left edge, keep the left edge
                 * at the edge of the view.
                 */
                hs = left;
            } else if (left > hs) {
                /*
                 * Is there whitespace visible at the left?  Fix it if so.
                 */
                hs = left;
            } else if (right < hs + hspace) {
                /*
                 * Is there whitespace visible at the right?  Fix it if so.
                 */
                hs = right - hspace;
            } else {
                /*
                 * Otherwise, float as needed.
                 */
                if (x - hs < hslack) {
                    hs = x - hslack;
                }
                if (x - hs > hspace - hslack) {
                    hs = x - (hspace - hslack);
                }
            }
        }

        if (hs != getScrollX() || vs != getScrollY()) {
            if (mScroller == null) {
                scrollTo(hs, vs);
            } else {
                long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
                int dx = hs - getScrollX();
                int dy = vs - getScrollY();

                if (duration > ANIMATED_SCROLL_GAP) {
                    mScroller.startScroll(getScrollX(), getScrollY(), dx, dy);
                    awakenScrollBars(mScroller.getDuration());
                    invalidate();
                } else {
                    if (!mScroller.isFinished()) {
                        mScroller.abortAnimation();
                    }

                    scrollBy(dx, dy);
                }

                mLastScroll = AnimationUtils.currentAnimationTimeMillis();
            }

            changed = true;
        }

        if (isFocused()) {
            // This offsets because getInterestingRect() is in terms of viewport coordinates, but
            // requestRectangleOnScreen() is in terms of content coordinates.

            // The offsets here are to ensure the rectangle we are using is
            // within our view bounds, in case the cursor is on the far left
            // or right.  If it isn't withing the bounds, then this request
            // will be ignored.
            if (mTempRect == null) mTempRect = new Rect();
            mTempRect.set(x - 2, top, x + 2, bottom);
            getInterestingRect(mTempRect, line);
            mTempRect.offset(getScrollX(), getScrollY());

            if (requestRectangleOnScreen(mTempRect)) {
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Move the cursor, if needed, so that it is at an offset that is visible
     * to the user.  This will not move the cursor if it represents more than
     * one character (a selection range).  This will only work if the
     * TextView contains spannable text; otherwise it will do nothing.
     *
     * @return True if the cursor was actually moved, false otherwise.
     */
    public boolean moveCursorToVisibleOffset() {
        if (!(mText instanceof Spannable)) {
            return false;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start != end) {
            return false;
        }

        // First: make sure the line is visible on screen:

        int line = mLayout.getLineForOffset(start);

        final int top = mLayout.getLineTop(line);
        final int bottom = mLayout.getLineTop(line + 1);
        final int vspace = getBottom() - getTop() - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int vslack = (bottom - top) / 2;
        if (vslack > vspace / 4) {
            vslack = vspace / 4;
        }
        final int vs = getScrollY();

        if (top < (vs + vslack)) {
            line = mLayout.getLineForVertical(vs + vslack + (bottom - top));
        } else if (bottom > (vspace + vs - vslack)) {
            line = mLayout.getLineForVertical(vspace + vs - vslack - (bottom - top));
        }

        // Next: make sure the character is visible on screen:

        final int hspace = getRight() - getLeft() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        final int hs = getScrollX();
        final int leftChar = mLayout.getOffsetForHorizontal(line, hs);
        final int rightChar = mLayout.getOffsetForHorizontal(line, hspace + hs);

        // line might contain bidirectional text
        final int lowChar = leftChar < rightChar ? leftChar : rightChar;
        final int highChar = leftChar > rightChar ? leftChar : rightChar;

        int newStart = start;
        if (newStart < lowChar) {
            newStart = lowChar;
        } else if (newStart > highChar) {
            newStart = highChar;
        }

        if (newStart != start) {
            Selection.setSelection(mSpannable, newStart);
            return true;
        }

        return false;
    }

    /**
     * Returns true, only while processing a touch gesture, if the initial
     * touch down event caused focus to move to the text view and as a result
     * its selection changed.  Only valid while processing the touch gesture
     * of interest, in an editable text view.
     */
    public boolean didTouchFocusSelect() {
        return /*mEditor != null && mEditor.*/mTouchFocusSelected;
    }

    private void getInterestingRect(Rect r, int line) {
        convertFromViewportToContentCoordinates(r);

        // Rectangle can can be expanded on first and last line to take
        // padding into account.
        // TODO Take left/right padding into account too?
        if (line == 0) r.top -= getExtendedPaddingTop();
        if (line == mLayout.getLineCount() - 1) r.bottom += getExtendedPaddingBottom();
    }

    private void convertFromViewportToContentCoordinates(Rect r) {
        final int horizontalOffset = viewportToContentHorizontalOffset();
        r.left += horizontalOffset;
        r.right += horizontalOffset;

        final int verticalOffset = viewportToContentVerticalOffset();
        r.top += verticalOffset;
        r.bottom += verticalOffset;
    }

    int viewportToContentHorizontalOffset() {
        return getCompoundPaddingLeft() - getScrollX();
    }

    int viewportToContentVerticalOffset() {
        int offset = getExtendedPaddingTop() - getScrollY();
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            offset += getVerticalOffset(false);
        }
        return offset;
    }


    void updateAfterEdit() {
        invalidate();
        int curs = getSelectionStart();

        if (curs >= 0 || (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            //TODO: (EW) is this necessary?
//            registerForPreDraw();
        }

        checkForResize();

        if (curs >= 0) {
            mHighlightPathBogus = true;
            /*if (mEditor != null) mEditor.*/makeBlink();
            bringPointIntoView(curs);
        }
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void handleTextChanged(CharSequence buffer, int start, int before, int after) {
//        sLastCutCopyOrTextChangedTime = 0;

        final Editor.InputMethodState ims = mEditor == null ? null : mEditor.mInputMethodState;
        if (ims == null || ims.mBatchEditNesting == 0) {
            updateAfterEdit();
        }
        if (ims != null) {
            ims.mContentChanged = true;
            if (ims.mChangedStart < 0) {
                ims.mChangedStart = start;
                ims.mChangedEnd = start + before;
            } else {
                ims.mChangedStart = Math.min(ims.mChangedStart, start);
                ims.mChangedEnd = Math.max(ims.mChangedEnd, start + before - ims.mChangedDelta);
            }
            ims.mChangedDelta += after - before;
        }
//        resetErrorChangedFlag();
        sendOnTextChanged(buffer, start, before, after);
        onTextChanged(buffer, start, before, after);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void spanChange(Spanned buf, Object what, int oldStart, int newStart, int oldEnd, int newEnd) {
        // XXX Make the start and end move together if this ends up
        // spending too much time invalidating.

        boolean selChanged = false;
        int newSelStart = -1, newSelEnd = -1;

        final Editor.InputMethodState ims = mEditor == null ? null : mEditor.mInputMethodState;

        if (what == Selection.SELECTION_END) {
            selChanged = true;
            newSelEnd = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                invalidateCursor(Selection.getSelectionStart(buf), oldStart, newStart);
                checkForResize();
//                registerForPreDraw();
                /*if (mEditor != null) mEditor.*/makeBlink();
            }
        }

        if (what == Selection.SELECTION_START) {
            selChanged = true;
            newSelStart = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                int end = Selection.getSelectionEnd(buf);
                invalidateCursor(end, oldStart, newStart);
            }
        }

        if (selChanged) {
            mHighlightPathBogus = true;
            if (mEditor != null && !isFocused()) /*mEditor.*/mSelectionMoved = true;

            if ((buf.getSpanFlags(what) & Spanned.SPAN_INTERMEDIATE) == 0) {
                if (newSelStart < 0) {
                    newSelStart = Selection.getSelectionStart(buf);
                }
                if (newSelEnd < 0) {
                    newSelEnd = Selection.getSelectionEnd(buf);
                }

                if (mEditor != null) {
//                    mEditor.refreshTextActionMode();
//                    if (!hasSelection()
//                            && mEditor.getTextActionMode() == null && hasTransientState()) {
//                        // User generated selection has been removed.
//                        setHasTransientState(false);
//                    }
                }
                onSelectionChanged(newSelStart, newSelEnd);
            }
        }

        if (what instanceof UpdateAppearance || what instanceof ParagraphStyle
                || what instanceof CharacterStyle) {
            if (ims == null || ims.mBatchEditNesting == 0) {
                invalidate();
                mHighlightPathBogus = true;
                checkForResize();
            } else {
                ims.mContentChanged = true;
            }
            if (mEditor != null) {
////                if (oldStart >= 0) mEditor.invalidateTextDisplayList(mLayout, oldStart, oldEnd);
////                if (newStart >= 0) mEditor.invalidateTextDisplayList(mLayout, newStart, newEnd);
//                mEditor.invalidateHandlesAndActionMode();
            }
        }

        if (MetaKeyKeyListener.isMetaTracker(buf, what)) {
            mHighlightPathBogus = true;
            if (ims != null && MetaKeyKeyListener.isSelectingMetaTracker(buf, what)) {
                ims.mSelectionModeChanged = true;
            }

            if (Selection.getSelectionStart(buf) >= 0) {
                if (ims == null || ims.mBatchEditNesting == 0) {
                    invalidateCursor();
                } else {
                    ims.mCursorChanged = true;
                }
            }
        }

        if (what instanceof ParcelableSpan) {
            // If this is a span that can be sent to a remote process,
            // the current extract editor would be interested in it.
            if (ims != null && ims.mExtractedTextRequest != null) {
                if (ims.mBatchEditNesting != 0) {
                    if (oldStart >= 0) {
                        if (ims.mChangedStart > oldStart) {
                            ims.mChangedStart = oldStart;
                        }
                        if (ims.mChangedStart > oldEnd) {
                            ims.mChangedStart = oldEnd;
                        }
                    }
                    if (newStart >= 0) {
                        if (ims.mChangedStart > newStart) {
                            ims.mChangedStart = newStart;
                        }
                        if (ims.mChangedStart > newEnd) {
                            ims.mChangedStart = newEnd;
                        }
                    }
                } else {
                    if (DEBUG_EXTRACT) {
                        Log.v(LOG_TAG, "Span change outside of batch: "
                                + oldStart + "-" + oldEnd + ","
                                + newStart + "-" + newEnd + " " + what);
                    }
                    ims.mContentChanged = true;
                }
            }
        }

//        if (mEditor != null && mEditor.mSpellChecker != null && newStart < 0
//                && what instanceof SpellCheckSpan) {
//            mEditor.mSpellChecker.onSpellCheckSpanRemoved((SpellCheckSpan) what);
//        }
    }

    /**
     * This method is called when the text is changed, in case any subclasses
     * would like to know.
     *
     * Within <code>text</code>, the <code>lengthAfter</code> characters
     * beginning at <code>start</code> have just replaced old text that had
     * length <code>lengthBefore</code>. It is an error to attempt to make
     * changes to <code>text</code> from this callback.
     *
     * @param text The text the TextView is displaying
     * @param start The offset of the start of the range of the text that was
     * modified
     * @param lengthBefore The length of the former text that has been replaced
     * @param lengthAfter The length of the replacement modified text
     */
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        // intentionally empty, template pattern method can be overridden by subclasses
    }

    /**
     * This method is called when the selection has changed, in case any
     * subclasses would like to know.
     * </p>
     * <p class="note"><strong>Note:</strong> Always call the super implementation, which informs
     * the accessibility subsystem about the selection change.
     * </p>
     *
     * @param selStart The new selection start location.
     * @param selEnd The new selection end location.
     */
//    @CallSuper
    protected void onSelectionChanged(int selStart, int selEnd) {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    /**
     * Check whether a change to the existing text layout requires a
     * new view layout.
     */
    private void checkForResize() {
        boolean sizeChanged = false;

        if (mLayout != null) {
            // Check if our width changed
            if (getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                sizeChanged = true;
                invalidate();
            }

            // Check if our height changed
            if (getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                int desiredHeight = getDesiredHeight();

                if (desiredHeight != this.getHeight()) {
                    sizeChanged = true;
                }
            } else if (getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT) {
                if (mDesiredHeightAtMeasure >= 0) {
                    int desiredHeight = getDesiredHeight();

                    if (desiredHeight != mDesiredHeightAtMeasure) {
                        sizeChanged = true;
                    }
                }
            }
        }

        if (sizeChanged) {
            requestLayout();
            // caller will have already invalidated
        }
    }


    /**
     * Adds a TextWatcher to the list of those whose methods are called
     * whenever this TextView's text changes.
     * <p>
     * In 1.0, the {@link TextWatcher#afterTextChanged} method was erroneously
     * not called after {@link #setText} calls.  Now, doing {@link #setText}
     * if there are any text changed listeners forces the buffer type to
     * Editable if it would not otherwise be and does call this method.
     */
    public void addTextChangedListener(TextWatcher watcher) {
        if (mListeners == null) {
            mListeners = new ArrayList<TextWatcher>();
        }

        mListeners.add(watcher);
    }

    /**
     * Removes the specified TextWatcher from the list of those whose
     * methods are called
     * whenever this TextView's text changes.
     */
    public void removeTextChangedListener(TextWatcher watcher) {
        if (mListeners != null) {
            int i = mListeners.indexOf(watcher);

            if (i >= 0) {
                mListeners.remove(i);
            }
        }
    }

    private void sendBeforeTextChanged(CharSequence text, int start, int before, int after) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).beforeTextChanged(text, start, before, after);
            }
        }

        // The spans that are inside or intersect the modified region no longer make sense
//        removeIntersectingNonAdjacentSpans(start, start + before, SpellCheckSpan.class);
//        removeIntersectingNonAdjacentSpans(start, start + before, SuggestionSpan.class);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void sendAfterTextChanged(Editable text) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).afterTextChanged(text);
            }
        }

//        notifyListeningManagersAfterTextChanged();

//        hideErrorIfUnchanged();
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void sendOnTextChanged(CharSequence text, int start, int before, int after) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).onTextChanged(text, start, before, after);
            }
        }

        /*if (mEditor != null) mEditor.*/sendOnTextChanged(start, before, after);
    }

    void sendOnTextChanged(int start, int before, int after) {
//        getSelectionActionModeHelper().onTextChanged(start, start + before);
//        updateSpellCheckSpans(start, start + after, false);

        // Flip flag to indicate the word iterator needs to have the text reset.
//        mUpdateWordIteratorText = true;

        // Hide the controllers as soon as text is modified (typing, procedural...)
        // We do not hide the span controllers, since they can be added when a new text is
        // inserted into the text view (voice IME).
//        hideCursorControllers();
        // Reset drag accelerator.
//        if (mSelectionModifierCursorController != null) {
//            mSelectionModifierCursorController.resetTouchOffsets();
//        }
//        stopTextActionMode();
    }

    static final String LOG_TAG = ChangeWatcher.class.getSimpleName();
    static final boolean DEBUG_EXTRACT = false;
    private class ChangeWatcher implements TextWatcher, SpanWatcher {

        private CharSequence mBeforeText;

        public void beforeTextChanged(CharSequence buffer, int start,
                                      int before, int after) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "beforeTextChanged start=" + start
                        + " before=" + before + " after=" + after + ": " + buffer);
            }

//            if (AccessibilityManager.getInstance(mContext).isEnabled() && (mTransformed != null)) {
//                mBeforeText = mTransformed.toString();
//            }

            CustomEditTextView.this.sendBeforeTextChanged(buffer, start, before, after);
        }

        public void onTextChanged(CharSequence buffer, int start, int before, int after) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onTextChanged start=" + start
                        + " before=" + before + " after=" + after + ": " + buffer);
            }
            CustomEditTextView.this.handleTextChanged(buffer, start, before, after);

//            if (AccessibilityManager.getInstance(mContext).isEnabled()
//                    && (isFocused() || isSelected() && isShown())) {
//                sendAccessibilityEventTypeViewTextChanged(mBeforeText, start, before, after);
//                mBeforeText = null;
//            }
        }

        public void afterTextChanged(Editable buffer) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "afterTextChanged: " + buffer);
            }
            CustomEditTextView.this.sendAfterTextChanged(buffer);

//            if (MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SELECTING) != 0) {
//                MetaKeyKeyListener.stopSelecting(CustomEditTextView.this, buffer);
//            }
        }

        public void onSpanChanged(Spannable buf, Object what, int s, int e, int st, int en) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onSpanChanged s=" + s + " e=" + e
                        + " st=" + st + " en=" + en + " what=" + what + ": " + buf);
            }
            CustomEditTextView.this.spanChange(buf, what, s, st, e, en);
        }

        public void onSpanAdded(Spannable buf, Object what, int s, int e) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onSpanAdded s=" + s + " e=" + e + " what=" + what + ": " + buf);
            }
            CustomEditTextView.this.spanChange(buf, what, -1, s, -1, e);
        }

        public void onSpanRemoved(Spannable buf, Object what, int s, int e) {
            if (DEBUG_EXTRACT) {
                Log.v(LOG_TAG, "onSpanRemoved s=" + s + " e=" + e + " what=" + what + ": " + buf);
            }
            CustomEditTextView.this.spanChange(buf, what, s, -1, e, -1);
        }
    }



    public Layout getLayout() {
        return mLayout;
    }

    /**
     * Gets the {@link TextPaint} used for the text.
     * Use this only to consult the Paint's properties and not to change them.
     * @return The base paint used for the text.
     */
    public TextPaint getPaint() {
        return mTextPaint;
    }

    /**
     * Returns whether the text is allowed to be wider than the View.
     * If false, the text will be wrapped to the width of the View.
     *
     * @attr ref android.R.styleable#TextView_scrollHorizontally
     * @hide
     */
    public boolean getHorizontallyScrolling() {
        return mHorizontallyScrolling;
    }


    @Override
    public boolean onCheckIsTextEditor() {
        //return mEditor != null && mEditor.mInputType != EditorInfo.TYPE_NULL;
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (onCheckIsTextEditor() && isEnabled()) {
            mEditor.createInputMethodStateIfNeeded();
            return new CustomInputConnection2(this);
        }

        return null;
    }

    public InputMethodManager getInputMethodManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getContext().getSystemService(InputMethodManager.class);
        }
        //TODO: verify this works
        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    }


    public Editable getText() {
        return mText;
    }

    /**
     * Returns the hint that is displayed when the text of the TextView
     * is empty.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    public CharSequence getHint() {
        return mHint;
    }

    /**
     * Returns if the text is constrained to a single horizontally scrolling line ignoring new
     * line characters instead of letting it wrap onto multiple lines.
     *
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public boolean isSingleLine() {
        return mSingleLine;
    }


    @Override
    public Editable getEditableText() {
        return mText;
    }

    @Override
    public void beginBatchEdit() {
        Log.w(TAG, "beginBatchEdit");
        mEditor.beginBatchEdit();
    }

    @Override
    public void endBatchEdit() {
        Log.w(TAG, "endBatchEdit");
        mEditor.endBatchEdit();
    }

    /**
     * Called by the framework in response to a request to begin a batch
     * of edit operations through a call to link {@link #beginBatchEdit()}.
     */
    public void onBeginBatchEdit() {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a request to end a batch
     * of edit operations through a call to link {@link #endBatchEdit}.
     */
    public void onEndBatchEdit() {
        // intentionally empty
    }
}
