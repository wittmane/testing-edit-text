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
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;

import androidx.annotation.Nullable;

import com.wittmane.testingedittext.CodePointUtils;
import com.wittmane.testingedittext.R;
import com.wittmane.testingedittext.settings.IntRange;
import com.wittmane.testingedittext.settings.NumericFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodepointRangeDialogPreference extends DialogPreferenceBase {
    private static final String TAG = CodepointRangeDialogPreference.class.getSimpleName();

    private static final String PREF_SPLIT_UNICODE =
            "pref_key_codepoint_range_dialog_split_unicode_for_surrogate_pairs";
    private static final int NOT_A_CODEPOINT = -1;

    private EditText mStartCharacterView;
    private EditText mStartCodepointView;
    private EditText mStartUnicodeView;
    private EditText mEndCharacterView;
    private EditText mEndCodepointView;
    private EditText mEndUnicodeView;
    private CheckBox mSplitUnicodeCheckbox;

    private Reader mReader;

    public CodepointRangeDialogPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.codepoint_range_dialog);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);
        mReader = new Reader(getPrefs(), getKey());
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
        mSplitUnicodeCheckbox = view.findViewById(R.id.split_unicode_for_surrogate_pairs);
        mSplitUnicodeCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                writeSplitUnicodeForSurrogatePairsPref(isChecked);
                final boolean useSingleUnicodeIdentifier = !isChecked;

                // read the unicode using the previous value from the check box since it was set
                // with that value
                int startCodepoint = getCodepointFromFields(mStartCharacterView,
                        mStartCodepointView, mStartUnicodeView, !useSingleUnicodeIdentifier);
                if (startCodepoint != NOT_A_CODEPOINT) {
                    setCodepointFields(null, null, mStartUnicodeView, startCodepoint, true);
                } else if (useSingleUnicodeIdentifier) {
                    // set the text to itself to run the filter to clear out extra characters that
                    // aren't valid for a single identifier
                    mStartUnicodeView.setText(mStartUnicodeView.getText());
                }
                int endCodepoint = getCodepointFromFields(mEndCharacterView, mEndCodepointView,
                        mEndUnicodeView, !useSingleUnicodeIdentifier);
                if (endCodepoint != NOT_A_CODEPOINT) {
                    setCodepointFields(null, null, mEndUnicodeView, endCodepoint, true);
                } else if (useSingleUnicodeIdentifier) {
                    // set the text to itself to run the filter to clear out extra characters that
                    // aren't valid for a single identifier
                    mEndUnicodeView.setText(mEndUnicodeView.getText());
                }
            }
        });

        setUpCodepointEntry(mStartCharacterView, mStartCodepointView, mStartUnicodeView);
        setUpCodepointEntry(mEndCharacterView, mEndCodepointView, mEndUnicodeView);

        return view;
    }

    private boolean useSingleUnicodeIdentifier() {
        return !mSplitUnicodeCheckbox.isChecked();
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
                if (CodePointUtils.codePointCount(charSequence) <= 1) {
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

        codepointView.setFilters(new InputFilter[] { new NumericFilter() });
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
                StringBuilder sb = new StringBuilder();
                if (source != null) {
                    for (int i = 0; i < source.length(); i++) {
                        char c = source.charAt(i);
                        if (c >= 'a' && c <= 'z') {
                            // convert to uppercase
                            c += 'A' - 'a';
                        }

                        // note that for useSingleUnicodeIdentifier, it only needs to check for a
                        // single "U+" at the beginning of the text, ignoring any text after this,
                        // because the existing (now later one) will be cleared with
                        // afterTextChanged. it's also fine in this case to add a new one at the
                        // beginning of the text right before the new text for the same reason.
                        if (c == ' ') {
                            if (useSingleUnicodeIdentifier()) {
                                // spaces aren't needed for a single identifier
                                continue;
                            }
                            if (dstart == 0 && sb.length() == 0) {
                                // a space shouldn't be added to the beginning of the text
                                continue;
                            }
                            String textBefore = "" + dest.subSequence(0, dstart) + sb;
                            if (textBefore.length() > 0) {
                                char charBefore = textBefore.charAt(textBefore.length() - 1);
                                if (charBefore == ' ') {
                                    // adjacent spaces are inappropriate
                                    continue;
                                }
                                if (charBefore == 'U') {
                                    // there should be a '+' and hex code for the previous
                                    // unicode identifier, but if the user wants to enter the
                                    // second one first, that's fine. we'll just add the plus
                                    // before the space between the identifiers to help make it more
                                    // obvious that the hex code still needs to be added.
                                    sb.append('+');
                                }
                            }
                        } else if (c == 'U') {
                            if (useSingleUnicodeIdentifier()) {
                                if (dstart > 0 || sb.length() > 0) {
                                    // there should only be a single 'U' at the beginning of the
                                    // text
                                    continue;
                                }
                            } else {
                                String textBefore = "" + dest.subSequence(0, dstart) + sb;
                                if (textBefore.length() > 0) {
                                    char charBefore = textBefore.charAt(textBefore.length() - 1);
                                    if (isHexChar(charBefore)) {
                                        // there should be a space between the unicode identifiers
                                        sb.append(' ');
                                    } else if (charBefore == 'U') {
                                        // there should be a '+' and hex code for the previous
                                        // unicode identifier, but if the user wants to enter the
                                        // second one first, that's fine. we'll just add the plus
                                        // and space between the identifiers to help make it more
                                        // obvious that the hex code still needs to be added.
                                        sb.append("+ ");
                                    } else if (charBefore == '+') {
                                        // there should be a '+' and hex code for the previous
                                        // unicode identifier, but if the user wants to enter the
                                        // second one first, that's fine. we'll just add the space
                                        // between the identifiers to help make it more obvious that
                                        // the hex code still needs to be added.
                                        sb.append(' ');
                                    }
                                }
                            }
                        } else if (c == '+') {
                            if (useSingleUnicodeIdentifier()) {
                                // there should only be a single '+' right after the single 'U' at
                                // the beginning of the text
                                if (dstart == 0 && sb.length() == 0) {
                                    // insert the skipped 'U'
                                    sb.append('U');
                                } else {
                                    String textBefore = "" + dest.subSequence(0, dstart) + sb;
                                    if (!textBefore.equals("U")) {
                                        // this isn't the appropriate place for the '+'
                                        continue;
                                    }
                                }
                            } else {
                                String textBefore = "" + dest.subSequence(0, dstart) + sb;
                                if (textBefore.length() == 0) {
                                    // insert the skipped 'U'
                                    sb.append('U');
                                } else {
                                    char charBefore = textBefore.charAt(textBefore.length() - 1);
                                    if (charBefore != 'U') {
                                        if (charBefore != ' ') {
                                            // insert the skipped space to divide identifiers
                                            sb.append(' ');
                                        }
                                        // insert the skipped 'U'
                                        sb.append('U');
                                    }
                                }
                            }
                        } else if (isHexChar(c)) {
                            if (useSingleUnicodeIdentifier()) {
                                if (dstart == 0 && sb.length() == 0) {
                                    // insert the skipped "U+"
                                    sb.append("U+");
                                } else {
                                    String textBefore = "" + dest.subSequence(0, dstart) + sb;
                                    if (textBefore.equals("U")) {
                                        // insert the skipped '+'
                                        sb.append('+');
                                    }
                                }
                            } else {
                                String textBefore = "" + dest.subSequence(0, dstart) + sb;
                                if (textBefore.length() == 0) {
                                    sb.append("U+");
                                } else {
                                    char charBefore = textBefore.charAt(textBefore.length() - 1);
                                    if (charBefore != '+' && !isHexChar(charBefore)) {
                                        if (charBefore != 'U') {
                                            if (charBefore != ' ') {
                                                // insert the skipped space to divide identifiers
                                                sb.append(' ');
                                            }
                                            // insert the skipped 'U'
                                            sb.append('U');
                                        }
                                        // insert the skipped '+'
                                        sb.append('+');
                                    }
                                }
                            }
                        } else {
                            // everything else is invalid
                            continue;
                        }
                        sb.append(c);
                    }
                }

                // as long as there are still hex characters, we shouldn't allow removing the "U+"
                // when using a single identifier
                if (useSingleUnicodeIdentifier() && dstart <= 1) {
                    CharSequence newText = "" + dest.subSequence(0, dstart) + sb
                            + dest.subSequence(dend, dest.length());
                    if (newText.length() > 0 && isHexChar(newText.charAt(newText.length() - 1))) {
                        if (newText.charAt(0) != 'U' && dstart == 0) {
                            if (newText.charAt(0) != '+') {
                                sb.insert(0, '+');
                            }
                            sb.insert(0, 'U');
                        } else if (newText.length() > 0 && newText.charAt(1) != '+') {
                            sb.insert(1 - dstart, '+');
                        }
                    }
                }

                if (source == null && sb.length() == 0) {
                    return null;
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
                if (useSingleUnicodeIdentifier()) {
                    // the filter only allows entering a single "U+" at the beginning, but the
                    // filter can only change the text that is already changing, which means a new
                    // 'U' and/or '+' could be added before the existing one, so we need to clear
                    // the existing (second) one now as if inserting a new one actually just skipped
                    // inserting the characters and just shifted the cursor to continue adding any
                    // new characters right after the existing ones
                    for (String s : new String[] { "U+", "U", "+"}) {
                        int firstIndex = editable.toString().indexOf(s);
                        if (firstIndex >= 0) {
                            int secondIndex = editable.toString().indexOf(s, firstIndex + 1);
                            if (secondIndex >= 0) {
                                // remove the existing text
                                editable.delete(secondIndex, secondIndex + s.length());
                                // skip processing this event and only handle the next call
                                return;
                            }
                        }
                    }
                }

                if (!TextUtils.equals(editable, unicodeView.getText())) {
                    // this must be from an old change that was changed in #onTextChanged
                    return;
                }

                int codepoint = parseCodepointFromUnicode(editable, useSingleUnicodeIdentifier());
                boolean triggeringTextChanges = codepoint == NOT_A_CODEPOINT
                        ? clearCodepointFields(charView, codepointView, null)
                        : setCodepointFields(charView, codepointView, null, codepoint);
                if (!triggeringTextChanges) {
                    afterCodepointFieldsChanged();
                }
            }
        });
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
    }

    private static boolean isValidCodepoint(int codepoint) {
        return codepoint >= 0 && codepoint <= Character.MAX_CODE_POINT;
    }

    private static int parseCodepointFromCharacter(CharSequence text) {
        if (CodePointUtils.codePointCount(text) != 1) {
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

    private static int parseCodepointFromUnicode(CharSequence text,
                                                 boolean useSingleUnicodeIdentifier) {
        String regex;
        if (useSingleUnicodeIdentifier) {
            regex = "U\\+([\\dABCDEF]{1,6})";
            if (!text.toString().matches("^" + regex + "$")) {
                return NOT_A_CODEPOINT;
            }
        } else {
            regex ="U\\+([\\dABCDEF]{1,4})";
            if (!text.toString().matches("^" + regex + "(,?\\s?" + regex + ")?$")) {
                return NOT_A_CODEPOINT;
            }
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        List<Integer> unicodeNumbers = new ArrayList<>();
        while (matcher.find()) {
            // start with 1 because group 0 is the full match
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group == null) {
                    // I don't think this should happen
                    return NOT_A_CODEPOINT;
                }
                try {
                    unicodeNumbers.add(Integer.parseInt(group, 16));
                } catch(NumberFormatException e) {
                    // this shouldn't happen
                    return NOT_A_CODEPOINT;
                }
            }
        }
        if (useSingleUnicodeIdentifier) {
            if (unicodeNumbers.size() != 1) {
                // this shouldn't happen
                return NOT_A_CODEPOINT;
            }
            // the single unicode identifier is just the hex of the codepoint
            int codepoint = unicodeNumbers.get(0);
            if (codepoint > Character.MAX_CODE_POINT) {
                return NOT_A_CODEPOINT;
            }
            return codepoint;
        } else {
            // create a string from the 1 or 2 unicode numbers (directly converted to characters)
            // and grab the codepoint from the string so it can handle determining (and validating)
            // the single code point from the surrogate pair
            char[] charArray = new char[unicodeNumbers.size()];
            for (int i = 0; i < unicodeNumbers.size(); i++) {
                charArray[i] = (char)(int)unicodeNumbers.get(i);
            }
            String characterString = String.valueOf(charArray);
            if (CodePointUtils.codePointCount(characterString) != 1) {
                return NOT_A_CODEPOINT;
            }
            return characterString.codePointAt(0);
        }
    }

    private  boolean setCodepointFields(EditText charView, EditText codepointView,
                                        EditText unicodeView, int codepoint) {
        return setCodepointFields(charView, codepointView, unicodeView, codepoint, false);
    }

    private  boolean setCodepointFields(EditText charView, EditText codepointView,
                                        EditText unicodeView, int codepoint,
                                        boolean ignoreFocusCheck) {
        boolean textChanged = false;

        String codepointString = new StringBuilder().appendCodePoint(codepoint).toString();

        if (charView != null) {
            textChanged |= setCodepointField(charView, codepointString, ignoreFocusCheck);
        }

        if (codepointView != null) {
            textChanged |= setCodepointField(codepointView, "" + codepoint, ignoreFocusCheck);
        }

        if (unicodeView != null) {
            StringBuilder sb = new StringBuilder();
            if (useSingleUnicodeIdentifier()) {
                sb.append("U+").append(String.format("%04X", codepoint));
            } else {
                char[] charArray = codepointString.toCharArray();
                sb.append("U+").append(String.format("%04X", (int) charArray[0]));
                if (charArray.length > 1) {
                    sb.append(" U+").append(String.format("%04X", (int) charArray[1]));
                }
            }
            textChanged |= setCodepointField(unicodeView, sb.toString(), ignoreFocusCheck);
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
        return setCodepointField(editText, text, false);
    }

    private static boolean setCodepointField(EditText editText, String text,
                                             boolean ignoreFocusCheck) {
        if (text == null) {
            text = "";
        }
        // don't change the text in the field that has focus to avoid a change in one field
        // triggering a change in another that triggers a change back in the original one
        if (!ignoreFocusCheck && editText.hasFocus()) {
            return false;
        }
        Editable currentText = editText.getText();
        // only set the text if it's actually changing to avoid getting into an infinite loop
        if (currentText == null || !text.equals(currentText.toString())) {
            editText.setText(text);
            // set it up so that the next tab to this will have the cursor at the end of the text
            editText.setSelection(editText.length());
            return true;
        }
        return false;
    }

    private int getCodepointFromFields(EditText charView, EditText codepointView,
                                       EditText unicodeView) {
        return getCodepointFromFields(charView, codepointView, unicodeView,
                useSingleUnicodeIdentifier());
    }

    private static int getCodepointFromFields(EditText charView, EditText codepointView,
                                              EditText unicodeView,
                                              boolean useSingleUnicodeIdentifier) {
        int codepoint1 = parseCodepointFromCharacter(charView.getText());
        int codepoint2 = parseCodepointFromCodepoint(codepointView.getText());
        int codepoint3 = parseCodepointFromUnicode(unicodeView.getText(),
                useSingleUnicodeIdentifier);
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

        mSplitUnicodeCheckbox.setChecked(readSplitUnicodeForSurrogatePairsPref());

        final IntRange value = mReader.readValue();
        if (value == null) {
            return;
        }
        setCodepointFields(mStartCharacterView, mStartCodepointView, mStartUnicodeView,
                value.getStart());
        setCodepointFields(mEndCharacterView, mEndCodepointView, mEndUnicodeView, value.getEnd());
    }

    @Override
    protected void onPrepareDialogBuilder(final AlertDialog.Builder builder) {
        builder.setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setNeutralButton(R.string.button_clear, this);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        // update whether the ok button is enabled
        afterCodepointFieldsChanged();
    }

    @Override
    public void onClick(final DialogInterface dialog, final int which) {
        super.onClick(dialog, which);
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            final IntRange value = mReader.readDefaultValue();
            updateValueSummary(value);
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

            IntRange value = new IntRange(startCodepoint, endCodepoint);
            updateValueSummary(value);
            writeValue(value);
        }
    }

    public void writeValue(final @Nullable IntRange value) {
        getPrefs().setString(getKey(),
                value == null ? null : value.getStart() + "-" + value.getEnd());
    }

    public void clearValue() {
        getPrefs().remove(getKey());
    }

    private boolean readSplitUnicodeForSurrogatePairsPref() {
        return getPrefs().getBoolean(PREF_SPLIT_UNICODE, false);
    }

    private void writeSplitUnicodeForSurrogatePairsPref(boolean splitUnicode) {
        getPrefs().setBoolean(PREF_SPLIT_UNICODE, splitUnicode);
    }

    @Override
    public void setKey(String key) {
        super.setKey(key);
        mReader.mKey = key;
    }

    public static class Reader {
        private final SharedPreferences mPrefs;
        private String mKey;

        public Reader(SharedPreferences prefs, String key) {
            mPrefs = prefs;
            mKey = key;
        }

        @Nullable
        public IntRange readValue() {
            String rawValue = mPrefs.getString(mKey, null);
            if (rawValue == null || rawValue.equals("")) {
                return null;
            }
            String[] pieces = rawValue.split("-");
            if (pieces.length != 2) {
                Log.e(TAG, "Unexpected number of codepoints in range preference: "
                        + rawValue);
                return null;
            }
            try {
                return new IntRange(Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1]));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Unexpected codepoint in range preference: " + rawValue);
                return null;
            }
        }

        @Nullable
        private IntRange readDefaultValue() {
            return null;
        }
    }

    public String getValueText(final @Nullable IntRange value) {
        if (value == null) {
            return "";
        }
        return new StringBuilder()
                .appendCodePoint(value.getStart())
                .append(" - ")
                .appendCodePoint(value.getEnd())
                .toString();
    }

    @Override
    protected void updateValueSummary() {
        updateValueSummary(mReader.readValue());
    }

    private void updateValueSummary(final IntRange value) {
        setValueSummary(getValueText(value));
    }
}
