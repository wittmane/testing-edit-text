package com.wittmane.testingedittext;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

public class CustomSimpleEditTextViewStripped extends View implements ICustomTextView {
    static final String TAG = CustomSimpleEditTextViewStripped.class.getSimpleName();

    private int textColor = Color.BLACK;
    private int textSize = sp(/*14*/18);
    private float mSpacingMult = 1.0f;//TextView_lineSpacingMultiplier
    private float mSpacingAdd = 0.0f;//TextView_lineSpacingExtra
    private boolean mIncludePad = true;//TextView_includeFontPadding
    private TextUtils.TruncateAt mEllipsize = TextUtils.TruncateAt.END;
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

    private static final int LINES = 1;
    private static final int EMS = LINES;
    private static final int PIXELS = 2;
    static final int VERY_WIDE = 1024 * 1024; // XXX should be much larger

    private TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private Paint mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Editable mText;
    private CharSequence mHint;
    private Layout mLayout;
    private Layout mHintLayout;

    private boolean mSingleLine;

    private boolean mHorizontallyScrolling = false;//TextView_scrollHorizontally or applySingleLine

    public CustomSimpleEditTextViewStripped(Context context) {
        this(context, null);
    }
    public CustomSimpleEditTextViewStripped(Context context, AttributeSet attrs) {
        //R.attr.editTextStyle pulls in the underline in the DynamicLayout and allows this view to be focusable by default
        this(context, attrs, R.attr.editTextStyle);
    }
    public CustomSimpleEditTextViewStripped(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);


        mTextPaint.density = getResources().getDisplayMetrics().density;
        mTextPaint.setColor(textColor);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setTextSize(textSize);





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
            }
        }

//        final int variation =
//                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
//        final boolean passwordInputType = variation
//                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
//        final boolean webPasswordInputType = variation
//                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD);
//        final boolean numberPasswordInputType = variation
//                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
//        final int targetSdkVersion = context.getApplicationInfo().targetSdkVersion;
//        mUseInternationalizedInput = targetSdkVersion >= Build.VERSION_CODES.O;
        if (inputType != EditorInfo.TYPE_NULL) {
//            setInputType(inputType, true);
            // If set, the input type overrides what was set using the deprecated singleLine flag.
            singleLine = !isMultilineInputType(inputType);
        }
        // Same as setSingleLine(), but make sure the transformation method and the maximum number
        // of lines of height are unchanged for multi-line TextViews.
//        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, singleLine, singleLine);
//        final boolean isPassword = passwordInputType || webPasswordInputType
//                || numberPasswordInputType;

        setText(initialText);



////        setEnabled(true);
//        setClickable(true);
////        setFocusable(true);
//        setFocusableInTouchMode(true);
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
        final float widthLimit = (widthMode == MeasureSpec.AT_MOST)
                ?  (float) widthSize : Float.MAX_VALUE;

        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            width = widthSize;
        } else {
            if (mLayout != null && mEllipsize == null) {
//                des = desired(mLayout);
            }

            if (des < 0) {
                boring = BoringLayout.isBoring(/*mTransformed*/mText, mTextPaint/*, mTextDir*//*, mBoring*/);
//                if (boring != null) {
//                    mBoring = boring;
//                }
            } else {
                fromexisting = true;
            }

            if (boring == null/* || boring == UNKNOWN_BORING*/) {
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
//                    hintDes = desired(mHintLayout);
                }

                if (hintDes < 0) {
                    hintBoring = BoringLayout.isBoring(mHint, mTextPaint/*, mTextDir*//*, mHintBoring*/);
//                    if (hintBoring != null) {
//                        mHintBoring = hintBoring;
//                    }
                }

                if (hintBoring == null/* || hintBoring == UNKNOWN_BORING*/) {
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
            makeNewLayout(want, hintWant,
                    width - getCompoundPaddingLeft() - getCompoundPaddingRight());
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
                    makeNewLayout(want, hintWant,
                            width - getCompoundPaddingLeft() - getCompoundPaddingRight());
                }
            } else {
                // Nothing has changed
            }
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            height = heightSize;
//            mDesiredHeightAtMeasure = -1;
        } else {
            int desired = getDesiredHeight();

            height = desired;
//            mDesiredHeightAtMeasure = desired;

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(desired, heightSize);
            }
        }

        int unpaddedHeight = height - getCompoundPaddingTop() - getCompoundPaddingBottom();
        if (mMaxMode == LINES && mLayout.getLineCount() > mMaximum) {
            unpaddedHeight = Math.min(unpaddedHeight, mLayout.getLineTop(mMaximum));
        }

