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
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
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
import com.wittmane.testingedittext.settings.TextList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

public abstract class TextListPreferenceBase<T> extends DialogPreferenceBase {
    private static final String TAG = TextListPreferenceBase.class.getSimpleName();

    private static final int RESOURCES_ID_NULL =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Resources.ID_NULL : 0;

    private TableLayout mTextTable;
    private CheckBox mEscapeCharactersCheckBox;

    protected final List<Row> mRows = new ArrayList<>();

    public TextListPreferenceBase(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.text_list_dialog);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        final TextList<T> value = getReader().readValue();
        setValueSummary(getValueText(value.getDataArray()));
        return view;
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

        final TextList<T> value = getReader().readValue();

        mRows.clear();
        for (T data : value.getDataArray()) {
            if (isRowEmpty(data)) {
                continue;
            }
            addRow(data);
        }
        addRow(null);

        mEscapeCharactersCheckBox.setChecked(value.escapeChars());
    }

    protected abstract View[] createRowContent(T data);

    protected EditText createEditText(CharSequence text) {
        EditText editText = new EditText(getContext());
        editText.setSingleLine();
        TableRow.LayoutParams editTextLayoutParams = new TableRow.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f);
        editText.setLayoutParams(editTextLayoutParams);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (!TextUtils.isEmpty(text)) {
            editText.setText(text);
        }
        return editText;
    }

    private int getColorForIcon(View view) {
        // based on how EditText gets it normal text color
        Theme theme = getContext().getTheme();
        TypedArray typedArray = theme.obtainStyledAttributes(R.styleable.TextViewAppearance);
        TypedArray appearance;
        int ap = typedArray.getResourceId(
                R.styleable.TextViewAppearance_android_textAppearance, -1);
        typedArray.recycle();
        int color = 0;
        if (ap != -1) {
            appearance = theme.obtainStyledAttributes(ap, R.styleable.TextAppearance);
            if (appearance.hasValue(R.styleable.TextAppearance_android_textColor)) {
                ColorStateList textColor = appearance.getColorStateList(
                        R.styleable.TextAppearance_android_textColor);
                color = textColor.getColorForState(view.getDrawableState(), 0);
            }
            appearance.recycle();
        }
        return color;
    }

    private void addRow(T data) {
        TableRow tableRow = new TableRow(getContext());

        View[] rowContent = createRowContent(data);

        ImageButton removeButton = new ImageButton(getContext());
        removeButton.setImageResource(R.drawable.ic_clear_white_24);
        removeButton.setColorFilter(getColorForIcon(removeButton));
        TypedArray ta = getContext().getTheme()
                .obtainStyledAttributes(new int[] {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                                ? android.R.attr.selectableItemBackgroundBorderless
                                : android.R.attr.selectableItemBackground
                });
        int background = ta.getResourceId(0, RESOURCES_ID_NULL);
        ta.recycle();
        removeButton.setBackgroundResource(background);
        TableRow.LayoutParams removeButtonLayoutParams = new TableRow.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f);
        removeButtonLayoutParams.gravity = Gravity.CENTER;
        removeButton.setLayoutParams(removeButtonLayoutParams);
        removeButton.setPadding(0, 0, 0, 0);
        // don't allow removing the last row
        removeButton.setVisibility(View.INVISIBLE);

        Row row = new Row(tableRow, rowContent, removeButton);

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

        if (mRows.size() > 0) {
            mRows.get(mRows.size() - 1).mRemoveButton.setVisibility(View.VISIBLE);
        }
        for (View view : rowContent) {
            if (view instanceof EditText) {
                EditText editText = (EditText)view;

                editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                                  int after) { }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int start, int before,
                                              int count) {
                        if (charSequence.length() == count && count > 0 && before == 0
                                && mRows.get(mRows.size() - 1).contains(editText)) {
                            // this event is adding text from blank in the last row. now that there
                            // is text in the row, we might want to add a new empty row and allow
                            // this row to be removed since it won't be the last row anymore.
                            if (shouldHaveExtraRow(mRows.get(mRows.size() - 1).mContent)) {
                                removeButton.setVisibility(View.VISIBLE);
                                addRow(null);
                            }

                        } else if (charSequence.length() == 0 && before > 0 && mRows.size() > 1
                                && mRows.get(mRows.size() - 2).contains(editText)) {
                            // this event is clearing text in the second last row. the last row
                            // should be blank or only partially filled out (if all fields are
                            // required), but we don't need multiple blank rows at the end (or a
                            // blank row after a incomplete row), so remove the last row if it's
                            // blank, making this the new last row, which means that the button to
                            // remove the row should be hidden too.

                            // verify the last row is blank to allow removing it
                            for (View view : mRows.get(mRows.size() - 1).mContent) {
                                if (!(view instanceof EditText)) {
                                    continue;
                                }
                                EditText rowEditText = (EditText)view;
                                if (!TextUtils.isEmpty(rowEditText.getText())) {
                                    // the last row isn't empty, so it shouldn't be removed
                                    return;
                                }
                            }

                            boolean shouldHaveExtraRow =
                                    shouldHaveExtraRow(mRows.get(mRows.size() - 2).mContent);

                            if (!shouldHaveExtraRow) {
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
                    public boolean onEditorAction(TextView textView, int actionId,
                                                  KeyEvent keyEvent) {
                        if (actionId == EditorInfo.IME_ACTION_NEXT
                                && textView instanceof EditText
                                && mRows.size() > 0) {

                            Row lastRow = mRows.get(mRows.size() - 1);
                            boolean isLastEditText = false;
                            for (int i = lastRow.mContent.length - 1; i >= 0; i--) {
                                View view = lastRow.mContent[i];
                                if (view instanceof EditText) {
                                    if (view == textView) {
                                        isLastEditText = true;
                                    }
                                    break;
                                }
                            }

                            if (isLastEditText) {
                                // there isn't a good way to refresh the IME's action, so we just
                                // always mark the action to be next since the last field should be
                                // next once text is entered because a new row will be added after
                                // it. just hide the keyboard if next is pressed for the last field
                                // in the last row to make sure the whole dialog is shown so the
                                // user can click the dialog buttons since they could be hidden by
                                // the keyboard if there are a lot of rows.
                                InputMethodManager imm = (InputMethodManager) getContext()
                                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                                }
                                return true;
                            }
                        }
                        return false;
                    }
                });
            }

            tableRow.addView(view);
        }
        tableRow.addView(removeButton);
        mTextTable.addView(tableRow);
        mRows.add(row);
    }

    public static class Row {
        public final TableRow mTableRow;
        public final View[] mContent;
        private final ImageButton mRemoveButton;
        private Row(TableRow tableRow, View[] content, ImageButton removeButton) {
            mTableRow = tableRow;
            mContent = content;
            mRemoveButton = removeButton;
        }

        public boolean contains(View view) {
            for (View v : mContent) {
                if (v == view) {
                    return true;
                }
            }
            return false;
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
            final TextList<T> value = getReader().readDefaultValue();
            setValueSummary(getValueText(value.getDataArray()));
            clearValue();
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            TextList<T> value = new TextList<T>(getData(), mEscapeCharactersCheckBox.isChecked());

            setValueSummary(getValueText(value.getDataArray()));
            writeValue(value);
        }
    }

    protected abstract T[] getData();

    @Override
    public void setKey(String key) {
        super.setKey(key);
        Reader reader = getReader();
        if (reader != null) {
            reader.mKey = key;
        }
    }

    @NonNull
    protected abstract Reader<T> getReader();

    protected static abstract class Reader<T> {
        private final SharedPreferences mPrefs;
        private String mKey;

        protected Reader(SharedPreferences prefs, String key) {
            mPrefs = prefs;
            mKey = key;
        }

        @NonNull
        public TextList<T> readValue() {
            String rawValue = mPrefs.getString(mKey, null);
            if (TextUtils.isEmpty(rawValue)) {
                return readDefaultValue();
            }
            StringBuilder sb = new StringBuilder();
            int delimiterStart = 0;
            for (int i = 0; i < rawValue.length(); i++) {
                char c = rawValue.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.append(c);
                } else {
                    delimiterStart = i;
                    break;
                }
            }
            int delimiterLength;
            try {
                delimiterLength = Integer.parseInt(sb.toString());
            } catch (NumberFormatException e) {
                Log.e(TAG, "Failed to parse delimiter length from preference " + mKey + ": "
                        + rawValue);
                return readDefaultValue();
            }
            if (delimiterStart + delimiterLength > rawValue.length()) {
                Log.e(TAG, "Invalid delimiter length length (" + delimiterLength
                        + ") from preference " + mKey + ": " + rawValue);
                return readDefaultValue();
            }
            String[] pieces = rawValue.split(Pattern.quote(
                    rawValue.substring(delimiterStart, delimiterStart + delimiterLength)));

            boolean escapeChars;
            if (pieces[1].equals("1")) {
                escapeChars = true;
            } else if (pieces[1].equals("0")) {
                escapeChars = false;
            } else {
                Log.e(TAG, "Invalid escape character flag (" + pieces[1] + ") from preference "
                        + mKey + ": " + rawValue);
                escapeChars = false;
            }

            // create a new array excluding pieces 0 (delimiter length) and 1 (special characters
            // flag)
            String[] data = new String[pieces.length - 2];
            if (pieces.length > 2) {
                System.arraycopy(pieces, 2, data, 0, pieces.length - 2);
            }
            return new TextList<T>(buildDataArray(data), escapeChars);
        }

        @NonNull
        private TextList<T> readDefaultValue() {
            return new TextList<T>(getDefaultDataArray(), false);
        }

        @NonNull
        protected abstract T[] buildDataArray(final @NonNull String[] data);

        @NonNull
        protected abstract T[] getDefaultDataArray();
    }

    private void writeValue(final @NonNull TextList<T> value) {
        String[] rowData = flattenDataArray(value.getDataArray());
        String[] dataForSave = new String[rowData.length + 1];
        dataForSave[0] = value.escapeChars() ? "1" : "0";
        System.arraycopy(rowData, 0, dataForSave, 1, rowData.length);

        String delimiter = determineDelimiter(dataForSave);
        StringBuilder sb = new StringBuilder().append(delimiter.length());
        for (String s : dataForSave) {
            sb.append(delimiter).append(s);
        }
        getSharedPreferences().edit().putString(getKey(), sb.toString()).apply();
    }

    private String determineDelimiter(final String[] pieces) {
        int delimiterLength = 1;
        String delimiter = "\0";
        char[] delimiterCharArray;
        HashSet<String> usedText = new HashSet<>();
        while (true) {
            // get a list of all of the text combinations that need to be delimited with a length of
            // the delimiter to be able to exclude them as the delimiter
            for (String piece : pieces) {
                for (int i = 0; i + delimiterLength <= piece.length(); i++) {
                    usedText.add(piece.substring(i, i + delimiterLength));
                }
            }

            // find a delimiter with the current length that isn't in the text that needs to be
            // delimited
            while (true) {
                if (!usedText.contains(delimiter)) {
                    return delimiter;
                }
                delimiterCharArray = delimiter.toCharArray();

                if (!incrementDelimiter(delimiterCharArray)) {
                    // ran out of options - need to increase the length of the delimiter
                    break;
                }
                delimiter = new String(delimiterCharArray);
            }

            // all valid delimiters were found in the text to delimit - try a longer delimiter
            usedText.clear();
            delimiterLength++;
            delimiterCharArray = new char[delimiterLength];
            // all \0 isn't a valid delimiter, so we need to get the next valid delimiter to start
            // with
            incrementDelimiter(delimiterCharArray);
            delimiter = new String(delimiterCharArray);
        }
    }

    private static boolean incrementDelimiter(char[] delimiterCharArray) {
        boolean incremented = false;
        while (true) {
            for (int i = delimiterCharArray.length - 1; i >= 0; i--) {
                if (delimiterCharArray[i] < Character.MAX_VALUE) {
                    delimiterCharArray[i]++;
                    incremented = true;
                    break;
                } else {
                    // overflow - move to next char
                    delimiterCharArray[i] = '\0';
                }
            }
            if (!incremented) {
                // ran out of options - need to increase the length of the delimiter
                return false;
            }
            if (!isValidDelimiter(new String(delimiterCharArray))) {
                // the new delimiter can't be used, so try incrementing more
                incremented = false;
            } else {
                // found the next valid delimiter
                return true;
            }
        }
    }

    private static boolean isValidDelimiter(@NonNull String delimiter) {
        // the first character can't be a number because the first piece will be a number to
        // determine the length of the delimiter when parsing the saved preference
        char firstChar = delimiter.charAt(0);
        if (firstChar >= '0' && firstChar <= '9') {
            return false;
        }

        // a delimiter is invalid if any sequence of characters at the beginning matches a sequence
        // at the end. for example:
        // 111: foo11 111 bar == foo1 111 1bar == foo 111 11bar
        // 12321: foo1232 1231 bar == foo 12321 231bar
        // 12312: foo1232 12321 bar == foo 12321 2321bar
        for (int sequenceLength = 1; sequenceLength < delimiter.length(); sequenceLength++) {
            String start = delimiter.substring(0, sequenceLength);
            String end = delimiter.substring(delimiter.length() - sequenceLength);
            if (start.equals(end)) {
                return false;
            }
        }
        return true;
    }

    protected abstract String[] flattenDataArray(final @NonNull T[] data);

    public void clearValue() {
        getSharedPreferences().edit().remove(getKey()).apply();
    }

    protected abstract String getValueText(final @NonNull T[] value);

    protected abstract boolean isRowEmpty(T rowData);

    protected boolean shouldHaveExtraRow(View[] rowContent) {
        // default implementation just needs any of the text fields to have content
        for (View view : rowContent) {
            if (!(view instanceof EditText)) {
                continue;
            }
            EditText editText = (EditText)view;
            if (!TextUtils.isEmpty(editText.getText())) {
                return true;
            }
        }
        return false;
    }
}
