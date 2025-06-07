/*
 * Copyright (C) 2022-2025 Eli Wittman
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
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
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
import com.wittmane.testingedittext.settings.SharedPreferenceManager;
import com.wittmane.testingedittext.settings.datamanager.ListDataManager;
import com.wittmane.testingedittext.text.inputfilters.PlainTextFilter;
import com.wittmane.testingedittext.text.inputfilters.SingleLineFilter;
import com.wittmane.testingedittext.util.IconUtils;
import com.wittmane.testingedittext.util.ResourceUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Preference for entering a list of items and possibly some extra data not tied to individual items
 * @param <TRowData> Type for the items in the list
 * @param <TFullData> Type for the full data containing the list of items and any extra data
 * @param <TDataManager> Type for reading and writing the preference data
 */
public abstract class EntryListPreference<TRowData, TFullData,
        TDataManager extends ListDataManager<TFullData>>
        extends DialogPreferenceBase {
    private static final String TAG = EntryListPreference.class.getSimpleName();

    private TableLayout mTextTable;

    protected final List<Row> mRows = new ArrayList<>();
    private int mMaxEntries;

    protected TDataManager mDataManager;

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
        mDataManager = createDataManager(getPrefs(), getKey());
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

        TFullData fullData = mDataManager.readValue();
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

    protected EditText createEditText(CharSequence text, boolean includeLeftPadding,
                                      boolean includeRightPadding, boolean isPlainText) {
        EditText editText = new EditText(getContext());
        editText.setSingleLine();
        TableRow.LayoutParams editTextLayoutParams = new TableRow.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        int paddingNegation = ResourceUtils.getDimensionPixels(
                R.attr.edittextUnderlineBackgroundPaddingNegation, getContext());
        // just setting the padding to 0 doesn't change the underline, so set a negative margin to
        // invert the padding to effectively have 0 margin/padding with the underline filling the
        // area
        if (!includeLeftPadding) {
            editTextLayoutParams.leftMargin = paddingNegation;
        }
        if (!includeRightPadding) {
            editTextLayoutParams.rightMargin = paddingNegation;
        }
        editText.setLayoutParams(editTextLayoutParams);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        if (!TextUtils.isEmpty(text)) {
            editText.setText(text);
        }
        List<InputFilter> inputFilters = new ArrayList<>();
        inputFilters.add(new SingleLineFilter());
        if (isPlainText) {
            inputFilters.add(new PlainTextFilter());
        }
        editText.setFilters(inputFilters.toArray(new InputFilter[0]));

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                          int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before,
                                      int count) { }

            @Override
            public void afterTextChanged(Editable editable) {
                updateAcceptButtonState();
            }
        });

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
        // add some space between the row content and the remove button since the row content
        // shouldn't have any margin/padding on the right/left
        removeButtonLayoutParams.leftMargin = (int) ResourceUtils.dpToPx(4, getContext());
        removeButton.setLayoutParams(removeButtonLayoutParams);
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
                // if the extra row wasn't added, it may be relevant to change whether there is
                // visible space for the remove button
                updateLastRowRemoveButtonVisibility();

                // in case invalid data got removed, we should revalidate and potentially enable the
                // accept button again
                updateAcceptButtonState();
            }
        });

        // make sure the previous row's remove button is visible
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
        updateLastRowRemoveButtonVisibility();
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

    protected void updateAcceptButtonState() {
        setAcceptButtonEnabled(isDataValid());
    }

    protected boolean isDataValid() {
        // default doesn't need to validate anything
        return true;
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        super.onClick(dialog, which);
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            clearValue();
            updateValueSummary();
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            if (!isDataValid()) {
                // this shouldn't happen since the button should be disabled
                Log.e(TAG, "Attempting to save invalid data");
                return;
            }
            mDataManager.writeValue(getUIData());
            updateValueSummary();
        }
    }

    /**
     * Get all of the data from the UI that needs to be saved.
     * @return The UI data to save.
     */
    protected abstract TFullData getUIData();

    /**
     * Get the data for the rows from the UI that needs to be saved.
     * @return The UI row data to save.
     */
    protected List<TRowData> getUIRowDataList() {
        List<TRowData> rowData = new ArrayList<>();
        for (Row row : mRows) {
            if (canRemoveAsExtraLine(row.mContent)) {
                continue;
            }
            rowData.add(getUIRowData(row.mContent));
        }
        return rowData;
    }

    /**
     * Build a data object for the row based on the values entered in the UI.
     * @param rowContent The views that make up the row.
     * @return The data that should be saved from the row.
     */
    protected abstract TRowData getUIRowData(View[] rowContent);

    @Override
    public void setKey(String key) {
        super.setKey(key);
        if (mDataManager != null) {
            mDataManager = createDataManager(getPrefs(), getKey());
        }
    }

    protected abstract TDataManager createDataManager(SharedPreferenceManager prefs, String key);

    public void clearValue() {
        getPrefs().remove(getKey());
    }

    protected abstract String getValueText(final @NonNull TRowData[] value);

    @Override
    protected void updateValueSummary() {
        updateValueSummary(getRowData(mDataManager.readValue()));
    }

    /**
     * Get the primary row data from the full data object.
     * @param fullData The full data object to extract row data.
     * @return The row data.
     */
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

    /**
     * Determine if any data that may be entered for the row can be discarded either for managing
     * the extra line or when saving.
     * @param rowContent The views that make up the row.
     * @return Whether the row can be discarded.
     */
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
            // since this row isn't functioning as the extra row anymore, it can be removed, so the
            // button should be visible
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
        // if there is only a single row, either the row isn't populated (enough) to get an extra
        // row or only the one row is allowed. in either case, we don't need to keep blank space for
        // the remove button. if there are multiple rows, we only should show the remove button when
        // the row is populated (or populated enough to normally add an extra row) because that
        // would mean we're at the row limit (we don't need to allow deleting a blank extra row
        // since it would just come back).
        lastRow.mRemoveButton.setVisibility(
                mRows.size() == 1
                        ? View.GONE
                        : (shouldHaveExtraRow(lastRow.mContent)
                                ? View.VISIBLE
                                : View.INVISIBLE));
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

            updateLastRowRemoveButtonVisibility();
        }
    }
}
