package com.wittmane.testingedittext.aosp.text.method;

import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.wittmane.testingedittext.aosp.text.HiddenSelection;
import com.wittmane.testingedittext.aosp.widget.EditText;

/**
 * A movement method that provides cursor movement and selection.
 * Supports displaying the context menu on DPad Center.
 *
 * (EW) copied from AOSP because we need to use our custom EditText instead of the AOSP TextView and
 * a couple methods were hidden.
 */
public class ArrowKeyMovementMethod extends BaseMovementMethod implements MovementMethod {
    static final String TAG = ArrowKeyMovementMethod.class.getSimpleName();

    private static boolean isSelecting(Spannable buffer) {
        return ((MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SHIFT_ON) == 1) ||
                (MetaKeyKeyListener.getMetaState(buffer, ProtectedMetaKeyKeyListener.META_SELECTING) != 0));
    }

    private static int getCurrentLineTop(Spannable buffer, Layout layout) {
        return layout.getLineTop(layout.getLineForOffset(Selection.getSelectionEnd(buffer)));
    }

    private static int getPageHeight(EditText widget) {
        // This calculation does not take into account the view transformations that
        // may have been applied to the child or its containers.  In case of scaling or
        // rotation, the calculated page height may be incorrect.
        final Rect rect = new Rect();
        return widget.getGlobalVisibleRect(rect) ? rect.height() : 0;
    }

    @Override
    protected boolean handleMovementKey(EditText widget, Spannable buffer, int keyCode,
                                        int movementMetaState, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (KeyEvent.metaStateHasNoModifiers(movementMetaState)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && event.getRepeatCount() == 0
                            && MetaKeyKeyListener.getMetaState(buffer,
                                    ProtectedMetaKeyKeyListener.META_SELECTING, event) != 0) {
                        return widget.showContextMenu();
                    }
                }
                break;
        }
        return super.handleMovementKey(widget, buffer, keyCode, movementMetaState, event);
    }

    @Override
    protected boolean left(EditText widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendLeft(buffer, layout);
        } else {
            return Selection.moveLeft(buffer, layout);
        }
    }

    @Override
    protected boolean right(EditText widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendRight(buffer, layout);
        } else {
            return Selection.moveRight(buffer, layout);
        }
    }

    @Override
    protected boolean up(EditText widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendUp(buffer, layout);
        } else {
            return Selection.moveUp(buffer, layout);
        }
    }

    @Override
    protected boolean down(EditText widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendDown(buffer, layout);
        } else {
            return Selection.moveDown(buffer, layout);
        }
    }

    @Override
    protected boolean pageUp(EditText widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final boolean selecting = isSelecting(buffer);
        final int targetY = getCurrentLineTop(buffer, layout) - getPageHeight(widget);
        boolean handled = false;
        for (;;) {
            final int previousSelectionEnd = Selection.getSelectionEnd(buffer);
            if (selecting) {
                Selection.extendUp(buffer, layout);
            } else {
                Selection.moveUp(buffer, layout);
            }
            if (Selection.getSelectionEnd(buffer) == previousSelectionEnd) {
                break;
            }
            handled = true;
            if (getCurrentLineTop(buffer, layout) <= targetY) {
                break;
            }
        }
        return handled;
    }

    @Override
    protected boolean pageDown(EditText widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        final boolean selecting = isSelecting(buffer);
        final int targetY = getCurrentLineTop(buffer, layout) + getPageHeight(widget);
        boolean handled = false;
        for (;;) {
            final int previousSelectionEnd = Selection.getSelectionEnd(buffer);
            if (selecting) {
                Selection.extendDown(buffer, layout);
            } else {
                Selection.moveDown(buffer, layout);
            }
            if (Selection.getSelectionEnd(buffer) == previousSelectionEnd) {
                break;
            }
            handled = true;
            if (getCurrentLineTop(buffer, layout) >= targetY) {
                break;
            }
        }
        return handled;
    }

    @Override
    protected boolean top(EditText widget, Spannable buffer) {
        if (isSelecting(buffer)) {
            Selection.extendSelection(buffer, 0);
        } else {
            Selection.setSelection(buffer, 0);
        }
        return true;
    }

    @Override
    protected boolean bottom(EditText widget, Spannable buffer) {
        if (isSelecting(buffer)) {
            Selection.extendSelection(buffer, buffer.length());
        } else {
            Selection.setSelection(buffer, buffer.length());
        }
        return true;
    }

    @Override
    protected boolean lineStart(EditText widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendToLeftEdge(buffer, layout);
        } else {
            return Selection.moveToLeftEdge(buffer, layout);
        }
    }

    @Override
    protected boolean lineEnd(EditText widget, Spannable buffer) {
        final Layout layout = widget.getLayout();
        if (isSelecting(buffer)) {
            return Selection.extendToRightEdge(buffer, layout);
        } else {
            return Selection.moveToRightEdge(buffer, layout);
        }
    }

    /** {@hide} */
    @Override
    protected boolean leftWord(EditText widget, Spannable buffer) {
        final int selectionEnd = widget.getSelectionEnd();
        final WordIterator wordIterator = widget.getWordIterator();
        wordIterator.setCharSequence(buffer, selectionEnd, selectionEnd);
        return HiddenSelection.moveToPreceding(buffer, wordIterator, isSelecting(buffer));
    }

    /** {@hide} */
    @Override
    protected boolean rightWord(EditText widget, Spannable buffer) {
        final int selectionEnd = widget.getSelectionEnd();
        final WordIterator wordIterator = widget.getWordIterator();
        wordIterator.setCharSequence(buffer, selectionEnd, selectionEnd);
        return HiddenSelection.moveToFollowing(buffer, wordIterator, isSelecting(buffer));
    }

    @Override
    protected boolean home(EditText widget, Spannable buffer) {
        return lineStart(widget, buffer);
    }

    @Override
    protected boolean end(EditText widget, Spannable buffer) {
        return lineEnd(widget, buffer);
    }

    @Override
    public boolean onTouchEvent(EditText widget, Spannable buffer, MotionEvent event) {
        int initialScrollX = -1;
        int initialScrollY = -1;
        final int action = event.getAction();

        if (action == MotionEvent.ACTION_UP) {
            initialScrollX = Touch.getInitialScrollX(widget, buffer);
            initialScrollY = Touch.getInitialScrollY(widget, buffer);
        }

        boolean wasTouchSelecting = isSelecting(buffer);
        boolean handled = Touch.onTouchEvent(widget, buffer, event);

        if (widget.didTouchFocusSelect()) {
            return handled;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            // For touch events, the code should run only when selection is active.
            if (isSelecting(buffer)) {
                if (!widget.isFocused()) {
                    if (!widget.requestFocus()) {
                        return handled;
                    }
                }
                int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                buffer.setSpan(LAST_TAP_DOWN, offset, offset, Spannable.SPAN_POINT_POINT);
                // Disallow intercepting of the touch events, so that
                // users can scroll and select at the same time.
                // without this, users would get booted out of select
                // mode once the view detected it needed to scroll.
                widget.getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else if (widget.isFocused()) {
            if (action == MotionEvent.ACTION_MOVE) {
                if (isSelecting(buffer) && handled) {
                    final int startOffset = buffer.getSpanStart(LAST_TAP_DOWN);
                    // Before selecting, make sure we've moved out of the "slop".
                    // handled will be true, if we're in select mode AND we're
                    // OUT of the slop

                    // Turn long press off while we're selecting. User needs to
                    // re-tap on the selection to enable long press
                    widget.cancelLongPress();

                    // Update selection as we're moving the selection area.

                    // Get the current touch position
                    final int offset = widget.getOffsetForPosition(event.getX(), event.getY());
                    Selection.setSelection(buffer, Math.min(startOffset, offset),
                            Math.max(startOffset, offset));
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                // If we have scrolled, then the up shouldn't move the cursor,
                // but we do need to make sure the cursor is still visible at
                // the current scroll offset to avoid the scroll jumping later
                // to show it.
                if ((initialScrollY >= 0 && initialScrollY != widget.getScrollY()) ||
                        (initialScrollX >= 0 && initialScrollX != widget.getScrollX())) {
                    widget.moveCursorToVisibleOffset();
                    return true;
                }

                if (wasTouchSelecting) {
                    final int startOffset = buffer.getSpanStart(LAST_TAP_DOWN);
                    final int endOffset = widget.getOffsetForPosition(event.getX(), event.getY());
                    Selection.setSelection(buffer, Math.min(startOffset, endOffset),
                            Math.max(startOffset, endOffset));
                    buffer.removeSpan(LAST_TAP_DOWN);
                }

                MetaKeyKeyListener.adjustMetaAfterKeypress(buffer);
                ProtectedMetaKeyKeyListener.resetLockedMeta(buffer);

                return true;
            }
        }
        return handled;
    }

    @Override
    public boolean canSelectArbitrarily() {
        return true;
    }

    @Override
    public void initialize(EditText widget, Spannable text) {
        Selection.setSelection(text, 0);
    }

    @Override
    public void onTakeFocus(EditText view, Spannable text, int dir) {
        if ((dir & (View.FOCUS_FORWARD | View.FOCUS_DOWN)) != 0) {
            if (view.getLayout() == null) {
                // This shouldn't be null, but do something sensible if it is.
                Selection.setSelection(text, text.length());
            }
        } else {
            Selection.setSelection(text, text.length());
        }
    }

    public static MovementMethod getInstance() {
        if (sInstance == null) {
            sInstance = new ArrowKeyMovementMethod();
        }

        return sInstance;
    }

    private static final Object LAST_TAP_DOWN = new Object();
    private static ArrowKeyMovementMethod sInstance;
}
