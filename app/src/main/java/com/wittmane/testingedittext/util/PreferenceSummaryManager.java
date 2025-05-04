/*
 * Copyright (C) 2025 Eli Wittman
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

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

    private final Preference mPref;
    private final Runnable mPrefOnClick;
    private final Consumer<CharSequence> mPrefSuperSetSummary;
    private final Consumer<CharSequence> mPrefSuperSetSummaryOn;
    private final Consumer<CharSequence> mPrefSuperSetSummaryOff;

    private CharSequence mBaseSummary;
    private CharSequence mBaseSummaryOn;
    private CharSequence mBaseSummaryOff;
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
        mBaseSummary = mPref.getSummary();
        if (mPref instanceof TwoStatePreference) {
            mBaseSummaryOn = ((TwoStatePreference) mPref).getSummaryOn();
            mBaseSummaryOff = ((TwoStatePreference) mPref).getSummaryOff();
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
        if (mBaseSummary != null && mBaseSummary.equals(summary)) {
            // this value was already set, so nothing needs to be done
            return;
        }
        mBaseSummary = summary;
        mEllipsisManager.setFullSummary();
    }

    /**
     * Handler for the managed Preference to call from {@link TwoStatePreference#setSummaryOn}
     * instead of the {@code super} call.
     * This sets the primary (description) summary for the Preference. For a secondary summary for
     * the Preference's value, use {@link #onSetValueSummary}.
     * @param summaryOn The (description) summary to be shown when checked.
     */
    public void onSetSummaryOn(CharSequence summaryOn) {
        if (mBaseSummaryOn != null && mBaseSummaryOn.equals(summaryOn)) {
            // this value was already set, so nothing needs to be done
            return;
        }
        mBaseSummaryOn = summaryOn;
        mEllipsisManager.setFullSummary();
    }

    /**
     * Handler for the managed Preference to call from {@link TwoStatePreference#setSummaryOff}
     * instead of the {@code super} call.
     * This sets the primary (description) summary for the Preference. For a secondary summary for
     * the Preference's value, use {@link #onSetValueSummary}.
     * @param summaryOff The (description) summary to be shown when unchecked.
     */
    public void onSetSummaryOff(CharSequence summaryOff) {
        if (mBaseSummaryOff != null && mBaseSummaryOff.equals(summaryOff)) {
            // this value was already set, so nothing needs to be done
            return;
        }
        mBaseSummaryOff = summaryOff;
        mEllipsisManager.setFullSummary();
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
                && (!TextUtils.isEmpty(mBaseSummaryOn) || !TextUtils.isEmpty(mBaseSummaryOff))) {
            mEllipsisManager.setFullSummary();
        }
    }

    private CharSequence getCurrentSummary(boolean getBase) {
        if (mPref instanceof TwoStatePreference) {
            // based on logic from TwoStatePreference#syncSummaryView
            boolean isChecked = ((TwoStatePreference) mPref).isChecked();
            CharSequence summaryOn = getBase
                    ? mBaseSummaryOn
                    : ((TwoStatePreference) mPref).getSummaryOn();
            if (isChecked && !TextUtils.isEmpty(summaryOn)) {
                return summaryOn;
            }
            CharSequence summaryOff = getBase
                    ? mBaseSummaryOff
                    : ((TwoStatePreference) mPref).getSummaryOff();
            if (!isChecked && !TextUtils.isEmpty(summaryOff)) {
                return summaryOff;
            }
        }
        return getBase ? mBaseSummary : mPref.getSummary();
    }

    private void setCurrentSuperSummary(CharSequence summary) {
        if (mPref instanceof TwoStatePreference) {
            // based on logic from TwoStatePreference#syncSummaryView
            boolean isChecked = ((TwoStatePreference) mPref).isChecked();
            if (isChecked && !TextUtils.isEmpty(mBaseSummaryOn)
                    && mPrefSuperSetSummaryOn != null) {
                mPrefSuperSetSummaryOn.accept(summary);
                return;
            }
            if (!isChecked && !TextUtils.isEmpty(mBaseSummaryOff)
                    && mPrefSuperSetSummaryOff != null) {
                mPrefSuperSetSummaryOff.accept(summary);
                return;
            }
        }
        mPrefSuperSetSummary.accept(summary);
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
        mEllipsisManager.setFullSummary();
    }

    private class EllipsisManager implements OnPreDrawListener {
        private int mPreDrawCallCount = 0;
        private boolean mIsAttached = false;
        private boolean mWasMeasured;
        private boolean mIsBaseSummaryEllipsized;
        private boolean mIsSummaryFlipped;

        public synchronized boolean wasMeasured() {
            return mWasMeasured;
        }

        public synchronized boolean isBaseSummaryEllipsized() {
            return mIsBaseSummaryEllipsized;
        }

        public synchronized void setFullSummary() {
            CharSequence currentBaseSummary = getCurrentSummary(true);
            if (TextUtils.isEmpty(currentBaseSummary)) {
                // we don't need a long click listener since there is no base summary text
                setClickListeners(false);
                mIsSummaryFlipped = false;
                setCurrentSuperSummary(mValueSummary);
                mWasMeasured = true;
                mIsBaseSummaryEllipsized = false;
            } else {
                // set up measuring for whether the base summary will be cut off
                mWasMeasured = false;
                boolean canMeasure = prepEllipsisMeasuring();
                if (TextUtils.isEmpty(mValueSummary)) {
                    mIsSummaryFlipped = false;
                    setCurrentSuperSummary(currentBaseSummary);
                } else if (canMeasure) {
                    // since we hooked up the pre-draw listener, temporarily set the value first to
                    // make sure that can be fully shown. this will be blocked and reset to the
                    // correct order in the pre-draw listener, where we'll be able to see how much
                    // of the base summary will get ellipsized so that we can manually add the
                    // ellipsis to prevent the value at the end from being hidden.
                    mIsSummaryFlipped = true;
                    setCurrentSuperSummary(new StringBuilder()
                            .append(mValueSummary)
                            .append('\n')
                            .append(currentBaseSummary));
                } else {
                    mIsSummaryFlipped = false;
                    setCurrentSuperSummary(new StringBuilder()
                            .append(currentBaseSummary)
                            .append('\n')
                            .append(mValueSummary));
                }
            }
        }

        public synchronized boolean prepEllipsisMeasuring() {
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

            // since we don't know yet if any text is cut off, as long as there is text we show for
            // the base summary, set up the click listener for that in case we never get a chance to
            // manage the ellipsis properly.
            mIsBaseSummaryEllipsized = !TextUtils.isEmpty(getCurrentSummary(true));
            setClickListeners(mIsBaseSummaryEllipsized);

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
            CharSequence currentBaseSummary = getCurrentSummary(true);
            if (++mPreDrawCallCount > 10) {
                // too many pre-draw calls. what we're trying to do doesn't seem to be working. bail
                // out.
                Log.w(TAG, mPref.getKey() + ": too many onPreDraw calls");
                detach();
                // make sure the text order is correct first.
                if (mIsSummaryFlipped) {
                    mIsSummaryFlipped = false;
                    setCurrentSuperSummary(new StringBuilder()
                            .append(currentBaseSummary)
                            .append('\n')
                            .append(mValueSummary));
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

            CharSequence summary = getCurrentSummary(false);
            CharSequence layoutText = layout.getText();
            int maxLines = mSummaryTextView.getMaxLines();
            int lineCount = layout.getLineCount();

            // determine what text is cut off
            int visibleLength;
            CharSequence ellipsis;
            if (lineCount >= maxLines && layout.getEllipsisCount(maxLines - 1) > 0) {
                // text is cut off with an ellipsis
                int ellipsisStart = layout.getLineStart(maxLines - 1)
                        + layout.getEllipsisStart(maxLines - 1);
                // this potentially includes the ellipsis
                int displayedTextLength = layout.getLineEnd(maxLines - 1);
                if (displayedTextLength > ellipsisStart) {
                    // we're given the ellipsis, so reuse that in case different locales use a
                    // different character
                    visibleLength = ellipsisStart;
                    ellipsis = layoutText.subSequence(ellipsisStart, displayedTextLength);
                } else {
                    // we're not given the ellipsis, so we'll need to add one, and in case this
                    // character takes up more space than some of the existing text, remove a couple
                    // characters to be safe
                    visibleLength = ellipsisStart - 3;
                    ellipsis = "\u2026";
                }
            } else if (lineCount > maxLines) {
                // text is cut off, but not with an ellipsis. theoretically, this shouldn't happen
                // since we tell the summary text view to ellipsize, but in case that doesn't work
                // for some reason, we'll need to add one, and in case this character takes up more
                // space than some of the existing text, we'll remove a couple extra characters to
                // be safe.
                visibleLength = layout.getLineEnd(maxLines - 1) - 3;
                ellipsis = "\u2026";
            } else {
                // text isn't cut off
                visibleLength = layoutText.length();
                ellipsis = null;
            }

            if (visibleLength > summary.length()
                    || !TextUtils.equals(summary.subSequence(0, visibleLength),
                            layoutText.subSequence(0, visibleLength))) {
                // none of the text matches, which means the layout must still have some older
                // text. skip this drawing pass and wait until the layout is updated. this seems to
                // happen when a view gets reused (has the text from some other preference that is
                // now off the screen), and it will eventually update to the text for this
                // preference. this also seems to happen when scrolling in and out of view quickly,
                // so we might never get back to this, so we need the counter to avoid infinitely
                // looping. allow the drawing pass to continue because we're only trying to block
                // the draw for flipping the text (if the text is from something old or a different
                // preference, either the framework should be managing this or it would have already
                // been a minor visual issue regardless of this handling) and repeatedly blocking
                // something could cause freezing issues.
                return true;
            }

            mIsBaseSummaryEllipsized = visibleLength < layoutText.length()
                    && !TextUtils.isEmpty(currentBaseSummary);
            mWasMeasured = true;

            // we managed ellipsis, so we don't need to keep listening
            detach();

            // the click listener is to show the full base summary if it is ellipsized
            setClickListeners(mIsBaseSummaryEllipsized);

            if (mIsSummaryFlipped) {
                // flip the summary back with the appropriate artificial ellipsis
                if (visibleLength > mValueSummary.length()) {
                    int baseSummaryAllowedLength = visibleLength - 1 - mValueSummary.length();
                    StringBuilder sb = new StringBuilder()
                            .append(currentBaseSummary.subSequence(0, baseSummaryAllowedLength));
                    if (ellipsis != null) {
                        sb.append(ellipsis);
                    }
                    sb.append('\n').append(mValueSummary);
                    setCurrentSuperSummary(sb);
                } else {
                    // this really shouldn't happen. the value summary should be shortened so it
                    // doesn't push out the entire base summary. we probably could try to ellipsize
                    // both so some of each fits, but it's probably not worth the effort for a case
                    // that shouldn't be hit. just show the ellipsis on the first line to show that
                    // the base summary was cut off (since all of it got cut off in the flipped
                    // order, we can't tell how much of the first line would fit, so rather than
                    // trying to measure that now, just take the lazy route).
                    setCurrentSuperSummary(new StringBuilder()
                            .append(ellipsis)
                            .append('\n')
                            .append(mValueSummary));
                }
                mIsSummaryFlipped = false;
                // cancel this drawing pass since we needed to flip the summary order back
                return false;
            }

            // text didn't need to change, so this drawing pass can continue
            return true;
        }
    }

    private void setClickListeners(boolean hasEllipsis) {
        OnLongClickListener longClickListener;
        if (hasEllipsis) {
            // since the text is cut off, we need a long click to be able to show the full text
            longClickListener = new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    // Create the object of AlertDialog Builder class
                    AlertDialog dialog = new AlertDialog.Builder(mPref.getContext())
                            .setTitle(mPref.getTitle())
                            .setMessage(getCurrentSummary(true))
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    })
                            .create();
                    dialog.show();

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
        }
        TextView summaryTextView = view.findViewById(android.R.id.summary);
        if (summaryTextView != mSummaryTextView && summaryTextView != null) {
            // make sure the text shows an ellipsis for any overflow
            summaryTextView.setEllipsize(TruncateAt.END);
        }
        if (view != mView || titleTextView != mTitleTextView
                || summaryTextView != mSummaryTextView) {
            mView = view;
            mTitleTextView = titleTextView;
            mSummaryTextView = summaryTextView;

            if (mEllipsisManager.wasMeasured()) {
                // even though the view changed, we previously had the summary text view and
                // measured it to see if the text fits and at least in most cases, the size
                // shouldn't have changed (the view seems to change whenever the summary changes
                // text on some versions, including when flipping the text back to the right order
                // after measuring it), so just reuse what was already measured.
                setClickListeners(mEllipsisManager.isBaseSummaryEllipsized());
            } else {
                // now that we have the view to measure, make sure the manual ellipsis and long
                // click events are handled. this seems to need to be run on the UI thread. not
                // doing so makes the layout text not get updated by the time onPreDraw is called
                // (even on repeated calls allowing the drawing pass to continue), causing us to
                // never determine if text is cut off.
                mSummaryTextView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mEllipsisManager.wasMeasured()) {
                            // something else measured the what text would fit now, so we don't need
                            // to do it again
                            return;
                        }
                        mEllipsisManager.setFullSummary();
                    }
                });
            }
        }
    }
}
