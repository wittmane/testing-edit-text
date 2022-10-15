/*
 * Copyright (C) 2022 Eli Wittman
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.internal.widget;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.NoCopySpan;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.method.KeyListener;
import android.util.Log;
import android.util.LogPrinter;
import android.view.ContentInfo;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.CodePointUtils;
import com.wittmane.testingedittext.settings.Settings;
import com.wittmane.testingedittext.aosp.internal.util.Preconditions;
import com.wittmane.testingedittext.aosp.widget.EditText;
import com.wittmane.testingedittext.settings.TranslateText;

import static android.view.ContentInfo.SOURCE_INPUT_METHOD;

// (EW) this is a merge of EditableInputConnection and BaseInputConnection to be able to insert
// custom behavior
/**
 * Base class for an editable InputConnection instance. This is created by {@link EditText}.
 */
public class EditableInputConnection implements InputConnection {
    private static final boolean DEBUG = false;
    private static final boolean LOG_CALLS = true;
    private static final boolean LOG_TEXT_MODIFICATION = true;
    private static final String TAG = EditableInputConnection.class.getSimpleName();
    private static final Object COMPOSING = new ComposingText();
    private static final int INVALID_INDEX = -1;

    private static boolean sHasCheckedCanLieAboutMissingMethods = false;
    private static boolean sCanLieAboutMissingMethods = false;

    private static class ComposingText implements NoCopySpan {
    }

    protected final InputMethodManager mIMM;
    protected final @NonNull EditText mEditText;

    // (EW) from InputMethodManager
    private static final int REQUEST_UPDATE_CURSOR_ANCHOR_INFO_NONE = 0x0;

    // (EW) from InputMethodManager. InputMethodManager.Handler#handleMessage (I think ultimately
    // triggered from IInputMethodClient.Stub#onBindMethod) also resets this in the AOSP version,
    // but I think that is due to it managing input methods and being reused. since a new input
    // connection gets created when switching input methods, that same reset shouldn't apply.
    /**
     * The monitor mode for
     * {@link InputMethodManager#updateCursorAnchorInfo(View, CursorAnchorInfo)}.
     */
    private int mRequestUpdateCursorAnchorInfoMonitorMode = REQUEST_UPDATE_CURSOR_ANCHOR_INFO_NONE;

    // (EW) from InputMethodManager. I'm not certain if this synchronization is necessary outside of
    // InputMethodManager, but it probably doesn't hurt to keep.
    protected final Object mH = new Object();

    // Keeps track of nested begin/end batch edit to ensure this connection always has a
    // balanced impact on its associated EditText.
    // A negative value means that this connection has been finished by the InputMethodManager.
    private int mBatchEditNesting;

    private Object[] mDefaultComposingSpans;

    public EditableInputConnection(@NonNull EditText targetView) {
        mIMM = (InputMethodManager)targetView.getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        mEditText = targetView;
    }

    public static void removeComposingSpans(Spannable text) {
        text.removeSpan(COMPOSING);
        Object[] sps = text.getSpans(0, text.length(), Object.class);
        if (sps != null) {
            for (int i = sps.length - 1; i >= 0; i--) {
                Object o = sps[i];
                if ((text.getSpanFlags(o) & Spanned.SPAN_COMPOSING) != 0) {
                    text.removeSpan(o);
                }
            }
        }
    }

    public static void setComposingSpans(Spannable text) {
        setComposingSpans(text, 0, text.length());
    }

    private static void setComposingSpans(Spannable text, int start, int end) {
        final Object[] sps = text.getSpans(start, end, Object.class);
        if (sps != null) {
            for (int i = sps.length - 1; i >= 0; i--) {
                final Object o = sps[i];
                if (o == COMPOSING) {
                    text.removeSpan(o);
                    continue;
                }

                final int fl = text.getSpanFlags(o);
                if ((fl & (Spanned.SPAN_COMPOSING | Spanned.SPAN_POINT_MARK_MASK))
                        != (Spanned.SPAN_COMPOSING | getCompositionSpanInclusivity())) {
                    text.setSpan(o, text.getSpanStart(o), text.getSpanEnd(o),
                            (fl & ~Spanned.SPAN_POINT_MARK_MASK)
                                    | Spanned.SPAN_COMPOSING
                                    | getCompositionSpanInclusivity());
                }
            }
        }

        text.setSpan(COMPOSING, start, end,
                getCompositionSpanInclusivity() | Spanned.SPAN_COMPOSING);
    }

    private static int getCompositionSpanInclusivity() {
        // (EW) the span won't be kept on a zero width range if it's marked as
        // SPAN_EXCLUSIVE_EXCLUSIVE, so when keeping the empty composing region, we'll use
        // SPAN_INCLUSIVE_INCLUSIVE. this could have negative effects of including adjacent text
        // that gets added, but the input connection should only be adding to the composing region
        // if there is one, and currently the EditText itself shouldn't be adding text, so I don't
        // think this will actually cause issues. an alternative would be to track it manually here,
        // but that also has risks of not shifting its position appropriately when adding text
        // before it, so we'll just change the inclusivity because that's simpler, and we can use
        // the alternative if this does turn out to be a problem.
        if (Settings.shouldKeepEmptyComposingPosition()) {
            return Spanned.SPAN_INCLUSIVE_INCLUSIVE;
        }
        return Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
    }

    public static int getComposingSpanStart(Spannable text) {
        return text.getSpanStart(COMPOSING);
    }

    public static int getComposingSpanEnd(Spannable text) {
        return text.getSpanEnd(COMPOSING);
    }

    private static CharSequence getComposition(Spannable text) {
        int start = getComposingSpanStart(text);
        int end = getComposingSpanEnd(text);
        if (start < 0 || end <= start) {
            return null;
        }
        return text.subSequence(start, end);
    }

