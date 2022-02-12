//package com.wittmane.testingedittext.editor;
//
//
//import android.graphics.Canvas;
//import android.graphics.Paint;
//import android.graphics.Point;
//import android.graphics.PointF;
//import android.graphics.Rect;
//import android.graphics.drawable.Drawable;
//import android.os.Build;
//import android.os.SystemClock;
//import android.text.Layout;
//import android.util.Log;
//import android.util.TypedValue;
//import android.view.Gravity;
//import android.view.HapticFeedbackConstants;
//import android.view.InputDevice;
//import android.view.MotionEvent;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.ViewParent;
//import android.view.WindowManager;
//import android.widget.PopupWindow;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//
//import com.wittmane.testingedittext.CustomEditTextView;
//import com.wittmane.testingedittext.Editor;
//import com.wittmane.testingedittext.R;
//import com.wittmane.testingedittext.SimpleTouchManager;
//
//import java.util.Collections;
//
//public abstract class HandleView extends View implements TextViewPositionListener {
//    static final String TAG = HandleView.class.getSimpleName();
//
//    public static final int UNSET_X_VALUE = -1;
//    public static final int UNSET_LINE = -1;
//
//    protected Drawable mDrawable;
//    protected Drawable mDrawableLtr;
//    protected Drawable mDrawableRtl;
//    private final PopupWindow mContainer;
//    // Position with respect to the parent TextView
//    private int mPositionX, mPositionY;
//    private boolean mIsDragging;
//    // Offset from touch position to mPosition
//    private float mTouchToWindowOffsetX, mTouchToWindowOffsetY;
//    protected int mHotspotX;
//    protected int mHorizontalGravity;
//    // Offsets the hotspot point up, so that cursor is not hidden by the finger when moving up
//    private float mTouchOffsetY;
//    // Where the touch position should be on the handle to ensure a maximum cursor visibility.
//    // This is the distance in pixels from the top of the handle view.
//    private final float mIdealVerticalOffset;
//    // Parent's (TextView) previous position in window
//    private int mLastParentX, mLastParentY;
//    // Parent's (TextView) previous position on screen
//    private int mLastParentXOnScreen, mLastParentYOnScreen;
//    // Previous text character offset
//    protected int mPreviousOffset = -1;
//    // Previous text character offset
//    private boolean mPositionHasChanged = true;
//    // Minimum touch target size for handles
//    private int mMinSize;
//    // Indicates the line of text that the handle is on.
//    protected int mPrevLine = UNSET_LINE;
//    // Indicates the line of text that the user was touching. This can differ from mPrevLine
//    // when selecting text when the handles jump to the end / start of words which may be on
//    // a different line.
//    protected int mPreviousLineTouched = UNSET_LINE;
//    // The raw x coordinate of the motion down event which started the current dragging session.
//    // Only used and stored when magnifier is used.
//    private float mCurrentDragInitialTouchRawX = UNSET_X_VALUE;
//    // The scale transform applied by containers to the TextView. Only used and computed
//    // when magnifier is used.
//    private float mTextViewScaleX;
//    private float mTextViewScaleY;
//    /**
//     * The vertical distance in pixels from finger to the cursor Y while dragging.
//     * See {@link Editor.InsertionPointCursorController#getLineDuringDrag}.
//     */
//    private final int mIdealFingerToCursorOffset;
//
//    protected final CustomEditTextView mTextView;
//
//    HandleView(Drawable drawableLtr, Drawable drawableRtl, final int id, CustomEditTextView textView) {
//        super(textView.getContext());
//        mTextView = textView;
//        setId(id);
//        mContainer = new PopupWindow(mTextView.getContext(), null,
//                /*com.android.internal.*/R.attr.textSelectHandleWindowStyle);
//        mContainer.setSplitTouchEnabled(true);
//        mContainer.setClippingEnabled(false);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            mContainer.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
//        }
//        mContainer.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
//        mContainer.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
//        mContainer.setContentView(this);
//
//        setDrawables(drawableLtr, drawableRtl);
//
//        mMinSize = mTextView.getContext().getResources().getDimensionPixelSize(
//                /*com.android.internal.*/R.dimen.text_handle_min_size);
//
//        final int handleHeight = getPreferredHeight();
//        mTouchOffsetY = -0.3f * handleHeight;
//        final int distance = /*AppGlobals.getIntCoreSetting(
//                WidgetFlags.KEY_FINGER_TO_CURSOR_DISTANCE,*/
//                WidgetFlags.FINGER_TO_CURSOR_DISTANCE_DEFAULT/*)*/;
//        if (distance < 0 || distance > 100) {
//            mIdealVerticalOffset = 0.7f * handleHeight;
//            mIdealFingerToCursorOffset = (int)(mIdealVerticalOffset - mTouchOffsetY);
//        } else {
//            mIdealFingerToCursorOffset = (int) TypedValue.applyDimension(
//                    TypedValue.COMPLEX_UNIT_DIP, distance,
//                    mTextView.getContext().getResources().getDisplayMetrics());
//            mIdealVerticalOffset = mIdealFingerToCursorOffset + mTouchOffsetY;
//        }
//    }
//
//    public float getIdealVerticalOffset() {
//        return mIdealVerticalOffset;
//    }
//
//    final int getIdealFingerToCursorOffset() {
//        return mIdealFingerToCursorOffset;
//    }
//
//    void setDrawables(final Drawable drawableLtr, final Drawable drawableRtl) {
//        mDrawableLtr = drawableLtr;
//        mDrawableRtl = drawableRtl;
//        updateDrawable(true /* updateDrawableWhenDragging */);
//    }
//
//    protected void updateDrawable(final boolean updateDrawableWhenDragging) {
//        if (!updateDrawableWhenDragging && mIsDragging) {
//            return;
//        }
//        final Layout layout = mTextView.getLayout();
//        if (layout == null) {
//            return;
//        }
//        final int offset = getCurrentCursorOffset();
//        final boolean isRtlCharAtOffset = isAtRtlRun(layout, offset);
//        final Drawable oldDrawable = mDrawable;
//        mDrawable = isRtlCharAtOffset ? mDrawableRtl : mDrawableLtr;
//        mHotspotX = getHotspotX(mDrawable, isRtlCharAtOffset);
//        mHorizontalGravity = getHorizontalGravity(isRtlCharAtOffset);
//        if (oldDrawable != mDrawable && isShowing()) {
//            // Update popup window position.
//            mPositionX = getCursorHorizontalPosition(layout, offset) - mHotspotX
//                    - getHorizontalOffset() + getCursorOffset();
//            mPositionX += mTextView.viewportToContentHorizontalOffset();
//            mPositionHasChanged = true;
//            updatePosition(mLastParentX, mLastParentY, false, false);
//            postInvalidate();
//        }
//    }
//
//    protected abstract int getHotspotX(Drawable drawable, boolean isRtlRun);
//    protected abstract int getHorizontalGravity(boolean isRtlRun);
//
//    // Touch-up filter: number of previous positions remembered
//    private static final int HISTORY_SIZE = 5;
//    private static final int TOUCH_UP_FILTER_DELAY_AFTER = 150;
//    private static final int TOUCH_UP_FILTER_DELAY_BEFORE = 350;
//    private final long[] mPreviousOffsetsTimes = new long[HISTORY_SIZE];
//    private final int[] mPreviousOffsets = new int[HISTORY_SIZE];
//    private int mPreviousOffsetIndex = 0;
//    private int mNumberPreviousOffsets = 0;
//
//    private void startTouchUpFilter(int offset) {
//        mNumberPreviousOffsets = 0;
//        addPositionToTouchUpFilter(offset);
//    }
//
//    private void addPositionToTouchUpFilter(int offset) {
//        mPreviousOffsetIndex = (mPreviousOffsetIndex + 1) % HISTORY_SIZE;
//        mPreviousOffsets[mPreviousOffsetIndex] = offset;
//        mPreviousOffsetsTimes[mPreviousOffsetIndex] = SystemClock.uptimeMillis();
//        mNumberPreviousOffsets++;
//    }
//
//    private void filterOnTouchUp(boolean fromTouchScreen) {
//        final long now = SystemClock.uptimeMillis();
//        int i = 0;
//        int index = mPreviousOffsetIndex;
//        final int iMax = Math.min(mNumberPreviousOffsets, HISTORY_SIZE);
//        while (i < iMax && (now - mPreviousOffsetsTimes[index]) < TOUCH_UP_FILTER_DELAY_AFTER) {
//            i++;
//            index = (mPreviousOffsetIndex - i + HISTORY_SIZE) % HISTORY_SIZE;
//        }
//
//        if (i > 0 && i < iMax
//                && (now - mPreviousOffsetsTimes[index]) > TOUCH_UP_FILTER_DELAY_BEFORE) {
//            positionAtCursorOffset(mPreviousOffsets[index], false, fromTouchScreen);
//        }
//    }
//
//    public boolean offsetHasBeenChanged() {
//        return mNumberPreviousOffsets > 1;
//    }
//
//    @Override
//    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        setMeasuredDimension(getPreferredWidth(), getPreferredHeight());
//    }
//
//    @Override
//    public void invalidate() {
//        super.invalidate();
//        if (isShowing()) {
//            positionAtCursorOffset(getCurrentCursorOffset(), true, false);
//        }
//    };
//
//    protected final int getPreferredWidth() {
//        return Math.max(mDrawable.getIntrinsicWidth(), mMinSize);
//    }
//
//    protected final int getPreferredHeight() {
//        return Math.max(mDrawable.getIntrinsicHeight(), mMinSize);
//    }
//
//    public void show() {
//        if (SimpleTouchManager.DEBUG_CURSOR) {
//            logCursor(getClass().getSimpleName() + ": HandleView: show()", "offset=%s",
//                    getCurrentCursorOffset());
//        }
//
//        if (isShowing()) return;
//
//        mTextView.mEditor.getPositionListener().addSubscriber(this, true /* local position may change */);
//
//        // Make sure the offset is always considered new, even when focusing at same position
//        mPreviousOffset = -1;
//        positionAtCursorOffset(getCurrentCursorOffset(), false, false);
//    }
//
//    protected void dismiss() {
//        mIsDragging = false;
//        mContainer.dismiss();
//        onDetached();
//    }
//
//    public void hide() {
//        if (SimpleTouchManager.DEBUG_CURSOR) {
//            logCursor(getClass().getSimpleName() + ": HandleView: hide()", "offset=%s",
//                    getCurrentCursorOffset());
//        }
//
//        dismiss();
//
//        mTextView.mEditor.getPositionListener().removeSubscriber(this);
//    }
//
//    public boolean isShowing() {
//        return mContainer.isShowing();
//    }
//
//    private boolean shouldShow() {
//        // A dragging handle should always be shown.
//        if (mIsDragging) {
//            return true;
//        }
//
//        if (mTextView.isInBatchEditMode()) {
//            return false;
//        }
//
//        return mTextView.isPositionVisible(
//                mPositionX + mHotspotX + getHorizontalOffset(), mPositionY);
//    }
//
//    private void setVisible(final boolean visible) {
//        mContainer.getContentView().setVisibility(visible ? VISIBLE : INVISIBLE);
//    }
//
//    public abstract int getCurrentCursorOffset();
//
//    protected abstract void updateSelection(int offset);
//
//    protected abstract void updatePosition(float x, float y, boolean fromTouchScreen);
//
////    @MagnifierHandleTrigger
//    protected abstract int getMagnifierHandleTrigger();
//
//    protected boolean isAtRtlRun(@NonNull Layout layout, int offset) {
//        return layout.isRtlCharAt(offset);
//    }
//
////    @VisibleForTesting
//    public float getHorizontal(@NonNull Layout layout, int offset) {
//        return layout.getPrimaryHorizontal(offset);
//    }
//
//    protected int getOffsetAtCoordinate(@NonNull Layout layout, int line, float x) {
//        return mTextView.getOffsetAtCoordinate(line, x);
//    }
//
//    /**
//     * @param offset Cursor offset. Must be in [-1, length].
//     * @param forceUpdatePosition whether to force update the position.  This should be true
//     * when If the parent has been scrolled, for example.
//     * @param fromTouchScreen {@code true} if the cursor is moved with motion events from the
//     * touch screen.
//     */
//    protected void positionAtCursorOffset(int offset, boolean forceUpdatePosition,
//                                          boolean fromTouchScreen) {
//        // A HandleView relies on the layout, which may be nulled by external methods
//        Layout layout = mTextView.getLayout();
//        if (layout == null) {
//            // Will update controllers' state, hiding them and stopping selection mode if needed
//            mTextView.mEditor.prepareCursorControllers();
//            return;
//        }
//        layout = mTextView.getLayout();
//
//        boolean offsetChanged = offset != mPreviousOffset;
//        if (offsetChanged || forceUpdatePosition) {
//            if (offsetChanged) {
//                updateSelection(offset);
//                if (fromTouchScreen && mTextView.mEditor.mHapticTextHandleEnabled) {
//                    mTextView.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
//                }
//                addPositionToTouchUpFilter(offset);
//            }
//            final int line = layout.getLineForOffset(offset);
//            mPrevLine = line;
//
//            mPositionX = getCursorHorizontalPosition(layout, offset) - mHotspotX
//                    - getHorizontalOffset() + getCursorOffset();
//            mPositionY = layout.getLineBottom/*WithoutSpacing*/(line);//TODO: (EW) validate this is fine
//
//            // Take TextView's padding and scroll into account.
//            mPositionX += mTextView.viewportToContentHorizontalOffset();
//            mPositionY += mTextView.viewportToContentVerticalOffset();
//
//            mPreviousOffset = offset;
//            mPositionHasChanged = true;
//        }
//    }
//
//    /**
//     * Return the clamped horizontal position for the cursor.
//     *
//     * @param layout Text layout.
//     * @param offset Character offset for the cursor.
//     * @return The clamped horizontal position for the cursor.
//     */
//    int getCursorHorizontalPosition(Layout layout, int offset) {
//        return (int) (getHorizontal(layout, offset) - 0.5f);
//    }
//
//    @Override
//    public void updatePosition(int parentPositionX, int parentPositionY,
//                               boolean parentPositionChanged, boolean parentScrolled) {
//        positionAtCursorOffset(getCurrentCursorOffset(), parentScrolled, false);
//        if (parentPositionChanged || mPositionHasChanged) {
//            if (mIsDragging) {
//                // Update touchToWindow offset in case of parent scrolling while dragging
//                if (parentPositionX != mLastParentX || parentPositionY != mLastParentY) {
//                    mTouchToWindowOffsetX += parentPositionX - mLastParentX;
//                    mTouchToWindowOffsetY += parentPositionY - mLastParentY;
//                    mLastParentX = parentPositionX;
//                    mLastParentY = parentPositionY;
//                }
//
//                onHandleMoved();
//            }
//
//            if (shouldShow()) {
//                // Transform to the window coordinates to follow the view tranformation.
//                final int[] pts = { mPositionX + mHotspotX + getHorizontalOffset(), mPositionY};
//                mTextView.transformFromViewToWindowSpace(pts);
//                pts[0] -= mHotspotX + getHorizontalOffset();
//
//                if (isShowing()) {
//                    mContainer.update(pts[0], pts[1], -1, -1);
//                } else {
//                    mContainer.showAtLocation(mTextView, Gravity.NO_GRAVITY, pts[0], pts[1]);
//                }
//            } else {
//                if (isShowing()) {
//                    dismiss();
//                }
//            }
//
//            mPositionHasChanged = false;
//        }
//    }
//
//    @Override
//    protected void onDraw(Canvas c) {
//        final int drawWidth = mDrawable.getIntrinsicWidth();
//        final int left = getHorizontalOffset();
//
//        mDrawable.setBounds(left, 0, left + drawWidth, mDrawable.getIntrinsicHeight());
//        mDrawable.draw(c);
//    }
//
//    private int getHorizontalOffset() {
//        final int width = getPreferredWidth();
//        final int drawWidth = mDrawable.getIntrinsicWidth();
//        final int left;
//        switch (mHorizontalGravity) {
//            case Gravity.LEFT:
//                left = 0;
//                break;
//            default:
//            case Gravity.CENTER:
//                left = (width - drawWidth) / 2;
//                break;
//            case Gravity.RIGHT:
//                left = width - drawWidth;
//                break;
//        }
//        return left;
//    }
//
//    protected int getCursorOffset() {
//        return 0;
//    }
//
//    private boolean tooLargeTextForMagnifier() {
////        if (mNewMagnifierEnabled) {
////            Layout layout = mTextView.getLayout();
////            final int line = layout.getLineForOffset(getCurrentCursorOffset());
////            return layout.getLineBottomWithoutSpacing(line) - layout.getLineTop(line)
////                    >= mMaxLineHeightForMagnifier;
////        }
////        final float magnifierContentHeight = Math.round(
////                mMagnifierAnimator.mMagnifier.getHeight()
////                        / mMagnifierAnimator.mMagnifier.getZoom());
////        final Paint.FontMetrics fontMetrics = mTextView.getPaint().getFontMetrics();
////        final float glyphHeight = fontMetrics.descent - fontMetrics.ascent;
////        return glyphHeight * mTextViewScaleY > magnifierContentHeight;
//        return true;
//    }
//
//    /**
//     * Traverses the hierarchy above the text view, and computes the total scale applied
//     * to it. If a rotation is encountered, the method returns {@code false}, indicating
//     * that the magnifier should not be shown anyways. It would be nice to keep these two
//     * pieces of logic separate (the rotation check and the total scale calculation),
//     * but for efficiency we can do them in a single go.
//     * @return whether the text view is rotated
//     */
//    private boolean checkForTransforms() {
////        if (mMagnifierAnimator.mMagnifierIsShowing) {
////            // Do not check again when the magnifier is currently showing.
////            return true;
////        }
//
//        if (mTextView.getRotation() != 0f || mTextView.getRotationX() != 0f
//                || mTextView.getRotationY() != 0f) {
//            return false;
//        }
//        mTextViewScaleX = mTextView.getScaleX();
//        mTextViewScaleY = mTextView.getScaleY();
//
//        ViewParent viewParent = mTextView.getParent();
//        while (viewParent != null) {
//            if (viewParent instanceof View) {
//                final View view = (View) viewParent;
//                if (view.getRotation() != 0f || view.getRotationX() != 0f
//                        || view.getRotationY() != 0f) {
//                    return false;
//                }
//                mTextViewScaleX *= view.getScaleX();
//                mTextViewScaleY *= view.getScaleY();
//            }
//            viewParent = viewParent.getParent();
//        }
//        return true;
//    }
//
//    /**
//     * Computes the position where the magnifier should be shown, relative to
//     * {@code mTextView}, and writes them to {@code showPosInView}. Also decides
//     * whether the magnifier should be shown or dismissed after this touch event.
//     * @return Whether the magnifier should be shown at the computed coordinates or dismissed.
//     */
//    private boolean obtainMagnifierShowCoordinates(@NonNull final MotionEvent event,
//                                                   final PointF showPosInView) {
//
////        final int trigger = getMagnifierHandleTrigger();
////        final int offset;
////        final int otherHandleOffset;
////        switch (trigger) {
////            case MagnifierHandleTrigger.INSERTION:
////                offset = mTextView.getSelectionStart();
////                otherHandleOffset = -1;
////                break;
////            case MagnifierHandleTrigger.SELECTION_START:
////                offset = mTextView.getSelectionStart();
////                otherHandleOffset = mTextView.getSelectionEnd();
////                break;
////            case MagnifierHandleTrigger.SELECTION_END:
////                offset = mTextView.getSelectionEnd();
////                otherHandleOffset = mTextView.getSelectionStart();
////                break;
////            default:
////                offset = -1;
////                otherHandleOffset = -1;
////                break;
////        }
////
////        if (offset == -1) {
////            return false;
////        }
////
////        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
////            mCurrentDragInitialTouchRawX = event.getRawX();
////        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
////            mCurrentDragInitialTouchRawX = UNSET_X_VALUE;
////        }
////
////        final Layout layout = mTextView.getLayout();
////        final int lineNumber = layout.getLineForOffset(offset);
////        // Compute whether the selection handles are currently on the same line, and,
////        // in this particular case, whether the selected text is right to left.
////        final boolean sameLineSelection = otherHandleOffset != -1
////                && lineNumber == layout.getLineForOffset(otherHandleOffset);
////        final boolean rtl = sameLineSelection
////                && (offset < otherHandleOffset)
////                != (getHorizontal(mTextView.getLayout(), offset)
////                < getHorizontal(mTextView.getLayout(), otherHandleOffset));
////
////        // Horizontally move the magnifier smoothly, clamp inside the current line / selection.
////        final int[] textViewLocationOnScreen = new int[2];
////        mTextView.getLocationOnScreen(textViewLocationOnScreen);
////        final float touchXInView = event.getRawX() - textViewLocationOnScreen[0];
////        float leftBound = mTextView.getTotalPaddingLeft() - mTextView.getScrollX();
////        float rightBound = mTextView.getTotalPaddingLeft() - mTextView.getScrollX();
////        if (sameLineSelection && ((trigger == MagnifierHandleTrigger.SELECTION_END) ^ rtl)) {
////            leftBound += getHorizontal(mTextView.getLayout(), otherHandleOffset);
////        } else {
////            leftBound += mTextView.getLayout().getLineLeft(lineNumber);
////        }
////        if (sameLineSelection && ((trigger == MagnifierHandleTrigger.SELECTION_START) ^ rtl)) {
////            rightBound += getHorizontal(mTextView.getLayout(), otherHandleOffset);
////        } else {
////            rightBound += mTextView.getLayout().getLineRight(lineNumber);
////        }
////        leftBound *= mTextViewScaleX;
////        rightBound *= mTextViewScaleX;
////        final float contentWidth = Math.round(mMagnifierAnimator.mMagnifier.getWidth()
////                / mMagnifierAnimator.mMagnifier.getZoom());
////        if (touchXInView < leftBound - contentWidth / 2
////                || touchXInView > rightBound + contentWidth / 2) {
////            // The touch is too far from the current line / selection, so hide the magnifier.
////            return false;
////        }
////
////        final float scaledTouchXInView;
////        if (mTextViewScaleX == 1f) {
////            // In the common case, do not use mCurrentDragInitialTouchRawX to compute this
////            // coordinate, although the formula on the else branch should be equivalent.
////            // Since the formula relies on mCurrentDragInitialTouchRawX being set on
////            // MotionEvent.ACTION_DOWN, this makes us more defensive against cases when
////            // the sequence of events might not look as expected: for example, a sequence of
////            // ACTION_MOVE not preceded by ACTION_DOWN.
////            scaledTouchXInView = touchXInView;
////        } else {
////            scaledTouchXInView = (event.getRawX() - mCurrentDragInitialTouchRawX)
////                    * mTextViewScaleX + mCurrentDragInitialTouchRawX
////                    - textViewLocationOnScreen[0];
////        }
////        showPosInView.x = Math.max(leftBound, Math.min(rightBound, scaledTouchXInView));
////
////        // Vertically snap to middle of current line.
////        showPosInView.y = ((mTextView.getLayout().getLineTop(lineNumber)
////                + mTextView.getLayout().getLineBottomWithoutSpacing(lineNumber)) / 2.0f
////                + mTextView.getTotalPaddingTop() - mTextView.getScrollY()) * mTextViewScaleY;
////        return true;
//        return false;
//    }
//
//    private boolean handleOverlapsMagnifier(@NonNull final HandleView handle,
//                                            @NonNull final Rect magnifierRect) {
////        final PopupWindow window = handle.mContainer;
////        if (!window.hasDecorView()) {
////            return false;
////        }
////        final Rect handleRect = new Rect(
////                window.getDecorViewLayoutParams().x,
////                window.getDecorViewLayoutParams().y,
////                window.getDecorViewLayoutParams().x + window.getContentView().getWidth(),
////                window.getDecorViewLayoutParams().y + window.getContentView().getHeight());
////        return Rect.intersects(handleRect, magnifierRect);
//        return false;
//    }
//
//    private @Nullable HandleView getOtherSelectionHandle() {
//        //TODO: (EW) is this necessary?
////        final SelectionModifierCursorController controller = getSelectionController();
////        if (controller == null || !controller.isActive()) {
////            return null;
////        }
////        return controller.mStartHandle != this
////                ? controller.mStartHandle
////                : controller.mEndHandle;
//        return null;
//    }
//
//    private void updateHandlesVisibility() {
////        final Point magnifierTopLeft = mMagnifierAnimator.mMagnifier.getPosition();
////        if (magnifierTopLeft == null) {
////            return;
////        }
////        final Rect magnifierRect = new Rect(magnifierTopLeft.x, magnifierTopLeft.y,
////                magnifierTopLeft.x + mMagnifierAnimator.mMagnifier.getWidth(),
////                magnifierTopLeft.y + mMagnifierAnimator.mMagnifier.getHeight());
////        setVisible(!handleOverlapsMagnifier(HandleView.this, magnifierRect));
////        final HandleView otherHandle = getOtherSelectionHandle();
////        if (otherHandle != null) {
////            otherHandle.setVisible(!handleOverlapsMagnifier(otherHandle, magnifierRect));
////        }
//    }
//
//    protected final void updateMagnifier(@NonNull final MotionEvent event) {
////        if (getMagnifierAnimator() == null) {
////            return;
////        }
////
////        final PointF showPosInView = new PointF();
////        final boolean shouldShow = checkForTransforms() /*check not rotated and compute scale*/
////                && !tooLargeTextForMagnifier()
////                && obtainMagnifierShowCoordinates(event, showPosInView);
////        if (shouldShow) {
////            // Make the cursor visible and stop blinking.
////            mRenderCursorRegardlessTiming = true;
////            mTextView.invalidateCursorPath();
////            suspendBlink();
////
////            if (mNewMagnifierEnabled) {
////                // Calculates the line bounds as the content source bounds to the magnifier.
////                Layout layout = mTextView.getLayout();
////                int line = layout.getLineForOffset(getCurrentCursorOffset());
////                int lineLeft = (int) layout.getLineLeft(line);
////                lineLeft += mTextView.getTotalPaddingLeft() - mTextView.getScrollX();
////                int lineRight = (int) layout.getLineRight(line);
////                lineRight += mTextView.getTotalPaddingLeft() - mTextView.getScrollX();
////                mMagnifierAnimator.mMagnifier.setSourceHorizontalBounds(lineLeft, lineRight);
////                final int lineHeight =
////                        layout.getLineBottomWithoutSpacing(line) - layout.getLineTop(line);
////                float zoom = mInitialZoom;
////                if (lineHeight < mMinLineHeightForMagnifier) {
////                    zoom = zoom * mMinLineHeightForMagnifier / lineHeight;
////                }
////                mMagnifierAnimator.mMagnifier.updateSourceFactors(lineHeight, zoom);
////                mMagnifierAnimator.mMagnifier.show(showPosInView.x, showPosInView.y);
////            } else {
////                mMagnifierAnimator.show(showPosInView.x, showPosInView.y);
////            }
////            updateHandlesVisibility();
////        } else {
////            dismissMagnifier();
////        }
//    }
//
//    protected final void dismissMagnifier() {
////        if (mMagnifierAnimator != null) {
////            mMagnifierAnimator.dismiss();
////            mRenderCursorRegardlessTiming = false;
////            resumeBlink();
////            setVisible(true);
////            final HandleView otherHandle = getOtherSelectionHandle();
////            if (otherHandle != null) {
////                otherHandle.setVisible(true);
////            }
////        }
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent ev) {
//        if (SimpleTouchManager.DEBUG_CURSOR) {
////                logCursor(this.getClass().getSimpleName() + ": HandleView: onTouchEvent",
////                        "%d: %s (%f,%f)",
////                        ev.getSequenceNumber(),
////                        MotionEvent.actionToString(ev.getActionMasked()),
////                        ev.getX(), ev.getY());
//        }
//
////        updateFloatingToolbarVisibility(ev);
//
//        switch (ev.getActionMasked()) {
//            case MotionEvent.ACTION_DOWN: {
//                startTouchUpFilter(getCurrentCursorOffset());
//
//                final PositionListener positionListener = mTextView.mEditor.getPositionListener();
//                mLastParentX = positionListener.getPositionX();
//                mLastParentY = positionListener.getPositionY();
//                mLastParentXOnScreen = positionListener.getPositionXOnScreen();
//                mLastParentYOnScreen = positionListener.getPositionYOnScreen();
//
//                final float xInWindow = ev.getRawX() - mLastParentXOnScreen + mLastParentX;
//                final float yInWindow = ev.getRawY() - mLastParentYOnScreen + mLastParentY;
//                mTouchToWindowOffsetX = xInWindow - mPositionX;
//                mTouchToWindowOffsetY = yInWindow - mPositionY;
//
//                mIsDragging = true;
//                mPreviousLineTouched = UNSET_LINE;
//                break;
//            }
//
//            case MotionEvent.ACTION_MOVE: {
//                final float xInWindow = ev.getRawX() - mLastParentXOnScreen + mLastParentX;
//                final float yInWindow = ev.getRawY() - mLastParentYOnScreen + mLastParentY;
//
//                // Vertical hysteresis: vertical down movement tends to snap to ideal offset
//                final float previousVerticalOffset = mTouchToWindowOffsetY - mLastParentY;
//                final float currentVerticalOffset = yInWindow - mPositionY - mLastParentY;
//                float newVerticalOffset;
//                if (previousVerticalOffset < mIdealVerticalOffset) {
//                    newVerticalOffset = Math.min(currentVerticalOffset, mIdealVerticalOffset);
//                    newVerticalOffset = Math.max(newVerticalOffset, previousVerticalOffset);
//                } else {
//                    newVerticalOffset = Math.max(currentVerticalOffset, mIdealVerticalOffset);
//                    newVerticalOffset = Math.min(newVerticalOffset, previousVerticalOffset);
//                }
//                mTouchToWindowOffsetY = newVerticalOffset + mLastParentY;
//
//                final float newPosX =
//                        xInWindow - mTouchToWindowOffsetX + mHotspotX + getHorizontalOffset();
//                final float newPosY = yInWindow - mTouchToWindowOffsetY + mTouchOffsetY;
//
//                updatePosition(newPosX, newPosY,
//                        ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
//                break;
//            }
//
//            case MotionEvent.ACTION_UP:
//                filterOnTouchUp(ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN));
//                // Fall through.
//            case MotionEvent.ACTION_CANCEL:
//                mIsDragging = false;
//                updateDrawable(false /* updateDrawableWhenDragging */);
//                break;
//        }
//        return true;
//    }
//
//    public boolean isDragging() {
//        return mIsDragging;
//    }
//
//    void onHandleMoved() {}
//
//    public void onDetached() {}
//
//    @Override
//    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
//        super.onSizeChanged(w, h, oldw, oldh);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, w, h)));
//        } else {
//            //TODO: (EW) does something ned to be done?
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
