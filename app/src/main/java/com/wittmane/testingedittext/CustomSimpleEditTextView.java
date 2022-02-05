package com.wittmane.testingedittext;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.os.Vibrator;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

// modified from https://stackoverflow.com/a/31168391
public class CustomSimpleEditTextView extends View /*implements ViewTreeObserver.OnPreDrawListener*/ implements ICustomTextView {
    static final String TAG = CustomSimpleEditTextView.class.getSimpleName();

//    private int borderWidthLeft = dp(4);
//
//    private int borderWidthRight = dp(4);
//
//    private int borderWidthTop = dp(4);
//
//    private int borderWidthBottom = dp(4);

    private int boderColor = Color.BLACK;

    private int backgroundColor = Color.BLUE;

    private int textColor = Color.WHITE;

    private int textSize = sp(18);

    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private Rect textBgRect = new Rect();

    private String defaultText = "A";

    public CustomSimpleEditTextView(Context context) {
        this(context, null);
    }
    public CustomSimpleEditTextView(Context context, AttributeSet attrs) {
        this(context, attrs, /*com.android.internal.*/R.attr.editTextStyle);
//        this(context, attrs, 0);
    }
    public CustomSimpleEditTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);

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
            }
        }
        setText(defaultText);

//        setEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.w(TAG, "getFocusable=" + getFocusable());
        }
        Log.w(TAG, "isClickable=" + isClickable());
    }

    private void init(Context context) {
        backgroundPaint.setColor(backgroundColor);
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.LEFT);
        TextView textViewReference = new TextView(context);
        textPaint.setTextSize(textSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);
        drawText(canvas);
    }

    private void drawBackground(Canvas canvas) {
        canvas.drawColor(boderColor);
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int right = getMeasuredWidth() - getPaddingRight();
        int bottom = getMeasuredHeight() - getPaddingBottom();
        textBgRect.set(left, top, right, bottom);
        canvas.save();
        canvas.drawRect(textBgRect, backgroundPaint);
        canvas.restore();
    }

    private void drawText(Canvas canvas) {
        Log.w(TAG, "drawText: text=\"" + mText.toString() + "\"");
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        int textHeight = (int) Math.ceil(fontMetrics.descent - fontMetrics.ascent);
        canvas.save();
        canvas.clipRect(textBgRect);
        canvas.drawText(/*defaultText*/mText.toString(), getPaddingLeft(), getPaddingTop() - fontMetrics.ascent, textPaint);
        canvas.restore();
    }

    //TODO: (EW) this seems to get called in sets of 3 - see if I'm doing anything wrong
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

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        setMeasuredDimension(backgroundRectWidth + borderWidthLeft + borderWidthRight,
//                backgroundRectHeight + borderWidthTop + borderWidthBottom);


        Log.w(TAG, "onMeasure: paddingLeft=" + getPaddingLeft());
        final int measuredWidth;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        } else {
            //TODO: need to handle splitting to multiple lines
            int textWidth = (int)Math.ceil(textPaint.measureText(/*defaultText*/mText.toString())) + getPaddingLeft() + getPaddingRight();
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                measuredWidth = Math.min(textWidth, MeasureSpec.getSize(widthMeasureSpec));
            } else {
                measuredWidth = textWidth;
            }
        }
        final int measuredHeight;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            //TODO: need to handle multiple lines
            int textHeight = (int) Math.ceil(fontMetrics.descent - fontMetrics.ascent) + getPaddingTop() + getPaddingBottom();
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
                measuredHeight = Math.min(textHeight, MeasureSpec.getSize(heightMeasureSpec));
            } else {
                measuredHeight = textHeight;
            }
        }
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private int sp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }





    // modified from https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android11-release

    private /*CharSequence*/Editable mText;
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
//        InputMethodManager imm = getInputMethodManager();
//        if (imm != null) {
//            imm.restartInput(this);
//        }
    }

