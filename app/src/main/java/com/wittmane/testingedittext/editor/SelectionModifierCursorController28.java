package com.wittmane.testingedittext.editor;

import android.content.pm.PackageManager;
import android.text.Selection;
import android.text.Spannable;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;

import com.wittmane.testingedittext.CustomEditTextView;
import com.wittmane.testingedittext.Editor;
import com.wittmane.testingedittext.R;

import static com.wittmane.testingedittext.Editor.TAP_STATE_DOUBLE_TAP;
import static com.wittmane.testingedittext.Editor.TAP_STATE_TRIPLE_CLICK;
import static com.wittmane.testingedittext.editor.SelectionHandleView28.HANDLE_TYPE_SELECTION_END;
import static com.wittmane.testingedittext.editor.SelectionHandleView28.HANDLE_TYPE_SELECTION_START;

public class SelectionModifierCursorController28 implements CursorController {
    static final String TAG = SelectionModifierCursorController28.class.getSimpleName();

    // The cursor controller handles, lazily created when shown.
    SelectionHandleView28 mStartHandle;
    private SelectionHandleView28 mEndHandle;
    // The offsets of that last touch down event. Remembered to start selection there.
    private int mMinTouchOffset, mMaxTouchOffset;

    private float mDownPositionX, mDownPositionY;
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
    public static final int DRAG_ACCELERATOR_MODE_INACTIVE = 0;
    // Character based selection by dragging. Only for mouse.
    public static final int DRAG_ACCELERATOR_MODE_CHARACTER = 1;
    // Word based selection by dragging. Enabled after long pressing or double tapping.
    public static final int DRAG_ACCELERATOR_MODE_WORD = 2;
    // Paragraph based selection by dragging. Enabled after mouse triple click.
    public static final int DRAG_ACCELERATOR_MODE_PARAGRAPH = 3;

    protected final CustomEditTextView mTextView;

    public SelectionModifierCursorController28(CustomEditTextView textView) {
        mTextView = textView;
        resetTouchOffsets();
    }

    public void show() {
        if (mTextView.isInBatchEditMode()) {
            return;
        }
        initDrawables();
        initHandles();
    }

    void initDrawables() {
        if (mTextView.mEditor.mSelectHandleLeft == null) {
            mTextView.mEditor.mSelectHandleLeft = mTextView.getContext().getDrawable(
                    mTextView.mTextSelectHandleLeftRes);
        }
        if (mTextView.mEditor.mSelectHandleRight == null) {
            mTextView.mEditor.mSelectHandleRight = mTextView.getContext().getDrawable(
                    mTextView.mTextSelectHandleRightRes);
        }
    }

    void initHandles() {
        // Lazy object creation has to be done before updatePosition() is called.
        if (mStartHandle == null) {
            mStartHandle = new SelectionHandleView28(mTextView.mEditor.mSelectHandleLeft, mTextView.mEditor.mSelectHandleRight,
                    /*com.android.internal.*/R.id.selection_start_handle,
                    HANDLE_TYPE_SELECTION_START, mTextView);
        }
        if (mEndHandle == null) {
            mEndHandle = new SelectionHandleView28(mTextView.mEditor.mSelectHandleRight, mTextView.mEditor.mSelectHandleLeft,
                    /*com.android.internal.*/R.id.selection_end_handle,
                    HANDLE_TYPE_SELECTION_END, mTextView);
        }

        mStartHandle.show();
        mEndHandle.show();

        mTextView.mEditor.hideInsertionPointCursorController();
    }

    public void hide() {
        if (mStartHandle != null) mStartHandle.hide();
        if (mEndHandle != null) mEndHandle.hide();
    }

