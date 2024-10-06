/*
 * Copyright (C) 2022-2024 Eli Wittman
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

package com.wittmane.testingedittext.settings.fragments;

import static com.wittmane.testingedittext.settings.fragments.TestFieldGroupListSettingsFragment.getGroupDisplayName;
import static com.wittmane.testingedittext.settings.fragments.TestFieldGroupSettingsFragment.getFieldDisplayName;
import static com.wittmane.testingedittext.settings.fragments.TestFieldGroupSettingsFragment.showWarningConfirmationDialog;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.wittmane.ThemedActivity;
import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.ImportExportDialog;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.settings.Settings.FieldInfo;
import com.wittmane.testingedittext.settings.Settings.GroupInfo;
import com.wittmane.testingedittext.settings.Settings.ImportFileInfo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainSettingsFragment extends PreferenceFragment {
    private static final String TAG = MainSettingsFragment.class.getSimpleName();

    private static final int EXPORT_SETTINGS_FILE = 1;
    private static final int IMPORT_SETTINGS_FILE = 2;
    // Android's system file picker didn't recognize the JSON MIME type until Android 10 (note
    // alternate file picker apps can be used, and even on older version, some can support it), so
    // to ensure the user is able to select the JSON files in those older versions, we'll have to
    // allow a more broad MIME type.
    private static final String SETTINGS_FILE_MIME_TYPE =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? "application/json"
                    : "application/octet-stream";

    private View mView;
    private List<GroupInfo> mGroupsForExport;
    private boolean mExportFieldDefaults;
    private boolean mExportOtherSettings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_main);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);
        return mView;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.settings, menu);

        ActionBar actionBar = getActivity().getActionBar();
        IconUtils.matchMenuIconColor(mView, menu, actionBar);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_import_settings) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(SETTINGS_FILE_MIME_TYPE);
            startActivityForResult(intent, IMPORT_SETTINGS_FILE);
        } else if (itemId == R.id.action_export_settings) {
            promptExportSettings();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }
        Uri uri = null;
        if (resultData != null) {
            uri = resultData.getData();
        }

        if (requestCode == IMPORT_SETTINGS_FILE) {
            if (resultCode != Activity.RESULT_OK || uri == null) {
                showErrorDialog(R.string.failed_to_import_settings, R.string.failed_to_access_file);
            } else {
                importSettings(uri);
            }
        } else if (requestCode == EXPORT_SETTINGS_FILE) {
            if (resultCode != Activity.RESULT_OK || uri == null) {
                showErrorDialog(R.string.failed_to_export_settings, R.string.failed_to_access_file);
            } else {
                exportSettings(uri);
            }
        }
    }

    private void showErrorDialog(int titleId, int messageId) {
        showErrorDialog(titleId, getActivity().getString(messageId));
    }

    private void showErrorDialog(int titleId, String message) {
        new AlertDialog.Builder(getActivity())
                .setTitle(titleId)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void importSettings(Uri uri) {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream =
                     getActivity().getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found for importing: " + e.getMessage());
            showErrorDialog(R.string.failed_to_import_settings, R.string.failed_to_find_file);
            return;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file: " + e.getMessage());
            showErrorDialog(R.string.failed_to_import_settings, R.string.failed_to_read_file);
            return;
        }
        String rawJson = stringBuilder.toString();
        ImportFileInfo info = Settings.validateJson(rawJson, getActivity());
        if (info.getError() != null) {
            showErrorDialog(R.string.failed_to_import_settings, info.getError());
            return;
        }
        StringBuilder message = new StringBuilder();
        for (String warning : info.getWarnings()) {
            message.append(warning).append("\n");
        }
        if (!info.getUnexpectedProps().isEmpty()) {
            for (String unexpectedProp : info.getUnexpectedProps()) {
                message.append(
                        getActivity().getString(R.string.unexpected_property, unexpectedProp))
                        .append("\n");
            }
        }
        if (message.length() > 0) {
            message.insert(0, "\n\n");
            message.insert(0, getActivity().getString(R.string.confirm_ignore_import_warnings));
            showWarningConfirmationDialog(R.string.import_warnings, message.toString(), () -> {
                ImportExportDialog.promptImport(getActivity(), info, this::importSettings);
            }, getActivity());
        } else {
            ImportExportDialog.promptImport(getActivity(), info, this::importSettings);
        }
    }

    private void importSettings(JSONObject jsonObject, boolean replaceFieldDefaults,
                                boolean replaceFields, List<GroupInfo> groupInfoList,
                                boolean replaceOtherSettings) {
        if (replaceFieldDefaults || replaceFields || replaceOtherSettings) {
            showWarningConfirmationDialog(R.string.import_settings,
                    R.string.replace_existing_settings_confirmation,
                    () -> {
                        importSettingsCore(jsonObject, replaceFieldDefaults, replaceFields,
                                groupInfoList, replaceOtherSettings);
                    },
                    getActivity());
        } else {
            importSettingsCore(jsonObject, replaceFieldDefaults, replaceFields, groupInfoList,
                    replaceOtherSettings);
        }
    }

    //TODO: (EW) name better
    private void importSettingsCore(JSONObject jsonObject, boolean replaceFieldDefaults,
                                    boolean replaceFields, List<GroupInfo> groupInfoList,
                                    boolean replaceOtherSettings) {
        int oldThemeId = Settings.getThemeId(getActivity());

        Settings.importSettings(jsonObject, replaceFieldDefaults, replaceFields, groupInfoList,
                replaceOtherSettings, getActivity());

        // handle theme changes
        int newThemeId = Settings.getThemeId(getActivity());
        ThemedActivity.recreateActivityOnThemeChange(getActivity(),
                oldThemeId, newThemeId);

        Toast.makeText(getActivity(),
                getActivity().getString(R.string.import_settings_successful),
                Toast.LENGTH_LONG).show();
    }

    private void promptExportSettings() {
        mGroupsForExport = new ArrayList<>();
        int groupCount = Settings.getTestFieldGroupCount();
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            GroupInfo groupInfo = new GroupInfo();
            //TODO: (EW) this method probably should be moved to Settings
            groupInfo.mName = getGroupDisplayName(getActivity(), groupIndex);
            groupInfo.mFields = new ArrayList<>();
            int fieldCount = Settings.getTestFieldCount(groupIndex);
            for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                FieldInfo fieldInfo = new FieldInfo();
                //TODO: (EW) this method probably should be moved to Settings
                fieldInfo.mName =
                        getFieldDisplayName(getActivity(), groupIndex, fieldIndex).toString();
                groupInfo.mFields.add(fieldInfo);
            }
            mGroupsForExport.add(groupInfo);
        }
        ImportExportDialog.promptExport(getActivity(), mGroupsForExport,
                (exportFieldDefaults, groupsForExport, exportOtherSettings) -> {
                    mExportFieldDefaults = exportFieldDefaults;
                    mExportOtherSettings = exportOtherSettings;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(SETTINGS_FILE_MIME_TYPE);
                    String instant = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US)
                            .format(Calendar.getInstance().getTime());
                    intent.putExtra(Intent.EXTRA_TITLE, "TestingEditTextSettings-" + instant + ".json");
                    startActivityForResult(intent, EXPORT_SETTINGS_FILE);
                });
    }

    private void exportSettings(Uri uri) {
        try (ParcelFileDescriptor pfd =
                     getActivity().getContentResolver(). openFileDescriptor(uri, "w")) {
            if (pfd == null) {
                Log.e(TAG, "Parcel file descriptor is null");
                showErrorDialog(R.string.failed_to_export_settings,
                        R.string.failed_to_open_file);
                return;
            }
            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(pfd.getFileDescriptor())) {
                String data = Settings.getJson(mExportFieldDefaults, mGroupsForExport,
                        mExportOtherSettings);
                if (data == null) {
                    showErrorDialog(R.string.failed_to_export_settings,
                            R.string.failed_to_generate_export_data);
                    return;
                }
                fileOutputStream.write(data.getBytes());
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found for exporting: " + e.getMessage());
            showErrorDialog(R.string.failed_to_export_settings,
                    R.string.failed_to_find_file);
            return;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to file: " + e.getMessage());
            showErrorDialog(R.string.failed_to_export_settings,
                    R.string.failed_to_write_file);
            return;
        }
        Toast.makeText(getActivity(),
                getActivity().getString(R.string.export_settings_successful),
                Toast.LENGTH_LONG).show();
    }
}
