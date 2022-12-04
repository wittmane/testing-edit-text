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

package com.wittmane.testingedittext.settings.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import com.wittmane.testingedittext.R;

public class TextDialogPreference extends DialogPreferenceBase {
    private static final String TAG = TextDialogPreference.class.getSimpleName();

    private static final int INPUT_TYPE_CLASS_MASK = 0x0000000F;
    private static final int INPUT_TYPE_FLAG_MASK = 0x000000F0;
    private static final int INPUT_TYPE_CLASS_TEXT = 0x00000000;
    private static final int INPUT_TYPE_CLASS_NUMBER = 0x00000001;
    private static final int INPUT_TYPE_NUMBER_FLAG_SIGNED = 0x00000011;
    private static final int INPUT_TYPE_NUMBER_FLAG_DECIMAL = 0x00000021;

    private static final int DATA_TYPE_STRING = 0;
    private static final int DATA_TYPE_INT = 1;
    private static final int DATA_TYPE_FLOAT = 2;

    private android.widget.EditText mEditText;
    private final int mInputType;
    private int mDefaultIntValue;
    private float mDefaultFloatValue;
    private String mDefaultStringValue;

    public TextDialogPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.TextDialogPreference, 0, 0);
        mInputType = a.getInt(R.styleable.TextDialogPreference_inputType, 0);
        if (a.hasValue(R.styleable.TextDialogPreference_defaultText)) {
            switch (getDataType()) {
                case DATA_TYPE_INT:
                    try {
                        mDefaultIntValue =
                                a.getInt(R.styleable.TextDialogPreference_defaultText, 0);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, e.getMessage());
                    }
                    break;
                case DATA_TYPE_FLOAT:
                    try {
                        mDefaultFloatValue =
                                a.getFloat(R.styleable.TextDialogPreference_defaultText, 0);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, e.getMessage());
                    }
                    break;
                default:
                    mDefaultStringValue =
                            a.getString(R.styleable.TextDialogPreference_defaultText);
                    break;
            }
        }
        a.recycle();

        setDialogLayoutResource(R.layout.text_dialog);
    }

    private int getDataType() {
        int inputTypeClass = INPUT_TYPE_CLASS_MASK & mInputType;
        if (inputTypeClass == INPUT_TYPE_CLASS_NUMBER) {
            int flags = INPUT_TYPE_FLAG_MASK & mInputType;
            if ((flags & INPUT_TYPE_NUMBER_FLAG_DECIMAL) > 0) {
                return DATA_TYPE_FLOAT;
            }
            return DATA_TYPE_INT;
        }
        return DATA_TYPE_STRING;
    }

    @Override
    protected View onCreateDialogView() {
        final View view = super.onCreateDialogView();
        mEditText = view.findViewById(R.id.text_dialog_text);
        int inputTypeClass = INPUT_TYPE_CLASS_MASK & mInputType;
        int inputType;
        if (inputTypeClass == INPUT_TYPE_CLASS_NUMBER) {
            inputType = InputType.TYPE_CLASS_NUMBER;
            int flags = INPUT_TYPE_FLAG_MASK & mInputType;
            if ((flags & INPUT_TYPE_NUMBER_FLAG_SIGNED) > 0) {
                inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
            }
            if ((flags & INPUT_TYPE_NUMBER_FLAG_DECIMAL) > 0) {
                inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
            }
        } else {
            inputType = InputType.TYPE_CLASS_TEXT;
        }
        mEditText.setInputType(inputType);


        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                          int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before,
                                      int count) { }

            @Override
            public void afterTextChanged(Editable editable) {
                TextDialogPreference.this.afterTextChanged();
            }
        });

        return view;
    }

    private void afterTextChanged() {
        AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog == null) {
            return;
        }
        Button acceptButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (acceptButton == null) {
            return;
        }

        String text = mEditText.getText().toString();
        boolean isValid;
        switch (getDataType()) {
            case DATA_TYPE_INT:
                isValid = tryParseInt(text) != null;
                break;
            case DATA_TYPE_FLOAT:
                isValid = tryParseFloat(text) != null;
                break;
            default:
                // no validation for strings
                return;
        }

        acceptButton.setEnabled(isValid);
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);

        mEditText.setText(readValue());
    }

    @Override
    protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
        boolean allowClear;
        int flags = INPUT_TYPE_FLAG_MASK & mInputType;
        switch (getDataType()) {
            case DATA_TYPE_INT:
                allowClear = mDefaultIntValue < 0 && (flags & INPUT_TYPE_NUMBER_FLAG_SIGNED) < 1;
                break;
            case DATA_TYPE_FLOAT:
                allowClear = mDefaultFloatValue < 0 && (flags & INPUT_TYPE_NUMBER_FLAG_SIGNED) < 1;
                break;
            default:
                allowClear = mDefaultStringValue == null;
                break;
        }
        builder.setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setNeutralButton(
                        allowClear ? R.string.button_clear : R.string.button_default, this);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        // update whether the ok button is enabled
        afterTextChanged();

        openKeyboard();
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        super.onClick(dialog, which);
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            writeDefaultValue();
            updateValueSummary();
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            writeValue();
            updateValueSummary();
        }
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Float tryParseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void writeValue() {
        String text = mEditText.getText().toString();
        switch (getDataType()) {
            case DATA_TYPE_INT:
                Integer intValue = tryParseInt(text);
                if (intValue != null) {
                    getPrefs().edit().putInt(getKey(), intValue).apply();
                } else {
                    Log.e(TAG, "Failed to write \"" + text + "\" because it isn't an int");
                }
                break;
            case DATA_TYPE_FLOAT:
                Float floatValue = tryParseFloat(text);
                if (floatValue != null) {
                    getPrefs().edit().putFloat(getKey(), floatValue).apply();
                } else {
                    Log.e(TAG, "Failed to write \"" + text + "\" because it isn't a float");
                }
                break;
            default:
                getPrefs().edit().putString(getKey(), text).apply();
                break;
        }
    }

    public void writeDefaultValue() {
        getPrefs().edit().remove(getKey()).apply();
    }

    public String readValue() {
        int flags = INPUT_TYPE_FLAG_MASK & mInputType;
        switch (getDataType()) {
            case DATA_TYPE_INT:
                int intValue = getPrefs().getInt(getKey(), mDefaultIntValue);
                if (intValue < 0 && (flags & INPUT_TYPE_NUMBER_FLAG_SIGNED) < 1) {
                    return "";
                }
                return Integer.toString(intValue);
            case DATA_TYPE_FLOAT:
                float floatValue = getPrefs().getFloat(getKey(), mDefaultFloatValue);
                if (floatValue < 0 && (flags & INPUT_TYPE_NUMBER_FLAG_SIGNED) < 1) {
                    return "";
                }
                return Float.toString(floatValue);
            default:
                return getPrefs().getString(getKey(), mDefaultStringValue);
        }
    }

    @Override
    protected void updateValueSummary() {
        String value = readValue();
        if (getDataType() == DATA_TYPE_STRING && mDefaultStringValue == null && "".equals(value)) {
            value = "\"\"";
        }
        setValueSummary(value);
    }

    private void openKeyboard() {
        mEditText.requestFocus();
        mEditText.post(new Runnable() {
            public void run() {
                InputMethodManager imm = (InputMethodManager)getContext().getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(mEditText, 0);
            }
        });
    }
}
