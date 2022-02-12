package com.wittmane.testingedittext.editor;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.wittmane.testingedittext.CustomEditTextView;
import com.wittmane.testingedittext.R;

import static com.wittmane.testingedittext.Editor.TAP_STATE_DOUBLE_TAP;
import static com.wittmane.testingedittext.Editor.TAP_STATE_TRIPLE_CLICK;

public class InsertionHandleView28 extends HandleView28 {
    private static final int DELAY_BEFORE_HANDLE_FADES_OUT = 4000;
    private static final int RECENT_CUT_COPY_DURATION = 15 * 1000; // seconds

    // Used to detect taps on the insertion handle, which will affect the insertion action mode
    private float mDownPositionX, mDownPositionY;
    private Runnable mHider;

    public InsertionHandleView28(Drawable drawable, CustomEditTextView textView) {
        super(drawable, drawable, /*com.android.internal.*/R.id.insertion_handle, textView);
    }

    @Override
    public void show() {
        super.show();

        final long durationSinceCutOrCopy =
                SystemClock.uptimeMillis() - CustomEditTextView.sLastCutCopyOrTextChangedTime;

        // Cancel the single tap delayed runnable.
        if (mTextView.mEditor.mInsertionActionModeRunnable != null
                && ((mTextView.mEditor.mTapState == TAP_STATE_DOUBLE_TAP)
                || (mTextView.mEditor.mTapState == TAP_STATE_TRIPLE_CLICK)
                /*|| isCursorInsideEasyCorrectionSpan()*/)) {
            mTextView.removeCallbacks(mTextView.mEditor.mInsertionActionModeRunnable);
        }

        // Prepare and schedule the single tap runnable to run exactly after the double tap
        // timeout has passed.
        if ((mTextView.mEditor.mTapState != TAP_STATE_DOUBLE_TAP) && (mTextView.mEditor.mTapState != TAP_STATE_TRIPLE_CLICK)
//                && !isCursorInsideEasyCorrectionSpan()
                && (durationSinceCutOrCopy < RECENT_CUT_COPY_DURATION)) {
            if (mTextView.mEditor.mTextActionMode == null) {
                if (mTextView.mEditor.mInsertionActionModeRunnable == null) {
                    mTextView.mEditor.mInsertionActionModeRunnable = new Runnable() {
                        @Override
                        public void run() {
                            mTextView.mEditor.startInsertionActionMode();
                        }
                    };
                }
                mTextView.postDelayed(
                        mTextView.mEditor.mInsertionActionModeRunnable,
                        ViewConfiguration.getDoubleTapTimeout() + 1);
            }

        }

        hideAfterDelay();
    }

    private void hideAfterDelay() {
        if (mHider == null) {
            mHider = new Runnable() {
                public void run() {
                    hide();
                }
            };
        } else {
            removeHiderCallback();
        }
        mTextView.postDelayed(mHider, DELAY_BEFORE_HANDLE_FADES_OUT);
    }

    private void removeHiderCallback() {
        if (mHider != null) {
            mTextView.removeCallbacks(mHider);
        }
    }

    @Override
    protected int getHotspotX(Drawable drawable, boolean isRtlRun) {
        return drawable.getIntrinsicWidth() / 2;
    }

    @Override
    protected int getHorizontalGravity(boolean isRtlRun) {
        return Gravity.CENTER_HORIZONTAL;
    }

    @Override
    protected int getCursorOffset() {
        int offset = super.getCursorOffset();
        if (mTextView.mDrawableForCursor != null) {
            mTextView.mDrawableForCursor.getPadding(mTextView.mEditor.mTempRect);
            offset += (mTextView.mDrawableForCursor.getIntrinsicWidth()
                    - mTextView.mEditor.mTempRect.left - mTextView.mEditor.mTempRect.right) / 2;
        }
        return offset;
    }

    @Override
    int getCursorHorizontalPosition(Layout layout, int offset) {
        if (mTextView.mDrawableForCursor != null) {
            final float horizontal = getHorizontal(layout, offset);
            return mTextView.mEditor.clampHorizontalPosition(mTextView.mDrawableForCursor, horizontal) + mTextView.mEditor.mTempRect.left;
        }
        return super.getCursorHorizontalPosition(layout, offset);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final boolean result = super.onTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownPositionX = ev.getRawX();
                mDownPositionY = ev.getRawY();
                updateMagnifier(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                updateMagnifier(ev);
                break;

            case MotionEvent.ACTION_UP:
                if (!offsetHasBeenChanged()) {
                    final float deltaX = mDownPositionX - ev.getRawX();
                    final float deltaY = mDownPositionY - ev.getRawY();
                    final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

                    final ViewConfiguration viewConfiguration = ViewConfiguration.get(
                            mTextView.getContext());
                    final int touchSlop = viewConfiguration.getScaledTouchSlop();

                    if (distanceSquared < touchSlop * touchSlop) {
                        // Tapping on the handle toggles the insertion action mode.
                        if (mTextView.mEditor.mTextActionMode != null) {
                            mTextView.mEditor.stopTextActionMode();
                        } else {
                            mTextView.mEditor.startInsertionActionMode();
                        }
                    }
                } else {
                    if (mTextView.mEditor.mTextActionMode != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mTextView.mEditor.mTextActionMode.invalidateContentRect();
                        } else {
                            //TODO: (EW) handle?
                        }
                    }
                }
                // Fall through.
            case MotionEvent.ACTION_CANCEL:
                hideAfterDelay();
                dismissMagnifier();
                break;

            default:
                break;
        }

        return result;
    }

    @Override
    public int getCurrentCursorOffset() {
        return mTextView.getSelectionStart();
    }

    @Override
    public void updateSelection(int offset) {
        Selection.setSelection((Spannable) mTextView.getText(), offset);
    }

    @Override
    protected void updatePosition(float x, float y, boolean fromTouchScreen) {
        Layout layout = mTextView.getLayout();
        int offset;
        if (layout != null) {
            if (mPreviousLineTouched == UNSET_LINE) {
                mPreviousLineTouched = mTextView.getLineAtCoordinate(y);
            }
            int currLine = mTextView.mEditor.getCurrentLineAdjustedForSlop(layout, mPreviousLineTouched, y);
            offset = getOffsetAtCoordinate(layout, currLine, x);
            mPreviousLineTouched = currLine;
        } else {
            offset = -1;
        }
        positionAtCursorOffset(offset, false, fromTouchScreen);
        if (mTextView.mEditor.mTextActionMode != null) {
            mTextView.mEditor.invalidateActionMode();
        }
    }

    @Override
    void onHandleMoved() {
        super.onHandleMoved();
        removeHiderCallback();
    }

    @Override
    public void onDetached() {
        super.onDetached();
        removeHiderCallback();
    }

    @Override
//    @MagnifierHandleTrigger
    protected int getMagnifierHandleTrigger() {
        return /*MagnifierHandleTrigger.INSERTION*/0;
    }
}
