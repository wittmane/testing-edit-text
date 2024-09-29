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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.Settings.FieldInfo;
import com.wittmane.testingedittext.settings.Settings.GroupInfo;
import com.wittmane.testingedittext.settings.Settings.ImportFileInfo;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

//TODO: (EW) add validation for when nothing is selected to block the accept button
public class ImportExportDialog extends AlertDialog {

    private static final int IMPORT_EXPORT_FIELDS_ALL = 0;
    private static final int IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS = 1;
    private static final int IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS = 2;

    private final List<GroupInfo> mGroups;
    private final boolean mIsImport;

    public static void promptImport(Context context, ImportFileInfo info, Importer importer) {
        new ImportExportDialog(context, info.getGroups(), info.getJsonObject(), importer,
                null).show();
    }

    public static void promptExport(Context context, List<GroupInfo> groupInfoList,
                                    Exporter exporter) {
        new ImportExportDialog(context, groupInfoList, null, null, exporter)
                .show();
    }

    private ImportExportDialog(Context context, List<GroupInfo> groupInfoList,
                               JSONObject jsonObject, Importer importer,
                               Exporter exporter) {
        super(context);
        mIsImport = importer != null;
        mGroups = groupInfoList;
        // make sure all groups and fields are selected to be included by default
        for (GroupInfo group : mGroups) {
            group.mInclude = true;
            for (FieldInfo field : group.mFields) {
                field.mInclude = true;
            }
        }
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        setTitle(mIsImport ? R.string.import_settings : R.string.export_settings);
        setButton(DialogInterface.BUTTON_POSITIVE, context.getText(android.R.string.ok),
                (dialog, which) -> {
                    CheckBox fieldDefaultsCheckbox = findViewById(R.id.fieldDefaults);
                    CheckBox testFieldsCheckbox = findViewById(R.id.testFields);
                    Spinner testFieldOptionSpinner =
                            findViewById(R.id.testFieldOption);
                    int testFieldOption =
                            ((SpinnerEntry) testFieldOptionSpinner.getSelectedItem())
                                    .getValue();
                    if (!testFieldsCheckbox.isChecked()) {
                        // make sure no groups or fields are selected to be included
                        for (GroupInfo group : mGroups) {
                            group.mInclude = false;
                            for (FieldInfo field : group.mFields) {
                                field.mInclude = false;
                            }
                        }
                    } if (testFieldOption == IMPORT_EXPORT_FIELDS_ALL) {
                        // make sure all groups and fields are selected to be included
                        for (GroupInfo group : mGroups) {
                            group.mInclude = true;
                            for (FieldInfo field : group.mFields) {
                                field.mInclude = true;
                            }
                        }
                    } else if (testFieldOption == IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS) {
                        // make sure all fields in the selected groups (and only those fields)
                        // are selected to be included
                        for (GroupInfo group : mGroups) {
                            for (FieldInfo field : group.mFields) {
                                field.mInclude = group.mInclude;
                            }
                        }
                    } else if (testFieldOption == IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS) {
                        // make sure no groups are selected to be included
                        for (GroupInfo group : mGroups) {
                            group.mInclude = false;
                        }
                    }
                    CheckBox otherSettingsCheckbox = findViewById(R.id.otherSettings);
                    if (importer != null) {
                        importer.importSettings(jsonObject,
                                fieldDefaultsCheckbox.isChecked(),
                                testFieldsCheckbox.isChecked() && testFieldOption == 0,
                                mGroups,
                                otherSettingsCheckbox.isChecked());
                    } else {
                        exporter.exportSettings(fieldDefaultsCheckbox.isChecked(),
                                testFieldsCheckbox.isChecked() ? mGroups : null,
                                otherSettingsCheckbox.isChecked());
                    }
                });
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getText(android.R.string.cancel),
                (OnClickListener) null);
        setView(LayoutInflater.from(context).inflate(R.layout.import_export_dialog, null));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CheckBox testFieldsCheckbox = findViewById(R.id.testFields);
        Spinner testFieldOptionSpinner = findViewById(R.id.testFieldOption);
        LinearLayout testFieldDynamicDetails = findViewById(R.id.testFieldDynamicDetails);

        testFieldsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            testFieldOptionSpinner.setEnabled(isChecked);
            for (int i = 0; i < testFieldDynamicDetails.getChildCount(); i++) {
                testFieldDynamicDetails.getChildAt(i).setEnabled(isChecked);
            }
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
                for (GroupInfo group : mGroups) {
                    if (option == IMPORT_EXPORT_FIELDS_SPECIFIC_GROUPS) {
                        CheckBox groupCheckbox = new CheckBox(getContext());
                        groupCheckbox.setText(group.mName);
                        groupCheckbox.setChecked(group.mInclude);
                        groupCheckbox.setOnCheckedChangeListener(
                                (buttonView, isChecked) -> group.mInclude = isChecked);

                        testFieldDynamicDetails.addView(groupCheckbox);
                    } else if (option == IMPORT_EXPORT_FIELDS_SPECIFIC_FIELDS) {
                        // skip any groups that don't have any fields
                        if (group.mFields == null || group.mFields.isEmpty()) {
                            continue;
                        }

                        // show a label for the groups for organization
                        TextView groupLabel = new TextView(getContext());
                        groupLabel.setText(group.mName);

                        testFieldDynamicDetails.addView(groupLabel);

                        for (FieldInfo field : group.mFields) {
                            CheckBox fieldCheckbox = new CheckBox(getContext());
                            fieldCheckbox.setText(field.mName);
                            fieldCheckbox.setChecked(field.mInclude);
                            fieldCheckbox.setOnCheckedChangeListener(
                                    (buttonView, isChecked) -> field.mInclude = isChecked);

                            testFieldDynamicDetails.addView(fieldCheckbox);
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
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
                            boolean replaceOtherSettings);
    }

    public interface Exporter {
        void exportSettings(boolean exportFieldDefaults, List<GroupInfo> groupInfoList,
                            boolean exportOtherSettings);
    }
}
