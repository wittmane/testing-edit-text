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
import android.preference.SwitchPreference;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.TextView;

public class LongTextSwitchPreference extends SwitchPreference {

    public LongTextSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private CharSequence getDisplayedSummary() {
        // based on logic from TwoStatePreference#syncSummaryView
        boolean isChecked = isChecked();
        CharSequence summaryOn = getSummaryOn();
        if (isChecked && !TextUtils.isEmpty(summaryOn)) {
            return summaryOn;
        }
        CharSequence summaryOff = getSummaryOff();
        if (!isChecked && !TextUtils.isEmpty(summaryOff)) {
            return summaryOff;
        }
        return getSummary();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        TextView titleTextView = view.findViewById(android.R.id.title);
        if (titleTextView != null) {
            titleTextView.setSingleLine(false);
        }

        TextView summaryTextView = view.findViewById(android.R.id.summary);
        if (summaryTextView != null) {
            summaryTextView.post(new Runnable() {
                @Override
                public void run() {
                    Layout layout = summaryTextView.getLayout();
                    if (layout == null) {
                        // can't tell if the text is cut off
                        return;
                    }
                    if (layout.getLineCount() < summaryTextView.getMaxLines()) {
                        // text isn't cut off
                        return;
                    }

                    OnLongClickListener longClickListener = new OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            // Create the object of AlertDialog Builder class
                            AlertDialog dialog = new AlertDialog.Builder(getContext())
                                    .setTitle(getTitle())
                                    .setMessage(getDisplayedSummary())
                                    .setPositiveButton(android.R.string.ok,
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog,
                                                                    int which) {
                                                }
                                            })
                                    .create();
                            dialog.show();

                            return true;
                        }
                    };
                    summaryTextView.setOnLongClickListener(longClickListener);

                    // adding a long click listener seems to block the single click on the text from
                    // toggling the preference like it normally does
                    OnClickListener clickListener = new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            LongTextSwitchPreference.this.onClick();
                        }
                    };
                    summaryTextView.setOnClickListener(clickListener);

                    // add the listener to the title too
                    if (titleTextView != null) {
                        titleTextView.setOnLongClickListener(longClickListener);
                        titleTextView.setOnClickListener(clickListener);
                    }

                }
            });
        }
    }
}