//    @Override
//    public boolean onCheckIsTextEditor() {
//        //return mEditor != null && mEditor.mInputType != EditorInfo.TYPE_NULL;
//        return true;
//    }
//
//    @Override
//    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
//        if (onCheckIsTextEditor() && isEnabled()) {
//            return new CustomInputConnection2(this);
//        }
//
//        return null;
//    }
//
//    private InputMethodManager getInputMethodManager() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            return getContext().getSystemService(InputMethodManager.class);
//        }
//        //TODO: verify this works
//        return (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
//    }
//
//    private static final int NO_POINTER_ID = -1;
//    /**
//     * The prime (the 1st finger) pointer id which is used as a lock to prevent multi touch among
//     * TextView and the handle views which are rendered on popup windows.
//     */
//    private int mPrimePointerId = NO_POINTER_ID;
//    /**
//     * Whether the prime pointer is from the event delivered to selection handle or insertion
//     * handle.
//     */
//    private boolean mIsPrimePointerFromHandleView;
//    /**
//     * Called from onTouchEvent() to prevent the touches by secondary fingers.
//     * Dragging on handles can revise cursor/selection, so can dragging on the text view.
//     * This method is a lock to avoid processing multiple fingers on both text view and handles.
//     * Note: multiple fingers on handles (e.g. 2 fingers on the 2 selection handles) should work.
//     *
//     * @param event The motion event that is being handled and carries the pointer info.
//     * @param fromHandleView true if the event is delivered to selection handle or insertion
//     * handle; false if this event is delivered to TextView.
//     * @return Returns true to indicate that onTouchEvent() can continue processing the motion
//     * event, otherwise false.
//     *  - Always returns true for the first finger.
//     *  - For secondary fingers, if the first or current finger is from TextView, returns false.
//     *    This is to make touch mutually exclusive between the TextView and the handles, but
//     *    not among the handles.
//     */
//    boolean isFromPrimePointer(MotionEvent event, boolean fromHandleView) {
//        boolean res = true;
//        if (mPrimePointerId == NO_POINTER_ID)  {
//            mPrimePointerId = event.getPointerId(0);
//            mIsPrimePointerFromHandleView = fromHandleView;
//        } else if (mPrimePointerId != event.getPointerId(0)) {
//            res = mIsPrimePointerFromHandleView && fromHandleView;
//        }
//        if (event.getActionMasked() == MotionEvent.ACTION_UP
//                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
//            mPrimePointerId = -1;
//        }
//        return res;
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
////        if (DEBUG_CURSOR) {
////            logCursor("onTouchEvent", "%d: %s (%f,%f)",
////                    event.getSequenceNumber(),
////                    MotionEvent.actionToString(event.getActionMasked()),
////                    event.getX(), event.getY());
////        }
//
//        Log.w(TAG, String.format("onTouchEvent: %s (%f,%f)",
//                MotionEvent.actionToString(event.getActionMasked()),
//                event.getX(), event.getY()));
//        if (!isFromPrimePointer(event, false)) {
//            return true;
//        }
//
//        final int action = event.getActionMasked();
////        if (mEditor != null) {
////            mEditor.onTouchEvent(event);
////
////            if (mEditor.mInsertionPointCursorController != null
////                    && mEditor.mInsertionPointCursorController.isCursorBeingModified()) {
////                return true;
////            }
////            if (mEditor.mSelectionModifierCursorController != null
////                    && mEditor.mSelectionModifierCursorController.isDragAcceleratorActive()) {
////                return true;
////            }
////        }
////
//        final boolean superResult = super.onTouchEvent(event);
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
//            boolean handled = false;
////
////            if (mMovement != null) {
////                handled |= mMovement.onTouchEvent(this, mSpannable, event);
////            }
////
//            final boolean textIsSelectable = /*isTextSelectable()*/true;
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
//            if (touchIsFinished && (/*isTextEditable()*/true || textIsSelectable)) {
//                // Show the IME, except when selecting in read-only text.
//                final InputMethodManager imm = getInputMethodManager();
//                viewClicked(imm);
//                if (/*isTextEditable()*/true && /*mEditor.mShowSoftInputOnFocus*/true && imm != null) {
//                    imm.showSoftInput(this, 0);
//                }
//
////                // The above condition ensures that the mEditor is not null
////                mEditor.onTouchUpEvent(event);
//
//                handled = true;
//            }
//
//            if (handled) {
//                return true;
//            }
////        }
////
//        Log.w(TAG, "onTouchEvent: isFocused=" + isFocused());
//
//        return superResult;
//    }
//
//    protected void viewClicked(InputMethodManager imm) {
//        if (imm != null) {
//            imm.viewClicked(this);
//        }
//    }
//
//    @Override
//    public void setEnabled(boolean enabled) {
//        Log.w(TAG, "setEnabled " + enabled);
//        if (enabled == isEnabled()) {
//            return;
//        }
//
//        if (!enabled) {
//            // Hide the soft input if the currently active TextView is disabled
//            InputMethodManager imm = getInputMethodManager();
//            if (imm != null && imm.isActive(this)) {
//                imm.hideSoftInputFromWindow(getWindowToken(), 0);
//            }
//        }
//
//        super.setEnabled(enabled);
//
//        if (enabled) {
//            // Make sure IME is updated with current editor info.
//            InputMethodManager imm = getInputMethodManager();
//            if (imm != null) imm.restartInput(this);
//        }
//
////        // Will change text color
////        if (mEditor != null) {
//////            mEditor.invalidateTextDisplayList();
////            mEditor.prepareCursorControllers();
////
////            // start or stop the cursor blinking as appropriate
////            mEditor.makeBlink();
////        }
//    }
//
////    void invalidateCursor() {
////        int where = getSelectionEnd();
////
////        invalidateCursor(where, where, where);
////    }
////
////    private void invalidateCursor(int a, int b, int c) {
////        if (a >= 0 || b >= 0 || c >= 0) {
////            int start = Math.min(Math.min(a, b), c);
////            int end = Math.max(Math.max(a, b), c);
////            invalidateRegion(start, end, true /* Also invalidates blinking cursor */);
////        }
////    }
////
////    /**
////     * Invalidates the region of text enclosed between the start and end text offsets.
////     */
////    void invalidateRegion(int start, int end, boolean invalidateCursor) {
////        if (mLayout == null) {
////            invalidate();
////        } else {
////            int lineStart = mLayout.getLineForOffset(start);
////            int top = mLayout.getLineTop(lineStart);
////
////            // This is ridiculous, but the descent from the line above
////            // can hang down into the line we really want to redraw,
////            // so we have to invalidate part of the line above to make
////            // sure everything that needs to be redrawn really is.
////            // (But not the whole line above, because that would cause
////            // the same problem with the descenders on the line above it!)
////            if (lineStart > 0) {
////                top -= mLayout.getLineDescent(lineStart - 1);
////            }
////
////            int lineEnd;
////
////            if (start == end) {
////                lineEnd = lineStart;
////            } else {
////                lineEnd = mLayout.getLineForOffset(end);
////            }
////
////            int bottom = mLayout.getLineBottom(lineEnd);
////
////            // mEditor can be null in case selection is set programmatically.
////            if (invalidateCursor && mEditor != null && mEditor.mDrawableForCursor != null) {
////                final Rect bounds = mEditor.mDrawableForCursor.getBounds();
////                top = Math.min(top, bounds.top);
////                bottom = Math.max(bottom, bounds.bottom);
////            }
////
////            final int compoundPaddingLeft = getCompoundPaddingLeft();
////            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);
////
////            int left, right;
////            if (lineStart == lineEnd && !invalidateCursor) {
////                left = (int) mLayout.getPrimaryHorizontal(start);
////                right = (int) (mLayout.getPrimaryHorizontal(end) + 1.0);
////                left += compoundPaddingLeft;
////                right += compoundPaddingLeft;
////            } else {
////                // Rectangle bounding box when the region spans several lines
////                left = compoundPaddingLeft;
////                right = getWidth() - getCompoundPaddingRight();
////            }
////
////            invalidate(mScrollX + left, verticalPadding + top,
////                    mScrollX + right, verticalPadding + bottom);
////        }
////    }
//
////    /**
////     * Set whether the cursor is visible. The default is true. Note that this property only
////     * makes sense for editable TextView.
////     *
////     * @see #isCursorVisible()
////     *
////     * @attr ref android.R.styleable#TextView_cursorVisible
////     */
////    public void setCursorVisible(boolean visible) {
////        if (visible && mEditor == null) return; // visible is the default value with no edit data
////        createEditorIfNeeded();
////        if (mEditor.mCursorVisible != visible) {
////            mEditor.mCursorVisible = visible;
////            invalidate();
////
////            mEditor.makeBlink();
////
////            // InsertionPointCursorController depends on mCursorVisible
////            mEditor.prepareCursorControllers();
////        }
////    }
//
////    /**
////     * @return whether or not the cursor is visible (assuming this TextView is editable)
////     *
////     * @see #setCursorVisible(boolean)
////     *
////     * @attr ref android.R.styleable#TextView_cursorVisible
////     */
////    public boolean isCursorVisible() {
////        // true is the default value
////        return mEditor == null ? true : mEditor.mCursorVisible;
////    }
//
//
//    @Override
//    public boolean onKeyUp(int keyCode, KeyEvent event) {
//        Log.w(TAG, "onKeyUp: keyCode=" + keyCode + ", event=" + event);
////        if (!isEnabled()) {
////            return super.onKeyUp(keyCode, event);
////        }
////
////        if (!KeyEvent.isModifierKey(keyCode)) {
////            mPreventDefaultMovement = false;
////        }
////
////        switch (keyCode) {
////            case KeyEvent.KEYCODE_DPAD_CENTER:
////                if (event.hasNoModifiers()) {
////                    /*
////                     * If there is a click listener, just call through to
////                     * super, which will invoke it.
////                     *
////                     * If there isn't a click listener, try to show the soft
////                     * input method.  (It will also
////                     * call performClick(), but that won't do anything in
////                     * this case.)
////                     */
////                    if (!hasOnClickListeners()) {
////                        if (mMovement != null && mText instanceof Editable
////                                && mLayout != null && onCheckIsTextEditor()) {
////                            InputMethodManager imm = getInputMethodManager();
////                            viewClicked(imm);
////                            if (imm != null && getShowSoftInputOnFocus()) {
////                                imm.showSoftInput(this, 0);
////                            }
////                        }
////                    }
////                }
////                return super.onKeyUp(keyCode, event);
////
////            case KeyEvent.KEYCODE_ENTER:
////            case KeyEvent.KEYCODE_NUMPAD_ENTER:
////                if (event.hasNoModifiers()) {
////                    if (mEditor != null && mEditor.mInputContentType != null
////                            && mEditor.mInputContentType.onEditorActionListener != null
////                            && mEditor.mInputContentType.enterDown) {
////                        mEditor.mInputContentType.enterDown = false;
////                        if (mEditor.mInputContentType.onEditorActionListener.onEditorAction(
////                                this, EditorInfo.IME_NULL, event)) {
////                            return true;
////                        }
////                    }
////
////                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
////                            || shouldAdvanceFocusOnEnter()) {
////                        /*
////                         * If there is a click listener, just call through to
////                         * super, which will invoke it.
////                         *
////                         * If there isn't a click listener, try to advance focus,
////                         * but still call through to super, which will reset the
////                         * pressed state and longpress state.  (It will also
////                         * call performClick(), but that won't do anything in
////                         * this case.)
////                         */
////                        if (!hasOnClickListeners()) {
////                            View v = focusSearch(FOCUS_DOWN);
////
////                            if (v != null) {
////                                if (!v.requestFocus(FOCUS_DOWN)) {
////                                    throw new IllegalStateException("focus search returned a view "
////                                            + "that wasn't able to take focus!");
////                                }
////
////                                /*
////                                 * Return true because we handled the key; super
////                                 * will return false because there was no click
////                                 * listener.
////                                 */
////                                super.onKeyUp(keyCode, event);
////                                return true;
////                            } else if ((event.getFlags()
////                                    & KeyEvent.FLAG_EDITOR_ACTION) != 0) {
////                                // No target for next focus, but make sure the IME
////                                // if this came from it.
////                                InputMethodManager imm = getInputMethodManager();
////                                if (imm != null && imm.isActive(this)) {
////                                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
////                                }
////                            }
////                        }
////                    }
////                    return super.onKeyUp(keyCode, event);
////                }
////                break;
////        }
////
////        if (mEditor != null && mEditor.mKeyListener != null) {
////            if (mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, event)) {
////                return true;
////            }
////        }
////
////        if (mMovement != null && mLayout != null) {
////            if (mMovement.onKeyUp(this, mSpannable, keyCode, event)) {
////                return true;
////            }
////        }
//
//        return super.onKeyUp(keyCode, event);
//    }

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

