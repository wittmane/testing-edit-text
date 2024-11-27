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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Toast;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.function.Consumer;
import com.wittmane.testingedittext.settings.NonEditable.NonEditableFactory;

public class ImportExportSourceDialog extends AlertDialog {
    private static final String TAG = ImportExportSourceDialog.class.getSimpleName();

    private static final boolean SHOW_JSON_ON_SCREEN = false;

    private final boolean mIsImport;
    private final String mExportData;

    public static void promptImport(Context context, Consumer<String> onClickListener) {
        new ImportExportSourceDialog(context, true, null, onClickListener).show();
    }

    public static void promptExport(Context context, String exportData,
                                    Consumer<String> onClickListener) {
        new ImportExportSourceDialog(context, false, exportData, onClickListener).show();
    }

    private ImportExportSourceDialog(Context context, boolean isImport, String exportData,
                                     Consumer<String> onClickListener) {
        super(context);
        mIsImport = isImport;
        mExportData = exportData;
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        setTitle(isImport ? R.string.import_settings : R.string.export_settings);
        setButton(DialogInterface.BUTTON_POSITIVE, context.getText(android.R.string.ok),
                (dialog, which) -> {
                    if (((RadioButton)findViewById(R.id.fileSource)).isChecked()) {
                        onClickListener.accept(null);
                    } else {
                        CharSequence text;
                        if (SHOW_JSON_ON_SCREEN) {
                            text = ((EditText) findViewById(R.id.directText)).getText();
                        } else {
                            if (mIsImport) {
                                // get the settings json string from the clipboard
                                text = getFromClipboard();
                            } else {
                                text = exportData;
                                // copy the settings json string to the clipboard
                                setToClipboard(exportData);
                            }
                        }
                        onClickListener.accept(TextUtils.isEmpty(text) ? "" : text.toString());
                    }
                });
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getText(android.R.string.cancel),
                (OnClickListener) null);
        setView(LayoutInflater.from(context).inflate(R.layout.import_export_source_dialog, null));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RadioButton fileSourceButton = findViewById(R.id.fileSource);
        fileSourceButton.setText(
                mIsImport ? R.string.import_from_file : R.string.export_to_file);
        fileSourceButton.setOnClickListener(v -> findViewById(R.id.sourceGroup).requestFocus());

        RadioButton clipboardSourceButton = findViewById(R.id.clipboardSource);
        clipboardSourceButton.setText(
                mIsImport ? R.string.import_from_clipboard : R.string.export_to_clipboard);

        if (SHOW_JSON_ON_SCREEN) {
            EditText directText = findViewById(R.id.directText);
            if (!mIsImport) {
                directText.setText(mExportData);
                // block editing - only meant for copying
                directText.setEditableFactory(NonEditableFactory.getInstance());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    directText.setShowSoftInputOnFocus(false);
                }
            }
            directText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    // since the user is interacting with the text field, automatically flag that as
                    // the means to import/export
                    clipboardSourceButton.setChecked(true);
                }
            });

            Button textActionButton = findViewById(R.id.textAction);
            textActionButton.setText(
                    mIsImport ? R.string.paste_from_clipboard : R.string.copy_to_clipboard);
            textActionButton.setOnClickListener(v -> {
                // since the user is interacting with the clipboard, automatically flag that as the
                // means to import/export
                clipboardSourceButton.setChecked(true);
                if (mIsImport) {
                    // paste the settings json string from the clipboard to the text field (replace
                    // anything existing)
                    CharSequence clipboardText = getFromClipboard();
                    directText.setText(clipboardText);
                } else {
                    // copy the settings json string from the text field to the clipboard
                    setToClipboard(directText.getText().toString());
                }
            });
        } else {
            LinearLayout screenSourceContent = findViewById(R.id.screenSourceContent);
            screenSourceContent.setVisibility(View.GONE);
        }
    }

    private ClipboardManager getClipboardManager() {
        return (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private String getFromClipboard() {
        ClipboardManager clipboard = getClipboardManager();
        // paste the settings json string from the clipboard to the text field (replace
        // anything existing)
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData != null) {
            ClipData.Item item = clipData.getItemAt(0);
            CharSequence clipboardText = item.getText();
            if (clipboardText != null) {
                return clipboardText.toString();
            }
        }
        return null;
    }

    private void setToClipboard(String text) {
        ClipboardManager clipboard = getClipboardManager();
        ClipData clipData = ClipData.newPlainText("settings json", text);
        clipboard.setPrimaryClip(clipData);
        // more recent versions already show a toast-like notification of copying data to
        // the clipboard, so only show a custom toast for older versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(getContext(),
                    getContext().getString(R.string.settings_copied_to_clipboard),
                    Toast.LENGTH_LONG).show();
        }
    }
}