    /**
     * Return the target of edit operations.
     */
    public @NonNull Editable getEditable() {
        return mEditText.getEditableText();
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean beginBatchEdit() {
        if (LOG_CALLS) {
            Log.d(TAG, "beginBatchEdit");
        }
        synchronized(this) {
            if (mBatchEditNesting >= 0) {
                mEditText.beginBatchEdit();
                mBatchEditNesting++;
                return true;
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean endBatchEdit() {
        if (LOG_CALLS) {
            Log.d(TAG, "endBatchEdit");
        }
        synchronized(this) {
            if (mBatchEditNesting > 0) {
                // When the connection is reset by the InputMethodManager and reportFinish
                // is called, some endBatchEdit calls may still be asynchronously received from the
                // IME. Do not take these into account, thus ensuring that this IC's final
                // contribution to mEditText's nested batch edit count is zero.
                mEditText.endBatchEdit();
                mBatchEditNesting--;
                return true;
            }
        }
        return false;
    }

    /**
     * Called after only the composing region is modified (so it isn't called if the text also
     * changes).
     * <p>
     * Default implementation does nothing.
     */
    private void endComposingRegionEditInternal() {
        // The ContentCapture service is interested in Composing-state changes.
        mEditText.notifyContentCaptureTextChanged();
    }

    /**
     * Default implementation calls {@link #finishComposingText()} and
     * {@code setImeConsumesInput(false)}.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @CallSuper
    @Override
    public void closeConnection() {
        if (LOG_CALLS) {
            Log.d(TAG, "closeConnection");
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. the lying wrapper should prevent this from getting called, but as a
        // fallback, we can still just ignore the call.
        if (Settings.shouldSkipCloseConnection()) {
            return;
        }

        finishComposingText();
        // (EW) the AOSP version only did this starting in S, which is also when it is available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setImeConsumesInput(false);
        }

        synchronized(this) {
            while (mBatchEditNesting > 0) {
                endBatchEdit();
            }
            // Will prevent any further calls to begin or endBatchEdit
            mBatchEditNesting = -1;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean clearMetaKeyStates(int states) {
        if (LOG_CALLS) {
            Log.d(TAG, "clearMetaKeyStates: states=" + states);
        }
        final Editable content = getEditable();
        KeyListener kl = mEditText.getKeyListener();
        if (kl != null) {
            try {
                kl.clearMetaKeyState(mEditText, content, states);
            } catch (AbstractMethodError e) {
                // This is an old listener that doesn't implement the
                // new method.
            }
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean commitCompletion(CompletionInfo text) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitCompletion: CompletionInfo=" + text);
        }
        mEditText.beginBatchEdit();
        mEditText.onCommitCompletion(text);
        mEditText.endBatchEdit();
        return true;
    }

    /**
     * Calls the {@link EditText#onCommitCorrection} method of the associated EditText.
     */
    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    @Override
    public boolean commitCorrection(CorrectionInfo correctionInfo) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitCorrection: correctionInfo=" + correctionInfo);
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. the lying wrapper theoretically should prevent this from getting called,
        // and although documentation states "Since Android Build.VERSION_CODES.N until
        // Build.VERSION_CODES.TIRAMISU, this API returned false when the target application does
        // not implement this method.", what I've seen in testing is that this does the exact same
        // thing as prior to Nougat, that is, crash the app. I would have expected it to only crash
        // prior to Nougat, like getSelectedText, requestCursorUpdates, and setComposingRegion.
        // com.android.internal.view.InputConnectionWrapper#commitCorrection doesn't actually check
        // isMethodMissing like the others, so this just seems like a bug there. until android fixes
        // the bug, we'll just crash to match that behavior on all versions, rather than only on
        // versions prior to Nougat.
        // also, note that the return value sent here isn't actually sent to the IME, so even if
        // this was to return false, the IME still sees a return of true, so we can only mimic the
        // behavior as long as the lying wrapper still works.
        if (Settings.shouldSkipCommitCorrection()) {
            throw new AbstractMethodError(
                    "boolean android.view.inputmethod.InputConnection.commitCorrection(android.view.inputmethod.CorrectionInfo)");
        }

        mEditText.beginBatchEdit();
        //TODO: (EW) the AOSP version only flashes a highlight on the new text position as if
        // assuming that correction was already made and this method was only meant as a visual
        // indication despite the documentation sounding like this should actually change text. this
        // is probably a good candidate for alternate functionality options.
        mEditText.onCommitCorrection(correctionInfo);
        mEditText.endBatchEdit();
        return true;
    }

    /**
     * Default implementation replaces any existing composing text with
     * the given text.  In addition, only if fallback mode, a key event is
     * sent for the new text and the current editable buffer cleared.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitText: text=" + text + ", newCursorPosition=" + newCursorPosition);
        }
        if (Settings.shouldModifyCommittedText()) {
            text = modifyText(text);
        }
        replaceText(text, newCursorPosition, false);
        // (EW) the AOSP version also called sendCurrentText, but that does nothing since
        // mFallbackMode would be false for an EditText
        return true;
    }

    private static CharSequence modifyText(CharSequence text) {
        return modifyText(text, 0, 0);
    }

    private static CharSequence modifyText(CharSequence text,
                                           int startCodePointToSkip, int endCodePointsToSkip) {
        Editable editable = new SpannableStringBuilder(text);
        restrictText(editable, startCodePointToSkip, endCodePointsToSkip);
        String restricted = editable.toString();
        translateText(editable, startCodePointToSkip, endCodePointsToSkip);
        if (LOG_TEXT_MODIFICATION) {
            Log.d(TAG, "modifyText: \"" + text + "\" -> \"" + restricted + "\" -> \""
                    + editable + "\""
                    + (startCodePointToSkip > 0 || endCodePointsToSkip > 0
                            ? " (evaluating " + startCodePointToSkip + " - "
                                    + (CodePointUtils.codePointCount(text) - endCodePointsToSkip)
                                    + ")"
                            : ""));
        }
        return editable;
    }

    private static void restrictText(Editable text,
                                     int startCodePointToSkip, int endCodePointsToSkip) {
        boolean restrictToInclude = Settings.shouldRestrictToInclude();
        String[] specificRestrictions = Settings.getRestrictSpecific();
        int[] codepointRangeRestriction = Settings.getRestrictRange();

        int codePointIndex = startCodePointToSkip;
        while (codePointIndex < CodePointUtils.codePointCount(text) - endCodePointsToSkip) {
            int charIndex = CodePointUtils.codePointIndexToCharIndex(text, codePointIndex);
            int codePoint = Character.codePointAt(text, codePointIndex);
            int codePointCharLength = CodePointUtils.codePointLength(text, codePointIndex);

            boolean inSpecificRestrictions = false;
            int specificRestrictionCharLength = 0;
            for (String specificRestriction : specificRestrictions) {
                if (TextUtils.isEmpty(specificRestriction)) {
                    continue;
                }
                int restrictionCodepointLength = CodePointUtils.codePointCount(specificRestriction);
                int remainingTextLength =
                        CodePointUtils.codePointCount(text) - endCodePointsToSkip - codePointIndex;
                if (remainingTextLength >= restrictionCodepointLength
                        && TextUtils.equals(specificRestriction,
                                CodePointUtils.codePointSubsequence(text, codePointIndex,
                                        codePointIndex + restrictionCodepointLength))) {
                    inSpecificRestrictions = true;
                    specificRestrictionCharLength = specificRestriction.length();
                    break;
                }
            }

            if (restrictToInclude) {
                if (inSpecificRestrictions) {
                    // this whole text block is allowed. move the position to the end of the block.
                    codePointIndex += specificRestrictionCharLength;
                } else if (codepointRangeRestriction != null &&
                        (codePoint >= codepointRangeRestriction[0]
                                && codePoint <= codepointRangeRestriction[1])) {
                    // this codepoint is inside of the included range. move to the next codepoint.
                    codePointIndex++;
                } else {
                    // everything else is restricted, so get rid of the current codepoint. no need
                    // to update the position since the next codepoint will be in the current
                    // position.
                    if (LOG_TEXT_MODIFICATION) {
                        Log.d(TAG, "restrictText: \""
                                + text.subSequence(charIndex, charIndex + codePointCharLength)
                                + "\" not allowed");
                    }
                    text.delete(charIndex, charIndex + codePointCharLength);
                }

            } else {
                if (inSpecificRestrictions) {
                    // this whole text block is not allowed, so get rid of it. no need to update the
                    // position since the next codepoint will be in the current position.
                    if (LOG_TEXT_MODIFICATION) {
                        Log.d(TAG, "restrictText: \""
                                + text.subSequence(charIndex,
                                        charIndex + specificRestrictionCharLength)
                                + "\" blocked (specific)");
                    }
                    text.delete(charIndex, charIndex + specificRestrictionCharLength);
                } else if (codepointRangeRestriction != null &&
                        (codePoint >= codepointRangeRestriction[0]
                                && codePoint <= codepointRangeRestriction[1])) {
                    // this codepoint is not allowed, so get rid of it. no need to update the
                    // position since the next codepoint will be in the current position.
                    if (LOG_TEXT_MODIFICATION) {
                        Log.d(TAG, "restrictText: \""
                                + text.subSequence(charIndex, charIndex + codePointCharLength)
                                + "\" blocked (range)");
                    }
                    text.delete(charIndex, charIndex + codePointCharLength);
                } else {
                    // everything else is allowed. move to the next codepoint.
                    codePointIndex++;
                }
            }
        }
    }

    private static void translateText(Editable text,
                                      int startCodePointToSkip, int endCodePointsToSkip) {
        TranslateText[] specificTranslations = Settings.getTranslateSpecific();
        int codepointShift = Settings.getShiftCodepoint();

        int codePointIndex = startCodePointToSkip;
        while (codePointIndex < CodePointUtils.codePointCount(text) - endCodePointsToSkip) {
            int charIndex = CodePointUtils.codePointIndexToCharIndex(text, codePointIndex);
            int codePoint = Character.codePointAt(text, codePointIndex);
            int codePointCharLength = CodePointUtils.codePointLength(text, codePointIndex);

            TranslateText matchingTranslation = null;
            for (TranslateText specificTranslation : specificTranslations) {
                CharSequence original = specificTranslation.getOriginal();
                if (TextUtils.isEmpty(original)) {
                    // can't translate nothing into something, so just ignore this
                    continue;
                }
                int originalLength = CodePointUtils.codePointCount(original);
                int remainingTextLength =
                        CodePointUtils.codePointCount(text) - endCodePointsToSkip - codePointIndex;
                if (remainingTextLength >= originalLength
                        && TextUtils.equals(original,
                                CodePointUtils.codePointSubsequence(text, codePointIndex,
                                        codePointIndex + originalLength))) {
                    matchingTranslation = specificTranslation;
                    break;
                }
            }

            if (matchingTranslation != null) {
                int originalLength = matchingTranslation.getOriginal().length();
                if (LOG_TEXT_MODIFICATION) {
                    Log.d(TAG, "translateText: \""
                            + text.subSequence(charIndex, charIndex + originalLength) + "\" -> \""
                            + matchingTranslation.getTranslation() + "\" (specific)");
                }
                text.replace(charIndex, charIndex + originalLength,
                        matchingTranslation.getTranslation());
                codePointIndex +=
                        CodePointUtils.codePointCount(matchingTranslation.getTranslation());
            } else {
                int shiftedCodepoint = (codePoint + codepointShift) % Character.MAX_CODE_POINT;
                if (shiftedCodepoint != codePoint) {
                    String shiftedCodePointString =
                            CodePointUtils.codePointString(shiftedCodepoint);
                    if (LOG_TEXT_MODIFICATION) {
                        Log.d(TAG, "translateText: \""
                                + text.subSequence(charIndex, charIndex + codePointCharLength)
                                + "\" -> \"" + shiftedCodePointString + "\" (shift)");
                    }
                    text.replace(charIndex, charIndex + codePointCharLength,
                            CodePointUtils.codePointString(shiftedCodepoint));
                }
                codePointIndex++;
            }
        }
    }


    /**
     * The default implementation performs the deletion around the current selection position of the
     * editable text.
     *
     * @param beforeLength The number of characters before the cursor to be deleted, in code unit.
     *        If this is greater than the number of existing characters between the beginning of the
     *        text and the cursor, then this method does not fail but deletes all the characters in
     *        that range.
     * @param afterLength The number of characters after the cursor to be deleted, in code unit.
     *        If this is greater than the number of existing characters between the cursor and
     *        the end of the text, then this method does not fail but deletes all the characters in
     *        that range.
     *
     * @return {@code true} when selected text is deleted, {@code false} when either the
     *         selection is invalid or not yet attached (i.e. selection start or end is -1),
     *         or the editable text is {@code null}.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (LOG_CALLS) {
            Log.d(TAG, "deleteSurroundingText: beforeLength=" + beforeLength
                    + ", afterLength=" + afterLength);
        }
        final Editable content = getEditable();

        beginBatchEdit();

        int a = Selection.getSelectionStart(content);
        int b = Selection.getSelectionEnd(content);

        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }

        // Skip when the selection is not yet attached.
        if (a == -1 || b == -1) {
            endBatchEdit();
            return false;
        }

        // (EW) check the setting to determine if deleting should also be done around the composing
        // text. although this isn't documented functionality for this method, the AOSP code very
        // intentionally shifts the range for what can be deleted to skip the composing text.
        if (!Settings.shouldDeleteThroughComposingText()) {
            // Ignore the composing text.
            int ca = getComposingSpanStart(content);
            int cb = getComposingSpanEnd(content);
            if (cb < ca) {
                int tmp = ca;
                ca = cb;
                cb = tmp;
            }
            if (ca != -1 && cb != -1) {
                if (ca < a) a = ca;
                if (cb > b) b = cb;
            }
        }

        int deleted = 0;

        if (beforeLength > 0) {
            int start = a - beforeLength;
            if (start < 0) start = 0;

            final int numDeleteBefore = a - start;
            if (a >= 0 && numDeleteBefore > 0) {
                content.delete(start, a);
                deleted = numDeleteBefore;
            }
        }

        if (afterLength > 0) {
            b = b - deleted;

            int end = b + afterLength;
            if (end > content.length()) end = content.length();

            final int numDeleteAfter = end - b;
            if (b >= 0 && numDeleteAfter > 0) {
                content.delete(b, end);
            }
        }

        endBatchEdit();

        return true;
    }

    private static int findIndexBackward(final CharSequence cs, final int from,
                                         final int numCodePoints) {
        int currentIndex = from;
        boolean waitingHighSurrogate = false;
        final int N = cs.length();
        if (currentIndex < 0 || N < currentIndex) {
            return INVALID_INDEX;  // The starting point is out of range.
        }
        if (numCodePoints < 0) {
            return INVALID_INDEX;  // Basically this should not happen.
        }
        int remainingCodePoints = numCodePoints;
        while (true) {
            if (remainingCodePoints == 0) {
                return currentIndex;  // Reached to the requested length in code points.
            }

            --currentIndex;
            if (currentIndex < 0) {
                if (waitingHighSurrogate) {
                    return INVALID_INDEX;  // An invalid surrogate pair is found.
                }
                return 0;  // Reached to the beginning of the text w/o any invalid surrogate pair.
            }
            final char c = cs.charAt(currentIndex);
            if (waitingHighSurrogate) {
                if (!java.lang.Character.isHighSurrogate(c)) {
                    return INVALID_INDEX;  // An invalid surrogate pair is found.
                }
                waitingHighSurrogate = false;
                --remainingCodePoints;
                continue;
            }
            if (!java.lang.Character.isSurrogate(c)) {
                --remainingCodePoints;
                continue;
            }
            if (java.lang.Character.isHighSurrogate(c)) {
                return INVALID_INDEX;  // A invalid surrogate pair is found.
            }
            waitingHighSurrogate = true;
        }
    }

    private static int findIndexForward(final CharSequence cs, final int from,
                                        final int numCodePoints) {
        int currentIndex = from;
        boolean waitingLowSurrogate = false;
        final int N = cs.length();
        if (currentIndex < 0 || N < currentIndex) {
            return INVALID_INDEX;  // The starting point is out of range.
        }
        if (numCodePoints < 0) {
            return INVALID_INDEX;  // Basically this should not happen.
        }
        int remainingCodePoints = numCodePoints;

        while (true) {
            if (remainingCodePoints == 0) {
                return currentIndex;  // Reached to the requested length in code points.
            }

            if (currentIndex >= N) {
                if (waitingLowSurrogate) {
                    return INVALID_INDEX;  // An invalid surrogate pair is found.
                }
                return N;  // Reached to the end of the text w/o any invalid surrogate pair.
            }
            final char c = cs.charAt(currentIndex);
            if (waitingLowSurrogate) {
                if (!java.lang.Character.isLowSurrogate(c)) {
                    return INVALID_INDEX;  // An invalid surrogate pair is found.
                }
                --remainingCodePoints;
                waitingLowSurrogate = false;
                ++currentIndex;
                continue;
            }
            if (!java.lang.Character.isSurrogate(c)) {
                --remainingCodePoints;
                ++currentIndex;
                continue;
            }
            if (java.lang.Character.isLowSurrogate(c)) {
                return INVALID_INDEX;  // A invalid surrogate pair is found.
            }
            waitingLowSurrogate = true;
            ++currentIndex;
        }
    }

    /**
     * The default implementation performs the deletion around the current selection position of the
     * editable text.
     * @param beforeLength The number of characters before the cursor to be deleted, in code points.
     *        If this is greater than the number of existing characters between the beginning of the
     *        text and the cursor, then this method does not fail but deletes all the characters in
     *        that range.
     * @param afterLength The number of characters after the cursor to be deleted, in code points.
     *        If this is greater than the number of existing characters between the cursor and
     *        the end of the text, then this method does not fail but deletes all the characters in
     *        that range.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        if (LOG_CALLS) {
            Log.d(TAG, "deleteSurroundingTextInCodePoints: beforeLength="
                    + beforeLength + ", afterLength=" + afterLength);
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. the lying wrapper should prevent this from getting called, but if that
        // fails, and this does get called, we can't actually mimic the behavior because the return
        // value sent here isn't actually sent to the IME, which is specifically why we need the
        // lying wrapper. simply ignoring the request and returning false wouldn't be a valid test
        // case because the IME would have no indication that we don't implement this method, so it
        // would behave unexpectedly because technically the editor would be misbehaving. we'll just
        // log an error and behave as if this setting was disabled.
        if (Settings.shouldSkipDeleteSurroundingTextInCodePoints()) {
            Log.e(TAG, "couldn't fake not implementing deleteSurroundingTextInCodePoints");
        }

        final Editable content = getEditable();

        beginBatchEdit();

        int a = Selection.getSelectionStart(content);
        int b = Selection.getSelectionEnd(content);

        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }

        // (EW) check the setting to determine if deleting should also be done around the composing
        // text. although this isn't documented functionality for this method, the AOSP code very
        // intentionally shifts the range for what can be deleted to skip the composing text.
        if (!Settings.shouldDeleteThroughComposingText()) {
            // Ignore the composing text.
            int ca = getComposingSpanStart(content);
            int cb = getComposingSpanEnd(content);
            if (cb < ca) {
                int tmp = ca;
                ca = cb;
                cb = tmp;
            }
            if (ca != -1 && cb != -1) {
                if (ca < a) a = ca;
                if (cb > b) b = cb;
            }
        }

        if (a >= 0 && b >= 0) {
            final int start = findIndexBackward(content, a, Math.max(beforeLength, 0));
            if (start != INVALID_INDEX) {
                final int end = findIndexForward(content, b, Math.max(afterLength, 0));
                if (end != INVALID_INDEX) {
                    final int numDeleteBefore = a - start;
                    if (numDeleteBefore > 0) {
                        content.delete(start, a);
                    }
                    final int numDeleteAfter = end - b;
                    if (numDeleteAfter > 0) {
                        content.delete(b - numDeleteBefore, end - numDeleteBefore);
                    }
                }
            }
            // NOTE: You may think we should return false here if start and/or end is INVALID_INDEX,
            // but the truth is that IInputConnectionWrapper running in the middle of IPC calls
            // always returns true to the IME without waiting for the completion of this method as
            // IInputConnectionWrapper#isAtive() returns true.  This is actually why some methods
            // including this method look like asynchronous calls from the IME.
        }

        endBatchEdit();

        return true;
    }

    /**
     * The default implementation removes the composing state from the
     * current editable text.  In addition, only if fallback mode, a key event is
     * sent for the new text and the current editable buffer cleared.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean finishComposingText() {
        if (LOG_CALLS) {
            Log.d(TAG, "finishComposingText");
        }
        final Editable content = getEditable();
        beginBatchEdit();
        removeComposingSpans(content);
        // (EW) the AOSP version also called sendCurrentText, but that does nothing since
        // mFallbackMode would be false for an EditText
        endBatchEdit();
        endComposingRegionEditInternal();
        return true;
    }

    /**
     * The default implementation uses TextUtils.getCapsMode to get the
     * cursor caps mode for the current selection position in the editable
     * text, unless in fallback mode in which case 0 is always returned.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public int getCursorCapsMode(int reqModes) {
        if (LOG_CALLS) {
            Log.d(TAG, "getCursorCapsMode: reqModes=" + reqModes);
        }
        final Editable content = getEditable();

        int a = Selection.getSelectionStart(content);
        int b = Selection.getSelectionEnd(content);

        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }

        return TextUtils.getCapsMode(content, a, reqModes);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public ExtractedText getExtractedText(ExtractedTextRequest extractedTextRequest, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getExtractedText: extractedTextRequest=" + extractedTextRequest
                    + ", flags=" + flags);
        }

        // (EW) check the setting to force this method to do nothing since documentation says that
        // this can return null if the editor can't comply with the request for some reason
        if (Settings.shouldSkipExtractingText()) {
            return null;
        }

        //TODO: (EW) if this returns null, no text is shown in the full screen text field
        // (landscape) so be sure to consider this when figuring out the weird behavior options.
        // we might want a separate setting to skip the extracted text monitor so we can still
        // return something here to still allow the full screen text field to work. alternatively,
        // can we disable going into full screen mode? based on the documentation, I'm not sure that
        // just ignoring the text monitor is valid, but I've seen it happen, so it might still be
        // reasonable to have for testing.
        ExtractedText et = new ExtractedText();
        if (mEditText.extractText(extractedTextRequest, et)) {
            if ((flags & GET_EXTRACTED_TEXT_MONITOR) != 0) {
                mEditText.setExtracting(extractedTextRequest);
            }
            return et;
        }
        return null;
    }

    /**
     * The default implementation returns the given amount of text from the
     * current cursor position in the buffer.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Nullable
    @Override
    public CharSequence getTextBeforeCursor(@IntRange(from = 0) int length, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getTextBeforeCursor: length=" + length + ", flags=" + flags);
        }
        Preconditions.checkArgumentNonnegative(length);

        CharSequence textBeforeCursor = getTextBeforeCursorInternal(length, flags);
        // (EW) check the setting to force returning less text than requested. BaseInputConnection
        // returns half of a surrogate pair, so we don't need any special handling to try to avoid
        // cutting off in the middle of one.
        int returnedTextLimit = Settings.getReturnedTextLimit();
        if (returnedTextLimit > 0 && textBeforeCursor != null
                && textBeforeCursor.length() > returnedTextLimit) {
            return textBeforeCursor.subSequence(textBeforeCursor.length() - returnedTextLimit,
                    textBeforeCursor.length());
        }
        return textBeforeCursor;
    }

    @Nullable
    private CharSequence getTextBeforeCursorInternal(@IntRange(from = 0) int length, int flags) {
        final Editable content = getEditable();

        int a = Selection.getSelectionStart(content);
        int b = Selection.getSelectionEnd(content);

        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }

        if (a <= 0) {
            return "";
        }

        if (length > a) {
            length = a;
        }

        if ((flags & GET_TEXT_WITH_STYLES) != 0) {
            return content.subSequence(a - length, a);
        }
        return TextUtils.substring(content, a - length, a);
    }

    /**
     * The default implementation returns the text currently selected, or null if none is
     * selected.
     */
    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    @Override
    public CharSequence getSelectedText(int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getSelectedText: flags=" + flags);
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. prior to Nougat the app would crash, so we'll mimic that (other than the
        // stack being one level off since this method did get called). the lying wrapper should
        // prevent this from getting called starting in Nougat, but if that fails we can still just
        // return null to mimic the behavior.
        if (Settings.shouldSkipGetSelectedText()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                throw new AbstractMethodError(
                        "java.lang.CharSequence android.view.inputmethod.InputConnection.getSelectedText(int)");
            }
            return null;
        }

        final Editable content = getEditable();

        int a = Selection.getSelectionStart(content);
        int b = Selection.getSelectionEnd(content);

        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }

        if (a == b || a < 0) return null;

        if ((flags & GET_TEXT_WITH_STYLES) != 0) {
            return content.subSequence(a, b);
        }
        return TextUtils.substring(content, a, b);
    }

    /**
     * The default implementation returns the given amount of text from the
     * current cursor position in the buffer.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Nullable
    @Override
    public CharSequence getTextAfterCursor(@IntRange(from = 0) int length, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getTextAfterCursor: length=" + length + ", flags=" + flags);
        }
        Preconditions.checkArgumentNonnegative(length);

        CharSequence textAfterCursor = getTextAfterCursorInternal(length, flags);
        // (EW) check the setting to force returning less text than requested. BaseInputConnection
        // returns half of a surrogate pair, so we don't need any special handling to try to avoid
        // cutting off in the middle of one.
        int returnedTextLimit = Settings.getReturnedTextLimit();
        if (returnedTextLimit > 0 && textAfterCursor != null
                && textAfterCursor.length() > returnedTextLimit) {
            return textAfterCursor.subSequence(0, returnedTextLimit);
        }
        return textAfterCursor;
    }

    @Nullable
    private CharSequence getTextAfterCursorInternal(@IntRange(from = 0) int length, int flags) {
        final Editable content = getEditable();

        int a = Selection.getSelectionStart(content);
        int b = Selection.getSelectionEnd(content);

        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }

        // Guard against the case where the cursor has not been positioned yet.
        if (b < 0) {
            b = 0;
        }

        if (b + length > content.length()) {
            length = content.length() - b;
        }


        if ((flags & GET_TEXT_WITH_STYLES) != 0) {
            return content.subSequence(b, b + length);
        }
        return TextUtils.substring(content, b, b + length);
    }

    /**
     * The default implementation returns the given amount of text around the current cursor
     * position in the buffer.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Nullable
    @Override
    public SurroundingText getSurroundingText(
            @IntRange(from = 0) int beforeLength, @IntRange(from = 0)  int afterLength, int flags) {
        if (LOG_CALLS) {
            Log.d(TAG, "getTextAfterCursor: beforeLength=" + beforeLength
                    + ", afterLength=" + afterLength + ", flags=" + flags);
        }
        Preconditions.checkArgumentNonnegative(beforeLength);
        Preconditions.checkArgumentNonnegative(afterLength);

        // (EW) check the setting to skip implementing this method to simulate either an app
        // targeting an older version or an app that takes too long to process this method.
        if (Settings.shouldSkipGetSurroundingText()) {
            return null;
        }

        SurroundingText surroundingText =
                getSurroundingTextInternal(beforeLength, afterLength, flags);

        // (EW) check the setting to force returning less text than requested.
        int returnedTextLimit = Settings.getReturnedTextLimit();
        if (returnedTextLimit > 0 && surroundingText != null) {
            int extraBefore = Math.max(0, surroundingText.getSelectionStart() - returnedTextLimit);
            int extraAfter = Math.max(0,
                    surroundingText.getText().length() - surroundingText.getSelectionEnd()
                            - returnedTextLimit);
            if (extraBefore > 0 || extraAfter > 0) {
                return new SurroundingText(
                        surroundingText.getText().subSequence(
                                extraBefore, surroundingText.getText().length() - extraAfter),
                        surroundingText.getSelectionStart() - extraBefore,
                        surroundingText.getSelectionEnd() - extraBefore,
                        surroundingText.getOffset() + extraBefore);
            }
        }

        return surroundingText;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Nullable
    private SurroundingText getSurroundingTextInternal(
            @IntRange(from = 0) int beforeLength, @IntRange(from = 0)  int afterLength, int flags) {
        final Editable content = getEditable();

        int selStart = Selection.getSelectionStart(content);
        int selEnd = Selection.getSelectionEnd(content);

        // Guard against the case where the cursor has not been positioned yet.
        if (selStart < 0 || selEnd < 0) {
            return null;
        }

        if (selStart > selEnd) {
            int tmp = selStart;
            selStart = selEnd;
            selEnd = tmp;
        }

        int contentLength = content.length();
        int startPos = selStart - beforeLength;
        int endPos = selEnd + afterLength;

        // Guards the start and end pos within range [0, contentLength].
        startPos = Math.max(0, startPos);
        endPos = Math.min(contentLength, endPos);

        CharSequence surroundingText;
        if ((flags & GET_TEXT_WITH_STYLES) != 0) {
            surroundingText = content.subSequence(startPos, endPos);
        } else {
            surroundingText = TextUtils.substring(content, startPos, endPos);
        }
        return new SurroundingText(
                surroundingText, selStart - startPos, selEnd - startPos, startPos);
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean performEditorAction(int editorAction) {
        if (LOG_CALLS) {
            Log.d(TAG, "performEditorAction: editorAction=" + editorAction);
        }
        mEditText.onEditorAction(editorAction);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean performContextMenuAction(int id) {
        if (LOG_CALLS) {
            Log.d(TAG, "performContextMenuAction: id=" + id);
        }
        mEditText.beginBatchEdit();
        mEditText.onTextContextMenuItem(id);
        mEditText.endBatchEdit();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean performPrivateCommand(String action, Bundle bundle) {
        if (LOG_CALLS) {
            Log.d(TAG, "performPrivateCommand: action=" + action + ", bundle=" + bundle);
        }
        mEditText.onPrivateIMECommand(action, bundle);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean requestCursorUpdates(int cursorUpdateMode) {
        if (LOG_CALLS) {
            Log.d(TAG, "requestCursorUpdates: cursorUpdateMode=" + cursorUpdateMode);
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. prior to Nougat the app would crash, so we'll mimic that (other than the
        // stack being one level off since this method did get called). the lying wrapper should
        // prevent this from getting called starting in Nougat, but if that fails we can still just
        // return false to mimic the behavior.
        if (Settings.shouldSkipRequestCursorUpdates()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                throw new AbstractMethodError(
                        "boolean android.view.inputmethod.InputConnection.requestCursorUpdates(int)");
            }
            return false;
        }

        // It is possible that any other bit is used as a valid flag in a future release.
        // We should reject the entire request in such a case.
        final int KNOWN_FLAGS_MASK = InputConnection.CURSOR_UPDATE_IMMEDIATE |
                InputConnection.CURSOR_UPDATE_MONITOR;
        final int unknownFlags = cursorUpdateMode & ~KNOWN_FLAGS_MASK;
        if (unknownFlags != 0) {
            //TODO: (EW) failing because of an unknown flag seems weird, but the documentation does
            // call this out. still might be a decent thing for configurable handling.
            if (DEBUG) {
                Log.d(TAG, "Rejecting requestUpdateCursorAnchorInfo due to unknown flags." +
                        " cursorUpdateMode=" + cursorUpdateMode +
                        " unknownFlags=" + unknownFlags);
            }
            return false;
        }
        if (mIMM == null) {
            // In this case, TYPE_CURSOR_ANCHOR_INFO is not handled.
            // TODO: Return some notification code rather than false to indicate method that
            // CursorAnchorInfo is temporarily unavailable.
            return false;
        }
        // (EW) AOSP version calls InputMethodManager#setUpdateCursorAnchorInfoMode, but that is
        // hidden and marked with UnsupportedAppUsage. it's used to track the mode, mostly so
        // InputMethodManager#isCursorAnchorInfoEnabled can be checked in
        // Editor.CursorAnchorInfoNotifier#updatePosition. we just need to track it separately
        // since we're not allowed to use those for some reason.
        setUpdateCursorAnchorInfoMode(cursorUpdateMode);
        if ((cursorUpdateMode & InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0) {
            if (mEditText.isInLayout()) {
                // In this case, the view hierarchy is currently undergoing a layout pass.
                // IMM#updateCursorAnchorInfo is supposed to be called soon after the layout
                // pass is finished.
            } else {
                // This will schedule a layout pass of the view tree, and the layout event
                // eventually triggers IMM#updateCursorAnchorInfo.
                mEditText.requestLayout();
            }
        }
        return true;
    }

    // (EW) from InputMethodManager
    /**
     * Return true if the current input method wants to be notified when cursor/anchor location
     * is changed.
     */
    public boolean isCursorAnchorInfoEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        synchronized (mH) {
            final boolean isImmediate = (mRequestUpdateCursorAnchorInfoMonitorMode &
                    InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0;
            final boolean isMonitoring = (mRequestUpdateCursorAnchorInfoMonitorMode &
                    InputConnection.CURSOR_UPDATE_MONITOR) != 0;
            return isImmediate || isMonitoring;
        }
    }

    // (EW) based on InputMethodManager#updateCursorAnchorInfo
    public boolean isCursorAnchorInfoModeImmediate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        synchronized (mH) {
            return (mRequestUpdateCursorAnchorInfoMonitorMode &
                    InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0;
        }
    }

    // (EW) based on InputMethodManager#updateCursorAnchorInfo
    public void clearCursorAnchorInfoModeImmediate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        synchronized (mH) {
            // Clear immediate bit (if any).
            mRequestUpdateCursorAnchorInfoMonitorMode &= ~InputConnection.CURSOR_UPDATE_IMMEDIATE;
        }
    }

