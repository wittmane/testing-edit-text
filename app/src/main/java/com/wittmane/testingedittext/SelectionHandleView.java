//package com.wittmane.testingedittext;
//
//
//import android.graphics.drawable.Drawable;
//import android.text.Selection;
//import android.text.Spannable;
//import android.view.Gravity;
//import android.view.MotionEvent;
//
//import androidx.annotation.NonNull;
//
///** For selection handles */
//public final class SelectionHandleView extends HandleView {
//    // Indicates the handle type, selection start (HANDLE_TYPE_SELECTION_START) or selection
//    // end (HANDLE_TYPE_SELECTION_END).
//    @HandleType
//    private final int mHandleType;
//    // Indicates whether the cursor is making adjustments within a word.
//    private boolean mInWord = false;
//    // Difference between touch position and word boundary position.
//    private float mTouchWordDelta;
//    // X value of the previous updatePosition call.
//    private float mPrevX;
//    // Indicates if the handle has moved a boundary between LTR and RTL text.
//    private boolean mLanguageDirectionChanged = false;
//    // Distance from edge of horizontally scrolling text view
//    // to use to switch to character mode.
//    private final float mTextViewEdgeSlop;
//    // Used to save text view location.
//    private final int[] mTextViewLocation = new int[2];
//
//    public SelectionHandleView(Drawable drawableLtr, Drawable drawableRtl, int id,
//                               @HandleType int handleType) {
//        super(drawableLtr, drawableRtl, id);
//        mHandleType = handleType;
//        ViewConfiguration viewConfiguration = ViewConfiguration.get(mTextView.getContext());
//        mTextViewEdgeSlop = viewConfiguration.getScaledTouchSlop() * 4;
//    }
//
//    private boolean isStartHandle() {
//        return mHandleType == HANDLE_TYPE_SELECTION_START;
//    }
//
//    @Override
//    protected int getHotspotX(Drawable drawable, boolean isRtlRun) {
//        if (isRtlRun == isStartHandle()) {
//            return drawable.getIntrinsicWidth() / 4;
//        } else {
//            return (drawable.getIntrinsicWidth() * 3) / 4;
//        }
//    }
//
//    @Override
//    protected int getHorizontalGravity(boolean isRtlRun) {
//        return (isRtlRun == isStartHandle()) ? Gravity.LEFT : Gravity.RIGHT;
//    }
//
//    @Override
//    public int getCurrentCursorOffset() {
//        return isStartHandle() ? mTextView.getSelectionStart() : mTextView.getSelectionEnd();
//    }
//
//    @Override
//    protected void updateSelection(int offset) {
//        if (isStartHandle()) {
//            Selection.setSelection((Spannable) mTextView.getText(), offset,
//                    mTextView.getSelectionEnd());
//        } else {
//            Selection.setSelection((Spannable) mTextView.getText(),
//                    mTextView.getSelectionStart(), offset);
//        }
//        updateDrawable(false /* updateDrawableWhenDragging */);
//        if (mTextActionMode != null) {
//            invalidateActionMode();
//        }
//    }
//
//    @Override
//    protected void updatePosition(float x, float y, boolean fromTouchScreen) {
//        final Layout layout = mTextView.getLayout();
//        if (layout == null) {
//            // HandleView will deal appropriately in positionAtCursorOffset when
//            // layout is null.
//            positionAndAdjustForCrossingHandles(mTextView.getOffsetForPosition(x, y),
//                    fromTouchScreen);
//            return;
//        }
//
//        if (mPreviousLineTouched == UNSET_LINE) {
//            mPreviousLineTouched = mTextView.getLineAtCoordinate(y);
//        }
//
//        boolean positionCursor = false;
//        final int anotherHandleOffset =
//                isStartHandle() ? mTextView.getSelectionEnd() : mTextView.getSelectionStart();
//        int currLine = getCurrentLineAdjustedForSlop(layout, mPreviousLineTouched, y);
//        int initialOffset = getOffsetAtCoordinate(layout, currLine, x);
//
//        if (isStartHandle() && initialOffset >= anotherHandleOffset
//                || !isStartHandle() && initialOffset <= anotherHandleOffset) {
//            // Handles have crossed, bound it to the first selected line and
//            // adjust by word / char as normal.
//            currLine = layout.getLineForOffset(anotherHandleOffset);
//            initialOffset = getOffsetAtCoordinate(layout, currLine, x);
//        }
//
//        int offset = initialOffset;
//        final int wordEnd = getWordEnd(offset);
//        final int wordStart = getWordStart(offset);
//
//        if (mPrevX == UNSET_X_VALUE) {
//            mPrevX = x;
//        }
//
//        final int currentOffset = getCurrentCursorOffset();
//        final boolean rtlAtCurrentOffset = isAtRtlRun(layout, currentOffset);
//        final boolean atRtl = isAtRtlRun(layout, offset);
//        final boolean isLvlBoundary = layout.isLevelBoundary(offset);
//
//        // We can't determine if the user is expanding or shrinking the selection if they're
//        // on a bi-di boundary, so until they've moved past the boundary we'll just place
//        // the cursor at the current position.
//        if (isLvlBoundary || (rtlAtCurrentOffset && !atRtl) || (!rtlAtCurrentOffset && atRtl)) {
//            // We're on a boundary or this is the first direction change -- just update
//            // to the current position.
//            mLanguageDirectionChanged = true;
//            mTouchWordDelta = 0.0f;
//            positionAndAdjustForCrossingHandles(offset, fromTouchScreen);
//            return;
//        } else if (mLanguageDirectionChanged && !isLvlBoundary) {
//            // We've just moved past the boundary so update the position. After this we can
//            // figure out if the user is expanding or shrinking to go by word or character.
//            positionAndAdjustForCrossingHandles(offset, fromTouchScreen);
//            mTouchWordDelta = 0.0f;
//            mLanguageDirectionChanged = false;
//            return;
//        }
//
//        boolean isExpanding;
//        final float xDiff = x - mPrevX;
//        if (isStartHandle()) {
//            isExpanding = currLine < mPreviousLineTouched;
//        } else {
//            isExpanding = currLine > mPreviousLineTouched;
//        }
//        if (atRtl == isStartHandle()) {
//            isExpanding |= xDiff > 0;
//        } else {
//            isExpanding |= xDiff < 0;
//        }
//
//        if (mTextView.getHorizontallyScrolling()) {
//            if (positionNearEdgeOfScrollingView(x, atRtl)
//                    && ((isStartHandle() && mTextView.getScrollX() != 0)
//                    || (!isStartHandle()
//                    && mTextView.canScrollHorizontally(atRtl ? -1 : 1)))
//                    && ((isExpanding && ((isStartHandle() && offset < currentOffset)
//                    || (!isStartHandle() && offset > currentOffset)))
//                    || !isExpanding)) {
//                // If we're expanding ensure that the offset is actually expanding compared to
//                // the current offset, if the handle snapped to the word, the finger position
//                // may be out of sync and we don't want the selection to jump back.
//                mTouchWordDelta = 0.0f;
//                final int nextOffset = (atRtl == isStartHandle())
//                        ? layout.getOffsetToRightOf(mPreviousOffset)
//                        : layout.getOffsetToLeftOf(mPreviousOffset);
//                positionAndAdjustForCrossingHandles(nextOffset, fromTouchScreen);
//                return;
//            }
//        }
//
//        if (isExpanding) {
//            // User is increasing the selection.
//            int wordBoundary = isStartHandle() ? wordStart : wordEnd;
//            final boolean snapToWord = (!mInWord
//                    || (isStartHandle() ? currLine < mPrevLine : currLine > mPrevLine))
//                    && atRtl == isAtRtlRun(layout, wordBoundary);
//            if (snapToWord) {
//                // Sometimes words can be broken across lines (Chinese, hyphenation).
//                // We still snap to the word boundary but we only use the letters on the
//                // current line to determine if the user is far enough into the word to snap.
//                if (layout.getLineForOffset(wordBoundary) != currLine) {
//                    wordBoundary = isStartHandle()
//                            ? layout.getLineStart(currLine) : layout.getLineEnd(currLine);
//                }
//                final int offsetThresholdToSnap = isStartHandle()
//                        ? wordEnd - ((wordEnd - wordBoundary) / 2)
//                        : wordStart + ((wordBoundary - wordStart) / 2);
//                if (isStartHandle()
//                        && (offset <= offsetThresholdToSnap || currLine < mPrevLine)) {
//                    // User is far enough into the word or on a different line so we expand by
//                    // word.
//                    offset = wordStart;
//                } else if (!isStartHandle()
//                        && (offset >= offsetThresholdToSnap || currLine > mPrevLine)) {
//                    // User is far enough into the word or on a different line so we expand by
//                    // word.
//                    offset = wordEnd;
//                } else {
//                    offset = mPreviousOffset;
//                }
//            }
//            if ((isStartHandle() && offset < initialOffset)
//                    || (!isStartHandle() && offset > initialOffset)) {
//                final float adjustedX = getHorizontal(layout, offset);
//                mTouchWordDelta =
//                        mTextView.convertToLocalHorizontalCoordinate(x) - adjustedX;
//            } else {
//                mTouchWordDelta = 0.0f;
//            }
//            positionCursor = true;
//        } else {
//            final int adjustedOffset =
//                    getOffsetAtCoordinate(layout, currLine, x - mTouchWordDelta);
//            final boolean shrinking = isStartHandle()
//                    ? adjustedOffset > mPreviousOffset || currLine > mPrevLine
//                    : adjustedOffset < mPreviousOffset || currLine < mPrevLine;
//            if (shrinking) {
//                // User is shrinking the selection.
//                if (currLine != mPrevLine) {
//                    // We're on a different line, so we'll snap to word boundaries.
//                    offset = isStartHandle() ? wordStart : wordEnd;
//                    if ((isStartHandle() && offset < initialOffset)
//                            || (!isStartHandle() && offset > initialOffset)) {
//                        final float adjustedX = getHorizontal(layout, offset);
//                        mTouchWordDelta =
//                                mTextView.convertToLocalHorizontalCoordinate(x) - adjustedX;
//                    } else {
//                        mTouchWordDelta = 0.0f;
//                    }
//                } else {
//                    offset = adjustedOffset;
//                }
//                positionCursor = true;
//            } else if ((isStartHandle() && adjustedOffset < mPreviousOffset)
//                    || (!isStartHandle() && adjustedOffset > mPreviousOffset)) {
//                // Handle has jumped to the word boundary, and the user is moving
//                // their finger towards the handle, the delta should be updated.
//                mTouchWordDelta = mTextView.convertToLocalHorizontalCoordinate(x)
//                        - getHorizontal(layout, mPreviousOffset);
//            }
//        }
//
//        if (positionCursor) {
//            mPreviousLineTouched = currLine;
//            positionAndAdjustForCrossingHandles(offset, fromTouchScreen);
//        }
//        mPrevX = x;
//    }
//
//    @Override
//    protected void positionAtCursorOffset(int offset, boolean forceUpdatePosition,
//                                          boolean fromTouchScreen) {
//        super.positionAtCursorOffset(offset, forceUpdatePosition, fromTouchScreen);
//        mInWord = (offset != -1) && !getWordIteratorWithText().isBoundary(offset);
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        if (!mTextView.isFromPrimePointer(event, true)) {
//            return true;
//        }
//        boolean superResult = super.onTouchEvent(event);
//
//        switch (event.getActionMasked()) {
//            case MotionEvent.ACTION_DOWN:
//                // Reset the touch word offset and x value when the user
//                // re-engages the handle.
//                mTouchWordDelta = 0.0f;
//                mPrevX = UNSET_X_VALUE;
//                updateMagnifier(event);
//                break;
//
//            case MotionEvent.ACTION_MOVE:
//                updateMagnifier(event);
//                break;
//
//            case MotionEvent.ACTION_UP:
//            case MotionEvent.ACTION_CANCEL:
//                dismissMagnifier();
//                break;
//        }
//
//        return superResult;
//    }
//
//    private void positionAndAdjustForCrossingHandles(int offset, boolean fromTouchScreen) {
//        final int anotherHandleOffset =
//                isStartHandle() ? mTextView.getSelectionEnd() : mTextView.getSelectionStart();
//        if ((isStartHandle() && offset >= anotherHandleOffset)
//                || (!isStartHandle() && offset <= anotherHandleOffset)) {
//            mTouchWordDelta = 0.0f;
//            final Layout layout = mTextView.getLayout();
//            if (layout != null && offset != anotherHandleOffset) {
//                final float horiz = getHorizontal(layout, offset);
//                final float anotherHandleHoriz = getHorizontal(layout, anotherHandleOffset,
//                        !isStartHandle());
//                final float currentHoriz = getHorizontal(layout, mPreviousOffset);
//                if (currentHoriz < anotherHandleHoriz && horiz < anotherHandleHoriz
//                        || currentHoriz > anotherHandleHoriz && horiz > anotherHandleHoriz) {
//                    // This handle passes another one as it crossed a direction boundary.
//                    // Don't minimize the selection, but keep the handle at the run boundary.
//                    final int currentOffset = getCurrentCursorOffset();
//                    final int offsetToGetRunRange = isStartHandle()
//                            ? currentOffset : Math.max(currentOffset - 1, 0);
//                    final long range = layout.getRunRange(offsetToGetRunRange);
//                    if (isStartHandle()) {
//                        offset = TextUtils.unpackRangeStartFromLong(range);
//                    } else {
//                        offset = TextUtils.unpackRangeEndFromLong(range);
//                    }
//                    positionAtCursorOffset(offset, false, fromTouchScreen);
//                    return;
//                }
//            }
//            // Handles can not cross and selection is at least one character.
//            offset = getNextCursorOffset(anotherHandleOffset, !isStartHandle());
//        }
//        positionAtCursorOffset(offset, false, fromTouchScreen);
//    }
//
//    private boolean positionNearEdgeOfScrollingView(float x, boolean atRtl) {
//        mTextView.getLocationOnScreen(mTextViewLocation);
//        boolean nearEdge;
//        if (atRtl == isStartHandle()) {
//            int rightEdge = mTextViewLocation[0] + mTextView.getWidth()
//                    - mTextView.getPaddingRight();
//            nearEdge = x > rightEdge - mTextViewEdgeSlop;
//        } else {
//            int leftEdge = mTextViewLocation[0] + mTextView.getPaddingLeft();
//            nearEdge = x < leftEdge + mTextViewEdgeSlop;
//        }
//        return nearEdge;
//    }
//
//    @Override
//    protected boolean isAtRtlRun(@NonNull Layout layout, int offset) {
//        final int offsetToCheck = isStartHandle() ? offset : Math.max(offset - 1, 0);
//        return layout.isRtlCharAt(offsetToCheck);
//    }
//
//    @Override
//    public float getHorizontal(@NonNull Layout layout, int offset) {
//        return getHorizontal(layout, offset, isStartHandle());
//    }
//
//    private float getHorizontal(@NonNull Layout layout, int offset, boolean startHandle) {
//        final int line = layout.getLineForOffset(offset);
//        final int offsetToCheck = startHandle ? offset : Math.max(offset - 1, 0);
//        final boolean isRtlChar = layout.isRtlCharAt(offsetToCheck);
//        final boolean isRtlParagraph = layout.getParagraphDirection(line) == -1;
//        return (isRtlChar == isRtlParagraph)
//                ? layout.getPrimaryHorizontal(offset) : layout.getSecondaryHorizontal(offset);
//    }
//
//    @Override
//    protected int getOffsetAtCoordinate(@NonNull Layout layout, int line, float x) {
//        final float localX = mTextView.convertToLocalHorizontalCoordinate(x);
//        final int primaryOffset = layout.getOffsetForHorizontal(line, localX, true);
//        if (!layout.isLevelBoundary(primaryOffset)) {
//            return primaryOffset;
//        }
//        final int secondaryOffset = layout.getOffsetForHorizontal(line, localX, false);
//        final int currentOffset = getCurrentCursorOffset();
//        final int primaryDiff = Math.abs(primaryOffset - currentOffset);
//        final int secondaryDiff = Math.abs(secondaryOffset - currentOffset);
//        if (primaryDiff < secondaryDiff) {
//            return primaryOffset;
//        } else if (primaryDiff > secondaryDiff) {
//            return secondaryOffset;
//        } else {
//            final int offsetToCheck = isStartHandle()
//                    ? currentOffset : Math.max(currentOffset - 1, 0);
//            final boolean isRtlChar = layout.isRtlCharAt(offsetToCheck);
//            final boolean isRtlParagraph = layout.getParagraphDirection(line) == -1;
//            return isRtlChar == isRtlParagraph ? primaryOffset : secondaryOffset;
//        }
//    }
//
//    @MagnifierHandleTrigger
//    protected int getMagnifierHandleTrigger() {
//        return isStartHandle()
//                ? MagnifierHandleTrigger.SELECTION_START
//                : MagnifierHandleTrigger.SELECTION_END;
//    }
//}
