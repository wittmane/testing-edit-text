/*
 * Copyright (C) 2025-2026 Eli Wittman
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

package com.wittmane.testingedittext.util;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.wittmane.testingedittext.function.Consumer;

/**
 * Helper class to manage a Preference's summary and title to support long text. When the summary is
 * too long, the text will be ellipsized and long pressing will allow viewing the full text. This
 * also supports managing a value summary in addition to the summary description of the Preference.
 * To use this, the Preference should override {@link Preference#setSummary} and call
 * {@link #onSetSummary} instead of {@code super.setSummary}. Preferences that inherit from
 * {@link TwoStatePreference} should similarly override {@link TwoStatePreference#setSummaryOn},
 * {@link TwoStatePreference#setSummaryOff}, and {@link TwoStatePreference#setChecked} to call
 * {@link #onSetSummaryOn}, {@link #onSetSummaryOff}, and {@link #onSetChecked}
 * ({@code super.setChecked} should still be called). The Preference should also call
 * {@link #onBindView} in {@link Preference#onBindView}.
 */
public class PreferenceSummaryManager {
    private static final String TAG = PreferenceSummaryManager.class.getSimpleName();

    private static final CharSequence DEFAULT_ELLIPSIS = "\u2026";
    private static final char ZERO_WIDTH_NO_BREAK_SPACE = '\uFEFF';
    private static final int SUMMARY_PART_DIVIDER_NEW_LINE_COUNT = 2;
    private static final String SUMMARY_PART_DIVIDER;
    static {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SUMMARY_PART_DIVIDER_NEW_LINE_COUNT; i++) {
            sb.append('\n');
        }
        SUMMARY_PART_DIVIDER = sb.toString();
    }

    private final Preference mPref;
    private final Runnable mPrefOnClick;
    private final Consumer<CharSequence> mPrefSuperSetSummary;
    private final Consumer<CharSequence> mPrefSuperSetSummaryOn;
    private final Consumer<CharSequence> mPrefSuperSetSummaryOff;

    private CharSequence mDescriptionSummary;
    private CharSequence mDescriptionSummaryOn;
    private CharSequence mDescriptionSummaryOff;
    private CharSequence mValueSummary;

    private View mView;
    private TextView mTitleTextView;
    private TextView mSummaryTextView;

    private final EllipsisManager mEllipsisManager = new EllipsisManager();

    /**
     * Create a manager for a Preference's summary.
     * @param pref The Preference to manage.
     * @param onClick The Preference's {@code super} {@link Preference#onClick} method (since
     *                that's protected).
     * @param superSetSummary The Preference's {@code super} {@link Preference#setSummary} method
     *                        (since the Preference should have overridden the method to call the
     *                        {@link #onSetSummary} and this helper class is meant to manage actual
     *                        calls to that).
     */
    public PreferenceSummaryManager(Preference pref, Runnable onClick,
                                    Consumer<CharSequence> superSetSummary) {
        this(pref, onClick, superSetSummary, null, null);
    }

    /**
     *
     * Create a manager for a Preference's summary.
     * @param pref The Preference to manage.
     * @param onClick The Preference's {@code super} {@link Preference#onClick} method (since
     *                that's protected).
     * @param superSetSummary The Preference's {@code super} {@link Preference#setSummary} method
     *                        (since the Preference should have overridden the method to call the
     *                        {@link #onSetSummary} and this helper class is meant to manage actual
     *                        calls to that).
     * @param superSetSummaryOn The Preference's {@code super}
     *                          {@link TwoStatePreference#setSummaryOn} method (since the Preference
     *                          should have overridden the method to call the
     *                          {@link #onSetSummaryOn} and this helper class is meant to manage
     *                          actual calls to that).
     * @param superSetSummaryOff The Preference's {@code super}
     *                          {@link TwoStatePreference#setSummaryOff} method (since the
     *                          Preference should have overridden the method to call the
     *                          {@link #onSetSummaryOff} and this helper class is meant to manage
     *                          actual calls to that).
     */
    public PreferenceSummaryManager(Preference pref, Runnable onClick,
                                    Consumer<CharSequence> superSetSummary,
                                    Consumer<CharSequence> superSetSummaryOn,
                                    Consumer<CharSequence> superSetSummaryOff) {
        mPref = pref;
        mPrefOnClick = onClick;
        mPrefSuperSetSummary = superSetSummary;
        mPrefSuperSetSummaryOn = superSetSummaryOn;
        mPrefSuperSetSummaryOff = superSetSummaryOff;
        mDescriptionSummary = mPref.getSummary();
        if (mPref instanceof TwoStatePreference) {
            mDescriptionSummaryOn = ((TwoStatePreference) mPref).getSummaryOn();
            mDescriptionSummaryOff = ((TwoStatePreference) mPref).getSummaryOff();
        }
    }

    /**
     * Handler for the managed Preference to call from {@link Preference#setSummary} instead of the
     * {@code super} call.
     * This sets the primary (description) summary for the Preference. For a secondary summary for
     * the Preference's value, use {@link #onSetValueSummary}.
     * @param summary The (description) summary to use.
     */
    public void onSetSummary(CharSequence summary) {
        if (mDescriptionSummary != null && mDescriptionSummary.equals(summary)) {
            // this value was already set, so nothing needs to be done
            return;
        }
        mDescriptionSummary = summary;
        mEllipsisManager.updateSummary();
    }

    /**
     * Handler for the managed Preference to call from {@link TwoStatePreference#setSummaryOn}
     * instead of the {@code super} call.
     * This sets the primary (description) summary for the Preference. For a secondary summary for
     * the Preference's value, use {@link #onSetValueSummary}.
     * @param summaryOn The (description) summary to be shown when checked.
     */
    public void onSetSummaryOn(CharSequence summaryOn) {
        if (mDescriptionSummaryOn != null && mDescriptionSummaryOn.equals(summaryOn)) {
            // this value was already set, so nothing needs to be done
            return;
        }
        mDescriptionSummaryOn = summaryOn;
        mEllipsisManager.updateSummary();
    }

    /**
     * Handler for the managed Preference to call from {@link TwoStatePreference#setSummaryOff}
     * instead of the {@code super} call.
     * This sets the primary (description) summary for the Preference. For a secondary summary for
     * the Preference's value, use {@link #onSetValueSummary}.
     * @param summaryOff The (description) summary to be shown when unchecked.
     */
    public void onSetSummaryOff(CharSequence summaryOff) {
        if (mDescriptionSummaryOff != null && mDescriptionSummaryOff.equals(summaryOff)) {
            // this value was already set, so nothing needs to be done
            return;
        }
        mDescriptionSummaryOff = summaryOff;
        mEllipsisManager.updateSummary();
    }

    /**
     * Handler for the managed Preference to call from {@link TwoStatePreference#setChecked}
     * after the {@code super} call to manage updating the summary with the new value.
     * This sets the primary (description) summary for the Preference. For a secondary summary for
     * the Preference's value, use {@link #onSetValueSummary}.
     * @param checked The checked state.
     */
    public void onSetChecked(boolean checked) {
        // refresh the summary if there are different on/off summaries
        if (mPref instanceof TwoStatePreference
                && (!TextUtils.isEmpty(mDescriptionSummaryOn)
                        || !TextUtils.isEmpty(mDescriptionSummaryOff))) {
            mEllipsisManager.updateSummary();
        }
    }

    private CharSequence getFullSummary() {
        CharSequence currentDescriptionSummary = getCurrentDescriptionSummary();
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(currentDescriptionSummary)) {
            sb.append(currentDescriptionSummary);
        }
        if (!TextUtils.isEmpty(currentDescriptionSummary) && !TextUtils.isEmpty(mValueSummary)) {
            sb.append(SUMMARY_PART_DIVIDER);
        }
        if (!TextUtils.isEmpty(mValueSummary)) {
            sb.append(mValueSummary);
        }
        return sb;
    }

    private CharSequence getCurrentDescriptionSummary() {
        return getCurrentSummary(true);
    }

    private CharSequence getCurrentDisplayedSummary() {
        return getCurrentSummary(false);
    }

    private CharSequence getCurrentSummary(boolean getDescriptionSummary) {
        if (mPref instanceof TwoStatePreference) {
            // based on logic from TwoStatePreference#syncSummaryView
            boolean isChecked = ((TwoStatePreference) mPref).isChecked();
            CharSequence summaryOn = getDescriptionSummary
                    ? mDescriptionSummaryOn
                    : ((TwoStatePreference) mPref).getSummaryOn();
            if (isChecked && !TextUtils.isEmpty(summaryOn)) {
                return summaryOn;
            }
            CharSequence summaryOff = getDescriptionSummary
                    ? mDescriptionSummaryOff
                    : ((TwoStatePreference) mPref).getSummaryOff();
            if (!isChecked && !TextUtils.isEmpty(summaryOff)) {
                return summaryOff;
            }
        }
        return getDescriptionSummary ? mDescriptionSummary : mPref.getSummary();
    }

    private void setCurrentSuperSummary(CharSequence summary) {
        if (mPref instanceof TwoStatePreference) {
            // based on logic from TwoStatePreference#syncSummaryView
            boolean isChecked = ((TwoStatePreference) mPref).isChecked();
            if (isChecked && !TextUtils.isEmpty(mDescriptionSummaryOn)
                    && mPrefSuperSetSummaryOn != null) {
                if (!TextUtils.equals(summary, ((TwoStatePreference) mPref).getSummaryOn())) {
                    mPrefSuperSetSummaryOn.accept(summary);
                }
                return;
            }
            if (!isChecked && !TextUtils.isEmpty(mDescriptionSummaryOff)
                    && mPrefSuperSetSummaryOff != null) {
                if (!TextUtils.equals(summary, ((TwoStatePreference) mPref).getSummaryOff())) {
                    mPrefSuperSetSummaryOff.accept(summary);
                }
                return;
            }
        }
        if (!TextUtils.equals(summary, mPref.getSummary())) {
            mPrefSuperSetSummary.accept(summary);
        }
    }

    /**
     * Set the secondary summary for the Preference to display the value (as opposed to the regular
     * summary as a description of the Preference).
     * @param summary The display text for the current value of the Preference.
     */
    public void onSetValueSummary(CharSequence summary) {
        if (mValueSummary != null && mValueSummary.equals(summary)) {
            // this value was already set, so nothing needs to be done
            return;
        }
        mValueSummary = summary;
        mEllipsisManager.updateSummary();
    }

    private class EllipsisManager implements OnPreDrawListener {
        private int mPreDrawCallCount = 0;
        private boolean mIsAttached = false;
        private boolean mWasMeasured;
        private boolean mIsSummaryFlipped;
        private boolean mIsPartialSummary;
        CharSequence mEllipsis;
        int[] mDescriptionSummaryMaxRowsCharCounts;
        int[] mValueSummaryMaxRowsCharCounts;
        int[] mDescriptionSummaryShownRowsCharCounts;
        int[] mValueSummaryShownRowsCharCounts;

        public synchronized boolean wasMeasured() {
            return mWasMeasured;
        }

        public synchronized boolean isMeasuring() {
            return !mWasMeasured && mIsAttached;
        }

        public synchronized boolean isDescriptionSummaryEllipsized() {
            CharSequence currentDescriptionSummary = getCurrentDescriptionSummary();
            if (TextUtils.isEmpty(currentDescriptionSummary)) {
                // nothing to ellipsize
                return false;
            }
            if (!mWasMeasured) {
                // until we have measured the text, we'll have to assume that the text will be
                // ellipsized to ensure we set up the click listener for that in case we never get a
                // chance to manage the ellipsis properly
                return true;
            }
            int descriptionSummaryAllowedLength =
                    getSummaryAllowedLength(true, mSummaryTextView.getMaxLines());
            return descriptionSummaryAllowedLength < currentDescriptionSummary.length();
        }

        public synchronized boolean isValueSummaryEllipsized() {
            if (TextUtils.isEmpty(mValueSummary)) {
                // nothing to ellipsize
                return false;
            }
            if (!mWasMeasured) {
                // until we have measured the text, we'll have to assume that the text will be
                // ellipsized to ensure we set up the click listener for that in case we never get a
                // chance to manage the ellipsis properly
                return true;
            }
            int valueSummaryAllowedLength =
                    getSummaryAllowedLength(false, mSummaryTextView.getMaxLines());
            return valueSummaryAllowedLength < mValueSummary.length();
        }

        public synchronized boolean isEllipsized() {
            return isDescriptionSummaryEllipsized() || isValueSummaryEllipsized();
        }

        public synchronized void updateSummary() {
            CharSequence currentDescriptionSummary = getCurrentDescriptionSummary();
            if (TextUtils.isEmpty(currentDescriptionSummary) && TextUtils.isEmpty(mValueSummary)) {
                // we don't need a long click listener since there is no summary text
                setClickListeners(false);
                mIsSummaryFlipped = false;
                mIsPartialSummary = false;
                setCurrentSuperSummary(null);
                mWasMeasured = true;
            } else {
                // set up measuring for whether the summary will be cut off
                mWasMeasured = false;
                boolean canMeasure = prepEllipsisMeasuring();
                mIsPartialSummary = false;
                if (TextUtils.isEmpty(mValueSummary)) {
                    mIsSummaryFlipped = false;
                    setCurrentSuperSummary(currentDescriptionSummary);
                } else if (TextUtils.isEmpty(currentDescriptionSummary)) {
                    mIsSummaryFlipped = false;
                    setCurrentSuperSummary(mValueSummary);
                } else if (canMeasure) {
                    // since we hooked up the pre-draw listener, temporarily set the value first to
                    // make sure that can be fully shown. this will be blocked and reset to the
                    // correct order in the pre-draw listener, where we'll be able to see how much
                    // of the description summary will get ellipsized so that we can manually add
                    // the ellipsis to prevent the value at the end from being hidden.
                    mIsSummaryFlipped = true;
                    setCurrentSuperSummary(new StringBuilder()
                            .append(mValueSummary)
                            .append(SUMMARY_PART_DIVIDER)
                            .append(currentDescriptionSummary));
                } else {
                    mIsSummaryFlipped = false;
                    setCurrentSuperSummary(new StringBuilder()
                            .append(currentDescriptionSummary)
                            .append(SUMMARY_PART_DIVIDER)
                            .append(mValueSummary));
                }
            }
        }

        private synchronized boolean prepEllipsisMeasuring() {
            // reset the counter regardless of whether we actually attach. if we're not attached and
            // aren't going to, this won't do anything, but if we're already attached, presumably
            // getting called again means that we're changing something with the text, so we'll
            // probably want the counter reset to manage the new change rather than bail out of this
            // update due to an old update that looped too many times.
            mPreDrawCallCount = 0;

            if (mIsAttached) {
                return true;
            }

            if (mSummaryTextView == null) {
                // nothing to attach to that would allow measuring
                return false;
            }

            mEllipsis = null;
            mDescriptionSummaryMaxRowsCharCounts = null;
            mValueSummaryMaxRowsCharCounts = null;
            mDescriptionSummaryShownRowsCharCounts = null;
            mValueSummaryShownRowsCharCounts = null;
            setClickListeners(isEllipsized());

            // wait for the summary text view to pre-draw to be able to determine how much text will
            // get ellipsized to be able to flip the order and ellipsize the appropriate text in the
            // middle manually
            ViewTreeObserver textViewTreeObserver = mSummaryTextView.getViewTreeObserver();
            textViewTreeObserver.addOnPreDrawListener(this);
            mIsAttached = true;
            return true;
        }

        private synchronized void detach() {
            ViewTreeObserver textViewTreeObserver = mSummaryTextView.getViewTreeObserver();
            textViewTreeObserver.removeOnPreDrawListener(this);
            mIsAttached = false;
        }

        @Override
        public synchronized boolean onPreDraw() {
            if (++mPreDrawCallCount > 10) {
                // don't bother logging a warning if the preference isn't attached to a preference
                // screen since this view is probably just reused by something else, causing it to
                // perpetually have the wrong text
                if (PreferenceUtils.getParentScreen(mPref) != null) {
                    // too many pre-draw calls. what we're trying to do doesn't seem to be working.
                    // bail out.
                    Log.w(TAG, mPref.getKey() + ": too many onPreDraw calls");
                }
                detach();
                // make sure the text order is correct first.
                if (mIsSummaryFlipped) {
                    mIsSummaryFlipped = false;
                    mIsPartialSummary = false;
                    setCurrentSuperSummary(getFullSummary());
                    // cancel this drawing pass since we need to flip the summary order back
                    return false;
                }
                return true;
            }

            Layout layout = mSummaryTextView.getLayout();
            if (layout == null) {
                // we can't tell if an ellipsis is shown. wait for a later pre-draw where the layout
                // is populated to be able to check what text will be visible or cut off. cancel
                // this drawing pass since the text may be flipped.
                return false;
            }

            // determine how much of either piece of the summary is ellipsized
            EllipsisMeasurement measurement = measureEllipsizedContent(layout,
                    mSummaryTextView.getMaxLines(), getSummaryParts());

            if (measurement == null) {
                // the text didn't match, which means the layout must still have some older text.
                // this seems to happen when a view gets reused (has the text from some other
                // preference that is now off the screen), and it will eventually update to the text
                // for this preference. this also seems to happen when scrolling in and out of view
                // quickly, so we might never get back to this, so we need the counter to avoid
                // infinitely looping. allow the drawing pass to continue because we're only trying
                // to block the draw for flipping the text (if the text is from something old or a
                // different preference, either the framework should be managing this or it would
                // have already been a minor visual issue regardless of this handling) and
                // repeatedly blocking something could cause freezing issues.
                return true;
            }

            // determine if everything necessary has been measured or set up the next piece to be
            // measured
            if (handleNextPassSetup(measurement)) {
                // cancel this drawing pass since we needed to change the text for the next measure
                return false;
            }
            mWasMeasured = true;

            // we managed ellipsis, so we don't need to keep listening
            detach();

            // the click listener is to show the full description summary if it is ellipsized
            setClickListeners(isEllipsized());

            if (mEllipsis != null || mIsSummaryFlipped || mIsPartialSummary) {
                // set the summary with the appropriate artificial ellipsis or simply flip the
                // summary back to the correct order
                setFinalEllipsizedSummary();

                // cancel this drawing pass since we needed to update the summary
                return false;
            }

            // text didn't need to change, so this drawing pass can continue
            return true;
        }

        private CharSequence[] getSummaryParts() {
            int maxLines = mSummaryTextView.getMaxLines();
            CharSequence currentDescriptionSummary = getCurrentDescriptionSummary();
            CharSequence summaryPart1;
            CharSequence summaryPart2;
            if (mIsSummaryFlipped) {
                if (mIsPartialSummary) {
                    int valueSummaryAllowedLines = getSummaryAllowedLines(false, maxLines);
                    summaryPart1 = getPartialSummary(mValueSummary,
                            mValueSummaryMaxRowsCharCounts, valueSummaryAllowedLines);
                } else {
                    summaryPart1 = mValueSummary;
                }
                summaryPart2 = currentDescriptionSummary;
            } else {
                if (mIsPartialSummary) {
                    int descriptionSummaryAllowedLines = getSummaryAllowedLines(true, maxLines);
                    summaryPart1 = getPartialSummary(currentDescriptionSummary,
                            mDescriptionSummaryMaxRowsCharCounts, descriptionSummaryAllowedLines);
                } else {
                    summaryPart1 = currentDescriptionSummary;
                }
                summaryPart2 = mValueSummary;
            }
            return new CharSequence[] { summaryPart1, summaryPart2 };
        }

        private boolean handleNextPassSetup(EllipsisMeasurement measurement) {
            int maxLines = mSummaryTextView.getMaxLines();
            CharSequence currentDescriptionSummary = getCurrentDescriptionSummary();
            if (TextUtils.isEmpty(currentDescriptionSummary) || TextUtils.isEmpty(mValueSummary)) {
                // only need a single pass
                mEllipsis = measurement.ellipsis;
                mDescriptionSummaryMaxRowsCharCounts =
                        measurement.summaryPartsVisibleRowCharCounts[0];
                mValueSummaryMaxRowsCharCounts = measurement.summaryPartsVisibleRowCharCounts[1];
            } else if (mIsSummaryFlipped && mValueSummaryMaxRowsCharCounts == null) {
                // first pass - primarily measuring the value summary (effectively on its own)
                mEllipsis = measurement.ellipsis;
                mValueSummaryMaxRowsCharCounts = measurement.summaryPartsVisibleRowCharCounts[0];
                int [] descriptionSummaryRowsCharCounts =
                        measurement.summaryPartsVisibleRowCharCounts[1];
                // if the description summary is entirely visible, nothing is ellipsized
                if (sum(descriptionSummaryRowsCharCounts) >= currentDescriptionSummary.length()) {
                    // since everything is visible, we're fully measured
                    mDescriptionSummaryMaxRowsCharCounts = descriptionSummaryRowsCharCounts;
                } else {
                    // something needs to be ellipsized, but we only measured how much of the value
                    // fits or how much of the description fits when the whole value fits. we still
                    // need to determine how much of the description fits in the normal order.
                    mIsSummaryFlipped = false;
                    mIsPartialSummary = false;
                    setCurrentSuperSummary(new StringBuilder()
                            .append(currentDescriptionSummary)
                            .append(SUMMARY_PART_DIVIDER)
                            .append(mValueSummary));
                    // cancel this drawing pass since we need to change the text for the next
                    // measure
                    return true;
                }
            } else if (mDescriptionSummaryMaxRowsCharCounts == null) {
                // second pass - measuring the description summary (effectively on its own)
                mDescriptionSummaryMaxRowsCharCounts =
                        measurement.summaryPartsVisibleRowCharCounts[0];
                int descriptionSummaryAllowedLines = getSummaryAllowedLines(true, maxLines);
                int valueSummaryAllowedLines = getSummaryAllowedLines(false, maxLines);
                if (descriptionSummaryAllowedLines != mDescriptionSummaryMaxRowsCharCounts.length) {
                    // the description summary needs to be ellipsized, but we didn't measure how
                    // much of the last line we're planning on showing will fit when the ellipsis is
                    // on that line
                    mIsSummaryFlipped = true;
                    mIsPartialSummary = true;
                    StringBuilder sb = new StringBuilder();
                    CharSequence partialValueSummary = getPartialSummary(mValueSummary,
                            mValueSummaryMaxRowsCharCounts, valueSummaryAllowedLines);
                    sb.append(partialValueSummary);
                    sb.append(SUMMARY_PART_DIVIDER);
                    sb.append(currentDescriptionSummary);
                    setCurrentSuperSummary(sb);
                    // cancel this drawing pass since we need to change the text for the next
                    // measure
                    return true;
                } else if (valueSummaryAllowedLines != mValueSummaryMaxRowsCharCounts.length) {
                    // the value summary needs to be ellipsized, but we didn't measure how much of
                    // the last line we're planning on showing will fit when the ellipsis is on that
                    // line
                    mIsSummaryFlipped = false;
                    mIsPartialSummary = true;
                    StringBuilder sb = new StringBuilder();
                    CharSequence partialDescriptionSummary = getPartialSummary(
                            currentDescriptionSummary, mDescriptionSummaryMaxRowsCharCounts,
                            descriptionSummaryAllowedLines);
                    sb.append(partialDescriptionSummary);
                    sb.append(SUMMARY_PART_DIVIDER);
                    sb.append(mValueSummary);
                    setCurrentSuperSummary(sb);
                    // cancel this drawing pass since we need to change the text for the next
                    // measure
                    return true;
                }
            } else if (mIsSummaryFlipped) {
                // third pass - measuring the description summary for only the rows that will be shown
                mDescriptionSummaryShownRowsCharCounts = measurement.summaryPartsVisibleRowCharCounts[1];
                int descriptionSummaryAllowedLines = getSummaryAllowedLines(true, maxLines);
                int valueSummaryAllowedLines = getSummaryAllowedLines(false, maxLines);
                if (valueSummaryAllowedLines != mValueSummaryMaxRowsCharCounts.length) {
                    // the value summary needs to be ellipsized, but we didn't measure how much of
                    // the last line we're planning on showing will fit when the ellipsis is on that
                    // line
                    mIsSummaryFlipped = false;
                    mIsPartialSummary = true;
                    StringBuilder sb = new StringBuilder();
                    CharSequence partialDescriptionSummary = getPartialSummary(
                            currentDescriptionSummary, mDescriptionSummaryMaxRowsCharCounts,
                            descriptionSummaryAllowedLines);
                    sb.append(partialDescriptionSummary);
                    sb.append(SUMMARY_PART_DIVIDER);
                    sb.append(mValueSummary);
                    setCurrentSuperSummary(sb);
                    // cancel this drawing pass since we need to change the text for the next
                    // measure
                    return true;
                }
            } else {
                // last pass (third or fourth depending on need) - measuring the value summary for
                // only the rows that will be shown
                mValueSummaryShownRowsCharCounts = measurement.summaryPartsVisibleRowCharCounts[1];
            }

            // no additional passes are needed
            return false;
        }

        private void setFinalEllipsizedSummary() {
            int maxLines = mSummaryTextView.getMaxLines();
            CharSequence currentDescriptionSummary = getCurrentDescriptionSummary();
            StringBuilder sb = new StringBuilder();
            int descriptionSummaryAllowedLength = getSummaryAllowedLength(true, maxLines);
            int valueSummaryAllowedLength = getSummaryAllowedLength(false, maxLines);
            if (descriptionSummaryAllowedLength > 0) {
                if (descriptionSummaryAllowedLength < currentDescriptionSummary.length()) {
                    CharSequence visibleDescriptionSummary =
                            currentDescriptionSummary.subSequence(0,
                                    descriptionSummaryAllowedLength);
                    sb.append(visibleDescriptionSummary);
                    sb.append(mEllipsis);
                } else {
                    sb.append(currentDescriptionSummary);
                }
            }
            if (descriptionSummaryAllowedLength > 0 && valueSummaryAllowedLength > 0) {
                sb.append(SUMMARY_PART_DIVIDER);
            }
            if (valueSummaryAllowedLength > 0) {
                if (valueSummaryAllowedLength < mValueSummary.length()) {
                    CharSequence visibleValueSummary =
                            mValueSummary.subSequence(0, valueSummaryAllowedLength);
                    sb.append(visibleValueSummary);
                    sb.append(mEllipsis);
                } else {
                    sb.append(mValueSummary);
                }
            }
            setCurrentSuperSummary(sb);
            mIsSummaryFlipped = false;
        }

        private int getSummaryAllowedLines(boolean getDescriptionSummary, int maxLines) {
            if ((mDescriptionSummaryMaxRowsCharCounts == null
                    && !TextUtils.isEmpty(getCurrentDescriptionSummary()))
                    || (mValueSummaryMaxRowsCharCounts == null
                            && !TextUtils.isEmpty(mValueSummary))) {
                // not sufficiently measured to determine yet
                return -1;
            }

            int descriptionSummaryVisibleRowCount = mDescriptionSummaryMaxRowsCharCounts != null
                    ? mDescriptionSummaryMaxRowsCharCounts.length
                    : 0;
            int valueSummaryVisibleRowCount = mValueSummaryMaxRowsCharCounts != null
                    ? mValueSummaryMaxRowsCharCounts.length
                    : 0;

            // if both parts need to be shown, reduce the max by the number of blank lines between
            // the parts
            int maxSummaryLines = maxLines
                    - (descriptionSummaryVisibleRowCount == 0 || valueSummaryVisibleRowCount == 0
                            ? 0
                            : (SUMMARY_PART_DIVIDER_NEW_LINE_COUNT - 1));

            int descriptionSummaryAllowedRows;
            int valueSummaryAllowedRows;
            if (descriptionSummaryVisibleRowCount <= maxSummaryLines / 2) {
                descriptionSummaryAllowedRows = descriptionSummaryVisibleRowCount;
                valueSummaryAllowedRows = Math.min(maxSummaryLines - descriptionSummaryAllowedRows,
                        valueSummaryVisibleRowCount);
            } else if (valueSummaryVisibleRowCount <= maxSummaryLines / 2) {
                valueSummaryAllowedRows = valueSummaryVisibleRowCount;
                descriptionSummaryAllowedRows = Math.min(maxSummaryLines - valueSummaryAllowedRows,
                        descriptionSummaryVisibleRowCount);
            } else {
                descriptionSummaryAllowedRows = maxSummaryLines / 2;
                valueSummaryAllowedRows = maxSummaryLines - descriptionSummaryAllowedRows;
            }

            return getDescriptionSummary ? descriptionSummaryAllowedRows : valueSummaryAllowedRows;
        }

        private int getSummaryAllowedLength(boolean getDescriptionSummary, int maxLines) {
            int summaryAllowedLines = getSummaryAllowedLines(getDescriptionSummary, maxLines);
            if (summaryAllowedLines < 0) {
                return -1;
            }

            return sum(getDescriptionSummary
                    ? (mDescriptionSummaryShownRowsCharCounts != null
                            ? mDescriptionSummaryShownRowsCharCounts
                            : mDescriptionSummaryMaxRowsCharCounts)
                    : (mValueSummaryShownRowsCharCounts != null
                            ? mValueSummaryShownRowsCharCounts
                            : mValueSummaryMaxRowsCharCounts),
                    summaryAllowedLines);
        }
    }

    private static CharSequence getPartialSummary(CharSequence summaryPiece,
                                                  int[] visibleRowCharacters,
                                                  int allowedLines) {

        int partialDescriptionSummaryLength = sum(visibleRowCharacters, allowedLines);
        // trim the last new line that may be included since one will get added to split the summary
        // parts
        if (summaryPiece.charAt(partialDescriptionSummaryLength - 1) == '\n') {
            partialDescriptionSummaryLength--;
        }
        // trim any trailing spaces. at least on some versions (seen on kitkat), when there is a
        // trailing space before a new line (eg the first part of a partial summary) and there isn't
        // enough room to put an ellipsis (possibly even with removing the space), it seems to
        // ellipsize the end of that line (in the middle of the TextView) and add an extra blank
        // line. I assume that's just a bug in the framework, but we can avoid it by removing the
        // trailing space.
        while (partialDescriptionSummaryLength > 0
                && Character.isWhitespace(
                        summaryPiece.charAt(partialDescriptionSummaryLength - 1))) {
            partialDescriptionSummaryLength--;
        }

        return summaryPiece.subSequence(0, partialDescriptionSummaryLength);
    }

    private static int sum(int[] list) {
        return sum(list, -1);
    }

    private static int sum(int[] list, int firstN) {
        int sum = 0;
        if (firstN < 0) {
            firstN = list.length;
        } else if (firstN > list.length) {
            firstN = list.length;
        }
        for (int i = 0; i < firstN; i++) {
            sum += list[i];
        }
        return sum;
    }

    private static class EllipsisMeasurement {
        CharSequence ellipsis;
        int[][] summaryPartsVisibleRowCharCounts;
    }

    private EllipsisMeasurement measureEllipsizedContent(Layout layout, int maxLines,
                                                         CharSequence[] summaryParts) {
        CharSequence[] populatedSummaryParts = filterOutEmpty(summaryParts);
        EllipsisMeasurement measurement = new EllipsisMeasurement();

        CharSequence layoutText = layout.getText();
        int lineCount = layout.getLineCount();

        int[] visibleLineCharCounts = new int[Math.min(lineCount, maxLines)];
        int[] summaryPartVisibleLineCounts = new int[populatedSummaryParts.length];
        boolean[] summaryPartsEllipsized = new boolean[populatedSummaryParts.length];
        int currentPart = 0;
        int partTextPosition = 0;
        for (int i = 0; i < lineCount; i++) {
            CharSequence lineDisplayedText =
                    layoutText.subSequence(layout.getLineStart(i), layout.getLineEnd(i));

            if (i >= maxLines) {
                if (layout.getEllipsisCount(maxLines - 1) > 0) {
                    // the last visible line was ellipsized, so nothing extra needs to be handled
                    break;
                }
                // text is cut off, but not with an ellipsis. theoretically, this shouldn't happen
                // since we tell the summary text view to ellipsize, but in case that doesn't work
                // for some reason, we'll need to add one, and in case this character takes up more
                // space than some of the existing text, we'll remove a couple extra characters to
                // be safe.
                visibleLineCharCounts[visibleLineCharCounts.length - 1] = Math.max(0,
                        visibleLineCharCounts[visibleLineCharCounts.length - 1] - 3);
                if (measurement.ellipsis == null) {
                    measurement.ellipsis = DEFAULT_ELLIPSIS;
                }
                break;
            }

            if (currentPart > populatedSummaryParts.length) {
                Log.w(TAG, "Unexpected text after all summary parts in " + mPref.getKey() + ": "
                        + lineDisplayedText);
                return null;
            }
            // full text of the current summary part (description or value)
            CharSequence summaryPart = populatedSummaryParts[currentPart];

            int ellipsisCount = layout.getEllipsisCount(i);
            int ellipsisStart = layout.getEllipsisStart(i);
            int lineStart = layout.getLineStart(i);
            int lineEnd = layout.getLineEnd(i);
            // get the actual text for the line that is shown, excluding any ellipsis and anything
            // after it
            CharSequence visibleText = layoutText.subSequence(lineStart,
                    ellipsisCount > 0
                            ? lineStart + ellipsisStart
                            : lineEnd);

            // based on the number of characters shown in the current line, get the
            // corresponding text from the intended summary part (description or value). this
            // generally should just match the visible text (other than maybe a trailing new line),
            // but we should verify it to make sure we're measuring the right thing.
            int expectedLineSummaryPartEnd =
                    Math.min(partTextPosition + visibleText.length(), summaryPart.length());
            CharSequence expectedLineText = summaryPart.subSequence(partTextPosition,
                    expectedLineSummaryPartEnd);
            boolean mayHaveTrailingNewLine = (expectedLineSummaryPartEnd < summaryPart.length()
                    && summaryPart.charAt(expectedLineSummaryPartEnd) == '\n')
                    || currentPart + 1 < populatedSummaryParts.length;
            // verify the text matches what is expected
            if (TextUtils.equals(expectedLineText, visibleText)
                    || (mayHaveTrailingNewLine
                            && TextUtils.equals(expectedLineText + "\n", visibleText))) {

                // only add characters to the visible count list if the current part hasn't already
                // been ellipsized
                if (!summaryPartsEllipsized[currentPart]) {
                    visibleLineCharCounts[i] = expectedLineText.length();
                    summaryPartVisibleLineCounts[currentPart]++;
                }

                partTextPosition += expectedLineText.length();

                // determine the ellipsis character(s) if ellipsized
                if (ellipsisCount > 0) {
                    int ellipsisLength = getEllipsisLength(summaryPart, currentPart,
                            populatedSummaryParts.length, partTextPosition, lineDisplayedText,
                            ellipsisStart, ellipsisCount);
                    if (ellipsisLength > 0) {
                        // we're given the ellipsis, so reuse that in case different locales use a
                        // different character
                        measurement.ellipsis = lineDisplayedText.subSequence(ellipsisStart,
                                ellipsisStart + ellipsisLength);
                    } else {
                        // we're not given the ellipsis (I'm not sure if this is even a real thing
                        // that could happen), so we'll need to add one, and in case this character
                        // takes up more space than some of the existing text, remove a couple
                        // characters to be safe
                        visibleLineCharCounts[i] = Math.max(0, visibleLineCharCounts[i] - 3);
                        measurement.ellipsis = DEFAULT_ELLIPSIS;
                    }
                    summaryPartsEllipsized[currentPart] = true;
                    // shift the position in the summary part text forward for the ellipsized text
                    // and the text included on the line past the ellipsis (not actually shown)
                    int textAfterEllipsisLength =
                            lineDisplayedText.length() - (ellipsisStart + ellipsisLength);
                    partTextPosition += ellipsisCount + textAfterEllipsisLength;
                }

                if (partTextPosition >= summaryPart.length()) {
                    // advance to the next summary part since we've gone through the whole current
                    // summary part
                    partTextPosition = 0;
                    currentPart++;
                }
            } else if (TextUtils.equals(visibleText, "\n")
                    && currentPart > 0
                    && (summaryPartsEllipsized[currentPart - 1]
                            || SUMMARY_PART_DIVIDER_NEW_LINE_COUNT > 1)
                    && partTextPosition == 0) {
                // if we're adding multiple new lines as a divider, we should have a blank line
                // here, but if that's not the case, log an error
                if (SUMMARY_PART_DIVIDER_NEW_LINE_COUNT < 2) {
                    // for some reason the first part can get ellipsized in some circumstances. see
                    // the comment in #getPartialSummary for one case of it. theoretically the
                    // handling there should prevent this, but if this can happen from that, it
                    // seems reasonable that something else could also cause it, so we should handle
                    // it. we'll just ignore an extra blank line, which will simply result in one
                    // less line to show text once everything is measured, which isn't great, but it
                    // at least leaves the ellipses in appropriate (framework determined) places
                    // without trying to make even more passes.
                    Log.e(TAG, "Unexpected blank line in " + mPref.getKey() + " (line " + i + ")");
                }
            } else {
                // unexpected text
                Log.e(TAG, "Unexpected text in " + mPref.getKey() + " (line " + i + "): "
                        + lineDisplayedText);
                return null;
            }
        }
        measurement.summaryPartsVisibleRowCharCounts = addEmptySpaces(summaryParts,
                segregateSummaryPartLineCounts(visibleLineCharCounts,
                        summaryPartVisibleLineCounts));

        return measurement;
    }

    private static CharSequence[] filterOutEmpty(CharSequence[] charSequences) {
        int populatedCount = 0;
        for (CharSequence summaryPart : charSequences) {
            if (!TextUtils.isEmpty(summaryPart)) {
                populatedCount++;
            }
        }
        if (populatedCount == charSequences.length) {
            // nothing to filter out
            return charSequences;
        }
        CharSequence[] populatedCharSequences = new CharSequence[populatedCount];
        int populatedIndex = 0;
        for (CharSequence charSequence : charSequences) {
            if (!TextUtils.isEmpty(charSequence)) {
                populatedCharSequences[populatedIndex++] = charSequence;
            }
        }
        return populatedCharSequences;
    }

    private static int[][] addEmptySpaces(CharSequence[] summaryParts,
                                          int[][] populatedSummaryPartsVisibleRowCharCounts) {
        if (summaryParts.length == populatedSummaryPartsVisibleRowCharCounts.length) {
            return populatedSummaryPartsVisibleRowCharCounts;
        }
        int[][] summaryPartsVisibleRowCharCounts = new int[summaryParts.length][];
        int populatedIndex = 0;
        for (int fullIndex = 0; fullIndex < summaryParts.length; fullIndex++) {
            summaryPartsVisibleRowCharCounts[fullIndex] =
                    !TextUtils.isEmpty(summaryParts[fullIndex])
                            ? populatedSummaryPartsVisibleRowCharCounts[populatedIndex++]
                            : new int[0];
        }
        return  summaryPartsVisibleRowCharCounts;
    }

    private static int getEllipsisLength(CharSequence summaryPart, int currentPart, int partCount,
                                          int partTextPosition, CharSequence lineDisplayedText,
                                          int ellipsisStart, int ellipsisCount) {
        int postEllipsisStart = partTextPosition + ellipsisCount;
        int nextNewLineInSummaryPart = summaryPart.toString().indexOf('\n', postEllipsisStart);
        // this displayed line will only go as far as the next new line or the extent of
        // the current summary part if there isn't a new line before the end
        int postEllipsisSummaryPartMaxEnd = nextNewLineInSummaryPart >= postEllipsisStart
                ? nextNewLineInSummaryPart
                : summaryPart.length();
        boolean mayHaveTrailingNewLine = nextNewLineInSummaryPart >= postEllipsisStart
                || currentPart + 1 < partCount;
        // get the full text from the summary that could be included in the line after
        // an ellipsis (won't actually be visible)
        CharSequence postEllipsisSummaryLineMaxText =
                postEllipsisStart < summaryPart.length()
                        ? summaryPart.subSequence(postEllipsisStart, postEllipsisSummaryPartMaxEnd)
                        : "";

        int nextNewLineInDisplayedText =
                lineDisplayedText.toString().indexOf('\n', ellipsisStart);
        // when comparing text to try to determine the ellipsis, only process up to the next new
        // line since the ellipsis shouldn't span a new line and we're limiting the text to compare
        // to before a new line. this also may pull in content from the next summary parts that we
        // can't compare here (and don't really need to bother with).
        int postEllipsisDisplayedTextMaxEnd = nextNewLineInDisplayedText >= ellipsisStart
                ? nextNewLineInDisplayedText
                : lineDisplayedText.length();

        // iterate through each position of the line's text starting at the ellipsis to
        // see if the text from there to the end of the line matches what we expect from
        // the summary part. if we find a match, that must mean that the ellipsis ended
        // there. if we don't find a match the rest of the line must be the ellipsis.
        int ellipsisEnd = lineDisplayedText.length();
        for (int i = ellipsisStart; i < postEllipsisDisplayedTextMaxEnd; i++) {
            CharSequence lineTextAfterEllipsis = lineDisplayedText.subSequence(
                    i,
                    postEllipsisDisplayedTextMaxEnd);
            if (lineTextAfterEllipsis.length() < postEllipsisSummaryLineMaxText.length()) {
                if (TextUtils.equals(lineTextAfterEllipsis,
                        postEllipsisSummaryLineMaxText.subSequence(0,
                                lineTextAfterEllipsis.length()))) {
                    ellipsisEnd = i;
                    break;
                }
            } else if (TextUtils.equals(lineTextAfterEllipsis, postEllipsisSummaryLineMaxText)
                    || (mayHaveTrailingNewLine
                            && TextUtils.equals(lineTextAfterEllipsis,
                                    postEllipsisSummaryLineMaxText + "\n"))) {
                ellipsisEnd = i;
                break;
            }
            if (isOneRepeatedChar(lineTextAfterEllipsis, ZERO_WIDTH_NO_BREAK_SPACE)) {
                // some versions replace characters after the ellipsis with zero width no-break
                // space seemingly to maintain character length without risk of printing characters
                // after the ellipsis. rather than iterating through everything that presumably
                // won't match, just assume that the ellipsis ends here.
                ellipsisEnd = i;
                break;
            }
        }

        return ellipsisEnd - ellipsisStart;
    }

    private static boolean isOneRepeatedChar(CharSequence sequence, char repeatedChar) {
        for (int i = 0; i < sequence.length(); i++) {
            if (sequence.charAt(i) != repeatedChar) {
                return false;
            }
        }
        return true;
    }

    private static int[][] segregateSummaryPartLineCounts(int[] visibleLineCharCounts,
                                                   int[] summaryPartVisibleLineCounts) {
        int summaryPartCount = summaryPartVisibleLineCounts.length;
        int[][] populatedSummaryPartsVisibleRowCharCounts = new int[summaryPartCount][];
        int rowIndex = 0;
        for (int partIndex = 0; partIndex < summaryPartCount; partIndex++) {
            int partVisibleLineCount = summaryPartVisibleLineCounts[partIndex];
            populatedSummaryPartsVisibleRowCharCounts[partIndex] = new int[partVisibleLineCount];
            if (partVisibleLineCount > 0) {
                System.arraycopy(visibleLineCharCounts, rowIndex,
                        populatedSummaryPartsVisibleRowCharCounts[partIndex], 0,
                        partVisibleLineCount);
            }
            rowIndex += partVisibleLineCount;
            if (summaryPartCount > 1) {
                // skip over blank lines dividing the summary parts
                rowIndex += SUMMARY_PART_DIVIDER_NEW_LINE_COUNT - 1;
            }
        }
        return populatedSummaryPartsVisibleRowCharCounts;
    }

    private void setClickListeners(boolean hasEllipsis) {
        OnLongClickListener longClickListener;
        if (hasEllipsis) {
            // since the text is cut off, we need a long click to be able to show the full text
            longClickListener = new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new AlertDialogBuilder(mPref.getContext())
                            .setTitle(mPref.getTitle())
                            .setMessage(getFullSummary())
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    })
                            .show();

                    return true;
                }
            };
        } else {
            // the text isn't cut off, so we don't need a long click handler, but we need to
            // explicitly clear it because the system seems to reuse the UI content, which could
            // leak an old long click listener from some other preference that is out of view
            // otherwise
            longClickListener = null;
        }

        // adding a long click listener seems to block the single click on the text from
        // triggering the normal click action for the preference (open a screen, toggle
        // the preference, etc), so we'll need to add a regular click listener to
        // reproduce that. although simply triggering the Preference's #onClick may work
        // on some Preferences, such as SwitchPreference, the base Preference
        // implementation does nothing. AdapterView#performItemClick calls
        // PreferenceScreen#onItemClick, which calls Preference#performClick normally
        // when a preference is clicked. Preference#performClick what we really need,
        // but that's hidden from apps for some reason, so we'll just go one stack up
        // and replicate the call to PreferenceScreen#onItemClick.
        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                PreferenceScreen prefScreen = PreferenceUtils.getParentScreen(mPref);
                if (prefScreen != null) {
                    ListAdapter rootListAdapter = prefScreen.getRootAdapter();
                    int position = PreferenceUtils.getPosition(mPref, rootListAdapter);
                    if (position >= 0) {
                        long id = rootListAdapter.getItemId(position);
                        // PreferenceScreen#onItemClick only uses the AdapterView parent
                        // parameter to shift the position, but since we looked up the
                        // position directly from the root list adapter, we wouldn't
                        // actually want the position to be shifted.
                        // PreferenceScreen#onItemClick doesn't use the View view
                        // parameter. it's possible that there is some child class that
                        // does something with them, but until there is some known case
                        // for that, skipping these parameters should be fine.
                        prefScreen.onItemClick(null, null, position, id);
                        return;
                    }
                }
                // in case we know that the more complex logic isn't going to work, fall back to at
                // least trying to trigger the normal onClick
                mPrefOnClick.run();
            }
        };

        if (mView != null) {
            mView.setOnLongClickListener(longClickListener);
            mView.setOnClickListener(clickListener);
        }
    }

    /**
     * Handler for the managed Preference to call from {@link Preference#onBindView}.
     * @param view The View that shows this Preference.
     */
    public void onBindView(View view) {
        TextView titleTextView = view.findViewById(android.R.id.title);
        if (titleTextView != mTitleTextView && titleTextView != null) {
            // allow the title to wrap
            titleTextView.setSingleLine(false);
            // limit the number of lines for the title. currently there seems to be no limit on any
            // version, but if something somewhat reasonable gets added in the future, we can use
            // that
            int titleMaxLines = titleTextView.getMaxLines();
            if (titleMaxLines < 2 || titleMaxLines > 8) {
                titleTextView.setMaxLines(3);
            }
            // make sure the text shows an ellipsis for any overflow
            titleTextView.setEllipsize(TruncateAt.END);
            // fix the text alignment to match the LTR/RTL layout
            titleTextView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
        TextView summaryTextView = view.findViewById(android.R.id.summary);
        if (summaryTextView != mSummaryTextView && summaryTextView != null) {
            // make sure the text shows an ellipsis for any overflow
            summaryTextView.setEllipsize(TruncateAt.END);
            // fix the text alignment to match the LTR/RTL layout
            summaryTextView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
        if (view != mView || titleTextView != mTitleTextView
                || summaryTextView != mSummaryTextView) {
            mView = view;
            mTitleTextView = titleTextView;
            mSummaryTextView = summaryTextView;

            if (!mEllipsisManager.wasMeasured() && !mEllipsisManager.isMeasuring()) {
                // now that we have a view, we need to measure the text to determine what needs an
                // ellipsis and long click event handlers. this seems to need to be run on the UI
                // thread. not doing so makes the layout text not get updated by the time onPreDraw
                // is called (even on repeated calls allowing the drawing pass to continue), causing
                // us to never determine if text is cut off.
                mSummaryTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mEllipsisManager.wasMeasured() || mEllipsisManager.isMeasuring()) {
                            // something else already triggered the measure, so we don't need to do
                            // it again
                            return;
                        }
                        mEllipsisManager.updateSummary();
                    }
                });
            } else {
                // even though the view changed, we previously had the summary text view and
                // measured it (or were in the process of measuring) to see if the text fits and at
                // least in most cases, the size shouldn't have changed (the view seems to change
                // whenever the summary changes text on some versions, including when flipping the
                // text back to the right order after measuring it), so just reuse what was already
                // measured (or add the handlers until we finish measuring to determine if they can
                // be removed).
                setClickListeners(mEllipsisManager.isEllipsized());
            }
        }
    }
}
