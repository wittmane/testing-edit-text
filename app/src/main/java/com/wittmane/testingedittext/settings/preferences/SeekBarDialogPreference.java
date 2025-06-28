/*
 * Copyright (C) 2022-2025 Eli Wittman
 * Copyright (C) 2013 The Android Open Source Project
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
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.widget.ExtendingSeekBar;
import com.wittmane.testingedittext.widget.ExtendingSeekBar.OnExtendingSeekBarChangeListener;

public class SeekBarDialogPreference extends DialogPreferenceBase
        implements OnExtendingSeekBarChangeListener {
    private static final String TAG = SeekBarDialogPreference.class.getSimpleName();

    private final int mMaxValue;
    private final int mMinValue;
    private final int mStepValue;
    private final int mDefaultValue;
    private final String mValueText;
    private final String mDefaultValueText;
    private final boolean mShowEditText;

    private EditText mEditText;
    private TextView mLabelView;
    private ExtendingSeekBar mSeekBar;

    public SeekBarDialogPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SeekBarDialogPreference, 0, 0);
        mMaxValue = a.getInt(R.styleable.SeekBarDialogPreference_maxValue, 0);
        mMinValue = a.getInt(R.styleable.SeekBarDialogPreference_minValue, 0);
        mStepValue = a.getInt(R.styleable.SeekBarDialogPreference_stepValue, 1);
        final int defaultDefaultValue;
        if (mMinValue == Integer.MIN_VALUE) {
            defaultDefaultValue = mMaxValue < Integer.MAX_VALUE ? mMaxValue + 1 : Integer.MIN_VALUE;
        } else {
            defaultDefaultValue = mMinValue - 1;
        }
        mDefaultValue = a.getInt(R.styleable.SeekBarDialogPreference_defaultValue,
                defaultDefaultValue);
        mValueText = a.getString(R.styleable.SeekBarDialogPreference_valueText);
        mDefaultValueText = a.getString(R.styleable.SeekBarDialogPreference_defaultValueText);
        mShowEditText = a.getBoolean(R.styleable.SeekBarDialogPreference_showEditText, false);
        a.recycle();
        setDialogLayoutResource(R.layout.seek_bar_dialog);
    }

    @Override
    protected View onCreateDialogView() {
        final View view = super.onCreateDialogView();
        mSeekBar = view.findViewById(R.id.seek_bar_dialog_bar);
        mSeekBar.setRange(mMinValue, mMaxValue, mStepValue);
        mSeekBar.setOnSeekBarChangeListener(this);
        mLabelView = (TextView)view.findViewById(R.id.seek_bar_dialog_label);
        mEditText = (EditText)view.findViewById(R.id.seek_bar_dialog_value_edit);
        if (mShowEditText) {
            mEditText.setInputType(InputType.TYPE_CLASS_NUMBER
                    | (mMinValue < 0 ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
            mEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    int value;
                    try {
                        value = Integer.parseInt(s.toString());
                    } catch (Exception e) {
                        mEditText.setError(getContext().getString(R.string.invalid_number));
                        setAcceptButtonEnabled(false);
                        return;
                    }
                    if (mSeekBar.getProgress() == value) {
                        return;
                    }
                    if (value < mMinValue) {
                        mEditText.setError(getContext().getString(R.string.min_value, mMinValue));
                        setAcceptButtonEnabled(false);
                    } else if (value > mMaxValue) {
                        mEditText.setError(getContext().getString(R.string.max_value, mMaxValue));
                        setAcceptButtonEnabled(false);
                    } else {
                        if (!TextUtils.isEmpty(mEditText.getError())) {
                            mEditText.setError(null);
                        }
                        setAcceptButtonEnabled(true);
                        mSeekBar.setProgress(value);
                    }
                }
            });
        } else {
            mEditText.setVisibility(View.GONE);
        }

        return view;
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);
        final int value = readValue();
        mSeekBar.setProgress(value);
        if (mShowEditText) {
            mEditText.setText("" + value);
            mLabelView.setText(String.format(mValueText, "").trim());
        } else {
            mLabelView.setText(getValueText(value, true));
        }

        // allow the title to wrap
        TextView titleTextView = view.findViewById(android.R.id.title);
        if (titleTextView != null) {
            titleTextView.setSingleLine(false);
        }
    }

    @Override
    protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
        builder.setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setNeutralButton(R.string.button_default, this);
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        super.onClick(dialog, which);
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            final int value = readDefaultValue();
            updateValueSummary(value);
            writeDefaultValue();
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            final int value = mSeekBar.getProgress();
            updateValueSummary(value);
            writeValue(value);
        }
    }

    @Override
    public void onProgressChanged(final ExtendingSeekBar seekBar, final int progress,
                                  final boolean fromUser) {
        if (mShowEditText) {
            if (!("" + progress).equals(mEditText.getText().toString())) {
                if (!TextUtils.isEmpty(mEditText.getError())) {
                    mEditText.setError(null);
                    setAcceptButtonEnabled(true);
                }
                mEditText.setText("" + progress);
                if (mEditText.hasFocus()) {
                    mEditText.selectAll();
                }
            }
        } else {
            mLabelView.setText(getValueText(progress, true));
        }
    }

    @Override
    public void onStartTrackingTouch(final ExtendingSeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(final ExtendingSeekBar seekBar) {}

    public void writeValue(final int value) {
        getPrefs().setInt(getKey(), value);
    }

    public void writeDefaultValue() {
        getPrefs().remove(getKey());
    }

    public int readValue() {
        return getPrefs().getInt(getKey(), mDefaultValue);
    }

    public int readDefaultValue() {
        return mDefaultValue;
    }

    public String getValueText(final int value) {
        return getValueText(value, false);
    }

    private String getValueText(final int value, final boolean forSeekBarValue) {
        if (value < mMinValue || value > mMaxValue) {
            return mDefaultValueText == null ? "" : mDefaultValueText;
        }
        // if the default value is within the allowed range, the default text should be used (if it
        // exists) for it except for the value label for the seek bar
        if (!forSeekBarValue && value == mDefaultValue && !TextUtils.isEmpty(mDefaultValueText)) {
            return mDefaultValueText;
        }
        return String.format(mValueText, value);
    }

    @Override
    protected void updateValueSummary() {
        updateValueSummary(readValue());
    }

    private void updateValueSummary(final int value) {
        setValueSummary(getValueText(value));
    }
}
