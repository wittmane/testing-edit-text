/*
 * Copyright (C) 2022 Eli Wittman
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
 *
 * (EW) copied from AOSP because we need to use our custom EditText instead of the AOSP TextView
 */
public interface MovementMethod {
    void initialize(EditText widget, Spannable text);
    boolean onKeyDown(EditText widget, Spannable text, int keyCode, KeyEvent event);
    boolean onKeyUp(EditText widget, Spannable text, int keyCode, KeyEvent event);

    /**
     * If the key listener wants to other kinds of key events, return true,
     * otherwise return false and the caller (i.e. the widget host)
     * will handle the key.
     */
    boolean onKeyOther(EditText view, Spannable text, KeyEvent event);

    void onTakeFocus(EditText widget, Spannable text, int direction);
    boolean onTrackballEvent(EditText widget, Spannable text, MotionEvent event);
    boolean onTouchEvent(EditText widget, Spannable text, MotionEvent event);
    boolean onGenericMotionEvent(EditText widget, Spannable text, MotionEvent event);

    /**
     * Returns true if this movement method allows arbitrary selection
     * of any text; false if it has no selection (like a movement method
     * that only scrolls) or a constrained selection (for example
     * limited to links.  The "Select All" menu item is disabled
     * if arbitrary selection is not allowed.
     */
    boolean canSelectArbitrarily();
}