//        /*
//         * We didn't let makeNewLayout() register to bring the cursor into view,
//         * so do it here if there is any possibility that it is needed.
//         */
//        if (mMovement != null
//                || mLayout.getWidth() > unpaddedWidth
//                || mLayout.getHeight() > unpaddedHeight) {
////            registerForPreDraw();
//        } else {
//            scrollTo(0, 0);
//        }

        setMeasuredDimension(width, height);
        Log.w(TAG, "onMeasure: setMeasuredDimension: width=" + width + ", height=" + height);
    }

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

        int linecount = layout.getLineCount();
        final int padding = getCompoundPaddingTop() + getCompoundPaddingBottom();
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
        desired = Math.max(desired, getSuggestedMinimumHeight());

        return desired;
    }

    private void applySingleLine(boolean singleLine, boolean applyTransformation,
                                 boolean changeMaxLines) {
        mSingleLine = singleLine;
        if (singleLine) {
            setLines(1);
            setHorizontallyScrolling(true);
//            if (applyTransformation) {
//                setTransformationMethod(SingleLineTransformationMethod.getInstance());
//            }
        } else {
            if (changeMaxLines) {
                setMaxLines(Integer.MAX_VALUE);
            }
            setHorizontallyScrolling(false);
//            if (applyTransformation) {
//                setTransformationMethod(null);
//            }
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
        mLayout.draw(canvas, null, mHighlightPaint, 0);
    }

    private void assumeLayout() {
//        int width = getRight() - getLeft() - /*getCompoundPaddingLeft()*/getPaddingLeft() - /*getCompoundPaddingRight()*/getPaddingRight();
        int width = getRight() - getLeft() - getPaddingLeft() - getPaddingRight();

        if (width < 1) {
            width = 0;
        }

        int physicalWidth = width;

        if (mHorizontallyScrolling) {
            width = VERY_WIDE;
        }

        makeNewLayout(width, physicalWidth, physicalWidth);
    }
    public void makeNewLayout(int wantWidth, int hintWidth, int ellipsisWidth) {

        if (wantWidth < 0) {
            wantWidth = 0;
        }
        if (hintWidth < 0) {
            hintWidth = 0;
        }

        Layout.Alignment alignment = Layout.Alignment.ALIGN_NORMAL;

        mLayout = new DynamicLayout(mText, mTextPaint, wantWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);

        boolean shouldEllipsize = mEllipsize != null;
        mHintLayout = null;

        if (mHint != null) {
            BoringLayout.Metrics hintBoring;
            if (shouldEllipsize) hintWidth = wantWidth;

                hintBoring = BoringLayout.isBoring(mHint, mTextPaint/*, mTextDir*//*,
                        mHintBoring*/);
//                if (hintBoring != null) {
//                    mHintBoring = hintBoring;
//                }

            if (hintBoring != null) {
                if (hintBoring.width <= hintWidth
                        && (!shouldEllipsize || hintBoring.width <= ellipsisWidth)) {
                    mHintLayout = BoringLayout.make(mHint, mTextPaint,
                            hintWidth, alignment, mSpacingMult, mSpacingAdd,
                            hintBoring, mIncludePad);
                } else if (shouldEllipsize && hintBoring.width <= hintWidth) {
                    mHintLayout = BoringLayout.make(mHint, mTextPaint,
                            hintWidth, alignment, mSpacingMult, mSpacingAdd,
                            hintBoring, mIncludePad, mEllipsize,
                            ellipsisWidth);
                }
            }
            // TODO: code duplication with makeSingleLayout()
            if (mHintLayout == null) {
                mHintLayout = new StaticLayout(mHint, mTextPaint, hintWidth, alignment, mSpacingMult, mSpacingAdd, mIncludePad);
            }
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

    private void setText(CharSequence text) {
        if (text == null) {
            text = "";
        }
        mText = Editable.Factory.getInstance().newEditable(text);
//        mSpannable = (text instanceof Spannable) ? (Spannable) text : null;
        InputMethodManager imm = getInputMethodManager();
        if (imm != null) {
            imm.restartInput(this);
        }
//
//        if (mTransformation == null) {
//            mTransformed = text;
//        } else {
//            mTransformed = mTransformation.getTransformation(text, this);
//        }
//        if (mTransformed == null) {
//            // Should not happen if the transformation method follows the non-null postcondition.
//            mTransformed = "";
//        }
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






    @Override
    public boolean onCheckIsTextEditor() {
        //return mEditor != null && mEditor.mInputType != EditorInfo.TYPE_NULL;
        return true;
    }

    @Override
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




    @Override
    public Editable getEditableText() {
        return mText;
    }

    @Override
    public void beginBatchEdit() {

    }

    @Override
    public void endBatchEdit() {

    }
}
