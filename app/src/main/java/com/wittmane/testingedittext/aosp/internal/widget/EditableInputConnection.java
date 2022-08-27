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
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.Settings;
import com.wittmane.testingedittext.aosp.internal.util.Preconditions;
import com.wittmane.testingedittext.aosp.widget.EditText;

import static android.view.ContentInfo.SOURCE_INPUT_METHOD;

// (EW) this is a merge of EditableInputConnection and BaseInputConnection to be able to insert
// custom behavior
/**
 * Base class for an editable InputConnection instance. This is created by {@link EditText}.
 */
public class EditableInputConnection implements InputConnection {
    private static final boolean DEBUG = false;
    private static final boolean LOG_CALLS = true;
    private static final String TAG = EditableInputConnection.class.getSimpleName();
    private static final Object COMPOSING = new ComposingText();
    private static final int INVALID_INDEX = -1;

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
        replaceText(text, newCursorPosition, false);
        // (EW) the AOSP version also called sendCurrentText, but that does nothing since
        // mFallbackMode would be false for an EditText
        return true;
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
        replaceText(text, newCursorPosition, true);
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    @Override
    public boolean setComposingRegion(int start, int end) {
        if (LOG_CALLS) {
            Log.d(TAG, "setComposingRegion: start=" + start + ", end=" + end);
        }

        // (EW) check the setting to skip implementing this method to simulate an app targeting an
        // older version
        if (Settings.shouldSkipSetComposingRegion()) {
            return false;
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
        mEditText.onPerformSpellCheck();
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public boolean setImeConsumesInput(boolean imeConsumesInput) {
        if (LOG_CALLS) {
            Log.d(TAG, "setImeConsumesInput: imeConsumesInput=" + imeConsumesInput);
        }
        mEditText.setImeConsumesInput(imeConsumesInput);
        return true;
    }
}
