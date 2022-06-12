package com.wittmane.testingedittext.aosp.text.method;

import android.text.Layout;
import android.text.Layout.Alignment;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.text.method.MetaKeyKeyListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.wittmane.testingedittext.aosp.widget.EditText;

/**
 * (EW) copied from AOSP because we need to use our custom EditText instead of the AOSP TextView
 */
public class Touch {
    private Touch() { }

    /**
     * Scrolls the specified widget to the specified coordinates, except
     * constrains the X scrolling position to the horizontal regions of
     * the text that will be visible after scrolling to the specified
     * Y position.
     */
    public static void scrollTo(EditText widget, Layout layout, int x, int y) {
        final int horizontalPadding = widget.getTotalPaddingLeft() + widget.getTotalPaddingRight();
        final int availableWidth = widget.getWidth() - horizontalPadding;

        final int top = layout.getLineForVertical(y);
        Alignment a = layout.getParagraphAlignment(top);
        boolean ltr = layout.getParagraphDirection(top) > 0;

        int left, right;
        if (widget.getHorizontallyScrolling()) {
            final int verticalPadding = widget.getTotalPaddingTop() + widget.getTotalPaddingBottom();
            final int bottom = layout.getLineForVertical(y + widget.getHeight() - verticalPadding);

            left = Integer.MAX_VALUE;
            right = 0;

            for (int i = top; i <= bottom; i++) {
                left = (int) Math.min(left, layout.getLineLeft(i));
                right = (int) Math.max(right, layout.getLineRight(i));
            }
        } else {
            left = 0;
            right = availableWidth;
        }

        final int actualWidth = right - left;

        if (actualWidth < availableWidth) {
            if (a == Alignment.ALIGN_CENTER) {
                x = left - ((availableWidth - actualWidth) / 2);
            } else if ((ltr && (a == Alignment.ALIGN_OPPOSITE)) ||
                    (!ltr && (a == Alignment.ALIGN_NORMAL))/* ||
                    (a == Alignment.ALIGN_RIGHT)*/) {//TODO: (EW) does this need to be handled?
                // align_opposite does NOT mean align_right, we need the paragraph
                // direction to resolve it to left or right
                x = left - (availableWidth - actualWidth);
            } else {
                x = left;
            }
        } else {
            x = Math.min(x, right - availableWidth);
            x = Math.max(x, left);
        }

        widget.scrollTo(x, y);
    }

    /**
     * Handles touch events for dragging.  You may want to do other actions
     * like moving the cursor on touch as well.
     */
    public static boolean onTouchEvent(EditText widget, Spannable buffer,
                                       MotionEvent event) {
        DragState[] ds;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ds = buffer.getSpans(0, buffer.length(), DragState.class);

                for (int i = 0; i < ds.length; i++) {
                    buffer.removeSpan(ds[i]);
                }

                buffer.setSpan(new DragState(event.getX(), event.getY(),
                                widget.getScrollX(), widget.getScrollY()),
                        0, 0, Spannable.SPAN_MARK_MARK);
                return true;

            case MotionEvent.ACTION_UP:
                ds = buffer.getSpans(0, buffer.length(), DragState.class);

                for (int i = 0; i < ds.length; i++) {
                    buffer.removeSpan(ds[i]);
                }

                if (ds.length > 0 && ds[0].mUsed) {
                    return true;
                } else {
                    return false;
                }

            case MotionEvent.ACTION_MOVE:
                ds = buffer.getSpans(0, buffer.length(), DragState.class);

                if (ds.length > 0) {
                    if (ds[0].mFarEnough == false) {
                        int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();

                        if (Math.abs(event.getX() - ds[0].mX) >= slop ||
                                Math.abs(event.getY() - ds[0].mY) >= slop) {
                            ds[0].mFarEnough = true;
                        }
                    }

                    if (ds[0].mFarEnough) {
                        ds[0].mUsed = true;
                        // (EW) the AOSP version also checked MetaKeyKeyListener#getMetaState with
                        // MetaKeyKeyListener.META_SELECTING, which is hidden.
                        // MetaKeyKeyListener.META_SELECTING = KeyEvent.META_SELECTING = 0x800 has
                        // been defined at least since Kitkat, but it has been hidden with a comment
                        // saying it's pending API review, and at least as of S,
                        // KeyEvent.META_SELECTING has been marked UnsupportedAppUsage (maxTargetSdk
                        // R). after this long it seems unlikely for this to be released for apps to
                        // use, and this could theoretically get changed in a future version, so it
                        // wouldn't be completely safe to just hard-code 0x800. I only found this
                        // constant used in getMetaState throughout AOSP code, so skipping it
                        // probably won't even cause a real lack of functionality (at least
                        // currently) since other apps probably aren't using it either. same basic
                        // need to skip this in EditText.ChangeWatcher#afterTextChanged,
                        // Editor#extractTextInternal, and ArrowKeyMovementMethod#handleMovementKey.
                        boolean cap = (event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0
                                || MetaKeyKeyListener.getMetaState(buffer,
                                        MetaKeyKeyListener.META_SHIFT_ON) == 1;

                        float dx;
                        float dy;
                        if (cap) {
                            // if we're selecting, we want the scroll to go in
                            // the direction of the drag
                            dx = event.getX() - ds[0].mX;
                            dy = event.getY() - ds[0].mY;
                        } else {
                            dx = ds[0].mX - event.getX();
                            dy = ds[0].mY - event.getY();
                        }
                        ds[0].mX = event.getX();
                        ds[0].mY = event.getY();

                        int nx = widget.getScrollX() + (int) dx;
                        int ny = widget.getScrollY() + (int) dy;

                        int padding = widget.getTotalPaddingTop() + widget.getTotalPaddingBottom();
                        Layout layout = widget.getLayout();

                        ny = Math.min(ny, layout.getHeight() - (widget.getHeight() - padding));
                        ny = Math.max(ny, 0);

                        int oldX = widget.getScrollX();
                        int oldY = widget.getScrollY();

                        scrollTo(widget, layout, nx, ny);

                        // If we actually scrolled, then cancel the up action.
                        if (oldX != widget.getScrollX() || oldY != widget.getScrollY()) {
                            widget.cancelLongPress();
                        }

                        return true;
                    }
                }
        }

        return false;
    }

    /**
     * @param widget The text view.
     * @param buffer The text buffer.
     */
    public static int getInitialScrollX(EditText widget, Spannable buffer) {
        DragState[] ds = buffer.getSpans(0, buffer.length(), DragState.class);
        return ds.length > 0 ? ds[0].mScrollX : -1;
    }

    /**
     * @param widget The text view.
     * @param buffer The text buffer.
     */
    public static int getInitialScrollY(EditText widget, Spannable buffer) {
        DragState[] ds = buffer.getSpans(0, buffer.length(), DragState.class);
        return ds.length > 0 ? ds[0].mScrollY : -1;
    }

    private static class DragState implements NoCopySpan {
        public float mX;
        public float mY;
        public int mScrollX;
        public int mScrollY;
        public boolean mFarEnough;
        public boolean mUsed;

        public DragState(float x, float y, int scrollX, int scrollY) {
            mX = x;
            mY = y;
            mScrollX = scrollX;
            mScrollY = scrollY;
        }
    }
}
