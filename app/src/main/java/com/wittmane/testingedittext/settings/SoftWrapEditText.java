/*
 * Copyright (C) 2022 Eli Wittman
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

package com.wittmane.testingedittext.settings;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.InputType;
import android.text.method.SingleLineTransformationMethod;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

/**
 * Single line (no new lines allowed) edit text that visually wraps onto new lines, rather than
 * scrolling horizontally.
 */
@SuppressLint("AppCompatCustomView")
public class SoftWrapEditText extends EditText {
    public SoftWrapEditText(Context context) {
        super(context);
        init();
    }

    public SoftWrapEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SoftWrapEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SoftWrapEditText(Context context, AttributeSet attrs, int defStyleAttr,
                            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        if (!isMultilineInputType(getInputType())) {
            setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        setWrappingSingleLine();
    }

    private void setWrappingSingleLine() {
        // apply a transformation matching a single line (block new lines) even though this is
        // technically a multi-line field
        if (!isPasswordInputType()) {
            setTransformationMethod(SingleLineTransformationMethod.getInstance());
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection connection = super.onCreateInputConnection(outAttrs);
        // block the flag the base EditText adds to all multi-line text editors to always show an
        // enter key since this doesn't actually accept new lines
        outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        // although internally we're considered a multi-line field, that is really only a visual
        // distinction, so the IME should just behave as if we were just a single line
        outAttrs.inputType &= ~InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        return connection;
    }

    @Override
    public void setInputType(int type) {
        if (!isMultilineInputType(type)) {
            // this should be considered multiline (in this case that doesn't mean this actually
            // allows new lines), so this entry isn't valid
            return;
        }
        super.setInputType(type);
        setWrappingSingleLine();
    }

    private boolean isPasswordInputType() {
        final int inputType = getInputType();
        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        return variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
                || variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || variation
                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private static boolean isMultilineInputType(int type) {
        return (type & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE))
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    }
}