    public void enterDrag(int dragAcceleratorMode) {
        // Just need to init the handles / hide insertion cursor.
        show();
        mDragAcceleratorMode = dragAcceleratorMode;
        // Start location of selection.
        mStartOffset = mTextView.getOffsetForPosition(mTextView.mEditor.mLastDownPositionX,
                mTextView.mEditor.mLastDownPositionY);
        mLineSelectionIsOn = mTextView.getLineAtCoordinate(mTextView.mEditor.mLastDownPositionY);
        // Don't show the handles until user has lifted finger.
        hide();

        // This stops scrolling parents from intercepting the touch event, allowing
        // the user to continue dragging across the screen to select text; TextView will
        // scroll as necessary.
        mTextView.getParent().requestDisallowInterceptTouchEvent(true);
        mTextView.cancelLongPress();
    }

    public void onTouchEvent(MotionEvent event) {
        // This is done even when the View does not have focus, so that long presses can start
        // selection and tap can move cursor from this tap position.
        final float eventX = event.getX();
        final float eventY = event.getY();
        final boolean isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mTextView.mEditor.extractedTextModeWillBeStarted()) {
                    // Prevent duplicating the selection handles until the mode starts.
                    hide();
                } else {
                    // Remember finger down position, to be able to start selection from there.
                    mMinTouchOffset = mMaxTouchOffset = mTextView.getOffsetForPosition(
                            eventX, eventY);
                    Log.w(TAG, "onTouchEvent: touchOffset=" + mMinTouchOffset);

                    // Double tap detection
                    if (mGestureStayedInTapRegion) {
                        if (mTextView.mEditor.mTapState == TAP_STATE_DOUBLE_TAP
                                || mTextView.mEditor.mTapState == TAP_STATE_TRIPLE_CLICK) {
                            final float deltaX = eventX - mDownPositionX;
                            final float deltaY = eventY - mDownPositionY;
                            final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

                            ViewConfiguration viewConfiguration = ViewConfiguration.get(
                                    mTextView.getContext());
                            int doubleTapSlop = viewConfiguration.getScaledDoubleTapSlop();
                            boolean stayedInArea =
                                    distanceSquared < doubleTapSlop * doubleTapSlop;

                            if (stayedInArea && (isMouse || mTextView.mEditor.isPositionOnText(eventX, eventY))) {
                                if (mTextView.mEditor.mTapState == TAP_STATE_DOUBLE_TAP) {
                                    mTextView.mEditor.selectCurrentWordAndStartDrag();
                                } else if (mTextView.mEditor.mTapState == TAP_STATE_TRIPLE_CLICK) {
                                    selectCurrentParagraphAndStartDrag();
                                }
                                mTextView.mEditor.mDiscardNextActionUp = true;
                            }
                        }
                    }

                    mDownPositionX = eventX;
                    mDownPositionY = eventY;
                    mGestureStayedInTapRegion = true;
                    mHaventMovedEnoughToStartDrag = true;
                }
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
                final ViewConfiguration viewConfig = ViewConfiguration.get(
                        mTextView.getContext());
                final int touchSlop = viewConfig.getScaledTouchSlop();

                if (mGestureStayedInTapRegion || mHaventMovedEnoughToStartDrag) {
                    final float deltaX = eventX - mDownPositionX;
                    final float deltaY = eventY - mDownPositionY;
                    final float distanceSquared = deltaX * deltaX + deltaY * deltaY;

                    if (mGestureStayedInTapRegion) {
                        int doubleTapTouchSlop = viewConfig./*getScaledDoubleTapTouchSlop*/getScaledTouchSlop();
                        mGestureStayedInTapRegion =
                                distanceSquared <= doubleTapTouchSlop * doubleTapTouchSlop;
                    }
                    if (mHaventMovedEnoughToStartDrag) {
                        // We don't start dragging until the user has moved enough.
                        mHaventMovedEnoughToStartDrag =
                                distanceSquared <= touchSlop * touchSlop;
                    }
                }

