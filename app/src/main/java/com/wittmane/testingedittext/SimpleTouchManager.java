package com.wittmane.testingedittext;

import android.content.pm.PackageManager;
import android.text.Selection;
import android.text.Spannable;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class SimpleTouchManager {
    static final String TAG = SimpleTouchManager.class.getSimpleName();
    private final boolean DEBUG_CURSOR = true;

    private static final int NO_POINTER_ID = -1;

    private final CustomEditTextView mTextView;

    // The offsets of that last touch down event. Remembered to start selection there.
    private int mMinTouchOffset, mMaxTouchOffset;

    private boolean mGestureStayedInTapRegion;

    // Where the user first starts the drag motion.
    private int mStartOffset = -1;

    private boolean mHaventMovedEnoughToStartDrag;
    // The line that a selection happened most recently with the drag accelerator.
    private int mLineSelectionIsOn = -1;
    // Whether the drag accelerator has selected past the initial line.
    private boolean mSwitchedLines = false;

    // Indicates the drag accelerator mode that the user is currently using.
    private int mDragAcceleratorMode = DRAG_ACCELERATOR_MODE_INACTIVE;
    // Drag accelerator is inactive.
    private static final int DRAG_ACCELERATOR_MODE_INACTIVE = 0;
    // Character based selection by dragging. Only for mouse.
    private static final int DRAG_ACCELERATOR_MODE_CHARACTER = 1;
    // Word based selection by dragging. Enabled after long pressing or double tapping.
    private static final int DRAG_ACCELERATOR_MODE_WORD = 2;
    // Paragraph based selection by dragging. Enabled after mouse triple click.
    private static final int DRAG_ACCELERATOR_MODE_PARAGRAPH = 3;

    private final EditorTouchState mTouchState = new EditorTouchState();

    // The button state as of the last time #onTouchEvent is called.
    private int mLastButtonState;

    //TODO: why are there 2 of these - they sound like they do the same thing - consolidate or clarify
    boolean mDiscardNextActionUp;
    boolean mIgnoreActionUpEvent;

    private boolean mTouchFocusSelected;

    public SimpleTouchManager(CustomEditTextView textView) {
        mTextView = textView;
    }

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

    //returns if the event was handled and should not continue processing
    public boolean onTouchEventPre(MotionEvent event) {
        if (!isFromPrimePointer(event, false)) {
            return true;
        }


        final boolean filterOutEvent = shouldFilterOutTouchEvent(event);
        mLastButtonState = event.getButtonState();
        if (filterOutEvent) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                mDiscardNextActionUp = true;
            }

//            if (mEditor.mInsertionPointCursorController != null
//                    && mEditor.mInsertionPointCursorController.isCursorBeingModified()) {
//                return true;
//            }
//            if (mEditor.mSelectionModifierCursorController != null
//                    && mEditor.mSelectionModifierCursorController.isDragAcceleratorActive()) {
//                return true;
//            }
            return false;
        }
        ViewConfiguration viewConfiguration = ViewConfiguration.get(mTextView.getContext());
        mTouchState.update(event, viewConfiguration);


        // this is from Editor#SelectionModifierCursorController#onTouchEvent
        final float eventX = event.getX();
        final float eventY = event.getY();
        final boolean isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
//                if (extractedTextModeWillBeStarted()) {
//                    // Prevent duplicating the selection handles until the mode starts.
//                    hide();
//                } else {
                // Remember finger down position, to be able to start selection from there.
                mMinTouchOffset = mMaxTouchOffset = mTextView.getOffsetForPosition(
                        eventX, eventY);

                // Double tap detection
                if (mGestureStayedInTapRegion
                        && mTouchState.isMultiTapInSameArea()
                        && (isMouse || mTextView.isPositionOnText(eventX, eventY)
                        || mTouchState.isOnHandle())) {
                    if (DEBUG_CURSOR) {
                        Log.d(TAG, "onTouchEvent: ACTION_DOWN: select and start drag");
                    }
                    if (mTouchState.isDoubleTap()) {
                        selectCurrentWordAndStartDrag();
//                        } else if (mTouchState.isTripleClick()) {
//                            selectCurrentParagraphAndStartDrag();
                    }
                    mDiscardNextActionUp = true;
                }
                mGestureStayedInTapRegion = true;
                mHaventMovedEnoughToStartDrag = true;
