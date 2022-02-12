//package com.wittmane.testingedittext.editor;
//
//
//import android.os.SystemClock;
//import android.text.Layout;
//import android.text.Selection;
//import android.text.Spannable;
//import android.util.Log;
//import android.view.HapticFeedbackConstants;
//import android.view.InputDevice;
//import android.view.MotionEvent;
//import android.view.ViewConfiguration;
//import android.view.ViewTreeObserver;
//
//import androidx.annotation.Nullable;
//
//import com.wittmane.testingedittext.CustomEditTextView;
//import com.wittmane.testingedittext.SimpleTouchManager;
//
//import static com.wittmane.testingedittext.Editor.UNSET_LINE;
//
///** Controller for the insertion cursor. */
//public class InsertionPointCursorController implements CursorController {
//    static final String TAG = InsertionPointCursorController.class.getSimpleName();
//
//    private static final int RECENT_CUT_COPY_DURATION_MS = 15 * 1000; // 15 seconds in millis
//
//    private InsertionHandleView mHandle;
//    // Tracks whether the cursor is currently being dragged.
//    private boolean mIsDraggingCursor;
//    // During a drag, tracks whether the user's finger has adjusted to be over the handle rather
//    // than the cursor bar.
//    private boolean mIsTouchSnappedToHandleDuringDrag;
//    // During a drag, tracks the line of text where the cursor was last positioned.
//    private int mPrevLineDuringDrag;
//
//    private final CustomEditTextView mTextView;
//
//    public InsertionPointCursorController(CustomEditTextView textView) {
//        mTextView = textView;
//    }
//
//    public void onTouchEvent(MotionEvent event) {
//        //TODO: (EW) add this back once the selection controller is added
////        if (mTextView.mEditor.hasSelectionController() && mTextView.mEditor.getSelectionController().isCursorBeingModified()) {
////            return;
////        }
//        switch (event.getActionMasked()) {
//            case MotionEvent.ACTION_MOVE:
//                if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
//                    break;
//                }
//                if (mIsDraggingCursor) {
//                    performCursorDrag(event);
//                } else if (mTextView.mEditor.mFlagCursorDragFromAnywhereEnabled
//                        && mTextView.getLayout() != null
//                        && mTextView.isFocused()
//                        && mTextView.mTouchState.isMovedEnoughForDrag()
//                        && !mTextView.mTouchState.isDragCloseToVertical()) {
//                    startCursorDrag(event);
//                }
//                break;
//            case MotionEvent.ACTION_UP:
//            case MotionEvent.ACTION_CANCEL:
//                if (mIsDraggingCursor) {
//                    endCursorDrag(event);
//                }
//                break;
//        }
//    }
//
//    private void positionCursorDuringDrag(MotionEvent event) {
//        mPrevLineDuringDrag = getLineDuringDrag(event);
//        int offset = mTextView.getOffsetAtCoordinate(mPrevLineDuringDrag, event.getX());
//        int oldSelectionStart = mTextView.getSelectionStart();
//        int oldSelectionEnd = mTextView.getSelectionEnd();
//        if (offset == oldSelectionStart && offset == oldSelectionEnd) {
//            return;
//        }
//        Selection.setSelection((Spannable) mTextView.getText(), offset);
//        mTextView.updateCursorPosition();
//        if (mTextView.mEditor.mHapticTextHandleEnabled) {
//            mTextView.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
//        }
//    }
//
//    /**
//     * Returns the line where the cursor should be positioned during a cursor drag. Rather than
//     * simply returning the line directly at the touch position, this function has the following
//     * additional logic:
//     * 1) Apply some slop to avoid switching lines if the touch moves just slightly off the
//     * current line.
//     * 2) Allow the user's finger to slide down and "snap" to the handle to provide better
//     * visibility of the cursor and text.
//     */
//    private int getLineDuringDrag(MotionEvent event) {
//        final Layout layout = mTextView.getLayout();
//        if (mPrevLineDuringDrag == UNSET_LINE) {
//            return mTextView.mEditor.getCurrentLineAdjustedForSlop(layout, mPrevLineDuringDrag, event.getY());
//        }
//        // In case of touch through on handle (when isOnHandle() returns true), event.getY()
//        // returns the midpoint of the cursor vertical bar, while event.getRawY() returns the
//        // finger location on the screen. See {@link InsertionHandleView#touchThrough}.
//        int[] location = new int[2];
//        mTextView.getLocationOnScreen(location);
//        final float fingerY = mTextView.mTouchState.isOnHandle()
//                ? event.getRawY() - /*mTextView.getLocationOnScreen()*/location[1]
//                : event.getY();
//        final float cursorY = fingerY - getHandle().getIdealFingerToCursorOffset();
//        int line = mTextView.mEditor.getCurrentLineAdjustedForSlop(layout, mPrevLineDuringDrag, cursorY);
//        if (mIsTouchSnappedToHandleDuringDrag) {
//            // Just returns the line hit by cursor Y when already snapped.
//            return line;
//        }
//        if (line < mPrevLineDuringDrag) {
//            // The cursor Y aims too high & not yet snapped, check the finger Y.
//            // If finger Y is moving downwards, don't jump to lower line (until snap).
//            // If finger Y is moving upwards, can jump to upper line.
//            return Math.min(mPrevLineDuringDrag,
//                    mTextView.mEditor.getCurrentLineAdjustedForSlop(layout, mPrevLineDuringDrag, fingerY));
//        }
//        // The cursor Y aims not too high, so snap!
//        mIsTouchSnappedToHandleDuringDrag = true;
//        if (SimpleTouchManager.DEBUG_CURSOR) {
//            logCursor("InsertionPointCursorController",
//                    "snapped touch to handle: fingerY=%d, cursorY=%d, mLastLine=%d, line=%d",
//                    (int) fingerY, (int) cursorY, mPrevLineDuringDrag, line);
//        }
//        return line;
//    }
//
//    private void startCursorDrag(MotionEvent event) {
//        if (SimpleTouchManager.DEBUG_CURSOR) {
//            logCursor("InsertionPointCursorController", "start cursor drag");
//        }
//        mIsDraggingCursor = true;
//        mIsTouchSnappedToHandleDuringDrag = false;
//        mPrevLineDuringDrag = UNSET_LINE;
//        // We don't want the parent scroll/long-press handlers to take over while dragging.
//        mTextView.getParent().requestDisallowInterceptTouchEvent(true);
//        mTextView.cancelLongPress();
//        // Update the cursor position.
//        positionCursorDuringDrag(event);
//        // Show the cursor handle and magnifier.
//        show();
//        getHandle().removeHiderCallback();
//        getHandle().updateMagnifier(event);
//        // TODO(b/146555651): Figure out if suspendBlink() should be called here.
//    }
//
//    private void performCursorDrag(MotionEvent event) {
//        positionCursorDuringDrag(event);
//        getHandle().updateMagnifier(event);
//    }
//
//    private void endCursorDrag(MotionEvent event) {
//        if (SimpleTouchManager.DEBUG_CURSOR) {
//            logCursor("InsertionPointCursorController", "end cursor drag");
//        }
//        mIsDraggingCursor = false;
//        mIsTouchSnappedToHandleDuringDrag = false;
//        mPrevLineDuringDrag = UNSET_LINE;
//        // Hide the magnifier and set the handle to be hidden after a delay.
//        getHandle().dismissMagnifier();
//        getHandle().hideAfterDelay();
//        // We're no longer dragging, so let the parent receive events.
//        mTextView.getParent().requestDisallowInterceptTouchEvent(false);
//    }
//
//    public void show() {
//        getHandle().show();
//
//        final long durationSinceCutOrCopy =
//                SystemClock.uptimeMillis() - CustomEditTextView.sLastCutCopyOrTextChangedTime;
//
//        if (mTextView.mEditor.mInsertionActionModeRunnable != null) {
//            if (mIsDraggingCursor
//                    || mTextView.mTouchState.isMultiTap()
//                    /*|| isCursorInsideEasyCorrectionSpan()*/) {
//                // Cancel the runnable for showing the floating toolbar.
//                mTextView.removeCallbacks(mTextView.mEditor.mInsertionActionModeRunnable);
//            }
//        }
//
//        // If the user recently performed a Cut or Copy action, we want to show the floating
//        // toolbar even for a single tap.
//        if (!mIsDraggingCursor
//                && !mTextView.mTouchState.isMultiTap()
////                && !isCursorInsideEasyCorrectionSpan()
//                && (durationSinceCutOrCopy < RECENT_CUT_COPY_DURATION_MS)) {
//            if (mTextView.mEditor.mTextActionMode == null) {
//                if (mTextView.mEditor.mInsertionActionModeRunnable == null) {
//                    mTextView.mEditor.mInsertionActionModeRunnable = new Runnable() {
//                        @Override
//                        public void run() {
//                            mTextView.mEditor.startInsertionActionMode();
//                        }
//                    };
//                }
//                mTextView.postDelayed(
//                        mTextView.mEditor.mInsertionActionModeRunnable,
//                        ViewConfiguration.getDoubleTapTimeout() + 1);
//            }
//        }
//
//        if (!mIsDraggingCursor) {
//            getHandle().hideAfterDelay();
//        }
//
////        if (mTextView.mEditor.mSelectionModifierCursorController != null) {
////            mTextView.mEditor.mSelectionModifierCursorController.hide();
////        }
//    }
//
//    public void hide() {
//        if (mHandle != null) {
//            mHandle.hide();
//        }
//    }
//
//    public void onTouchModeChanged(boolean isInTouchMode) {
//        if (!isInTouchMode) {
//            hide();
//        }
//    }
//
//    public InsertionHandleView getHandle() {
//        if (mHandle == null) {
//            //TODO: (EW) load the drawable if this version gets used
////            mTextView.mEditor.loadHandleDrawables(false /* overwrite */);
//            mHandle = new InsertionHandleView(mTextView.mEditor.mSelectHandleCenter, mTextView);
//        }
//        return mHandle;
//    }
//
//    private void reloadHandleDrawable() {
//        if (mHandle == null) {
//            // No need to reload, the potentially new drawable will
//            // be used when the handle is created.
//            return;
//        }
//        mHandle.setDrawables(mTextView.mEditor.mSelectHandleCenter, mTextView.mEditor.mSelectHandleCenter);
//    }
//
//    @Override
//    public void onDetached() {
//        final ViewTreeObserver observer = mTextView.getViewTreeObserver();
//        observer.removeOnTouchModeChangeListener(this);
//
//        if (mHandle != null) mHandle.onDetached();
//    }
//
//    @Override
//    public boolean isCursorBeingModified() {
//        return mIsDraggingCursor || (mHandle != null && mHandle.isDragging());
//    }
//
//    @Override
//    public boolean isActive() {
//        return mHandle != null && mHandle.isShowing();
//    }
//
//    public void invalidateHandle() {
//        if (mHandle != null) {
//            mHandle.invalidate();
//        }
//    }
//
//    static void logCursor(String location, @Nullable String msgFormat, Object ... msgArgs) {
//        if (msgFormat == null) {
//            Log.d(TAG, location);
//        } else {
//            Log.d(TAG, location + ": " + String.format(msgFormat, msgArgs));
//        }
//    }
//}