////    void updateAfterEdit() {
////        invalidate();
////        int curs = getSelectionStart();
////
////        if (curs >= 0 || (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
////            registerForPreDraw();
////        }
////
////        checkForResize();
////
////        if (curs >= 0) {
////            mHighlightPathBogus = true;
////            if (mEditor != null) mEditor.makeBlink();
////            bringPointIntoView(curs);
////        }
////    }
////
////
////    private boolean mPreDrawRegistered;
//////    private boolean mPreDrawListenerDetached;
////    private void registerForPreDraw() {
////        if (!mPreDrawRegistered) {
////            getViewTreeObserver().addOnPreDrawListener(this);
////            mPreDrawRegistered = true;
////        }
////    }
////    private void unregisterForPreDraw() {
////        getViewTreeObserver().removeOnPreDrawListener(this);
////        mPreDrawRegistered = false;
//////        mPreDrawListenerDetached = false;
////    }
////    /**
////     * {@inheritDoc}
////     */
////    @Override
////    public boolean onPreDraw() {
////        if (mLayout == null) {
////            assumeLayout();
////        }
////
////        if (mMovement != null) {
////            /* This code also provides auto-scrolling when a cursor is moved using a
////             * CursorController (insertion point or selection limits).
////             * For selection, ensure start or end is visible depending on controller's state.
////             */
////            int curs = getSelectionEnd();
////            // Do not create the controller if it is not already created.
////            if (mEditor != null && mEditor.mSelectionModifierCursorController != null
////                    && mEditor.mSelectionModifierCursorController.isSelectionStartDragged()) {
////                curs = getSelectionStart();
////            }
////
////            /*
////             * TODO: This should really only keep the end in view if
////             * it already was before the text changed.  I'm not sure
////             * of a good way to tell from here if it was.
////             */
////            if (curs < 0 && (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
////                curs = mText.length();
////            }
////
////            if (curs >= 0) {
////                bringPointIntoView(curs);
////            }
////        } else {
////            bringTextIntoView();
////        }
////
////        // This has to be checked here since:
////        // - onFocusChanged cannot start it when focus is given to a view with selected text (after
////        //   a screen rotation) since layout is not yet initialized at that point.
////        if (mEditor != null && mEditor.mCreatedWithASelection) {
////            mEditor.refreshTextActionMode();
////            mEditor.mCreatedWithASelection = false;
////        }
////
////        unregisterForPreDraw();
////
////        return true;
////    }
//
//    /**
//     * Convenience for {@link Selection#getSelectionStart}.
//     */
//    public int getSelectionStart() {
//        return Selection.getSelectionStart(mText);
//    }
//
//    /**
//     * Convenience for {@link Selection#getSelectionEnd}.
//     */
//    public int getSelectionEnd() {
//        return Selection.getSelectionEnd(mText);
//    }
}