//                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                // Handle multi-point gestures. Keep min and max offset positions.
                // Only activated for devices that correctly handle multi-touch.
                if (mTextView.getContext().getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)) {
                    updateMinAndMaxOffsets(event);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mGestureStayedInTapRegion) {
                    final ViewConfiguration viewConfig = ViewConfiguration.get(
                            mTextView.getContext());
                    mGestureStayedInTapRegion = EditorTouchState.isDistanceWithin(
                            mTouchState.getLastDownX(), mTouchState.getLastDownY(),
                            eventX, eventY, viewConfig./*getScaledDoubleTapTouchSlop*/getScaledTouchSlop());
                    //getScaledDoubleTapTouchSlop is hidden
                    // getScaledDoubleTapTouchSlop returns mDoubleTapTouchSlop, which is set to mTouchSlop
                }

                if (mHaventMovedEnoughToStartDrag) {
                    mHaventMovedEnoughToStartDrag = !mTouchState.isMovedEnoughForDrag();
                }

//                if (isMouse && !isDragAcceleratorActive()) {
//                    final int offset = mTextView.getOffsetForPosition(eventX, eventY);
//                    if (mTextView.hasSelection()
//                            && (!mHaventMovedEnoughToStartDrag || mStartOffset != offset)
//                            && offset >= mTextView.getSelectionStart()
//                            && offset <= mTextView.getSelectionEnd()) {
//                        startDragAndDrop();
//                        break;
//                    }
//
//                    if (mStartOffset != offset) {
//                        // Start character based drag accelerator.
//                        stopTextActionMode();
//                        enterDrag(DRAG_ACCELERATOR_MODE_CHARACTER);
//                        mDiscardNextActionUp = true;
//                        mHaventMovedEnoughToStartDrag = false;
//                    }
//                }

