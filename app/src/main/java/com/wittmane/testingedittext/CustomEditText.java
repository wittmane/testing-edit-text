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

package com.wittmane.testingedittext;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.RequiresApi;
//TODO: (EW) should this use app compat? if this is built off of a fixed version, we shouldn't have
// unexpected things happen based on how we're overriding onCreateInputConnection, but other things
// could break or simply behave different from the default TextView (aside from the intentional
// changes here). we might have to more handling in the view that the EditText doesn't give access
// to, so we might need to create a custom view and not use either of these options.
import androidx.appcompat.widget.AppCompatEditText;

/*
 * This is supposed to be a *very* thin veneer over TextView.
 * Do not make any changes here that do anything that a TextView
 * with a key listener and a movement method wouldn't do!
 */
/**
 * A user interface element for entering and modifying text.
 * When you define an edit text widget, you must specify the
 * {@link android.R.styleable#TextView_inputType}
 * attribute. For example, for plain text input set inputType to "text":
 * <p>
 * <pre>
 * &lt;EditText
 *     android:id="@+id/plain_text_input"
 *     android:layout_height="wrap_content"
 *     android:layout_width="match_parent"
 *     android:inputType="text"/&gt;</pre>
 *
 * Choosing the input type configures the keyboard type that is shown, acceptable characters,
 * and appearance of the edit text.
 * For example, if you want to accept a secret number, like a unique pin or serial number,
 * you can set inputType to "numericPassword".
 * An inputType of "numericPassword" results in an edit text that accepts numbers only,
 * shows a numeric keyboard when focused, and masks the text that is entered for privacy.
 * <p>
 * See the <a href="{@docRoot}guide/topics/ui/controls/text.html">Text Fields</a>
 * guide for examples of other
 * {@link android.R.styleable#TextView_inputType} settings.
 * </p>
 * <p>You also can receive callbacks as a user changes text by
 * adding a {@link android.text.TextWatcher} to the edit text.
 * This is useful when you want to add auto-save functionality as changes are made,
 * or validate the format of user input, for example.
 * You add a text watcher using the {@link CustomTextView#addTextChangedListener} method.
 * </p>
 * <p>
 * This widget does not support auto-sizing text.
 * <p>
 * <b>XML attributes</b>
 * <p>
 * See {@link android.R.styleable#EditText EditText Attributes},
 * {@link android.R.styleable#TextView TextView Attributes},
 * {@link android.R.styleable#View View Attributes}
 */
public class CustomEditText extends AppCompatEditText {
    public CustomEditText(Context context) {
        this(context, null);
    }
    public CustomEditText(Context context, AttributeSet attrs) {
        this(context, attrs, /*com.android.internal.*/R.attr.editTextStyle);
    }
    public CustomEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//    public CustomEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
//        super(context, attrs, defStyleAttr, defStyleRes);
//    }
    @Override
    public boolean getFreezesText() {
        return true;
    }
    @Override
    protected boolean getDefaultEditable() {
        return true;
    }
    @Override
    protected MovementMethod getDefaultMovementMethod() {
        return ArrowKeyMovementMethod.getInstance();
    }
    @Override
    public Editable getText() {
        CharSequence text = super.getText();
        // This can only happen during construction.
        if (text == null) {
            return null;
        }
        if (text instanceof Editable) {
            return (Editable) super.getText();
        }
        super.setText(text, BufferType.EDITABLE);
        return (Editable) super.getText();
    }
    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, BufferType.EDITABLE);
    }
    /**
     * Convenience for {@link Selection#setSelection(Spannable, int, int)}.
     */
    public void setSelection(int start, int stop) {
        Selection.setSelection(getText(), start, stop);
    }
    /**
     * Convenience for {@link Selection#setSelection(Spannable, int)}.
     */
    public void setSelection(int index) {
        Selection.setSelection(getText(), index);
    }
    /**
     * Convenience for {@link Selection#selectAll}.
     */
    public void selectAll() {
        Selection.selectAll(getText());
    }
    /**
     * Convenience for {@link Selection#extendSelection}.
     */
    public void extendSelection(int index) {
        Selection.extendSelection(getText(), index);
    }
    /**
     * Causes words in the text that are longer than the view's width to be ellipsized instead of
     * broken in the middle. {@link TextUtils.TruncateAt#MARQUEE
     * TextUtils.TruncateAt#MARQUEE} is not supported.
     *
     * @param ellipsis Type of ellipsis to be applied.
     * @throws IllegalArgumentException When the value of <code>ellipsis</code> parameter is
     *      {@link TextUtils.TruncateAt#MARQUEE}.
     * @see CustomTextView#setEllipsize(TextUtils.TruncateAt)
     */
    @Override
    public void setEllipsize(TextUtils.TruncateAt ellipsis) {
        if (ellipsis == TextUtils.TruncateAt.MARQUEE) {
            throw new IllegalArgumentException("CustomEditText cannot use the ellipsize mode "
                    + "TextUtils.TruncateAt.MARQUEE");
        }
        super.setEllipsize(ellipsis);
    }
    @Override
    public CharSequence getAccessibilityClassName() {
        return CustomEditText.class.getName();
    }
//    /** @hide */
//    @Override
//    protected boolean supportsAutoSizeText() {
//        return false;
//    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection ic = super.onCreateInputConnection(outAttrs);
        if (ic != null) {
            return new CustomEditableInputConnection30(this);
        }
        return null;
    }
}
