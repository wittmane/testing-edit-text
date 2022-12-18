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
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.StringArraySerializer;
import com.wittmane.testingedittext.settings.StringArraySerializer.InvalidSerializedDataException;
import com.wittmane.testingedittext.settings.preferences.EntryListPreference.ReaderBase;

import java.util.ArrayList;
import java.util.List;

public abstract class EntryListPreference<TRowData, TFullData,
        TReader extends ReaderBase<TFullData>>
        extends DialogPreferenceBase {
    private static final String TAG = EntryListPreference.class.getSimpleName();

    private TableLayout mTextTable;

    protected final List<Row> mRows = new ArrayList<>();
    private int mMaxEntries;

    protected TReader mReader;

    public EntryListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.EntryListPreference, 0, 0);
        setMaxEntries(a.getInt(R.styleable.EntryListPreference_maxEntries, -1));
        a.recycle();

        setDialogLayoutResource(R.layout.entry_list_dialog);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        mReader = createReader(getPrefs(), getKey());
    }

    @Override
    protected View onCreateDialogView() {
        final View view = super.onCreateDialogView();
        mTextTable = view.findViewById(R.id.text_table);
        return view;
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);

        TFullData fullData = mReader.readValue();
        mRows.clear();
        for (TRowData rowData : getRowData(fullData)) {
            if (isRowEmpty(rowData)) {
                continue;
            }
            addRow(rowData);
        }
        addRow(null);
        updateLastRowRemoveButtonVisibility();

        setExtraDataUI(fullData);
    }

    protected abstract View[] createRowContent(TRowData data, TableRow tableRow);

    protected abstract void setExtraDataUI(TFullData data);

    protected EditText createEditText(CharSequence text) {
        EditText editText = new EditText(getContext());
        editText.setSingleLine();
        TableRow.LayoutParams editTextLayoutParams = new TableRow.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        editText.setLayoutParams(editTextLayoutParams);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (!TextUtils.isEmpty(text)) {
            editText.setText(text);
        }
        return editText;
    }

    private void addRow(TRowData data) {
        if (mMaxEntries > 0 && mRows.size() >= mMaxEntries) {
            // don't add more rows than the limit
            return;
        }

        TableRow tableRow = new TableRow(getContext());

        View[] rowContent = createRowContent(data, tableRow);


        LinearLayout buttonWrapperLayout = new LinearLayout(getContext());
        buttonWrapperLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonWrapperLayout.setLayoutParams(new TableRow.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        // adding a dummy edit text to align the button
        EditText dummyEditText = new EditText(getContext());
        dummyEditText.setLayoutParams(new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT));
        dummyEditText.setFocusable(false);
        dummyEditText.setVisibility(View.INVISIBLE);
        dummyEditText.setEnabled(false);
        buttonWrapperLayout.addView(dummyEditText);

        ImageButton removeButton = IconUtils.createImageButton(getContext(),
                R.drawable.ic_clear_white_24);
        TableRow.LayoutParams removeButtonLayoutParams = new TableRow.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0f);
        removeButtonLayoutParams.gravity = Gravity.CENTER;
        removeButton.setLayoutParams(removeButtonLayoutParams);
        // don't allow removing the last row
        removeButton.setVisibility(View.INVISIBLE);
        buttonWrapperLayout.addView(removeButton);

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
                // see if an extra row should be added in case it was skipped before due to the max
                // row limit since there is room now
                addExtraRowIfNecessary();
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
                        if (charSequence.length() == count && count > 0 && before == 0) {
                            // this event is for adding text from blank
                            Row lastRow = mRows.get(mRows.size() - 1);
                            if (lastRow.contains(editText)) {
                                // now that there is text in the last row, we might need to add a
                                // new empty row
                                addExtraRowIfNecessary();
                                // if the extra row wasn't added due to a max row limit, it still
                                // may be relevant to add the remove button
                                updateLastRowRemoveButtonVisibility();
                            }
                        } else if (charSequence.length() == 0 && before > 0) {
                            // this event is for clearing text
                            Row lastRow = mRows.get(mRows.size() - 1);
                            if (lastRow.contains(editText)) {
                                // a populate row may be at the end if there is a row limit, and
                                // since content was cleared, it may no longer be relevant to show
                                // the remove button
                                updateLastRowRemoveButtonVisibility();
                            } else if (mRows.size() > 1) {
                                Row secondLastRow = mRows.get(mRows.size() - 2);
                                if (secondLastRow.contains(editText)) {
                                    // we don't need multiple blank rows at the end (or a blank row
                                    // after a incomplete row), so remove the last row if that's the
                                    // case
                                    removeDuplicateExtraRowIfNecessary();
                                }
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
        }
        tableRow.addView(buttonWrapperLayout);
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
            clearValue();
            updateValueSummary();
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            writeValue(getUIData());
            updateValueSummary();
        }
    }

    protected abstract TFullData getUIData();

    @Override
    public void setKey(String key) {
        super.setKey(key);
        if (mReader != null) {
            mReader.mKey = key;
        }
    }

    protected abstract TReader createReader(SharedPreferences prefs, String key);

    protected static abstract class ReaderBase<T> {
        protected final SharedPreferences mPrefs;
        protected String mKey;

        protected ReaderBase(SharedPreferences prefs, String key) {
            mPrefs = prefs;
            mKey = key;
        }

        protected abstract int getExtraDataLength();

        @NonNull
        public T readValue() {
            String rawValue = mPrefs.getString(mKey, null);
            String[] pieces;
            try {
                pieces = StringArraySerializer.deserialize(rawValue);
            } catch (InvalidSerializedDataException e) {
                Log.e(TAG, "Failed to parse preference " + mKey + " (\"" + rawValue + "\"):"
                        + e.getMessage());
                pieces = null;
            }
            if (pieces == null) {
                return readDefaultValue();
            }

            String[] extraData = new String[getExtraDataLength()];
            if (extraData.length > 0) {
                System.arraycopy(pieces, 0, extraData, 0, extraData.length);
            }

            // create a new array excluding any extra data
            String[] rowData = new String[pieces.length - extraData.length];
            if (pieces.length > extraData.length) {
                System.arraycopy(pieces, extraData.length, rowData, 0,
                        pieces.length - extraData.length);
            }

            return buildFullData(rowData, extraData);
        }

        protected abstract T buildFullData(String[] rowData, String[] extraData);

        @NonNull
        protected abstract T readDefaultValue();
    }

    private void writeValue(final @NonNull TFullData value) {
        String[] rowData = flattenDataArray(getRowData(value));
        String[] extraData = getFlattenedExtraData(value);
        String[] dataForSave = new String[rowData.length + extraData.length];
        System.arraycopy(extraData, 0, dataForSave, 0, extraData.length);
        System.arraycopy(rowData, 0, dataForSave, extraData.length, rowData.length);

        getPrefs().setString(getKey(), StringArraySerializer.serialize(dataForSave));
    }

    @NonNull
    protected abstract String[] flattenDataArray(final @NonNull TRowData[] data);

    @NonNull
    protected abstract String[] getFlattenedExtraData(final TFullData fullData);

    public void clearValue() {
        getPrefs().remove(getKey());
    }

    protected abstract String getValueText(final @NonNull TRowData[] value);

    @Override
    protected void updateValueSummary() {
        updateValueSummary(getRowData(mReader.readValue()));
    }

    protected abstract TRowData[] getRowData(final TFullData fullData);

    private void updateValueSummary(final TRowData[] value) {
        setValueSummary(getValueText(value));
    }

    protected abstract boolean isRowEmpty(TRowData rowData);

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

    protected boolean canRemoveAsExtraLine(View[] rowContent) {
        // default implementation just makes sure no populated text fields are thrown away
        for (View view : rowContent) {
            if (!(view instanceof EditText)) {
                continue;
            }
            EditText rowEditText = (EditText)view;
            if (!TextUtils.isEmpty(rowEditText.getText())) {
                return false;
            }
        }
        return true;
    }

    public int getMaxEntries() {
        return mMaxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        mMaxEntries = maxEntries;
        if (mRows.size() < 1) {
            // UI isn't built yet, so there isn't anything else to do
            return;
        }
        if (mMaxEntries > 0) {
            while (mRows.size() > mMaxEntries) {
                mTextTable.removeView(mRows.get(mRows.size() - 1).mTableRow);
                mRows.remove(mRows.size() - 1);
            }
        }
        addExtraRowIfNecessary();
        updateLastRowRemoveButtonVisibility();
    }

    protected void addExtraRowIfNecessary() {
        if (mRows.size() < 1) {
            // UI isn't built yet, so there isn't anything else to do
            return;
        }
        if (mMaxEntries > 0 &&  mRows.size() >= mMaxEntries) {
            // no room for another row
            return;
        }
        Row lastRow = mRows.get(mRows.size() - 1);
        if (shouldHaveExtraRow(lastRow.mContent)) {
            // since this row isn't functioning as the extra row anymore, it can be removed
            lastRow.mRemoveButton.setVisibility(View.VISIBLE);

            addRow(null);
        }
    }

    protected void updateLastRowRemoveButtonVisibility() {
        if (mRows.size() < 1) {
            // UI isn't built yet, so there isn't anything else to do
            return;
        }
        Row lastRow = mRows.get(mRows.size() - 1);
        lastRow.mRemoveButton.setVisibility(
                shouldHaveExtraRow(lastRow.mContent) && mRows.size() > 1
                        ? View.VISIBLE : View.INVISIBLE);
    }

    protected void removeDuplicateExtraRowIfNecessary() {
        if (mRows.size() < 2) {
            // no duplicate extra row if there aren't multiple rows
            return;
        }
        Row secondLastRow = mRows.get(mRows.size() - 2);
        if (shouldHaveExtraRow(secondLastRow.mContent)) {
            // content from second last row expects an extra row, so that can't be used as the extra
            // row
            return;
        }
        Row lastRow = mRows.get(mRows.size() - 1);
        // make sure the current extra row doesn't have anything relevant to need to keep it
        if (canRemoveAsExtraLine(lastRow.mContent)) {
            mTextTable.removeView(lastRow.mTableRow);
            mRows.remove(lastRow);

            // this is the new extra row, so it shouldn't be able to be removed
            secondLastRow.mRemoveButton.setVisibility(View.INVISIBLE);
        }
    }
}
