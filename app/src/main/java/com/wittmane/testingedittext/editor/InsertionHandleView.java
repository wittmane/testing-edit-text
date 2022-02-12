//package com.wittmane.testingedittext.editor;
//
//import android.graphics.Matrix;
//import android.graphics.drawable.Drawable;
//import android.os.Build;
//import android.text.Layout;
//import android.text.Selection;
//import android.text.Spannable;
//import android.view.Gravity;
//import android.view.MotionEvent;
//import android.view.ViewConfiguration;
//
//import com.wittmane.testingedittext.CustomEditTextView;
//import com.wittmane.testingedittext.Editor;
//import com.wittmane.testingedittext.EditorTouchState;
//import com.wittmane.testingedittext.R;
//import com.wittmane.testingedittext.SimpleTouchManager;
//
//public class InsertionHandleView extends HandleView {
//    static final String TAG = InsertionHandleView.class.getSimpleName();
//
//    private static final int DELAY_BEFORE_HANDLE_FADES_OUT = 4000;
//
//    // Used to detect taps on the insertion handle, which will affect the insertion action mode
//    private float mLastDownRawX, mLastDownRawY;
//    private Runnable mHider;
//
//    // Members for fake-dismiss effect in touch through mode.
//    // It is to make InsertionHandleView can receive the MOVE/UP events after calling dismiss(),
//    // which could happen in case of long-press (making selection will dismiss the insertion
//    // handle).
//
//    // Whether the finger is down and hasn't been up yet.
//    private boolean mIsTouchDown = false;
//    // Whether the popup window is in the invisible state and will be dismissed when finger up.
//    private boolean mPendingDismissOnUp = false;
//    // The alpha value of the drawable.
//    private final int mDrawableOpacity;
//
//    // Members for toggling the insertion menu in touch through mode.
//
//    // The coordinate for the touch down event, which is used for transforming the coordinates
//    // of the events to the text view.
//    private float mTouchDownX;
//    private float mTouchDownY;
//    // The cursor offset when touch down. This is to detect whether the cursor is moved when
//    // finger move/up.
//    private int mOffsetDown;
//    // Whether the cursor offset has been changed on the move/up events.
//    private boolean mOffsetChanged;
//    // Whether it is in insertion action mode when finger down.
//    private boolean mIsInActionMode;
//    // The timestamp for the last up event, which is used for double tap detection.
//    private long mLastUpTime;
//
//    // The delta height applied to the insertion handle view.
//    private final int mDeltaHeight;
//
//    public InsertionHandleView(Drawable drawable, CustomEditTextView textView) {
//        super(drawable, drawable, /*com.android.internal.*/R.id.insertion_handle, textView);
//
//        int deltaHeight = 0;
//        int opacity = 255;
//        if (mTextView.mEditor.mFlagInsertionHandleGesturesEnabled) {
//            deltaHeight = /*AppGlobals.getIntCoreSetting(
//                    WidgetFlags.KEY_INSERTION_HANDLE_DELTA_HEIGHT,*/
//                    WidgetFlags.INSERTION_HANDLE_DELTA_HEIGHT_DEFAULT/*)*/;
//            opacity = /*AppGlobals.getIntCoreSetting(
//                    WidgetFlags.KEY_INSERTION_HANDLE_OPACITY,*/
//                    WidgetFlags.INSERTION_HANDLE_OPACITY_DEFAULT/*)*/;
//            // Avoid invalid/unsupported values.
//            if (deltaHeight < -25 || deltaHeight > 50) {
//                deltaHeight = 25;
//            }
//            if (opacity < 10 || opacity > 100) {
//                opacity = 50;
//            }
//            // Converts the opacity value from range {0..100} to {0..255}.
//            opacity = opacity * 255 / 100;
//        }
//        mDeltaHeight = deltaHeight;
//        mDrawableOpacity = opacity;
//    }
//
//    void hideAfterDelay() {
//        if (mHider == null) {
//            mHider = new Runnable() {
//                public void run() {
//                    hide();
//                }
//            };
//        } else {
//            removeHiderCallback();
//        }
//        mTextView.postDelayed(mHider, DELAY_BEFORE_HANDLE_FADES_OUT);
//    }
//
//    void removeHiderCallback() {
//        if (mHider != null) {
//            mTextView.removeCallbacks(mHider);
//        }
//    }
//
//    @Override
//    protected int getHotspotX(Drawable drawable, boolean isRtlRun) {
//        return drawable.getIntrinsicWidth() / 2;
//    }
//
//    @Override
//    protected int getHorizontalGravity(boolean isRtlRun) {
//        return Gravity.CENTER_HORIZONTAL;
//    }
//
//    @Override
//    protected int getCursorOffset() {
//        int offset = super.getCursorOffset();
//        if (mTextView.mDrawableForCursor != null) {
//            mTextView.mDrawableForCursor.getPadding(mTextView.mEditor.mTempRect);
//            offset += (mTextView.mDrawableForCursor.getIntrinsicWidth()
//                    - mTextView.mEditor.mTempRect.left - mTextView.mEditor.mTempRect.right) / 2;
//        }
//        return offset;
//    }
//
//    @Override
//    int getCursorHorizontalPosition(Layout layout, int offset) {
//        if (mTextView.mDrawableForCursor != null) {
//            final float horizontal = getHorizontal(layout, offset);
//            return mTextView.mEditor.clampHorizontalPosition(mTextView.mDrawableForCursor, horizontal) + mTextView.mEditor.mTempRect.left;
//        }
//        return super.getCursorHorizontalPosition(layout, offset);
//    }
//
//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        if (mTextView.mEditor.mFlagInsertionHandleGesturesEnabled) {
//            final int height = Math.max(
//                    getPreferredHeight() + mDeltaHeight, mDrawable.getIntrinsicHeight());
//            setMeasuredDimension(getPreferredWidth(), height);
//            return;
//        }
//        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent ev) {
//        if (!mTextView.mTouchManager.isFromPrimePointer(ev, true)) {
//            return true;
//        }
//        if (mTextView.mEditor.mFlagInsertionHandleGesturesEnabled && mTextView.mEditor.mFlagCursorDragFromAnywhereEnabled) {
//            // Should only enable touch through when cursor drag is enabled.
//            // Otherwise the insertion handle view cannot be moved.
//            return touchThrough(ev);
//        }
//        final boolean result = super.onTouchEvent(ev);
//
//        switch (ev.getActionMasked()) {
//            case MotionEvent.ACTION_DOWN:
//                mLastDownRawX = ev.getRawX();
//                mLastDownRawY = ev.getRawY();
//                updateMagnifier(ev);
//                break;
//
//            case MotionEvent.ACTION_MOVE:
//                updateMagnifier(ev);
//                break;
//
//            case MotionEvent.ACTION_UP:
//                if (!offsetHasBeenChanged()) {
//                    ViewConfiguration config = ViewConfiguration.get(mTextView.getContext());
//                    boolean isWithinTouchSlop = EditorTouchState.isDistanceWithin(
//                            mLastDownRawX, mLastDownRawY, ev.getRawX(), ev.getRawY(),
//                            config.getScaledTouchSlop());
//                    if (isWithinTouchSlop) {
//                        // Tapping on the handle toggles the insertion action mode.
//                        mTextView.mEditor.toggleInsertionActionMode();
//                    }
//                } else {
//                    if (mTextView.mEditor.mTextActionMode != null) {
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                            mTextView.mEditor.mTextActionMode.invalidateContentRect();
//                        } else {
//                            //TODO: (EW) handle?
//                        }
//                    }
//                }
//                // Fall through.
//            case MotionEvent.ACTION_CANCEL:
//                hideAfterDelay();
//                dismissMagnifier();
//                break;
//
//            default:
//                break;
//        }
//
//        return result;
//    }
//
//    // Handles the touch events in touch through mode.
//    private boolean touchThrough(MotionEvent ev) {
//        final int actionType = ev.getActionMasked();
//        switch (actionType) {
//            case MotionEvent.ACTION_DOWN:
//                mIsTouchDown = true;
//                mOffsetChanged = false;
//                mOffsetDown = mTextView.getSelectionStart();
//                mTouchDownX = ev.getX();
//                mTouchDownY = ev.getY();
//                mIsInActionMode = mTextView.mEditor.mTextActionMode != null;
//                if (ev.getEventTime() - mLastUpTime < ViewConfiguration.getDoubleTapTimeout()) {
//                    mTextView.mEditor.stopTextActionMode();  // Avoid crash when double tap and drag backwards.
//                }
//                mTextView.mTouchState.setIsOnHandle(true);
//                break;
//            case MotionEvent.ACTION_UP:
//                mLastUpTime = ev.getEventTime();
//                break;
//        }
//        // Performs the touch through by forward the events to the text view.
//        boolean ret = mTextView.onTouchEvent(transformEventForTouchThrough(ev));
//
//        if (actionType == MotionEvent.ACTION_UP || actionType == MotionEvent.ACTION_CANCEL) {
//            mIsTouchDown = false;
//            if (mPendingDismissOnUp) {
//                dismiss();
//            }
//            mTextView.mTouchState.setIsOnHandle(false);
//        }
//
//        // Checks for cursor offset change.
//        if (!mOffsetChanged) {
//            int start = mTextView.getSelectionStart();
//            int end = mTextView.getSelectionEnd();
//            if (start != end || mOffsetDown != start) {
//                mOffsetChanged = true;
//            }
//        }
//
//        // Toggling the insertion action mode on finger up.
//        if (!mOffsetChanged && actionType == MotionEvent.ACTION_UP) {
//            if (mIsInActionMode) {
//                mTextView.mEditor.stopTextActionMode();
//            } else {
//                mTextView.mEditor.startInsertionActionMode();
//            }
//        }
//        return ret;
//    }
//
//    private MotionEvent transformEventForTouchThrough(MotionEvent ev) {
//        final Layout layout = mTextView.getLayout();
//        final int line = layout.getLineForOffset(getCurrentCursorOffset());
//        final int textHeight =
//                layout.getLineBottom/*WithoutSpacing*/(line) - layout.getLineTop(line);//TODO: (EW) verify this is fine
//        // Transforms the touch events to screen coordinates.
//        // And also shift up to make the hit point is on the text.
//        // Note:
//        //  - The revised X should reflect the distance to the horizontal center of touch down.
//        //  - The revised Y should be at the top of the text.
//        Matrix m = new Matrix();
//        m.setTranslate(ev.getRawX() - ev.getX() + (getMeasuredWidth() >> 1) - mTouchDownX,
//                ev.getRawY() - ev.getY() - (textHeight >> 1) - mTouchDownY);
//        ev.transform(m);
//        // Transforms the touch events to text view coordinates.
//        mTextView.toLocalMotionEvent(ev);//TODO: (EW) figure out how to call this from View (maybe reflection - blocked by UnsupportedAppUsage) or replicate it
//        if (SimpleTouchManager.DEBUG_CURSOR) {
//            logCursor("InsertionHandleView#transformEventForTouchThrough",
//                    "Touch through: %d, (%f, %f)",
//                    ev.getAction(), ev.getX(), ev.getY());
//        }
//        return ev;
//    }
//
//    @Override
//    public boolean isShowing() {
//        if (mPendingDismissOnUp) {
//            return false;
//        }
//        return super.isShowing();
//    }
//
//    @Override
//    public void show() {
//        super.show();
//        mPendingDismissOnUp = false;
//        mDrawable.setAlpha(mDrawableOpacity);
//    }
//
//    @Override
//    public void dismiss() {
//        if (mIsTouchDown) {
//            if (SimpleTouchManager.DEBUG_CURSOR) {
//                logCursor("InsertionHandleView#dismiss",
//                        "Suppressed the real dismiss, only become invisible");
//            }
//            mPendingDismissOnUp = true;
//            mDrawable.setAlpha(0);
//        } else {
//            super.dismiss();
//            mPendingDismissOnUp = false;
//        }
//    }
//
//    @Override
//    protected void updateDrawable(final boolean updateDrawableWhenDragging) {
//        super.updateDrawable(updateDrawableWhenDragging);
//        mDrawable.setAlpha(mDrawableOpacity);
//    }
//
//    @Override
//    public int getCurrentCursorOffset() {
//        return mTextView.getSelectionStart();
//    }
//
//    @Override
//    public void updateSelection(int offset) {
//        Selection.setSelection((Spannable) mTextView.getText(), offset);
//    }
//
//    @Override
//    protected void updatePosition(float x, float y, boolean fromTouchScreen) {
//        Layout layout = mTextView.getLayout();
//        int offset;
//        if (layout != null) {
//            if (mPreviousLineTouched == UNSET_LINE) {
//                mPreviousLineTouched = mTextView.getLineAtCoordinate(y);
//            }
//            int currLine = mTextView.mEditor.getCurrentLineAdjustedForSlop(layout, mPreviousLineTouched, y);
//            offset = getOffsetAtCoordinate(layout, currLine, x);
//            mPreviousLineTouched = currLine;
//        } else {
//            offset = -1;
//        }
//        if (SimpleTouchManager.DEBUG_CURSOR) {
//            logCursor("InsertionHandleView: updatePosition", "x=%f, y=%f, offset=%d, line=%d",
//                    x, y, offset, mPreviousLineTouched);
//        }
//        positionAtCursorOffset(offset, false, fromTouchScreen);
//        if (mTextView.mEditor.mTextActionMode != null) {
//            mTextView.mEditor.invalidateActionMode();
//        }
//    }
//
//    @Override
//    void onHandleMoved() {
//        super.onHandleMoved();
//        removeHiderCallback();
//    }
//
//    @Override
//    public void onDetached() {
//        super.onDetached();
//        removeHiderCallback();
//    }
//
//    @Override
////    @MagnifierHandleTrigger
//    protected int getMagnifierHandleTrigger() {
//        return /*MagnifierHandleTrigger.INSERTION*/0;
//    }
//}
