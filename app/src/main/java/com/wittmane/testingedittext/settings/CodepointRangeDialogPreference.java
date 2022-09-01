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
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.wittmane.testingedittext.R;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodepointRangeDialogPreference extends DialogPreference {

    private static final int NOT_A_CODEPOINT = -1;

    private EditText mStartCharacterView;
    private EditText mStartCodepointView;
    private EditText mStartUnicodeView;
    private EditText mEndCharacterView;
    private EditText mEndCodepointView;
    private EditText mEndUnicodeView;

    private final CharSequence mBaseSummary;

    public CodepointRangeDialogPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.codepoint_range_dialog);
        mBaseSummary = getSummary();
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        final int[] value = readValue();
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
        mStartCharacterView = view.findViewById(R.id.codepoint_dialog_start_character);
        mStartCodepointView = view.findViewById(R.id.codepoint_dialog_start_codepoint);
        mStartUnicodeView = view.findViewById(R.id.codepoint_dialog_start_unicode);
        mEndCharacterView = view.findViewById(R.id.codepoint_dialog_end_character);
        mEndCodepointView = view.findViewById(R.id.codepoint_dialog_end_codepoint);
        mEndUnicodeView = view.findViewById(R.id.codepoint_dialog_end_unicode);

        setUpCodepointEntry(mStartCharacterView, mStartCodepointView, mStartUnicodeView);
        setUpCodepointEntry(mEndCharacterView, mEndCodepointView, mEndUnicodeView);

        return view;
    }

    private void setUpCodepointEntry(EditText charView, EditText codepointView,
                                     EditText unicodeView) {

        charView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                          int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before,
                                      int count) {
                if (codePointCount(charSequence) <= 1) {
                    return;
                }
                CharSequence newText = new StringBuilder().appendCodePoint(
                        Character.codePointBefore(charSequence, start + count));
                charView.setText(newText);
                charView.setSelection(newText.length());
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!TextUtils.equals(editable, charView.getText())) {
                    // this must be from an old change that was changed in #onTextChanged
                    return;
                }

                int codepoint = parseCodepointFromCharacter(editable);
                boolean triggeringTextChanges = codepoint == NOT_A_CODEPOINT
                        ? clearCodepointFields(null, codepointView, unicodeView)
                        : setCodepointFields(null, codepointView, unicodeView, codepoint);
                if (!triggeringTextChanges) {
                    afterCodepointFieldsChanged();
                }
            }
        });


        codepointView.setFilters(new InputFilter[] {new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                                       int dstart, int dend) {
                if (source == null || codePointCount(source) == 0) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < source.length(); i++) {
                    char c = source.charAt(i);
                    if (c < '0' || c > '9') {
                        continue;
                    }
                    sb.append(c);
                }
                return sb;
            }
        }});
        codepointView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                          int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before,
                                      int count) { }

            @Override
            public void afterTextChanged(Editable editable) {
                int codepoint = parseCodepointFromCodepoint(editable);
                boolean triggeringTextChanges = codepoint == NOT_A_CODEPOINT
                        ? clearCodepointFields(charView, null, unicodeView)
                        : setCodepointFields(charView, null, unicodeView, codepoint);
                if (!triggeringTextChanges) {
                    afterCodepointFieldsChanged();
                }
            }
        });


        unicodeView.setFilters(new InputFilter[] {new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                                       int dstart, int dend) {
                if (source == null || codePointCount(source) == 0) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < source.length(); i++) {
                    char c = source.charAt(i);
                    if (c >= 'a' && c <= 'z') {
                        c += 'A' - 'a';
                    }
                    if ((c < '0' || c > '9') && (c < 'A' || c > 'F')
                            && c != 'U' && c != '+' && c != ' ' && c != ',') {
                        continue;
                    }
                    sb.append(c);
                }
                return sb;
            }
        }});
        unicodeView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count,
                                          int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before,
                                      int count) { }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!TextUtils.equals(editable, unicodeView.getText())) {
                    // this must be from an old change that was changed in #onTextChanged
                    return;
                }

                int codepoint = parseCodepointFromUnicode(editable);
                boolean triggeringTextChanges = codepoint == NOT_A_CODEPOINT
                        ? clearCodepointFields(charView, codepointView, null)
                        : setCodepointFields(charView, codepointView, null, codepoint);
                if (!triggeringTextChanges) {
                    afterCodepointFieldsChanged();
                }
            }
        });
    }

    private static int codePointCount(final CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return 0;
        }
        return Character.codePointCount(text, 0, text.length());
    }

    private static boolean isValidCodepoint(int codepoint) {
        return codepoint >= 0 && codepoint <= 0x10FFFF;
    }

    private static int parseCodepointFromCharacter(CharSequence text) {
        if (codePointCount(text) != 1) {
            return NOT_A_CODEPOINT;
        }
        return Character.codePointAt(text, 0);
    }

    private static int parseCodepointFromCodepoint(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return NOT_A_CODEPOINT;
        }
        int codepoint;
        try {
            codepoint = Integer.parseInt(text.toString());
        } catch(NumberFormatException e) {
            return NOT_A_CODEPOINT;
        }
        if (!isValidCodepoint(codepoint)) {
            return NOT_A_CODEPOINT;
        }
        return codepoint;
    }

    private static int parseCodepointFromUnicode(CharSequence text) {
        String regex = "U\\+([\\dABCDEF]{1,4}),?\\s?";
        if (!text.toString().matches("^(" + regex + ")+$")) {
            return NOT_A_CODEPOINT;
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        List<Character> chars = new ArrayList<>();
        while (matcher.find()) {
            // start with 1 because group 0 is the full match
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group == null) {
                    // I don't think this should happen
                    return NOT_A_CODEPOINT;
                }
                try {
                    chars.add((char)Integer.parseInt(group, 16));
                } catch(NumberFormatException e) {
                    // this shouldn't happen
                    return NOT_A_CODEPOINT;
                }
            }
        }
        char[] charArray = new char[chars.size()];
        for (int i = 0; i < chars.size(); i++) {
            charArray[i] = chars.get(i);
        }
        String characterString = String.valueOf(charArray);
        if (codePointCount(characterString) != 1) {
            return NOT_A_CODEPOINT;
        }
        return characterString.codePointAt(0);
    }

    private static boolean setCodepointFields(EditText charView, EditText codepointView,
                                              EditText unicodeView, int codepoint) {
        boolean textChanged = false;

        String codepointString = new StringBuilder().appendCodePoint(codepoint).toString();

        if (charView != null) {
            textChanged |= setCodepointField(charView, codepointString);
        }

        if (codepointView != null) {
            textChanged |= setCodepointField(codepointView, "" + codepoint);
        }

        if (unicodeView != null) {
            char[] charArray = codepointString.toCharArray();
            StringBuilder sb = new StringBuilder();
            sb.append("U+").append(String.format("%04X", (int) charArray[0]));
            if (charArray.length > 1) {
                sb.append(" U+").append(String.format("%04X", (int) charArray[1]));
            }
            textChanged |= setCodepointField(unicodeView, sb.toString());
        }

        return textChanged;
    }

    private static boolean clearCodepointFields(EditText charView, EditText codepointView,
                                                EditText unicodeView) {
        boolean textChanged = false;

        if (charView != null) {
            textChanged |= setCodepointField(charView, "");
        }

        if (codepointView != null) {
            textChanged |= setCodepointField(codepointView, "");
        }

        if (unicodeView != null) {
            textChanged |= setCodepointField(unicodeView, "");
        }

        return textChanged;
    }

    private static boolean setCodepointField(EditText editText, String text) {
        if (text == null) {
            text = "";
        }
        // don't change the text in the field that has focus to avoid a change in one field
        // triggering a change in another that triggers a change back in the original one
        if (editText.hasFocus()) {
            return false;
        }
        Editable currentText = editText.getText();
        // only set the text if it's actually changing to avoid getting into an infinite loop
        if (currentText == null || !text.equals(currentText.toString())) {
            editText.setText(text);
            return true;
        }
        return false;
    }

    private static int getCodepointFromFields(EditText charView, EditText codepointView,
                                              EditText unicodeView) {
        int codepoint1 = parseCodepointFromCharacter(charView.getText());
        int codepoint2 = parseCodepointFromCodepoint(codepointView.getText());
        int codepoint3 = parseCodepointFromUnicode(unicodeView.getText());
        // make sure all 3 fields match
        if (codepoint1 != codepoint2 || codepoint1 != codepoint3) {
            return NOT_A_CODEPOINT;
        }
        return codepoint1;
    }

    private void afterCodepointFieldsChanged() {
        int startCodepoint =
                getCodepointFromFields(mStartCharacterView, mStartCodepointView, mStartUnicodeView);
        int endCodepoint =
                getCodepointFromFields(mEndCharacterView, mEndCodepointView, mEndUnicodeView);
        AlertDialog dialog = (AlertDialog)getDialog();
        if (dialog == null) {
            return;
        }
        Button acceptButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (acceptButton == null) {
            return;
        }
        acceptButton.setEnabled(
                startCodepoint != NOT_A_CODEPOINT && endCodepoint != NOT_A_CODEPOINT);
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);
        final int[] value = readValue();
        if (value == null) {
            return;
        }
        setCodepointFields(mStartCharacterView, mStartCodepointView, mStartUnicodeView, value[0]);
        setCodepointFields(mEndCharacterView, mEndCodepointView, mEndUnicodeView, value[1]);
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
            final int[] value = readDefaultValue();
            setSummary(getValueText(value));
            clearValue();
        } else if (which == DialogInterface.BUTTON_POSITIVE) {
            int startCodepoint = getCodepointFromFields(
                    mStartCharacterView, mStartCodepointView, mStartUnicodeView);
            int endCodepoint = getCodepointFromFields(
                    mEndCharacterView, mEndCodepointView, mEndUnicodeView);

            if (startCodepoint == NOT_A_CODEPOINT || endCodepoint == NOT_A_CODEPOINT) {
                // this shouldn't happen
                return;
            }

            if (startCodepoint > endCodepoint) {
                int temp = startCodepoint;
                startCodepoint = endCodepoint;
                endCodepoint = temp;
            }

            int[] value = new int[] { startCodepoint, endCodepoint };
            setSummary(getValueText(value));
            writeValue(value);
        }
    }

    private SharedPreferences getPrefs() {
        return getPreferenceManager().getSharedPreferences();
    }

    public void writeValue(final @Nullable @Size(2) int[] value) {
        getPrefs().edit().putString(getKey(), value == null ? null : value[0] + "-" + value[1])
                .apply();
    }

    public void clearValue() {
        getPrefs().edit().remove(getKey()).apply();
    }

    public @Nullable @Size(2) int[] readValue() {
        String rawValue = getPrefs().getString(getKey(), null);
        if (rawValue == null || rawValue.equals("")) {
            return null;
        }
        String[] pieces = rawValue.split("-");
        if (pieces.length != 2) {
            return null;
        }
        try {
            return new int[]{ Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]) };
        } catch(NumberFormatException e) {
            return null;
        }
    }

    public @Nullable @Size(2) int[] readDefaultValue() {
        return null;
    }

    public String getValueText(final @Nullable @Size(2) int[] value) {
        if (value == null) {
            return "";
        }
        return new StringBuilder()
                .appendCodePoint(value[0])
                .append(" - ")
                .appendCodePoint(value[1])
                .toString();
    }
}
