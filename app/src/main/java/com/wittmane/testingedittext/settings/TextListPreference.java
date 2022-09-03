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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TextListPreference extends DialogPreference {
    private static final String TAG = TextListPreference.class.getSimpleName();

    private static final int RESOURCES_ID_NULL =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Resources.ID_NULL : 0;

    private TableLayout mTextTable;
    private CheckBox mEscapeCharactersCheckBox;

    private final List<Row> mRows = new ArrayList<>();

    private final CharSequence mBaseSummary;

    public TextListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.text_list_dialog);
        mBaseSummary = getSummary();
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        final TextList value = readValue();
        setSummary(getValueText(value));
        return view;
    }

    @Override
    public void setSummary(CharSequence summary) {
        if (mBaseSummary != null && mBaseSummary.length() > 0) {
            if (summary != null && !summary.equals("")) {
                summary = mBaseSummary + "\n" + summary;
            } else {
                summary = mBaseSummary;
            }
        }
        super.setSummary(summary);
    }

    @Override
    protected View onCreateDialogView() {
        final View view = super.onCreateDialogView();
        mTextTable = view.findViewById(R.id.text_table);
        mEscapeCharactersCheckBox = view.findViewById(R.id.escape_characters);

        return view;
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);

        final TextList value = readValue();

        mRows.clear();
        for (String text : value.mTextArray) {
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            addRow(text);
        }
        addRow(null);

        mEscapeCharactersCheckBox.setChecked(value.mEscapeChars);
    }

    private void addRow(String text) {
        TableRow tableRow = new TableRow(getContext());

        EditText editText = new EditText(getContext());
        editText.setSingleLine();
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (!TextUtils.isEmpty(text)) {
            editText.setText(text);
        }

        ImageButton removeButton = new ImageButton(getContext());
        removeButton.setImageResource(R.drawable.ic_clear_white_24);
        removeButton.setColorFilter(editText.getCurrentTextColor());
        TypedArray ta = getContext().getTheme()
                .obtainStyledAttributes(new int[] {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                ? android.R.attr.selectableItemBackgroundBorderless
                                : android.R.attr.selectableItemBackground
                });
        int background = ta.getResourceId(0, RESOURCES_ID_NULL);
        ta.recycle();
        removeButton.setBackgroundResource(background);
        // don't allow removing the last row
        removeButton.setVisibility(View.INVISIBLE);

        Row row = new Row(tableRow, editText, removeButton);

        removeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRows.size() < 2) {
                    // don't allow removing the last row
                    return;
                }
                mTextTable.removeView(tableRow);
                mRows.remove(row);
            }
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                          int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before,
                                      int count) {
                if (charSequence.length() == count && count > 0 && before == 0
                        && mRows.get(mRows.size() - 1).mEditText == editText) {
                    // this event is adding text from blank in the last row. now that there is text
                    // in the row, add a new empty row and allow this row to be removed since it
                    // isn't the last row anymore.
                    removeButton.setVisibility(View.VISIBLE);
                    addRow(null);

                } else if (charSequence.length() == 0 && before > 0
                        && mRows.get(mRows.size() - 2).mEditText == editText) {
                    // this event is clearing text in the second last row. the last row should
                    // always be blank (still verify to be safe), but we don't need multiple blank
                    // rows at the end, so remove the last row, making this the new last row, which
                    // means that the button to remove the row should be hidden too.
                    EditText lastEditText = mRows.get(mRows.size() - 1).mEditText;
                    if (TextUtils.isEmpty(lastEditText.getText())) {
                        removeButton.setVisibility(View.INVISIBLE);

                        Row lastRow = mRows.get(mRows.size() - 1);
                        mTextTable.removeView(lastRow.mTableRow);
                        mRows.remove(lastRow);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        editText.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_NEXT
                        && textView instanceof EditText
                        && mRows.size() > 0 && mRows.get(mRows.size() - 1).mEditText == textView) {
                    // there isn't a good way to refresh the IME's action, so we just always mark
                    // the action to be next since the last field should be next once text is
                    // entered because a new row will be added after it. just hide the keyboard if
                    // next is pressed for the last (empty) row to make sure the whole dialog is
                    // shown so the user can click the dialog buttons since they could be hidden by
                    // the keyboard if there are a lot of rows.
                    InputMethodManager imm = (InputMethodManager)getContext()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                    }
                    return true;
                }
                return false;
            }
        });

        if (mRows.size() > 0) {
            mRows.get(mRows.size() - 1).mRemoveButton.setVisibility(View.VISIBLE);
        }
        tableRow.addView(editText);
        tableRow.addView(removeButton);
        mTextTable.addView(tableRow);
        mRows.add(row);
    }

    private static class Row {
        final TableRow mTableRow;
        final EditText mEditText;
        final ImageButton mRemoveButton;
        Row(TableRow tableRow, EditText editText, ImageButton removeButton) {
            mTableRow = tableRow;
            mEditText = editText;
            mRemoveButton = removeButton;
        }
    }

    @Override
    protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
        builder.setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setNeutralButton(R.string.button_clear, this);
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        super.onClick(dialog, which);
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            final TextList value = readDefaultValue();
            setSummary(getValueText(value));
            clearValue();
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            TextList value = new TextList();
            value.mTextArray = new String[mRows.size()];
            for (int i = 0; i < mRows.size(); i++) {
                value.mTextArray[i] = mRows.get(i).mEditText.getText().toString();
            }
            value.mEscapeChars = mEscapeCharactersCheckBox.isChecked();

            setSummary(getValueText(value));
            writeValue(value);
        }
    }

    private SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    public void writeValue(final @NonNull TextList value) {
        HashSet<Character> usedChars = new HashSet<>();
        for (String text : value.mTextArray) {
            for (int i = 0; i < text.length(); i++) {
                usedChars.add(text.charAt(i));
            }
        }
        char delimiter = 0;
        while (usedChars.contains(delimiter)) {
            if (delimiter == Character.MAX_VALUE) {
                // all of the chars are used, so we can't use any individual one as a delimiter.
                // this probably won't happen. maybe create some multi-character delimiter, but it
                // probably isn't worth it.
                Log.e(TAG, "All characters used. Failed to save preference.");
                return;
            }
            delimiter++;
        }
        StringBuilder sb = new StringBuilder().append(value.mEscapeChars ? "1" : "0");
        for (int i = 0; i < value.mTextArray.length; i++) {
            if (TextUtils.isEmpty(value.mTextArray[i])) {
                continue;
            }
            sb.append(delimiter).append(value.mTextArray[i]);
        }

        getPrefs().edit().putString(getKey(), sb.toString()).apply();
    }

    public void clearValue() {
        getPrefs().edit().remove(getKey()).apply();
    }

    public @NonNull TextList readValue() {
        String rawValue = getPrefs().getString(getKey(), null);
        TextList textList = new TextList();
        if (rawValue == null || rawValue.length() == 0) {
            return textList;
        }

        char char0 = rawValue.charAt(0);
        if (char0 == '1') {
            textList.mEscapeChars = true;
        } else if (char0 == '0') {
            textList.mEscapeChars = false;
        } else {
            // bad data
            return textList;
        }

        if (rawValue.length() < 2) {
            return textList;
        }
        char delimiter = rawValue.charAt(1);

        String[] pieces = rawValue.split("" + delimiter);
        textList.mTextArray = new String[pieces.length - 1];
        // copy all of the pieces except for piece 0 (escape characters flag) to the text array
        if (pieces.length > 1) {
            System.arraycopy(pieces, 1, textList.mTextArray, 0, pieces.length - 1);
        }
        return textList;
    }

    public @NonNull TextList readDefaultValue() {
        return new TextList();
    }

    public String getValueText(final @NonNull TextList value) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < value.mTextArray.length; i++) {
            if (TextUtils.isEmpty(value.mTextArray[i])) {
                continue;
            }
            if (sb.length() != 0) {
                sb.append(", ");
            }
            // the text may contain a quote, but there isn't really a good way to handle this. we
            // could use angled quotes, but it could contain both. there may be other quote-like
            // characters that we could go through, but all of them could be included, so we can't
            // fully fix this, and the extra logic and slightly weird behavior of changing quote
            // types doesn't seem worth it for this minor visual bug.
            sb.append("\"").append(value.mTextArray[i]).append("\"");
        }
        return sb.toString();
    }

    private static class TextList {
        boolean mEscapeChars;
        @NonNull String[] mTextArray = new String[0];
    }
}
