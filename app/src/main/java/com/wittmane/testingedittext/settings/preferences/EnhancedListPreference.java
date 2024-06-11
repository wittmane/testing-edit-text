/*
 * Copyright (C) 2022-2024 Eli Wittman
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
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.TextView;

//TODO: (EW) see if there is a way to reduce duplicate code with DialogPreferenceBase and
// LongTextSwitchPreference
/**
 * A ListPreference with a few minor enhancements.
 * - shows its value in the summary
 * - forces a default value when one isn't specified
 * - allows the title to wrap
 * - supports long clicking to read the full text if ellipsized
 */
public class EnhancedListPreference extends ListPreference {
    private static final String TAG = EnhancedListPreference.class.getSimpleName();

    private CharSequence mBaseSummary;
    private CharSequence mValueSummary;
    private CharSequence mDefaultValue;

    private View mView;
    private TextView mTitleTextView;
    private TextView mSummaryTextView;

    private final EllipsisManager mEllipsisManager = new EllipsisManager();

    public EnhancedListPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mBaseSummary = getSummary();
        // if a default hasn't already been defined, use the first entry
        if (TextUtils.isEmpty(mDefaultValue)) {
            CharSequence[] entryValues = getEntryValues();
            if (entryValues != null && entryValues.length > 0) {
                setDefaultValue(entryValues[0]);
            }
        }
    }

    @Override
    public void setDefaultValue(Object defaultValue) {
        super.setDefaultValue(defaultValue);
        if (defaultValue instanceof CharSequence) {
            mDefaultValue = (CharSequence)defaultValue;
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        Object defaultValue = super.onGetDefaultValue(a, index);
        if (defaultValue instanceof CharSequence) {
            mDefaultValue = (CharSequence)defaultValue;
        }
        return defaultValue;
    }

    @Override
    public void setSummary(CharSequence summary) {
        if (mBaseSummary != null && mBaseSummary.equals(summary)) {
            // this value was already set, so nothing needs to be done
            return;
        }
        mBaseSummary = summary;
        mEllipsisManager.setFullSummary();
    }

    /**
     * Set the secondary summary for the preference to display the value (as opposed to the regular
     * summary as a description of the preference)
     * @param summary the display text for the current value of the preference
     */
    protected void setValueSummary(CharSequence summary) {
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
            if (TextUtils.isEmpty(mBaseSummary)) {
                // we don't need a long click listener since there is no base summary text
                setClickListeners(false);
                mIsSummaryFlipped = false;
                setSummary(mValueSummary);
                mWasMeasured = true;
                mIsBaseSummaryEllipsized = false;
            } else {
                // set up measuring for whether the base summary will be cut off
                mWasMeasured = false;
                boolean canMeasure = prepEllipsisMeasuring();
                if (TextUtils.isEmpty(mValueSummary)) {
                    mIsSummaryFlipped = false;
                    setSummary(mBaseSummary);
                } else if (canMeasure) {
                    // since we hooked up the pre-draw listener, temporarily set the value first to
                    // make sure that can be fully shown. this will be blocked and reset to the
                    // correct order in the pre-draw listener, where we'll be able to see how much
                    // of the base summary will get ellipsized so that we can manually add the
                    // ellipsis to prevent the value at the end from being hidden.
                    mIsSummaryFlipped = true;
                    setSummary(new StringBuilder()
                            .append(mValueSummary)
                            .append('\n')
                            .append(mBaseSummary));
                } else {
                    mIsSummaryFlipped = false;
                    setSummary(new StringBuilder()
                            .append(mBaseSummary)
                            .append('\n')
                            .append(mValueSummary));
                }
            }
        }

        private void setSummary(CharSequence summary) {
            EnhancedListPreference.super.setSummary(summary);
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
            mIsBaseSummaryEllipsized = !TextUtils.isEmpty(mBaseSummary);
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
            mSummaryTextView.getViewTreeObserver().removeOnPreDrawListener(this);
            mIsAttached = false;
        }

        @Override
        public synchronized boolean onPreDraw() {
            if (++mPreDrawCallCount > 10) {
                // too many pre-draw calls. what we're trying to do doesn't seem to be working. bail
                // out.
                Log.w(TAG, getKey() + ": too many onPreDraw calls");
                detach();
                // make sure the text order is correct first.
                if (mIsSummaryFlipped) {
                    mIsSummaryFlipped = false;
                    setSummary(new StringBuilder()
                            .append(mBaseSummary)
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

            CharSequence summary = getSummary();
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
                    && !TextUtils.isEmpty(mBaseSummary);
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
                            .append(mBaseSummary.subSequence(0, baseSummaryAllowedLength));
                    if (ellipsis != null) {
                        sb.append(ellipsis);
                    }
                    sb.append('\n').append(mValueSummary);
                    setSummary(sb);
                } else {
                    // this really shouldn't happen. the value summary should be shortened so it
                    // doesn't push out the entire base summary. we probably could try to ellipsize
                    // both so some of each fits, but it's probably not worth the effort for a case
                    // that shouldn't be hit. just show the ellipsis on the first line to show that
                    // the base summary was cut off (since all of it got cut off in the flipped
                    // order, we can't tell how much of the first line would fit, so rather than
                    // trying to measure that now, just take the lazy route).
                    setSummary(new StringBuilder()
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
                    AlertDialog dialog = new AlertDialog.Builder(getContext())
                            .setTitle(getTitle())
                            .setMessage(mBaseSummary)
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
        // toggling the preference like it normally does
        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                EnhancedListPreference.this.onClick();
            }
        };

        if (mView != null) {
            mView.setOnLongClickListener(longClickListener);
            mView.setOnClickListener(clickListener);
        }
    }

    protected void updateValueSummary() {
        setValueSummary(getEntry());
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        updateValueSummary();
        return view;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        updateValueSummary();

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

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();
        updateValueSummary();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        updateValueSummary();
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        updateValueSummary();
    }
}
