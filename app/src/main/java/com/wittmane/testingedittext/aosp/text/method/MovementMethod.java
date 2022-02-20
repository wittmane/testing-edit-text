package com.wittmane.testingedittext.aosp.text.method;

import android.text.Spannable;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.wittmane.testingedittext.aosp.widget.EditText;

/**
 * Provides cursor positioning, scrolling and text selection functionality in a {@link EditText}.
 * <p>
 * The {@link EditText} delegates handling of key events, trackball motions and touches to
 * the movement method for purposes of content navigation.  The framework automatically
 * selects an appropriate movement method based on the content of the {@link EditText}.
 * </p><p>
 * This interface is intended for use by the framework; it should not be implemented
 * directly by applications.
 * </p>
 */
public interface MovementMethod {
    public void initialize(EditText widget, Spannable text);
    public boolean onKeyDown(EditText widget, Spannable text, int keyCode, KeyEvent event);
    public boolean onKeyUp(EditText widget, Spannable text, int keyCode, KeyEvent event);

    /**
     * If the key listener wants to other kinds of key events, return true,
     * otherwise return false and the caller (i.e. the widget host)
     * will handle the key.
     */
    public boolean onKeyOther(EditText view, Spannable text, KeyEvent event);

    public void onTakeFocus(EditText widget, Spannable text, int direction);
    public boolean onTrackballEvent(EditText widget, Spannable text, MotionEvent event);
    public boolean onTouchEvent(EditText widget, Spannable text, MotionEvent event);
    public boolean onGenericMotionEvent(EditText widget, Spannable text, MotionEvent event);

    /**
     * Returns true if this movement method allows arbitrary selection
     * of any text; false if it has no selection (like a movement method
     * that only scrolls) or a constrained selection (for example
     * limited to links.  The "Select All" menu item is disabled
     * if arbitrary selection is not allowed.
     */
    public boolean canSelectArbitrarily();
}