    // (EW) from InputMethodManager
    /**
     * Set the requested mode for
     * {@link InputMethodManager#updateCursorAnchorInfo(View, CursorAnchorInfo)}.
     */
    private void setUpdateCursorAnchorInfoMode(int flags) {
        synchronized (mH) {
            mRequestUpdateCursorAnchorInfoMonitorMode = flags;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public Handler getHandler() {
        if (LOG_CALLS) {
            Log.d(TAG, "getHandler");
        }
        return null;
    }

    /**
     * The default implementation places the given text into the editable,
     * replacing any existing composing text.  The new text is marked as
     * in a composing state with the composing style.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (LOG_CALLS) {
            Log.d(TAG, "setComposingText: text=" + text
                    + ", newCursorPosition=" + newCursorPosition);
        }
        if (Settings.shouldModifyComposedText()) {
            // (EW) due to some weird behavior in #replaceText (see comment there), the default
            // composing span won't be added if the input is already a Spannable, so we need to keep
            // that distinction here so that this modification doesn't impact that functionality.
            boolean needsDowngradeFromSpannable = !(text instanceof Spannable);

            CharSequence composition = getComposition(getEditable());
            if (Settings.shouldModifyComposedChangesOnly() && composition != null) {
                Editable editable = getEditable();
                CharSequence currentComposition = editable.subSequence(
                        getComposingSpanStart(getEditable()),
                        getComposingSpanEnd(getEditable()));
                ChangedTextBlock changedText = Settings.shouldConsiderComposedChangesFromEnd()
                        ? ChangedTextBlock.diffEndChange(currentComposition, text)
                        : ChangedTextBlock.diff(currentComposition, text, false);
                text = modifyText(text,
                        CodePointUtils.codePointCount(changedText.unchangedBeginning),
                        CodePointUtils.codePointCount(changedText.unchangedEnd));
            } else {
                text = modifyText(text);
            }
            if (needsDowngradeFromSpannable) {
                text = new SpannedString(text);
            }
        }
        replaceText(text, newCursorPosition, true);
        return true;
    }

    private static class ChangedTextBlock {
        public final CharSequence unchangedBeginning;
        public final CharSequence changedBefore;
        public final CharSequence changedAfter;
        public final CharSequence unchangedEnd;

        private ChangedTextBlock(CharSequence unchangedBeginning, CharSequence changedBefore,
                                 CharSequence changedAfter, CharSequence unchangedEnd) {
            this.unchangedBeginning = unchangedBeginning;
            this.changedBefore = changedBefore;
            this.changedAfter = changedAfter;
            this.unchangedEnd = unchangedEnd;
        }

        /**
         * Determine a single continuous block of text that differs between two sequences. Note this
         * only evaluates the text, not other things like spans.
         * @param initialText the initial sequence of text.
         * @param updatedText the modified sequence of text.
         * @param includeWholeAmbiguousChange indicates whether the whole ambiguous portion of a
         *                                   change should be included or if it should be assumed
         *                                   that the change was at the end. for example, with an
         *                                   initial sequence of "a" and an updated sequence of
         *                                   "aa", there isn't a way to determine whether the second
         *                                   'a' was added before or after the first. in this
         *                                   example, true would return "aa" as the changed portion,
         *                                   and false would return one "a" as the unchanged
         *                                   beginning and the second "a" as the only change.
         * @return a ChangedTextBlock indicating what region of the text changed.
         */
        public static ChangedTextBlock diff(CharSequence initialText, CharSequence updatedText,
                                            boolean includeWholeAmbiguousChange) {
            if (TextUtils.equals(initialText, updatedText)) {
                return new ChangedTextBlock(initialText, "", "", "");
            }

            // get the number of characters from the beginning of both the initial and updated text
            // that match
            int startMatchLength = 0;
            for (int i = 0; i < Math.min(updatedText.length(), initialText.length()); i++) {
                if (initialText.charAt(i) == updatedText.charAt(i)) {
                    startMatchLength++;
                } else {
                    break;
                }
            }
            // get the number of characters from the end of both the initial and updated text that
            // match
            int endMatchLength = 0;
            for (int i = 1; i <= Math.min(updatedText.length(), initialText.length()); i++) {
                char initialChar = initialText.charAt(initialText.length() - i);
                char updateChar = updatedText.charAt(updatedText.length() - i);
                if (initialChar == updateChar) {
                    endMatchLength++;
                } else {
                    break;
                }
            }

            // find the number of characters that are shared between the subsequence of the matching
            // leading subsequence and the matching trailing subsequence for each of the sequences
            // (or the gap between these subsequences). these will identify the changing region or
            // the region that's ambiguous as to what specifically changed.
            // for example "abcd" -> "abbcd":
            //              ¯=__ 1    ¯¯___ 0
            // for example "abcd" -> "abzcd":
            //              ¯¯__ 0    ¯¯ __ -1
            final int initialTextMatchOverlap =
                    startMatchLength - (initialText.length() - endMatchLength);
            final int newTextMatchOverlap =
                    startMatchLength - (updatedText.length() - endMatchLength);

            int unchangedBeginningLength;
            int unchangedEndLength;
            if (initialTextMatchOverlap > 0 || newTextMatchOverlap > 0) {
                // the overlapping match means that there must be a repeated subsequence that was
                // added or removed, such as abcbcd -> abcbcbcd or abcded -> abcd. it isn't clear
                // which repeated piece was added/removed, so we'll just assume it is the last one
                // if not including the whole ambiguous piece as changed.
                if (initialTextMatchOverlap > newTextMatchOverlap) {
                    // adding text
                    unchangedBeginningLength = includeWholeAmbiguousChange
                            ? initialText.length() - endMatchLength
                            : startMatchLength;
                    unchangedEndLength = initialText.length() - startMatchLength;
                } else /*if (initialTextMatchOverlap < newTextMatchOverlap)*/ {
                    // removing text
                    unchangedBeginningLength = includeWholeAmbiguousChange
                            ? updatedText.length() - endMatchLength
                            : startMatchLength;
                    unchangedEndLength = updatedText.length() - startMatchLength;
                }
            } else {
                unchangedBeginningLength = startMatchLength;
                unchangedEndLength = endMatchLength;
            }
            int initialTextChangeEnd = initialText.length() - unchangedEndLength;
            int updatedTextChangeEnd = updatedText.length() - unchangedEndLength;

            return new ChangedTextBlock(
                    initialText.subSequence(0, unchangedBeginningLength),
                    initialText.subSequence(unchangedBeginningLength, initialTextChangeEnd),
                    updatedText.subSequence(unchangedBeginningLength, updatedTextChangeEnd),
                    initialText.subSequence(initialTextChangeEnd, initialText.length()));
        }

        /**
         * Determine a single continuous block of text that differs between two sequences assuming
         * that the change always includes the end of the text. This essentially just finds any
         * matching leading subsequence in both sequences. Note this only evaluates the text, not
         * other things like spans.
         * @param initialText the initial sequence of text.
         * @param updatedText the modified sequence of text.
         * @return a ChangedTextBlock indicating what region of the text changed.
         */
        public static ChangedTextBlock diffEndChange(CharSequence initialText,
                                                     CharSequence updatedText) {
            // get the number of characters from the beginning of both the initial and updated text
            // that match
            int startMatchLength = 0;
            for (int i = 0; i < Math.min(updatedText.length(), initialText.length()); i++) {
                if (updatedText.charAt(i) == initialText.charAt(i)) {
                    startMatchLength++;
                } else {
                    break;
                }
            }

            return new ChangedTextBlock(
                    initialText.subSequence(0, startMatchLength),
                    initialText.subSequence(startMatchLength, initialText.length()),
                    updatedText.subSequence(startMatchLength, updatedText.length()),
                    "");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    @Override
    public boolean setComposingRegion(int start, int end) {
        if (LOG_CALLS) {
            Log.d(TAG, "setComposingRegion: start=" + start + ", end=" + end);
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. prior to Nougat the app would crash, so we'll mimic that (other than the
        // stack being one level off since this method did get called). the lying wrapper should
        // prevent this from getting called starting in Nougat, but if that fails, and this does get
        // called, we can't actually mimic the behavior because the return value sent here isn't
        // actually sent to the IME, which is specifically why we need the lying wrapper. simply
        // ignoring the request and returning false wouldn't be a valid test case because the IME
        // would have no indication that we don't implement this method, so it would behave
        // unexpectedly because technically the editor would be misbehaving. we'll just log an error
        // and behave as if this setting was disabled.
        if (Settings.shouldSkipSetComposingRegion()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                throw new AbstractMethodError(
                        "boolean android.view.inputmethod.InputConnection.setComposingRegion(int, int)");
            }
            Log.e(TAG, "couldn't fake not implementing setComposingRegion");
        }

        final Editable content = getEditable();
        beginBatchEdit();
        removeComposingSpans(content);
        int a = start;
        int b = end;
        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }
        // Clip the end points to be within the content bounds.
        final int length = content.length();
        if (a < 0) a = 0;
        if (b < 0) b = 0;
        if (a > length) a = length;
        if (b > length) b = length;

        ensureDefaultComposingSpans();
        if (mDefaultComposingSpans != null) {
            for (int i = 0; i < mDefaultComposingSpans.length; ++i) {
                content.setSpan(mDefaultComposingSpans[i], a, b,
                        getCompositionSpanInclusivity() | Spanned.SPAN_COMPOSING);
            }
        }

        content.setSpan(COMPOSING, a, b,
                getCompositionSpanInclusivity() | Spanned.SPAN_COMPOSING);

        // (EW) the AOSP version also called sendCurrentText, but that does nothing since
        // mFallbackMode would be false for an EditText
        endBatchEdit();
        endComposingRegionEditInternal();
        return true;
    }

    /**
     * The default implementation changes the selection position in the
     * current editable text.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean setSelection(int start, int end) {
        if (LOG_CALLS) {
            Log.d(TAG, "setSelection: start=" + start + ", end=" + end);
        }
        final Editable content = getEditable();
        int len = content.length();
        if (start > len || end > len || start < 0 || end < 0) {
            // If the given selection is out of bounds, just ignore it.
            // Most likely the text was changed out from under the IME,
            // and the IME is going to have to update all of its state
            // anyway.
            return true;
        }
        // (EW) the AOSP version checked MetaKeyKeyListener#getMetaState with
        // MetaKeyKeyListener.META_SELECTING, which is hidden, in order to call
        // Selection#extendSelection on the edit text.
        // MetaKeyKeyListener.META_SELECTING = KeyEvent.META_SELECTING = 0x800 has been defined at
        // least since Kitkat, but it has been hidden with a comment saying it's pending API review,
        // and at least as of S, KeyEvent.META_SELECTING has been marked UnsupportedAppUsage
        // (maxTargetSdk R). after this long it seems unlikely for this to be released for apps to
        // use, and this could theoretically get changed in a future version, so it wouldn't be
        // completely safe to just hard-code 0x800. I only found this constant used in getMetaState
        // throughout AOSP code, so skipping it probably won't even cause a real lack of
        // functionality (at least currently) since other apps probably aren't using it either. same
        // basic need to skip this in ArrowKeyMovementMethod#handleMovementKey,
        // EditText.ChangeWatcher#afterTextChanged, Editor#extractTextInternal, and
        // Touch#onTouchEvent.
        Selection.setSelection(content, start, end);
        return true;
    }

    /**
     * Provides standard implementation for sending a key event to the window
     * attached to the input connection's view.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean sendKeyEvent(KeyEvent event) {
        if (LOG_CALLS) {
            Log.d(TAG, "sendKeyEvent: event=" + event);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mIMM.dispatchKeyEventFromInputMethod(mEditText, event);
        } else {
            // (EW) the AOSP version synchronized with mIMM.mH and did things with ViewRootImpl,
            // which are both hidden, so we'll just call the framework BaseInputConnection and let
            // it handle this since we have access to it, and we're not trying to do anything
            // special
            android.view.inputmethod.BaseInputConnection baseInputConnection =
                    new android.view.inputmethod.BaseInputConnection(mEditText, true);
            return baseInputConnection.sendKeyEvent(event);
        }
        return false;
    }

    /**
     * Updates InputMethodManager with the current fullscreen mode.
     */
    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    @Override
    public boolean reportFullscreenMode(boolean enabled) {
        if (LOG_CALLS) {
            Log.d(TAG, "reportFullscreenMode: enabled=" + enabled);
        }
        return true;
    }

    private void ensureDefaultComposingSpans() {
        if (mDefaultComposingSpans == null) {
            Context context;
            context = mEditText.getContext();
            if (context != null) {
                TypedArray ta = context.getTheme()
                        .obtainStyledAttributes(new int[] {
                                android.R.attr.candidatesTextStyleSpans
                        });
                CharSequence style = ta.getText(0);
                ta.recycle();
                if (style instanceof Spanned) {
                    mDefaultComposingSpans = ((Spanned)style).getSpans(
                            0, style.length(), Object.class);
                }
            }
        }
    }

    private void replaceText(CharSequence text, int newCursorPosition,
                             boolean composing) {
        final Editable content = getEditable();

        beginBatchEdit();

        // delete composing text set previously.
        int a = getComposingSpanStart(content);
        int b = getComposingSpanEnd(content);

        if (DEBUG) Log.v(TAG, "Composing span: " + a + " to " + b);

        if (b < a) {
            int tmp = a;
            a = b;
            b = tmp;
        }

        if (a != -1 && b != -1) {
            removeComposingSpans(content);
        } else {
            a = Selection.getSelectionStart(content);
            b = Selection.getSelectionEnd(content);
            if (a < 0) a = 0;
            if (b < 0) b = 0;
            if (b < a) {
                int tmp = a;
                a = b;
                b = tmp;
            }
        }

        if (composing) {
            Spannable sp;
            //TODO: (EW) this is weird. the default spans aren't added if the input is already a
            // Spannable. my best guess is to allow the IME to override the style for the
            // composition (although that may be weird since it wouldn't match when calling
            // #setComposingRegion). still, if that was the intention, I would expect it to be
            // checking for Spanned, since that's the parent that allows spans.
            if (!(text instanceof Spannable)) {
                sp = new SpannableStringBuilder(text);
                text = sp;
                ensureDefaultComposingSpans();
                if (mDefaultComposingSpans != null) {
                    for (int i = 0; i < mDefaultComposingSpans.length; ++i) {
                        sp.setSpan(mDefaultComposingSpans[i], 0, sp.length(),
                                getCompositionSpanInclusivity() | Spanned.SPAN_COMPOSING);
                    }
                }
            } else {
                sp = (Spannable)text;
            }
            setComposingSpans(sp);
        }

        if (DEBUG) {
            Log.v(TAG, "Replacing from " + a + " to " + b + " with \""
                    + text + "\", composing=" + composing
                    + ", type=" + text.getClass().getCanonicalName());

            LogPrinter lp = new LogPrinter(Log.VERBOSE, TAG);
            lp.println("Current text:");
            TextUtils.dumpSpans(content, lp, "  ");
            lp.println("Composing text:");
            TextUtils.dumpSpans(text, lp, "  ");
        }

        // Position the cursor appropriately, so that after replacing the
        // desired range of text it will be located in the correct spot.
        // This allows us to deal with filters performing edits on the text
        // we are providing here.
        if (newCursorPosition > 0) {
            newCursorPosition += b - 1;
        } else {
            newCursorPosition += a;
        }
        if (newCursorPosition < 0) newCursorPosition = 0;
        if (newCursorPosition > content.length())
            newCursorPosition = content.length();
        Selection.setSelection(content, newCursorPosition);

        content.replace(a, b, text);

        if (DEBUG) {
            LogPrinter lp = new LogPrinter(Log.VERBOSE, TAG);
            lp.println("Final text:");
            TextUtils.dumpSpans(content, lp, "  ");
        }

        endBatchEdit();
    }

    /**
     * Default implementation which invokes {@link View#performReceiveContent} on the target
     * view if the view {@link View#getReceiveContentMimeTypes allows} content insertion;
     * otherwise returns false without any side effects.
     */
    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    @Override
    public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
        if (LOG_CALLS) {
            Log.d(TAG, "commitContent: inputContentInfo=" + inputContentInfo
                    + ", flags=" + flags + ", opts=" + opts);
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. the lying wrapper should prevent this from getting called, but if that
        // fails we can still just return false to mimic the behavior.
        if (Settings.shouldSkipCommitContent()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ClipDescription description = inputContentInfo.getDescription();
            if (mEditText.getReceiveContentMimeTypes() == null) {
                if (DEBUG) {
                    Log.d(TAG, "Can't insert content from IME: content=" + description);
                }
                return false;
            }
            if ((flags & InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    inputContentInfo.requestPermission();
                } catch (Exception e) {
                    Log.w(TAG, "Can't insert content from IME; requestPermission() failed", e);
                    return false;
                }
            }
            final ClipData clip = new ClipData(inputContentInfo.getDescription(),
                    new ClipData.Item(inputContentInfo.getContentUri()));
            // (EW) the AOSP version also calls ContentInfo.Builder#setInputContentInfo, but that's
            // hidden and marked with @TestApi, and it seems to be for supporting proactive release
            // of permissions, so I don't think it's actually necessary
            final ContentInfo payload = new ContentInfo.Builder(clip, SOURCE_INPUT_METHOD)
                    .setLinkUri(inputContentInfo.getLinkUri())
                    .setExtras(opts)
                    .build();
            return mEditText.performReceiveContent(payload) == null;
        }
        // (EW) prior to S, the default implementation did nothing
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public boolean performSpellCheck() {
        if (LOG_CALLS) {
            Log.d(TAG, "performSpellCheck");
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. the lying wrapper should prevent this from getting called, but as a
        // fallback, we can still just ignore the call.
        if (Settings.shouldSkipPerformSpellCheck()) {
            // (EW) documentation notes that the return value will always be ignored from the
            // editor, and the return to the IME is whether the input connection is valid, and this
            // is the behavior I've seen in testing, so we'll just return true to match what the IME
            // would see, not that this really matters.
            return true;
        }

        mEditText.onPerformSpellCheck();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public boolean setImeConsumesInput(boolean imeConsumesInput) {
        if (LOG_CALLS) {
            Log.d(TAG, "setImeConsumesInput: imeConsumesInput=" + imeConsumesInput);
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version. the lying wrapper should prevent this from getting called, but as a
        // fallback, we can still just ignore the call.
        if (Settings.shouldSkipSetImeConsumesInput()) {
            // (EW) documentation notes that the return value will always be ignored from the
            // editor, and the return to the IME is whether the input connection is valid, and this
            // is the behavior I've seen in testing, so we'll just return true to match what the IME
            // would see, not that this really matters.
            return true;
        }

        mEditText.setImeConsumesInput(imeConsumesInput);
        return true;
    }

    public InputConnection createWrapper() {
        if (shouldSkipMethodsForOldVersionTest()
                && canLieAboutMissingMethods(mEditText.getContext())) {
            return new InputConnectionLyingWrapper(this);
        }
        // (EW) we know the wrapper won't work or isn't necessary, so don't bother with it
        return this;
    }

    /**
     * (EW) Wrapper class to lie about what methods are missing in order to mimic functionality of
     * an old app.
     *
     * The IME doesn't actually directly interact with the InputConnection that is returned from
     * View#onCreateInputConnection. Instead, InputMethodService#getCurrentInputConnection returns a
     * com.android.internal.view.InputConnectionWrapper that the IME actually interacts with.
     * That, by some means involving some sort of message dispatcher, calls
     * com.android.internal.view.IInputConnectionWrapper#executeMessage, which is what actually
     * calls the methods on the InputConnection returned from View#onCreateInputConnection.
     *
     * This is relevant because the values we return in InputConnection methods are sometimes
     * ignored, notably in #setComposingRegion and #deleteSurroundingTextInCodePoints, which we want
     * to mimic the behavior of an old app, which (on Nougat and later) would return false if it
     * wasn't implemented, but since we have to implement it and our return value is ignored, just
     * using the InputConnection normally would make it look to the IME that we support those
     * method, so if we just ignored the request, that would be a bad test, which is why this
     * wrapper is necessary.
     *
     * Prior to Nougat, when an InputConnection method was called on an app compiled against an old
     * SDK version, and didn't implement the method, the app would crash with an
     * AbstractMethodError (an old app still might implement them if it extended BaseInputConnection
     * since it would use the one from the running android version, which does implement the
     * method). This was fixed by having com.android.internal.view.InputConnectionWrapper check if
     * the method was missing prior to dispatching the message to call it. That class determines if
     * methods are missing with a bit flag of missing methods, which are passed to it's constructor.
     *
     * View#onCreateInputConnection is called by InputMethodManager#startInputInner, which
     * determines the missing methods with InputConnectionInspector#getMissingMethodFlags and passes
     * them to com.android.internal.view.IInputMethodManager#startInputOrWindowGainedFocus. That
     * somehow gets to the com.android.internal.view.InputConnectionWrapper based on reviewing some
     * stack traces in testing, but I'm not sure how to follow the code flow from the aidl to be
     * sure of how. InputConnectionInspector#getMissingMethodFlags has an optimization for the known
     * InputConnectionWrapper class, which just checks InputConnectionWrapper.getMissingMethodFlags
     * (done starting in Nougat, where the class was added, through at least S).
     * InputConnectionWrapper is an available class we can extend, and although
     * #getMissingMethodFlags is marked as hidden, we can still implement that method, which will
     * have our version called instead, where we can tell it that we don't implement methods even if
     * we technically do. Without the optimization it would just use reflection to check if the
     * method exists and isn't abstract, and I don't think there is a way to hide a method from
     * reflection.
     *
     * This is a slightly fragile hack relying on that hidden method and general understanding of
     * how this internally works, but this is just for a test setting to mimic an old app, so worst
     * case scenario, it makes this test case invalid, but we can do some defensive things to guard
     * against it if the test stops working with a newer version.
     */
    private static class InputConnectionLyingWrapper extends InputConnectionWrapper {

        /**
         * Initializes a wrapper.
         *
         * <p><b>Caveat:</b> Although the system can accept {@code (InputConnection) null} in some
         * places, you cannot emulate such a behavior by non-null {@link InputConnectionWrapper} that
         * has {@code null} in {@code target}.</p>
         *
         * @param target  the {@link InputConnection} to be proxied.
         */
        public InputConnectionLyingWrapper(InputConnection target) {
            super(target, true);
        }

        // InputConnectionWrapper#getMissingMethodFlags was added in Nougat to handle when some of
        // the InputConnection methods aren't implemented, and its signature has remained unchanged
        // through at least S.
        public int getMissingMethodFlags() {
            // since this got called, that should mean that what we return will be used, so we
            // should be able to lie about what methods were implemented
            sCanLieAboutMissingMethods = true;

            // ideally we would call InputConnectionWrapper#getMissingMethodFlags to start with the
            // real value in case methods get added in newer versions, but that is a restricted API
            // (warning logged specifies "dark greylist"), so we can't actually call it.
            int missingMethodFlags = 0;
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S) {
                // we know that we implement all of the methods through S, but we don't know if new
                // methods have been added, but the bit flag follows a  consistent pattern so far,
                // so we can just mark the rest of the bits so that if they mean anything, it should
                // accurately be marking the new methods as not implemented
                for (int i = 9; i < Integer.SIZE; i++) {
                    missingMethodFlags |= 1 << i;
                }
            }

            // unfortunately trying to access InputConnectionInspector$MissingMethodFlags fields via
            // reflection throws a NoSuchFieldException and logs a warning indicating it is on the
            // dark greylist, so we'll just have to hard-code the values here
            if (Settings.shouldSkipGetSelectedText()) {
                missingMethodFlags |= 1 << 0;
            }
            if (Settings.shouldSkipSetComposingRegion()) {
                missingMethodFlags |= 1 << 1;
            }
            if (Settings.shouldSkipCommitCorrection()) {
                // note that at least as of S, this isn't actually checked and the call causes a
                // crash
                missingMethodFlags |= 1 << 2;
            }
            if (Settings.shouldSkipRequestCursorUpdates()) {
                missingMethodFlags |= 1 << 3;
            }
            if (Settings.shouldSkipDeleteSurroundingTextInCodePoints()) {
                missingMethodFlags |= 1 << 4;
            }
            // skipping getHandler since that currently always just returns null
            if (Settings.shouldSkipCloseConnection()) {
                missingMethodFlags |= 1 << 6;
            }
            if (Settings.shouldSkipCommitContent()) {
                missingMethodFlags |= 1 << 7;
            }
            if (Settings.shouldSkipGetSurroundingText()) {
                missingMethodFlags |= 1 << 8;
            }
            // there are no flags for skipPerformSpellCheck or setImeConsumesInput

            return missingMethodFlags;
        }
    }

    private boolean shouldSkipMethodsForOldVersionTest() {
        return Settings.shouldSkipGetSelectedText()
                || Settings.shouldSkipSetComposingRegion()
                || Settings.shouldSkipCommitCorrection()
                || Settings.shouldSkipRequestCursorUpdates()
                || Settings.shouldSkipDeleteSurroundingTextInCodePoints()
                || Settings.shouldSkipCloseConnection()
                || Settings.shouldSkipCommitContent()
                || Settings.shouldSkipGetSurroundingText();
    }

    public static boolean canLieAboutMissingMethods(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // tracking missing methods was added in Nougat, so it won't work before that
            return false;
        }

        // avoid checking this multiple times since it shouldn't be changing
        if (sHasCheckedCanLieAboutMissingMethods) {
            return sCanLieAboutMissingMethods;
        }

        EditText testEditText = new EditText(context);
        EditableInputConnection testInputConnection = new EditableInputConnection(testEditText);

        // InputConnectionWrapper's constructor should call
        // InputConnectionInspector#getMissingMethodFlags on the InputConnection it wraps (it has
        // done this from Nougat through at least S), and that should use the optimization and check
        // InputConnectionWrapper#getMissingMethodFlags, which should call our replacement, which
        // will set sCanLieAboutMissingMethods, and if that doesn't happen, that probably means our
        // lying wrapper doesn't work anymore. we're not actually checking the full code path, which
        // would be calling InputMethodManager#restartInput, but that doesn't seem to work on a
        // random view not attached to a layout, and it might do some async things that would break
        // this synchronous check. we also can't just call
        // InputConnectionWrapper#getMissingMethodFlags because that is a restricted API (warning
        // logged specifies "dark greylist"), so we can't directly validate that much exists. this
        // isn't perfect, but it currently seems like the best option.
        new InputConnectionWrapper(new InputConnectionLyingWrapper(testInputConnection), true);
        sHasCheckedCanLieAboutMissingMethods = true;
        return sCanLieAboutMissingMethods;
    }
}
