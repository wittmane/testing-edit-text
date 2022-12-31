/*
 * Copyright (C) 2022 Eli Wittman
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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ListView;
import android.widget.TextView;

import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.DraggableListAdapter;
import com.wittmane.testingedittext.settings.IconUtils;
import com.wittmane.testingedittext.settings.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.wittmane.testingedittext.settings.fragments.TestFieldSettingsFragment.FIELD_INDEX_BUNDLE_KEY;

public class TestFieldListSettingsFragment extends PreferenceFragment {
    private View mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference_screen_empty);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        mView = super.onCreateView(inflater, container, savedInstanceState);
        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        buildContent();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.test_field_list, menu);

        ActionBar actionBar = getActivity().getActionBar();
        MenuItem addFieldMenuItem = menu.findItem(R.id.action_add_field);
        IconUtils.matchMenuIconColor(mView, addFieldMenuItem, actionBar);
        MenuItem reorderFieldsMenuItem = menu.findItem(R.id.action_reorder_fields);
        IconUtils.matchMenuIconColor(mView, reorderFieldsMenuItem, actionBar);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_add_field) {
            // add a preference for a new field
            Settings.addTestField();
            final PreferenceGroup group = getPreferenceScreen();
            Preference newPref =
                    new SingleFieldPreference(getActivity(), group.getPreferenceCount());
            group.addPreference(newPref);
            // launch sub setting screen for the new field preference
            ((OnPreferenceStartFragmentCallback)getActivity()).onPreferenceStartFragment(
                    this, newPref);
        } else if (itemId == R.id.action_reorder_fields) {
            ListView content = new ListView(getActivity());
            DraggableListAdapter<TestField> adapter = new DraggableListAdapter<>(getActivity(),
                    new DraggableListAdapter.ListItemBuilder<TestField>() {
                        @Override
                        public void populateView(View view, TestField item) {
                            TextView titleView = view.findViewById(R.id.title);
                            titleView.setText(item.mTitle);
                        }
                    });
            for (int i = 0; i < Settings.getTestFieldCount(); i++) {
                adapter.add(new TestField(getActivity(), i));
            }
            content.setAdapter(adapter);

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reorder_fields)
                    .setView(content)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    int[] testFields = new int[adapter.getCount()];
                                    for (int i = 0; i < adapter.getCount(); i++) {
                                        testFields[i] = adapter.getItem(i).getId();
                                    }
                                    Settings.setTestFieldIds(testFields);
                                    buildContent();
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                }
                            })
                    .create();
            dialog.show();
        }
        return super.onOptionsItemSelected(item);
    }

    private static class TestField {
        private CharSequence mTitle;
        private int mId;

        public TestField(Context context, int index) {
            mTitle = getFieldTitle(context, index);
            mId = Settings.getTestFieldId(index);
        }

        public int getId() {
            return mId;
        }

        public CharSequence getTitle() {
            return mTitle;
        }
    }

    /**
     * Build the preferences and them to this settings screen.
     */
    private void buildContent() {
        final Context context = getActivity();
        final PreferenceGroup group = getPreferenceScreen();
        group.removeAll();

        for (int i = 0; i < Settings.getTestFieldCount(); i++) {
            group.addPreference(new SingleFieldPreference(context, i));
        }
    }

    private static CharSequence getFieldTitle(final Context context, final int fieldIndex) {
        CharSequence defaultText = Settings.getTestFieldDefaultText(fieldIndex);
        if (!TextUtils.isEmpty(defaultText)) {
            return defaultText;
        } else {
            CharSequence hintText = Settings.getTestFieldHintText(fieldIndex);
            if (!TextUtils.isEmpty(hintText)) {
                return hintText;
            } else {
                return context.getString(R.string.test_field_default_name, (fieldIndex + 1));
            }
        }
    }

    /**
     * Preference to link to a language specific settings screen.
     */
    private static class SingleFieldPreference extends Preference {
        private final int mFieldIndex;
        private Bundle mExtras;

        /**
         * Create a new preference for a language.
         * @param context the context for this application.
         * @param fieldIndex the index of the field in the UI.
         */
        public SingleFieldPreference(final Context context, final int fieldIndex) {
            super(context);
            mFieldIndex = fieldIndex;

            setTitle(getFieldTitle(context, fieldIndex));
            String[] summaryInfo = new String[] {
                    getInputTypeDescription(Settings.getTestFieldInputType(fieldIndex), context),
                    getImeOptionsDescription(Settings.getTestFieldImeOptions(fieldIndex), context),
                    getImeActionDescription(Settings.getTestFieldImeActionId(fieldIndex),
                            Settings.getTestFieldImeActionLabel(fieldIndex), context),
                    getPrivateImeOptionsDescription(
                            Settings.getTestFieldPrivateImeOptions(fieldIndex), context),
                    Settings.shouldTestFieldSelectAllOnFocus(fieldIndex)
                            ? context.getString(R.string.select_all_on_focus) : null,
                    getMaxLengthDescription(Settings.getTestFieldMaxLength(fieldIndex), context),
                    Settings.shouldTestFieldAllowUndo(fieldIndex)
                            ? context.getString(R.string.allow_undo) : null,
                    getTextLocalesDescription(
                            Settings.getTestFieldTextLocales(fieldIndex), context),
                    getImeHintLocalesDescription(
                            Settings.getTestFieldImeHintLocales(fieldIndex), context)
            };
            StringBuilder sb = new StringBuilder();
            for (String summaryPiece : summaryInfo) {
                if (summaryPiece == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(summaryPiece);
            }
            setSummary(sb);

            setFragment(TestFieldSettingsFragment.class.getName());
        }

        @Override
        public Bundle getExtras() {
            if (mExtras == null) {
                mExtras = new Bundle();
                mExtras.putString(FIELD_INDEX_BUNDLE_KEY, "" + mFieldIndex);
            }
            return mExtras;
        }

        @Override
        public Bundle peekExtras() {
            return mExtras;
        }
    }

    private static String getDescription(int titleRes, String display, Context context) {
        return getDescription(titleRes, display, (List<String>)null, context);
    }

    private static String getDescription(int titleRes, List<String> details, Context context) {
        return getDescription(titleRes, null, details, context);
    }

    private static String getDescription(int titleRes, String baseDisplay, String details,
                                         Context context) {
        List<String> detailsList;
        if (details != null) {
            detailsList = new ArrayList<>();
            detailsList.add(details);
        } else {
            detailsList = null;
        }
        return getDescription(titleRes, baseDisplay, detailsList, context);
    }

    private static String getDescription(int titleRes, String baseDisplay, List<String> details,
                                         Context context) {
        String valueSummary;
        if (details == null || details.size() == 0) {
            if (TextUtils.isEmpty(baseDisplay)) {
                return null;
            }
            valueSummary = baseDisplay;
        } else {
            String detailsDisplay;
            if (details.size() == 1) {
                detailsDisplay = details.get(0);
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < details.size(); i++) {
                    if (i > 0) {
                        sb.append(context.getString(
                                R.string.test_field_property_details_delimiter));
                    }
                    sb.append(details.get(i));
                }
                detailsDisplay = sb.toString();
            }
            if (TextUtils.isEmpty(baseDisplay)) {
                valueSummary = detailsDisplay;
            } else {
                valueSummary = context.getString(R.string.test_field_property_detailed_summary,
                        baseDisplay, detailsDisplay);
            }
        }
        return context.getString(R.string.test_field_property_description,
                context.getString(titleRes), valueSummary);
    }

    private static String getInputTypeDescription(int inputType, Context context) {
        int inputTypeClass = inputType & InputType.TYPE_MASK_CLASS;
        int inputTypeFlags = inputType & InputType.TYPE_MASK_FLAGS;
        int inputTypeVariation = inputType & InputType.TYPE_MASK_VARIATION;

        String inputTypeClassBaseDisplay;
        List<String> inputTypeClassDetails = new ArrayList<>();
        switch (inputTypeClass) {
            case InputType.TYPE_CLASS_DATETIME:
                switch (inputTypeVariation) {
                    case InputType.TYPE_DATETIME_VARIATION_DATE:
                        inputTypeClassBaseDisplay =
                                context.getString(R.string.input_type_datetime_variation_date);
                        break;
                    case InputType.TYPE_DATETIME_VARIATION_TIME:
                        inputTypeClassBaseDisplay =
                                context.getString(R.string.input_type_datetime_variation_time);
                        break;
                    case InputType.TYPE_DATETIME_VARIATION_NORMAL:
                        inputTypeClassBaseDisplay =
                                context.getString(R.string.input_type_datetime_variation_normal);
                        break;
                    default:
                        // this shouldn't happen
                        inputTypeClassBaseDisplay =
                                context.getString(R.string.input_type_class_datetime);
                        break;
                }
                break;
            case InputType.TYPE_CLASS_NUMBER:
                inputTypeClassBaseDisplay = context.getString(R.string.input_type_class_number);
                if (inputTypeVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_number_variation_password));
                }
                if ((inputTypeFlags & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_number_flag_signed));
                }
                if ((inputTypeFlags & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_number_flag_decimal));
                }
                break;
            case InputType.TYPE_CLASS_PHONE:
                inputTypeClassBaseDisplay = context.getString(R.string.input_type_class_phone);
                break;
            case InputType.TYPE_CLASS_TEXT:
                inputTypeClassBaseDisplay = context.getString(R.string.input_type_class_text);
                switch (inputTypeVariation) {
                    case InputType.TYPE_TEXT_VARIATION_URI:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_uri));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_email_address));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_email_subject));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_short_message));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_long_message));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_PERSON_NAME:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_person_name));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_postal_address));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_PASSWORD:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_password));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_visible_password));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_web_edit_text));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_FILTER:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_filter));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_PHONETIC:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_phonetic));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_web_email_address));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                        inputTypeClassDetails.add(context.getString(
                                R.string.input_type_text_variation_web_password));
                        break;
                    case InputType.TYPE_TEXT_VARIATION_NORMAL:
                    default:
                        break;
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_multi_line));
                } else if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_ime_multi_line));
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_cap_characters));
                } else if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_cap_words));
                } else if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_cap_sentences));
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_auto_complete));
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_auto_correct));
                }
                if ((inputTypeFlags & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
                    inputTypeClassDetails.add(context.getString(
                            R.string.input_type_text_flag_no_suggestions));
                }
                break;
            default:
                // this shouldn't happen
                return null;
        }

        return getDescription(R.string.input_type_category, inputTypeClassBaseDisplay,
                inputTypeClassDetails, context);
    }

    private static String getImeOptionsDescription(int imeOptions, Context context) {
        int imeOptionsAction = imeOptions & EditorInfo.IME_MASK_ACTION;
        String imeOptionsActionBaseDisplay;
        List<String> imeOptionsActionDetails = new ArrayList<>();
        switch (imeOptionsAction) {
            case EditorInfo.IME_NULL:
                imeOptionsActionBaseDisplay =
                        context.getString(R.string.ime_options_action_unspecified);
                break;
            case EditorInfo.IME_ACTION_NONE:
                imeOptionsActionBaseDisplay =
                        context.getString(R.string.ime_options_action_none);
                break;
            case EditorInfo.IME_ACTION_GO:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_go);
                break;
            case EditorInfo.IME_ACTION_SEARCH:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_search);
                break;
            case EditorInfo.IME_ACTION_SEND:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_send);
                break;
            case EditorInfo.IME_ACTION_NEXT:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_next);
                break;
            case EditorInfo.IME_ACTION_DONE:
                imeOptionsActionBaseDisplay = context.getString(R.string.ime_options_action_done);
                break;
            case EditorInfo.IME_ACTION_PREVIOUS:
                imeOptionsActionBaseDisplay =
                        context.getString(R.string.ime_options_action_previous);
                break;
            default:
                // this shouldn't happen
                return null;
        }
        if ((imeOptions & EditorInfo.IME_FLAG_FORCE_ASCII) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_force_ascii));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_navigate_next));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_navigate_previous));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_accessory_action));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_enter_action));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_extract_ui));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_fullscreen));
        }
        if ((imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            imeOptionsActionDetails.add(context.getString(
                    R.string.ime_options_flag_no_personalized_learning));
        }

        return getDescription(R.string.ime_options_category, imeOptionsActionBaseDisplay,
                imeOptionsActionDetails, context);
    }

    private static String getImeActionDescription(int imeActionId, String imeActionLabel,
                                                  Context context) {
        if (imeActionId == 0 && TextUtils.isEmpty(imeActionLabel)) {
            return null;
        }
        return getDescription(R.string.ime_action_category, imeActionLabel, "" + imeActionId,
                context);
    }

    private static String getPrivateImeOptionsDescription(String privateImeOptions,
                                                          Context context) {
        return getDescription(R.string.private_ime_options, privateImeOptions, context);
    }

    private static String getMaxLengthDescription(int maxLength, Context context) {
        return maxLength >= 0
                ? getDescription(R.string.max_length, "" + maxLength, context)
                : null;
    }

    private static String getTextLocalesDescription(Locale[] textLocales, Context context) {
        return getLocalesDescription(R.string.text_locales, textLocales, context);
    }

    private static String getImeHintLocalesDescription(Locale[] imeHintLocales, Context context) {
        return getLocalesDescription(R.string.ime_hint_locales, imeHintLocales, context);
    }

    private static String getLocalesDescription(int titleRes, Locale[] locales, Context context) {
        if (locales == null || locales.length < 1) {
            return null;
        }
        List<String> localeDisplayNames = new ArrayList<>();
        for (Locale locale : locales) {
            localeDisplayNames.add(locale.getDisplayName());
        }
        return getDescription(titleRes, localeDisplayNames, context);
    }
}
