/*
 * Copyright (C) 2022 Eli Wittman
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
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.ExtendingSeekBar;
import com.wittmane.testingedittext.settings.ExtendingSeekBar.OnExtendingSeekBarChangeListener;

public class SeekBarDialogPreference extends DialogPreferenceBase
        implements OnExtendingSeekBarChangeListener {

    private final int mMaxValue;
    private final int mMinValue;
    private final int mStepValue;
    private final int mDefaultValue;
    private final String mValueText;
    private final String mDefaultValueText;

    private TextView mValueView;
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
        a.recycle();
        setDialogLayoutResource(R.layout.seek_bar_dialog);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        final int value = readValue();
        setValueSummary(getValueText(value));
        return view;
    }

    @Override
    protected View onCreateDialogView() {
        final View view = super.onCreateDialogView();
        mSeekBar = view.findViewById(R.id.seek_bar_dialog_bar);
        mSeekBar.setRange(mMinValue, mMaxValue, mStepValue);
        mSeekBar.setOnSeekBarChangeListener(this);
        mValueView = (TextView)view.findViewById(R.id.seek_bar_dialog_value);

        return view;
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);
        final int value = readValue();
        mValueView.setText(getValueText(value));
        mSeekBar.setProgress(value);

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
            setValueSummary(getValueText(value));
            writeDefaultValue();
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            final int value = mSeekBar.getProgress();
            setValueSummary(getValueText(value));
            writeValue(value);
        }
    }

    @Override
    public void onProgressChanged(final ExtendingSeekBar seekBar, final int progress,
                                  final boolean fromUser) {
        mValueView.setText(getValueText(progress));
    }

    @Override
    public void onStartTrackingTouch(final ExtendingSeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(final ExtendingSeekBar seekBar) {}

    private SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    public void writeValue(final int value) {
        getPrefs().edit().putInt(getKey(), value).apply();
    }

    public void writeDefaultValue() {
        getPrefs().edit().remove(getKey()).apply();
    }

    public int readValue() {
        return getPrefs().getInt(getKey(), mDefaultValue);
    }

    public int readDefaultValue() {
        return mDefaultValue;
    }

    public String getValueText(final int value) {
        if (value < mMinValue || value > mMaxValue) {
            return mDefaultValueText == null ? "" : mDefaultValueText;
        }
        return String.format(mValueText, value);
    }
}
