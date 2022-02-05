package com.wittmane.testingedittext;

import android.view.ViewTreeObserver;

/**
 * A CursorController instance can be used to control a cursor in the text.
 */
public interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {
    /**
     * Makes the cursor controller visible on screen.
     * See also {@link #hide()}.
     */
    void show();

    /**
     * Hide the cursor controller from screen.
     * See also {@link #show()}.
     */
    void hide();

    /**
     * Called when the view is detached from window. Perform house keeping task, such as
     * stopping Runnable thread that would otherwise keep a reference on the context, thus
     * preventing the activity from being recycled.
     */
    void onDetached();

    boolean isCursorBeingModified();

    boolean isActive();
}
