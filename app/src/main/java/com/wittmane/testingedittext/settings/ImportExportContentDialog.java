/*
 * Copyright (C) 2024 Eli Wittman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wittmane.testingedittext.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings.FieldInfo;
import com.wittmane.testingedittext.settings.Settings.GroupInfo;
import com.wittmane.testingedittext.settings.Settings.ImportFileInfo;
import com.wittmane.testingedittext.util.IterableUtils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ImportExportContentDialog extends AlertDialog {
    private static final String TAG = ImportExportContentDialog.class.getSimpleName();

    private static final int IMPORT_EXPORT_FIELDS_ALL = 0;
    private static final int IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS = 1;
    private static final int IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS = 2;

    private final boolean mIncludeFieldDefaults;
    private final @Nullable List<GroupInfo> mGroups;
    private final boolean mIncludeOtherSettings;
    private final boolean mIsImport;

    public static void promptImport(Context context, ImportFileInfo info, Importer importer) {
        new ImportExportContentDialog(context, info.isFieldDefaultsIncluded(), info.getGroups(),
                info.isOtherSettingsIncluded(), info.getJsonObject(), importer, null).show();
    }

    public static void promptExport(Context context, List<GroupInfo> groupInfoList,
                                    Exporter exporter) {
        new ImportExportContentDialog(context, true, groupInfoList, true, null, null, exporter)
                .show();
    }

    private ImportExportContentDialog(Context context, boolean includeFieldDefaults,
                                      @Nullable List<GroupInfo> groupInfoList,
                                      boolean includeOtherSettings, JSONObject jsonObject,
                                      Importer importer, Exporter exporter) {
        super(context);
        mIsImport = importer != null;
        mIncludeFieldDefaults = includeFieldDefaults;
        mGroups = groupInfoList;
        // make sure all groups and fields are selected to be included by default
        for (GroupInfo group : nonNull(mGroups)) {
            group.mInclude = true;
            for (FieldInfo field : group.mFields) {
                field.mInclude = true;
            }
        }
        mIncludeOtherSettings = includeOtherSettings;
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        setTitle(mIsImport ? R.string.import_settings : R.string.export_settings);
        setButton(DialogInterface.BUTTON_POSITIVE, context.getText(android.R.string.ok),
                (dialog, which) -> {
                    CheckBox fieldDefaultsCheckbox = findViewById(R.id.fieldDefaults);
                    CheckBox testFieldsCheckbox = findViewById(R.id.testFields);
                    Spinner testFieldOptionSpinner =
                            findViewById(R.id.testFieldOption);
                    CheckBox embedFieldDefaultsCheckbox = findViewById(R.id.embedFieldDefaults);
                    int testFieldOption =
                            ((SpinnerEntry) testFieldOptionSpinner.getSelectedItem())
                                    .getValue();
                    if (!testFieldsCheckbox.isChecked()) {
                        // make sure no groups or fields are selected to be included
                        for (GroupInfo group : nonNull(mGroups)) {
                            group.mInclude = false;
                            for (FieldInfo field : group.mFields) {
                                field.mInclude = false;
                            }
                        }
                    } if (testFieldOption == IMPORT_EXPORT_FIELDS_ALL) {
                        // make sure all groups and fields are selected to be included
                        for (GroupInfo group : nonNull(mGroups)) {
                            group.mInclude = true;
                            for (FieldInfo field : group.mFields) {
                                field.mInclude = true;
                            }
                        }
                    } else if (testFieldOption == IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS) {
                        // make sure all fields in the selected groups (and only those fields)
                        // are selected to be included
                        for (GroupInfo group : nonNull(mGroups)) {
                            for (FieldInfo field : group.mFields) {
                                field.mInclude = group.mInclude;
                            }
                        }
                    } else if (testFieldOption == IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS) {
                        // make sure no groups are selected to be included
                        for (GroupInfo group : nonNull(mGroups)) {
                            group.mInclude = false;
                        }
                    }
                    CheckBox otherSettingsCheckbox = findViewById(R.id.otherSettings);
                    boolean embedDefaults = !fieldDefaultsCheckbox.isChecked()
                            && embedFieldDefaultsCheckbox.isChecked();
                    if (importer != null) {
                        importer.importSettings(jsonObject,
                                fieldDefaultsCheckbox.isChecked(),
                                testFieldsCheckbox.isChecked() && testFieldOption == 0,
                                mGroups,
                                embedDefaults,
                                otherSettingsCheckbox.isChecked());
                    } else {
                        exporter.exportSettings(fieldDefaultsCheckbox.isChecked(),
                                testFieldsCheckbox.isChecked() ? mGroups : null,
                                embedDefaults,
                                otherSettingsCheckbox.isChecked());
                    }
                });
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getText(android.R.string.cancel),
                (OnClickListener) null);
        setView(LayoutInflater.from(context).inflate(R.layout.import_export_content_dialog, null));
    }

    private static <T> List<T> nonNull(@Nullable List<T> list) {
        return list == null ? new ArrayList<>(0) : list;
    }

    private static void setSpinnerValue(Spinner spinner, int value) {
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
            SpinnerEntry item = (SpinnerEntry) spinner.getAdapter().getItem(i);
            if (item.getValue() == value) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CheckBox fieldDefaultsCheckbox = findViewById(R.id.fieldDefaults);
        CheckBox testFieldsCheckbox = findViewById(R.id.testFields);
        Spinner testFieldOptionSpinner = findViewById(R.id.testFieldOption);
        CheckBox embedFieldDefaultsCheckbox = findViewById(R.id.embedFieldDefaults);
        LinearLayout testFieldDynamicDetails = findViewById(R.id.testFieldDynamicDetails);
        CheckBox otherSettingsCheckbox = findViewById(R.id.otherSettings);

        fieldDefaultsCheckbox.setChecked(mIncludeFieldDefaults);
        fieldDefaultsCheckbox.setEnabled(mIncludeFieldDefaults);
        fieldDefaultsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateEmbedFieldDefaultsEnabled();
            updateAcceptButtonState();
        });

        testFieldsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            testFieldOptionSpinner.setEnabled(isChecked);
            for (int i = 0; i < testFieldDynamicDetails.getChildCount(); i++) {
                testFieldDynamicDetails.getChildAt(i).setEnabled(isChecked);
            }
            updateEmbedFieldDefaultsEnabled();
            updateAcceptButtonState();
        });

        final List<SpinnerEntry> spinnerEntries = new ArrayList<>();
        spinnerEntries.add(new SpinnerEntry(IMPORT_EXPORT_FIELDS_ALL,
                getContext().getString(mIsImport
                        ? R.string.import_test_field_option_replace_all
                        : R.string.export_test_field_option_all)));
        spinnerEntries.add(new SpinnerEntry(IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS,
                getContext().getString(mIsImport
                        ? R.string.import_test_field_option_add_specific_groups
                        : R.string.export_test_field_option_specific_groups)));
        spinnerEntries.add(new SpinnerEntry(IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS,
                getContext().getString(mIsImport
                        ? R.string.import_test_field_option_add_specific_fields
                        : R.string.export_test_field_option_specific_fields)));
        ArrayAdapter<SpinnerEntry> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, spinnerEntries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        testFieldOptionSpinner.setAdapter(adapter);

        if (mGroups == null) {
            testFieldsCheckbox.setChecked(false);
            testFieldsCheckbox.setEnabled(false);
            testFieldOptionSpinner.setEnabled(false);
        } else if (!mIncludeFieldDefaults || !mIncludeOtherSettings
                || (mGroups.size() == 1 && mGroups.get(0).mIsAdHoc)) {
            // test fields should default to replace all only when all settings were exported since
            // that allow a complete replacement of all settings. since not all of the settings can
            // be replaced with what is in this import file, the user likely will want to keep the
            // existing fields, so update the default to add the groups instead. if the only group
            // is flagged as being ad hoc, individual fields were exported (not whole groups), so
            // the import should default to match.
            setSpinnerValue(testFieldOptionSpinner,
                    mGroups.size() == 1 && mGroups.get(0).mIsAdHoc
                            ? IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS
                            : IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS);
        }

        testFieldOptionSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position,
                                       long id) {
                SpinnerEntry item = (SpinnerEntry) testFieldOptionSpinner.getAdapter()
                        .getItem(position);
                int option = item.getValue();
                testFieldDynamicDetails.removeAllViews();
                if (option == IMPORT_EXPORT_FIELDS_ALL) {
                    // don't need to show individual groups/fields
                    return;
                }

                List<CheckBox> checkBoxes = new ArrayList<>();

                Button selectAllButton = IconUtils.createButton(getContext(),
                        R.drawable.baseline_select_all_white_24, R.string.select_all);
                // start hidden because all checkboxes start checked
                selectAllButton.setVisibility(View.GONE);
                selectAllButton.setOnClickListener(v -> {
                    // select all
                    for (CheckBox checkBox : checkBoxes) {
                        checkBox.setChecked(true);
                    }
                });
                testFieldDynamicDetails.addView(selectAllButton);

                Button deselectAllButton = IconUtils.createButton(getContext(),
                        R.drawable.baseline_deselect_white_24, R.string.deselect_all);
                deselectAllButton.setOnClickListener(v -> {
                    // deselect all
                    for (CheckBox checkBox : checkBoxes) {
                        checkBox.setChecked(false);
                    }
                });
                testFieldDynamicDetails.addView(deselectAllButton);

                for (GroupInfo group : nonNull(mGroups)) {
                    if (option == IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS) {
                        CheckBox groupCheckbox = new CheckBox(getContext());
                        groupCheckbox.setText(group.mName);
                        groupCheckbox.setChecked(group.mInclude);
                        groupCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            group.mInclude = isChecked;
                            if (checkBoxes.size() > 1) {
                                toggleButtons(selectAllButton, deselectAllButton,
                                        !areAllChecked(checkBoxes));
                            }
                            updateAcceptButtonState();
                        });

                        testFieldDynamicDetails.addView(groupCheckbox);
                        checkBoxes.add(groupCheckbox);
                    } else if (option == IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS) {
                        // skip any groups that don't have any fields
                        if (group.mFields == null || group.mFields.isEmpty()) {
                            continue;
                        }

                        if (mGroups.size() > 1) {
                            // show a label for the groups for organization
                            TextView groupLabel = new TextView(getContext());
                            groupLabel.setText(group.mName);

                            testFieldDynamicDetails.addView(groupLabel);
                        }

                        for (FieldInfo field : group.mFields) {
                            CheckBox fieldCheckbox = new CheckBox(getContext());
                            fieldCheckbox.setText(field.mName);
                            fieldCheckbox.setChecked(field.mInclude);
                            fieldCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                field.mInclude = isChecked;
                                if (checkBoxes.size() > 1) {
                                    toggleButtons(selectAllButton, deselectAllButton,
                                            !areAllChecked(checkBoxes));
                                }
                                updateAcceptButtonState();
                            });

                            testFieldDynamicDetails.addView(fieldCheckbox);
                            checkBoxes.add(fieldCheckbox);
                        }
                    }
                }

                if (checkBoxes.size() <= 1) {
                    deselectAllButton.setVisibility(View.GONE);
                }

                updateAcceptButtonState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        otherSettingsCheckbox.setChecked(mIncludeOtherSettings);
        otherSettingsCheckbox.setEnabled(mIncludeOtherSettings);
        otherSettingsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateAcceptButtonState();
        });
    }

    private static boolean areAllChecked(Iterable<CheckBox> checkBoxes) {
        return IterableUtils.all(checkBoxes, checkBox -> checkBox.isChecked());
    }

    private static void toggleButtons(Button buttonA, Button buttonB, boolean showA) {
        if (showA) {
            buttonA.setVisibility(View.VISIBLE);
            buttonB.setVisibility(View.GONE);
        } else {
            buttonA.setVisibility(View.GONE);
            buttonB.setVisibility(View.VISIBLE);
        }
    }

    private void updateEmbedFieldDefaultsEnabled() {
        CheckBox fieldDefaultsCheckbox = findViewById(R.id.fieldDefaults);
        CheckBox testFieldsCheckbox = findViewById(R.id.testFields);
        CheckBox embedFieldDefaultsCheckbox = findViewById(R.id.embedFieldDefaults);
        // if the defaults are being imported or exported, there's no point in embedding them
        // since handling defaults will be able to work normally, so disable the option. also,
        // if test fields aren't being exported, this option is irrelevant, so disable it.
        embedFieldDefaultsCheckbox.setEnabled(!fieldDefaultsCheckbox.isChecked()
                && testFieldsCheckbox.isChecked());
    }

    private void updateAcceptButtonState() {
        Button acceptButton = getButton(AlertDialog.BUTTON_POSITIVE);
        if (acceptButton == null) {
            return;
        }
        acceptButton.setEnabled(hasDataSelected());
    }

    private boolean hasDataSelected() {
        CheckBox fieldDefaultsCheckbox = findViewById(R.id.fieldDefaults);
        if (fieldDefaultsCheckbox.isChecked()) {
            return true;
        }
        CheckBox testFieldsCheckbox = findViewById(R.id.testFields);
        if (testFieldsCheckbox.isChecked()) {
            Spinner testFieldOptionSpinner = findViewById(R.id.testFieldOption);
            int testFieldOption =
                    ((SpinnerEntry) testFieldOptionSpinner.getSelectedItem())
                            .getValue();
            if (testFieldOption == IMPORT_EXPORT_FIELDS_ALL) {
                return true;
            }
            if (testFieldOption == IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS
                    && IterableUtils.any(mGroups, group -> group.mInclude)) {
                return true;
            }
            if (testFieldOption == IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS
                    && IterableUtils.any(mGroups, group -> IterableUtils.any(group.mFields,
                    field -> field.mInclude))) {
                return true;
            }
        }
        CheckBox otherSettingsCheckbox = findViewById(R.id.otherSettings);
        if (otherSettingsCheckbox.isChecked()) {
            return true;
        }
        return false;
    }

    private static class SpinnerEntry {
        private final int mValue;
        private final @NonNull String mDisplay;

        public SpinnerEntry(int value, @NonNull String display) {
            mValue = value;
            mDisplay = display;
        }

        public int getValue() {
            return mValue;
        }

        @Override
        public @NonNull String toString() {
            return mDisplay;
        }
    }

    public interface Importer {
        void importSettings(JSONObject jsonObject, boolean replaceFieldDefaults,
                            boolean replaceFields, List<GroupInfo> groupInfoList,
                            boolean embedFieldDefaults, boolean replaceOtherSettings);
    }

    public interface Exporter {
        void exportSettings(boolean exportFieldDefaults, List<GroupInfo> groupInfoList,
                            boolean embedFieldDefaults, boolean exportOtherSettings);
    }
}