//                if (mStartHandle != null && mStartHandle.isShowing()) {
//                    // Don't do the drag if the handles are showing already.
//                    break;
//                }

                updateSelection(event);
                break;

            case MotionEvent.ACTION_UP:
                if (DEBUG_CURSOR) {
                    Log.d(TAG, "onTouchEvent: ACTION_UP");
                }
                if (!isDragAcceleratorActive()) {
                    break;
                }
                updateSelection(event);

                // No longer dragging to select text, let the parent intercept events.
                mTextView.getParent().requestDisallowInterceptTouchEvent(false);

                // No longer the first dragging motion, reset.
                resetDragAcceleratorState();

                if (mTextView.hasSelection()) {
                    // Drag selection should not be adjusted by the text classifier.
//                    startSelectionActionModeAsync(mHaventMovedEnoughToStartDrag);
                }
                break;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Reset this state; it will be re-set if super.onTouchEvent
            // causes focus to move to the view.
            mTouchFocusSelected = false;
            mIgnoreActionUpEvent = false;
        }

        return false;
    }
    public boolean onTouchEventPost(MotionEvent event, boolean superResult) {
        // this is from TextView#onTouchEvent
        if (mDiscardNextActionUp) {
            mDiscardNextActionUp = false;
            if (DEBUG_CURSOR) {
                Log.d(TAG, "onTouchEvent: release after long press detected");
            }
//            if (mEditor.mIsInsertionActionModeStartPending) {
//                mEditor.startInsertionActionMode();
//                mEditor.mIsInsertionActionModeStartPending = false;
//            }
            return superResult;
        }

        final int action = event.getActionMasked();
        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP)
                && !mIgnoreActionUpEvent && mTextView.isFocused();

        if (mTextView.isEnabled()) {
            boolean handled = false;
            if (touchIsFinished) {
                mTextView.onTouchFinished();

                // this is from Editor#onTouchUpEvent
                if (!resetSelection(
                        mTextView.getOffsetForPosition(event.getX(), event.getY()))) {
                    boolean selectAllGotFocus = /*mSelectAllOnFocus && mTextView.didTouchFocusSelect()*/false;
//                    hideCursorAndSpanControllers();
//                    stopTextActionMode();
                    CharSequence text = mTextView.getText();
                    if (!selectAllGotFocus && text.length() > 0) {
                        // Move cursor
                        final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());

                        final boolean shouldInsertCursor = /*!mRequestingLinkActionMode*/true;
                        if (shouldInsertCursor) {
                            Selection.setSelection((Spannable) text, offset);
//                            if (mSpellChecker != null) {
//                                // When the cursor moves, the word that was typed may need spell check
//                                mSpellChecker.onSelectionChanged();
//                            }
                        }

//                        if (!extractedTextModeWillBeStarted()) {
//                            if (isCursorInsideEasyCorrectionSpan()) {
//                                // Cancel the single tap delayed runnable.
//                                if (mInsertionActionModeRunnable != null) {
//                                    mTextView.removeCallbacks(mInsertionActionModeRunnable);
//                                }
//
//                                mShowSuggestionRunnable = this::replace;
//
//                                // removeCallbacks is performed on every touch
//                                mTextView.postDelayed(mShowSuggestionRunnable,
//                                        ViewConfiguration.getDoubleTapTimeout());
//                            } else if (hasInsertionController()) {
//                                if (shouldInsertCursor) {
//                                    getInsertionController().show();
//                                } else {
//                                    getInsertionController().hide();
//                                }
//                            }
//                        }
                    }
                }

                handled = true;
            }

            if (handled) {
                return true;
            }
        }
        return superResult;
    }

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

    /**
     * @param event
     */
    private void updateMinAndMaxOffsets(MotionEvent event) {
        int pointerCount = event.getPointerCount();
        for (int index = 0; index < pointerCount; index++) {
            int offset = mTextView.getOffsetForPosition(event.getX(index), event.getY(index));
            if (offset < mMinTouchOffset) mMinTouchOffset = offset;
            if (offset > mMaxTouchOffset) mMaxTouchOffset = offset;
        }
    }

    public int getMinTouchOffset() {
        return mMinTouchOffset;
    }

    public int getMaxTouchOffset() {
        return mMaxTouchOffset;
    }

    /**
     * @return true if the user is selecting text using the drag accelerator.
     */
    public boolean isDragAcceleratorActive() {
        return mDragAcceleratorMode != DRAG_ACCELERATOR_MODE_INACTIVE;
    }

    private void resetDragAcceleratorState() {
        mStartOffset = -1;
        mDragAcceleratorMode = DRAG_ACCELERATOR_MODE_INACTIVE;
        mSwitchedLines = false;
        final int selectionStart = mTextView.getSelectionStart();
        final int selectionEnd = mTextView.getSelectionEnd();
        if (selectionStart < 0 || selectionEnd < 0) {
            Selection.removeSelection((Spannable) mTextView.getText());
        } else if (selectionStart > selectionEnd) {
            Selection.setSelection((Spannable) mTextView.getText(),
                    selectionEnd, selectionStart);
        }
    }



    private boolean resetSelection(int textIndex) {
//        if (mSelectionTracker.resetSelection(textIndex, mEditor)) {
//            invalidateActionModeAsync();
//            return true;
//        }
        return false;
    }



    private void updateSelection(MotionEvent event) {
//        if (mTextView.getLayout() != null) {
//            switch (mDragAcceleratorMode) {
//                case DRAG_ACCELERATOR_MODE_CHARACTER:
//                    updateCharacterBasedSelection(event);
//                    break;
//                case DRAG_ACCELERATOR_MODE_WORD:
//                    updateWordBasedSelection(event);
//                    break;
//                case DRAG_ACCELERATOR_MODE_PARAGRAPH:
//                    updateParagraphBasedSelection(event);
//                    break;
//            }
//        }
    }

    /**
     * If the TextView allows text selection, selects the current word when no existing selection
     * was available and starts a drag.
     *
     * @return true if the drag was started.
     */
    private boolean selectCurrentWordAndStartDrag() {
//        if (mInsertionActionModeRunnable != null) {
//            mTextView.removeCallbacks(mInsertionActionModeRunnable);
//        }
//        if (extractedTextModeWillBeStarted()) {
//            return false;
//        }
//        if (!checkField()) {
//            return false;
//        }
//        if (!mTextView.hasSelection() && !selectCurrentWord()) {
//            // No selection and cannot select a word.
//            return false;
//        }
//        stopTextActionModeWithPreservingSelection();
//        getSelectionController().enterDrag(
//                SelectionModifierCursorController.DRAG_ACCELERATOR_MODE_WORD);
        return true;
    }




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
}
