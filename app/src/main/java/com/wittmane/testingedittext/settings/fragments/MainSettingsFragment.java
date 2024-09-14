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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainSettingsFragment extends PreferenceFragment {
    private static final String TAG = MainSettingsFragment.class.getSimpleName();

    private static final int EXPORT_SETTINGS_FILE = 1;
    private static final int IMPORT_SETTINGS_FILE = 2;
    private static final String SETTINGS_FILE_MIME_TYPE = "application/json";

    private View mView;

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
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(SETTINGS_FILE_MIME_TYPE);
            String instant = new SimpleDateFormat("yyyyMMddhhmmss", Locale.US)
                    .format(Calendar.getInstance().getTime());
            intent.putExtra(Intent.EXTRA_TITLE, "TestingEditTextSettings-" + instant + ".json");
            startActivityForResult(intent, EXPORT_SETTINGS_FILE);
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
        if (resultCode != Activity.RESULT_OK || uri == null) {
            Toast.makeText(getActivity(), getActivity().getString(R.string.failed_to_access_file),
                    Toast.LENGTH_LONG).show();
        }

        if (requestCode == IMPORT_SETTINGS_FILE) {
            if (!importSettings(uri)) {
                Toast.makeText(getActivity(),
                        getActivity().getString(R.string.failed_to_import_settings),
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == EXPORT_SETTINGS_FILE) {
            if (!exportSettings(uri)) {
                Toast.makeText(getActivity(),
                        getActivity().getString(R.string.failed_to_export_settings),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean importSettings(Uri uri) {
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
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file: " + e.getMessage());
            return false;
        }
        return parseJsonSettings(stringBuilder.toString());
    }

    private boolean parseJsonSettings(String rawJson) {
        Log.d(TAG, "Reading raw JSON: " + rawJson);
        try {
            //TODO: (EW) build real settings data
            JSONObject jsonObject = new JSONObject(rawJson);
            Log.d(TAG, "foo: " + jsonObject.getInt("foo"));
            Log.d(TAG, "asdf: " + (jsonObject.has("asdf") ? jsonObject.getInt("asdf") : "null"));
            JSONArray jsonArray = jsonObject.getJSONArray("bar");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObjectNested = jsonArray.getJSONObject(i);
                Log.d(TAG, "baz" + i + ": " + jsonObjectNested.getInt("baz"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse settings file: " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean exportSettings(Uri uri) {
        try (ParcelFileDescriptor pfd =
                     getActivity().getContentResolver(). openFileDescriptor(uri, "w")) {
            if (pfd == null) {
                Log.e(TAG, "Parcel file descriptor is null");
                return false;
            }
            try (FileOutputStream fileOutputStream =
                         new FileOutputStream(pfd.getFileDescriptor())) {
                String data = Settings.getJson();
                Log.d(TAG, "Writing raw JSON: " + data);
                if (data == null) {
                    return false;
                }
                fileOutputStream.write(data.getBytes());
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found for exporting: " + e.getMessage());
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to file: " + e.getMessage());
            return false;
        }
        return true;
    }
}