                if (isMouse && !isDragAcceleratorActive()) {
                    final int offset = mTextView.getOffsetForPosition(eventX, eventY);
                    if (mTextView.hasSelection()
                            && (!mHaventMovedEnoughToStartDrag || mStartOffset != offset)
                            && offset >= mTextView.getSelectionStart()
                            && offset <= mTextView.getSelectionEnd()) {
                        mTextView.mEditor.startDragAndDrop();
                        break;
                    }

                    if (mStartOffset != offset) {
                        // Start character based drag accelerator.
                        mTextView.mEditor.stopTextActionMode();
                        enterDrag(DRAG_ACCELERATOR_MODE_CHARACTER);
                        mTextView.mEditor.mDiscardNextActionUp = true;
                        mHaventMovedEnoughToStartDrag = false;
                    }
                }

                if (mStartHandle != null && mStartHandle.isShowing()) {
                    // Don't do the drag if the handles are showing already.
                    break;
                }

                updateSelection(event);
                break;

            case MotionEvent.ACTION_UP:
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
                    mTextView.mEditor.startSelectionActionModeAsync(mHaventMovedEnoughToStartDrag);
                }
                break;
        }
    }

    private void updateSelection(MotionEvent event) {
        if (mTextView.getLayout() != null) {
            switch (mDragAcceleratorMode) {
                case DRAG_ACCELERATOR_MODE_CHARACTER:
                    updateCharacterBasedSelection(event);
                    break;
                case DRAG_ACCELERATOR_MODE_WORD:
                    updateWordBasedSelection(event);
                    break;
                case DRAG_ACCELERATOR_MODE_PARAGRAPH:
                    updateParagraphBasedSelection(event);
                    break;
            }
        }
    }

    /**
     * If the TextView allows text selection, selects the current paragraph and starts a drag.
     *
     * @return true if the drag was started.
     */
    private boolean selectCurrentParagraphAndStartDrag() {
        if (mTextView.mEditor.mInsertionActionModeRunnable != null) {
            mTextView.removeCallbacks(mTextView.mEditor.mInsertionActionModeRunnable);
        }
        mTextView.mEditor.stopTextActionMode();
        if (!mTextView.mEditor.selectCurrentParagraph()) {
            return false;
        }
        enterDrag(SelectionModifierCursorController28.DRAG_ACCELERATOR_MODE_PARAGRAPH);
        return true;
    }

    private void updateCharacterBasedSelection(MotionEvent event) {
        final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());
        updateSelectionInternal(mStartOffset, offset,
                event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
    }

    private void updateWordBasedSelection(MotionEvent event) {
        if (mHaventMovedEnoughToStartDrag) {
            return;
        }
        final boolean isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE);
        final ViewConfiguration viewConfig = ViewConfiguration.get(
                mTextView.getContext());
        final float eventX = event.getX();
        final float eventY = event.getY();
        final int currLine;
        if (isMouse) {
            // No need to offset the y coordinate for mouse input.
            currLine = mTextView.getLineAtCoordinate(eventY);
        } else {
            float y = eventY;
            if (mSwitchedLines) {
                // Offset the finger by the same vertical offset as the handles.
                // This improves visibility of the content being selected by
                // shifting the finger below the content, this is applied once
                // the user has switched lines.
                final int touchSlop = viewConfig.getScaledTouchSlop();
                final float fingerOffset = (mStartHandle != null)
                        ? mStartHandle.getIdealVerticalOffset()
                        : touchSlop;
                y = eventY - fingerOffset;
            }

            currLine = mTextView.mEditor.getCurrentLineAdjustedForSlop(mTextView.getLayout(), mLineSelectionIsOn,
                    y);
            if (!mSwitchedLines && currLine != mLineSelectionIsOn) {
                // Break early here, we want to offset the finger position from
                // the selection highlight, once the user moved their finger
                // to a different line we should apply the offset and *not* switch
                // lines until recomputing the position with the finger offset.
                mSwitchedLines = true;
                return;
            }
        }

        int startOffset;
        int offset = mTextView.getOffsetAtCoordinate(currLine, eventX);
        // Snap to word boundaries.
        if (mStartOffset < offset) {
            // Expanding with end handle.
            offset = mTextView.mEditor.getWordEnd(offset);
            startOffset = mTextView.mEditor.getWordStart(mStartOffset);
        } else {
            // Expanding with start handle.
            offset = mTextView.mEditor.getWordStart(offset);
            startOffset = mTextView.mEditor.getWordEnd(mStartOffset);
            if (startOffset == offset) {
                offset = mTextView.mEditor.getNextCursorOffset(offset, false);
            }
        }
        mLineSelectionIsOn = currLine;
        updateSelectionInternal(startOffset, offset,
                event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
    }

    private void updateParagraphBasedSelection(MotionEvent event) {
        final int offset = mTextView.getOffsetForPosition(event.getX(), event.getY());

        final int start = Math.min(offset, mStartOffset);
        final int end = Math.max(offset, mStartOffset);
        //TODO: (EW) handle
//        final long paragraphsRange = mTextView.mEditor.getParagraphsRange(start, end);
//        final int selectionStart = TextUtils.unpackRangeStartFromLong(paragraphsRange);
//        final int selectionEnd = TextUtils.unpackRangeEndFromLong(paragraphsRange);
//        updateSelectionInternal(selectionStart, selectionEnd,
//                event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
    }

    private void updateSelectionInternal(int selectionStart, int selectionEnd,
                                         boolean fromTouchScreen) {
        final boolean performHapticFeedback = fromTouchScreen && mTextView.mEditor.mHapticTextHandleEnabled
                && ((mTextView.getSelectionStart() != selectionStart)
                || (mTextView.getSelectionEnd() != selectionEnd));
        Selection.setSelection((Spannable) mTextView.getText(), selectionStart, selectionEnd);
        if (performHapticFeedback) {
            mTextView.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
        }
    }

    /**
     * @param event
     */
    private void updateMinAndMaxOffsets(MotionEvent event) {
        int pointerCount = event.getPointerCount();
        for (int index = 0; index < pointerCount; index++) {
            int offset = mTextView.getOffsetForPosition(event.getX(index), event.getY(index));
            if (offset < mMinTouchOffset) {
                mMinTouchOffset = offset;
                Log.w(TAG, "updateMinAndMaxOffsets: mMinTouchOffset=" + mMinTouchOffset);
            }
            if (offset > mMaxTouchOffset) {
                mMaxTouchOffset = offset;
                Log.w(TAG, "updateMinAndMaxOffsets: mMaxTouchOffset=" + mMaxTouchOffset);
            }
        }
    }

    public int getMinTouchOffset() {
        return mMinTouchOffset;
    }

    public int getMaxTouchOffset() {
        return mMaxTouchOffset;
    }

    public void resetTouchOffsets() {
        mMinTouchOffset = mMaxTouchOffset = -1;
        Log.w(TAG, "resetTouchOffsets: touchOffset=" + mMinTouchOffset);
        resetDragAcceleratorState();
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

    /**
     * @return true iff this controller is currently used to move the selection start.
     */
    public boolean isSelectionStartDragged() {
        return mStartHandle != null && mStartHandle.isDragging();
    }

    @Override
    public boolean isCursorBeingModified() {
        return isDragAcceleratorActive() || isSelectionStartDragged()
                || (mEndHandle != null && mEndHandle.isDragging());
    }

    /**
     * @return true if the user is selecting text using the drag accelerator.
     */
    public boolean isDragAcceleratorActive() {
        return mDragAcceleratorMode != DRAG_ACCELERATOR_MODE_INACTIVE;
    }

    public void onTouchModeChanged(boolean isInTouchMode) {
        if (!isInTouchMode) {
            hide();
        }
    }

    @Override
    public void onDetached() {
        final ViewTreeObserver observer = mTextView.getViewTreeObserver();
        observer.removeOnTouchModeChangeListener(this);

        if (mStartHandle != null) mStartHandle.onDetached();
        if (mEndHandle != null) mEndHandle.onDetached();
    }

    @Override
    public boolean isActive() {
        return mStartHandle != null && mStartHandle.isShowing();
    }

    public void invalidateHandles() {
        if (mStartHandle != null) {
            mStartHandle.invalidate();
        }
        if (mEndHandle != null) {
            mEndHandle.invalidate();
        }
    }
}
