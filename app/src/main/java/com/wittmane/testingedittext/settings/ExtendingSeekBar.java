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

import androidx.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.R;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

//TODO: (EW) it would be nice to be able to extend View instead of ViewGroup since we don't want to
// allow extra children
public class ExtendingSeekBar extends ViewGroup {
    private static final String TAG = ExtendingSeekBar.class.getSimpleName();

    private static final int TIMER_TIMEOUT = 200;
    private static final int SLOW_MOVEMENT_TIMEOUT = 1000;
    private static final int SECOND = 1000;
    private static final int LINEAR_STEP_MAX_EXTEND_RANGE = 10000;
    private static final int LINEAR_STEP_FULL_EXTEND_MAX_TIME = SECOND * 8;
    private static final int FULL_EXTEND_MAX_TIME = SECOND * 15;
    private static final int DEFAULT_STEPS_TO_SHIFT_RANGE = 10;

    private int mMaxValue;
    private int mMinValue;
    private int mStepValue = 1;
    private int mCurrentMaxValue;
    private int mCurrentMinValue;
    private int mStepsToShiftRange = DEFAULT_STEPS_TO_SHIFT_RANGE;
    private int mMaxVisibleSteps;

    private boolean mAlwaysShowTicks;
    private boolean mZeroBasedTick;
    private int mRequestedVisibleRange;

    private int mMediumTickStepInterval;
    private int mLargeTickStepInterval;

    private float mDensity;

    private OnExtendingSeekBarChangeListener mOnExtendingSeekBarChangeListener;
    private final SeekBar.OnSeekBarChangeListener mOnInternalSeekBarChangeListener =
            new OnSeekBarChangeListenerProxy();
    private boolean mIgnoreInternalProgressChanges = false;

    private InternalSeekBar mInternalSeekBar;

    public ExtendingSeekBar(Context context) {
        super(context);
        init(null);
    }

    public ExtendingSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ExtendingSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ExtendingSeekBar(Context context, AttributeSet attrs, int defStyleAttr,
                             int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        mDensity = getDisplayMetrics().density;

        mInternalSeekBar = new InternalSeekBar(getContext(), attrs, android.R.attr.seekBarStyle);

        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.ExtendingSeekBar, 0, 0);
        mAlwaysShowTicks = a.getBoolean(R.styleable.ExtendingSeekBar_alwaysShowTickMarks, false);
        mZeroBasedTick = a.getBoolean(R.styleable.ExtendingSeekBar_zeroBasedTickMarks, true);
        int min = a.getInt(R.styleable.ExtendingSeekBar_min, mInternalSeekBar.getMin());
        int max = a.getInt(R.styleable.ExtendingSeekBar_max, mInternalSeekBar.getMax());
        int step = a.getInt(R.styleable.ExtendingSeekBar_step, 1);
        mRequestedVisibleRange = a.getInt(R.styleable.ExtendingSeekBar_visibleRange, -1);
        int progress = a.getInt(R.styleable.ExtendingSeekBar_progress, min);
        mMediumTickStepInterval = a.getInt(R.styleable.ExtendingSeekBar_mediumTickMarkInterval, 10);
        mLargeTickStepInterval = a.getInt(R.styleable.ExtendingSeekBar_largeTickMarkInterval, 100);

        // based on ProgressBar
        final Drawable progressDrawable =
                a.getDrawable(R.styleable.ExtendingSeekBar_progressDrawable);
        if (progressDrawable != null) {
            // Calling setProgressDrawable can set mMaxHeight, so make sure the
            // corresponding XML attribute for mMaxHeight is read after calling
            // this method.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    && needsTileify(progressDrawable)) {
                mInternalSeekBar.setProgressDrawableTiled(progressDrawable);
            } else {
                mInternalSeekBar.setProgressDrawable(progressDrawable);
            }
        }

        if (a.hasValue(R.styleable.ExtendingSeekBar_extendLeftArrow)) {
            mInternalSeekBar.setExtendLeftArrow(
                    a.getDrawable(R.styleable.ExtendingSeekBar_extendLeftArrow));
        }
        if (a.hasValue(R.styleable.ExtendingSeekBar_extendRightArrow)) {
            mInternalSeekBar.setExtendRightArrow(
                    a.getDrawable(R.styleable.ExtendingSeekBar_extendRightArrow));
        }

        if (a.hasValue(R.styleable.ExtendingSeekBar_smallTickMark)) {
            mInternalSeekBar.setSmallTickMark(
                    a.getDrawable(R.styleable.ExtendingSeekBar_smallTickMark));
        }
        if (a.hasValue(R.styleable.ExtendingSeekBar_mediumTickMark)) {
            mInternalSeekBar.setMediumTickMark(
                    a.getDrawable(R.styleable.ExtendingSeekBar_mediumTickMark));
        }
        if (a.hasValue(R.styleable.ExtendingSeekBar_largeTickMark)) {
            mInternalSeekBar.setLargeTickMark(
                    a.getDrawable(R.styleable.ExtendingSeekBar_largeTickMark));
        }

        if (a.hasValue(R.styleable.ExtendingSeekBar_thumb)) {
            mInternalSeekBar.setThumb(a.getDrawable(R.styleable.ExtendingSeekBar_thumb));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // based on ProgressBar
            if (a.hasValue(R.styleable.ExtendingSeekBar_progressTintMode)) {
                mInternalSeekBar.setProgressTintMode(parseTintMode(a.getInt(
                        R.styleable.ExtendingSeekBar_progressTintMode, -1), null));
            }
            if (a.hasValue(R.styleable.ExtendingSeekBar_progressTint)) {
                mInternalSeekBar.setProgressTintList(a.getColorStateList(
                        R.styleable.ExtendingSeekBar_progressTint));
            }
            if (a.hasValue(R.styleable.ExtendingSeekBar_progressBackgroundTintMode)) {
                mInternalSeekBar.setProgressBackgroundTintMode(parseTintMode(a.getInt(
                        R.styleable.ExtendingSeekBar_progressBackgroundTintMode, -1),
                        null));
            }
            if (a.hasValue(R.styleable.ExtendingSeekBar_progressBackgroundTint)) {
                mInternalSeekBar.setProgressBackgroundTintList(a.getColorStateList(
                        R.styleable.ExtendingSeekBar_progressBackgroundTint));
            }

            // based on AbsSeekBar
            if (a.hasValue(R.styleable.ExtendingSeekBar_tickMarkTintMode)) {
                mInternalSeekBar.setTickMarkTintMode(parseTintMode(a.getInt(
                        R.styleable.ExtendingSeekBar_tickMarkTintMode, -1), null));
            }
            if (a.hasValue(R.styleable.ExtendingSeekBar_tickMarkTint)) {
                mInternalSeekBar.setTickMarkTintList(
                        a.getColorStateList(R.styleable.ExtendingSeekBar_tickMarkTint));
            }

            // based on AbsSeekBar
            if (a.hasValue(R.styleable.ExtendingSeekBar_thumbTintMode)) {
                mInternalSeekBar.setThumbTintMode(parseTintMode(a.getInt(
                        R.styleable.ExtendingSeekBar_thumbTintMode, -1), null));
            }
            if (a.hasValue(R.styleable.ExtendingSeekBar_thumbTint)) {
                mInternalSeekBar.setThumbTintList(
                        a.getColorStateList(R.styleable.ExtendingSeekBar_thumbTint));
            }
        }
        a.recycle();

        LayoutParams layoutParams =
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(mInternalSeekBar, layoutParams);

        mMaxVisibleSteps = mRequestedVisibleRange;
        if (max <= min) {
            max++;
        }
        setRange(min, max, step);
        setProgress(progress);

        mInternalSeekBar.setOnSeekBarChangeListener(mOnInternalSeekBarChangeListener);
    }

    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);
        return metrics;
    }

    // from ProgressBar
    /**
     * Returns {@code true} if the target drawable needs to be tileified.
     *
     * @param dr the drawable to check
     * @return {@code true} if the target drawable needs to be tileified,
     *         {@code false} otherwise
     */
    private static boolean needsTileify(Drawable dr) {
        if (dr instanceof LayerDrawable) {
            final LayerDrawable orig = (LayerDrawable) dr;
            final int N = orig.getNumberOfLayers();
            for (int i = 0; i < N; i++) {
                if (needsTileify(orig.getDrawable(i))) {
                    return true;
                }
            }
            return false;
        }

        if (dr instanceof StateListDrawable) {
            final StateListDrawable in = (StateListDrawable) dr;
            final int N = getStateCount(in);
            for (int i = 0; i < N; i++) {
                if (needsTileify(getStateDrawable(in, i))) {
                    return true;
                }
            }
            return false;
        }

        // If there's a bitmap that's not wrapped with a ClipDrawable or
        // ScaleDrawable, we'll need to wrap it and apply tiling.
        if (dr instanceof BitmapDrawable) {
            return true;
        }

        return false;
    }

    // StateListDrawable#getStateCount was made available in Q, but it was actually added with the
    // current signature at least by Kitkat, so it should be safe to call on these older versions
    // (it's only marked as "light greylist" on Pie so it can still be called), but to be extra safe
    // we'll wrap it in a try/catch.
    @SuppressLint("NewApi")
    private static int getStateCount(StateListDrawable d) {
        try {
            return d.getStateCount();
        } catch (Exception e) {
            Log.w(TAG, "StateListDrawable#getStateCount couldn't be called: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return 0;
        }
    }

    // StateListDrawable#getStateDrawable was made available in Q, but it was actually added with
    // the current signature at least by Kitkat, so it should be safe to call on these older
    // versions (it's only marked as "light greylist" on Pie so it can still be called), but to be
    // extra safe we'll wrap it in a try/catch.
    @SuppressLint("NewApi")
    @Nullable
    private static Drawable getStateDrawable(StateListDrawable d, int index) {
        try {
            return d.getStateDrawable(index);
        } catch (Exception e) {
            Log.w(TAG, "StateListDrawable#getStateDrawable couldn't be called: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    // from Drawable
    /**
     * Parses a {@link android.graphics.PorterDuff.Mode} from a tintMode
     * attribute's enum value.
     */
    private static PorterDuff.Mode parseTintMode(int value, Mode defaultMode) {
        switch (value) {
            case 3: return Mode.SRC_OVER;
            case 5: return Mode.SRC_IN;
            case 9: return Mode.SRC_ATOP;
            case 14: return Mode.MULTIPLY;
            case 15: return Mode.SCREEN;
            case 16: return Mode.ADD;
            default: return defaultMode;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChild(mInternalSeekBar, widthMeasureSpec, heightMeasureSpec);
        int maxHeight = Math.max(mInternalSeekBar.getMeasuredHeight(), getSuggestedMinimumHeight());
        int maxWidth = Math.max(mInternalSeekBar.getMeasuredWidth(), getSuggestedMinimumWidth());
        int childState = mInternalSeekBar.getMeasuredState();
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childState << MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int parentLeft = getPaddingLeft();
        int parentRight = right - left - getPaddingRight();
        final int parentTop = getPaddingTop();
        final int parentBottom = bottom - top - getPaddingBottom();
        mInternalSeekBar.layout(parentLeft, parentTop, parentRight, parentBottom);
    }

    /**
     * <p>Return the lower limit of this progress bar's range.</p>
     *
     * @return a positive integer
     *
     * @see #setMin(int)
     * @see #getProgress()
     */
    public synchronized int getMin() {
        return mMinValue;
    }

    /**
     * <p>Return the upper limit of this progress bar's range.</p>
     *
     * @return a positive integer
     *
     * @see #setMax(int)
     * @see #getProgress()
     */
    public synchronized int getMax() {
        return mMaxValue;
    }

    /**
     * <p>Set the lower range of the progress bar to <tt>min</tt>.</p>
     *
     * @param min the lower range of this progress bar
     *
     * @see #getMin()
     * @see #setProgress(int)
     */
    public synchronized void setMin(int min) {
        setRange(min, mMaxValue, mStepValue);
    }

    /**
     * <p>Set the upper range of the progress bar <tt>max</tt>.</p>
     *
     * @param max the upper range of this progress bar
     *
     * @see #getMax()
     * @see #setProgress(int)
     */
    public synchronized void setMax(int max) {
        setRange(mMinValue, max, mStepValue);
    }

    public synchronized void setRange(int min, int max, int step) {
        if (max <= min) {
            throw new IllegalArgumentException("Invalid range (min=" + min + ", max=" + max + ")");
        }
        if (step < 1 || step > max - min) {
            throw new IllegalArgumentException("Invalid step (" + step + ")");
        }

        int currentProgress = getProgress();
        mIgnoreInternalProgressChanges = true;

        mMinValue = min;
        mCurrentMinValue = mMinValue;
        mMaxValue = max;
        mCurrentMaxValue = mMaxValue;
        mStepValue = step;
        mMaxVisibleSteps = mMaxValue - mMinValue + 1;

        // shift the range to keep the min at 0 since #setMin wasn't added until Oreo
        max -= min;

        // don't include the step in the base value
        if (max % step == 0) {
            max = max / step;
        } else {
            // since the max isn't directly on an step interval, round up for the base value
            max = max / step + 1;
        }

        // don't bother with setting the internal min since it should always be 0
        mInternalSeekBar.setMax(max);
        // reset the progress to match the new range
        setProgress(currentProgress);
        mIgnoreInternalProgressChanges = false;

        handleSeekBarWidth();
    }

    private synchronized void shiftRange(int shift, boolean changeProgressPosition) {
        if (shift == 0) {
            return;
        }

        // if shifting away from an edge, normal steps need to be reallocated for extra space for
        // shifting
        if (shift > 0 && mMinValue == mCurrentMinValue) {
            mCurrentMinValue += mStepsToShiftRange * mStepValue;
        } else if (shift < 0 && mMaxValue == mCurrentMaxValue) {
            mCurrentMaxValue -= mStepsToShiftRange * mStepValue;
        }

        // if the shift goes past the real end, cap it and reallocate the extra shifting space to be
        // used for normal steps
        if (mCurrentMinValue + shift - mStepsToShiftRange * mStepValue <= mMinValue) {
            mCurrentMinValue = mMinValue;
            mCurrentMaxValue = mMinValue
                    + (mInternalSeekBar.getTotalSteps() - 1 - mStepsToShiftRange) * mStepValue;
        } else if (mCurrentMaxValue + shift + mStepsToShiftRange * mStepValue >= mMaxValue) {
            mCurrentMaxValue = mMaxValue;
            mCurrentMinValue = mMaxValue
                    - (mInternalSeekBar.getTotalSteps() - 1 - mStepsToShiftRange) * mStepValue;
        } else {
            mCurrentMinValue += shift;
            mCurrentMaxValue += shift;
        }
        if (changeProgressPosition) {
            setProgress(shift > 0 ? mCurrentMaxValue : mCurrentMinValue);
        }
    }

    /**
     * Sets the current progress to the specified value.
     * <p>
     * This method will immediately update the visual position of the progress
     * indicator.
     *
     * @param progress the new progress, between {@link #getMin()} and {@link #getMax()}
     *
     * @see #getProgress()
     */
    public synchronized void setProgress(int progress) {
        setProgress(progress, false);
    }

    public synchronized void setProgress(int progress, boolean center) {
        progress = Math.max(mMinValue, Math.min(progress, mMaxValue));
        int internalProgress = getInternalProgress(progress);
        if (center) {
            int currentCenter = (mCurrentMaxValue - mCurrentMinValue) / 2;
            shiftRange(progress - currentCenter, false);
            internalProgress = getInternalProgress(progress);
        } else if (internalProgress < mInternalSeekBar.getMin()) {
            shiftRange(progress - mCurrentMinValue, false);
            internalProgress = getInternalProgress(progress);
        } else if (internalProgress > mInternalSeekBar.getMax()) {
            shiftRange(progress - mCurrentMaxValue, false);
            internalProgress = getInternalProgress(progress);
        }
        mInternalSeekBar.setProgress(internalProgress);
    }

    /**
     * <p>Get the progress bar's current level of progress.</p>
     *
     * @return the current progress, between {@link #getMin()} and {@link #getMax()}
     *
     * @see #setProgress(int)
     * @see #setMax(int)
     * @see #getMax()
     */
    public synchronized int getProgress() {
        int internalProgress = mInternalSeekBar.getProgress();
        int unclippedExternalProgress = getExternalProgress(internalProgress);
        return clipToCurrentRange(unclippedExternalProgress);
    }

    private synchronized int getInternalProgress(int externalProgress) {
        int result;
        if (externalProgress == mMaxValue) {
            // if the max isn't directly on an step interval, just round it up for the base value
            result = mInternalSeekBar.getMax();
        } else {
            result = (externalProgress - getBaseOffset()) / mStepValue;
        }
        return result;
    }

    // note that this doesn't clip to the current min/max
    private synchronized int getExternalProgress(int internalProgress) {
        int baseOffset = getBaseOffset();
        return Math.min(baseOffset + internalProgress * mStepValue, mMaxValue);
    }

    private int clipToCurrentRange(int progress) {
        return Math.max(mCurrentMinValue, Math.min(progress, mCurrentMaxValue));
    }

    private int getBaseOffset() {
        if (mMinValue < mCurrentMinValue) {
            return mCurrentMinValue - Math.min(mCurrentMinValue - mMinValue,
                    mStepsToShiftRange * mStepValue);
        }
        return mMinValue;
    }

    private synchronized int getTotalSteps() {
        int total = mMaxValue - mMinValue + 1;
        // include an extra step if the total isn't exactly divisible by the step size
        return (total) / mStepValue + (total % mStepValue == 0 ? 0 : 1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        handleSeekBarWidth();
    }

    private synchronized void handleSeekBarWidth() {
        final int width = getWidth();
        if (width == 0) {
            // can't determine what will fit yet
            return;
        }

        // make sure there is room for a tick and a space between ticks, each being 1 dp (or at
        // least 1px)
        mMaxVisibleSteps = width / Math.max(2, (int)(dpToPx(1) * 2));

        int stepsToShiftRange;
        if (mMaxVisibleSteps >= DEFAULT_STEPS_TO_SHIFT_RANGE * 4) {
            // since there is at least as much space for real steps as the combined shift range on
            // both sides, the default can be used
            stepsToShiftRange = DEFAULT_STEPS_TO_SHIFT_RANGE;
        } else if (mMaxVisibleSteps < 3) {
            // don't have enough room to even have a point one each side to shift and a single point
            // to select a value, so we can't use the shifting range functionality. this really
            // shouldn't ever happen.
            final int currentValue = getProgress();
            mIgnoreInternalProgressChanges = true;
            mCurrentMinValue = mMinValue;
            mCurrentMaxValue = mMaxValue;
            setProgress(currentValue);
            mIgnoreInternalProgressChanges = false;
            return;
        } else {
            // make sure there is at least as much space for real steps as the combined shift range
            // on both sides
            stepsToShiftRange = mMaxVisibleSteps / 4;
        }
        if (mRequestedVisibleRange > 0) {
            // make sure there aren't more total steps for shifting than real steps
            stepsToShiftRange = Math.min(Math.max(1, mRequestedVisibleRange / 2),
                    stepsToShiftRange);
        }

        // subtract 1 to convert the number of allowed points to the number of spaces between points
        int maxInternalRange = mMaxVisibleSteps - 1;

        int visibleRange = (maxInternalRange - stepsToShiftRange * 2) * mStepValue;
        if (mRequestedVisibleRange > 0 && visibleRange > mRequestedVisibleRange) {
            maxInternalRange = mRequestedVisibleRange / mStepValue + stepsToShiftRange * 2;
        }

        if (maxInternalRange >= mMaxValue - mMinValue) {
            // the full range with the specified precision fits, so nothing to do
            return;
        }

        mStepsToShiftRange = stepsToShiftRange;

        // save the current progress value so we can set it back after modifying the range and block
        // notifying listeners of progress changes since this shouldn't actually change the
        // progress, even though the internal progress will shift (any calculations from that may be
        // off while we haven't fully updated ranges
        final int currentValue = getProgress();
        mIgnoreInternalProgressChanges = true;

        int currentValueCenteredMin = currentValue - maxInternalRange / 2 * mStepValue;
        int currentValueCenteredMax = currentValueCenteredMin + maxInternalRange * mStepValue;
        int newCurrentMinValue;
        int newCurrentMaxValue;
        if (currentValueCenteredMin <= mMinValue) {
            newCurrentMinValue = mMinValue;
            newCurrentMaxValue = mMinValue + (maxInternalRange - mStepsToShiftRange) * mStepValue;
        } else if (currentValueCenteredMax >= mMaxValue) {
            newCurrentMaxValue = mMaxValue;
            newCurrentMinValue = mMaxValue - (maxInternalRange - mStepsToShiftRange) * mStepValue;
        } else {
            newCurrentMinValue = currentValueCenteredMin + mStepsToShiftRange * mStepValue;
            newCurrentMaxValue = currentValueCenteredMax - mStepsToShiftRange * mStepValue;
        }

        // don't bother setting anything if it didn't change in case that would trigger any events
        if (mCurrentMinValue == newCurrentMinValue && mCurrentMaxValue == newCurrentMaxValue) {
            return;
        }

        // don't bother with setting the internal min since it should always be 0
        mInternalSeekBar.setMax(maxInternalRange);

        mCurrentMinValue = newCurrentMinValue;
        mCurrentMaxValue = newCurrentMaxValue;

        setProgress(currentValue);
        mIgnoreInternalProgressChanges = false;
    }

    private float dpToPx(float dp) {
        return dp * mDensity;
    }

    @SuppressLint("AppCompatCustomView")
    private class InternalSeekBar extends SeekBar {
        private Drawable mSmallTickMark;
        private Drawable mMediumTickMark;
        private Drawable mLargeTickMark;
        private Drawable mExtendLeftArrow;
        private Drawable mExtendRightArrow;

        public InternalSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        private void init() {
            resetMin();

            //TODO: (EW) hard-coding to a medium gray to look ok in both light and dark themes. this
            // could be better, such as shifting a bit with the theme, and possibly pairing with the
            // seek bar progress/thumb color (seems to be colorControlActivated based on
            // seekbar_track_material (progressDrawable in Widget.Material.SeekBar in
            // styles_material), although holo theme would need to be considered too). maybe there
            // is a themed color that would match appropriately. the best I found was
            // colorControlNormal, which sounds good, but it didn't have great contrast (and it's
            // not available in Kitkat).
            // I also found colorControlNormal from seekbar_thumb_material_anim (specified in
            // styles_material), but changing that in the theme didn't seem to affect the
            // thumb/progress, but colorAccent happens to (colorControlActivated still overrides
            // it), but I'm not sure where that comes from.
            // note that due to some quirk/bug with DialogPreference, it doesn't seem to get the
            // colorControlActivated color (and others like it) defined in the app theme, although
            // they do change the color if this view is in a regular activity, so it's not easy to
            // test adjustments with that in the preference activity. some other things in the
            // non-dialog portion of the preferences seem to not follow colors from the theme, so it
            // seems like some issue with the preferences, but they're not all ignored so I'm not
            // certain if that is a real issue beyond the dialog.
            int color = Color.parseColor("#999999");

            setSmallTickMark(createRectangleDrawable(1, 2, color));
            setMediumTickMark(createRectangleDrawable(1, 6, color));
            setLargeTickMark(createRectangleDrawable(1, 12, color));
            setExtendLeftArrow(createTriangleDrawable(5, 5, false, color));
            setExtendRightArrow(createTriangleDrawable(5, 5, true, color));
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
        }

        @Override
        protected synchronized void onDraw(final Canvas canvas) {
            super.onDraw(canvas);

            // always show ticks when there is an extended range in order to show movement when
            // shifting
            if (!mAlwaysShowTicks
                    && ExtendingSeekBar.this.getTotalSteps() <= this.getTotalSteps()) {
                return;
            }

            // loosely based on AbsSeekBar#drawTickMarks

            final int min = getMin();
            final int max = getMax();
            final int count = max - min;
            if (count <= 1) {
                return;
            }

            final float spacing =
                    (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) count;
            final int saveCount = canvas.save();
            canvas.translate(getPaddingLeft(), getHeight() / 2f);

            // draw the start arrow if there is more
            if (mMinValue < mCurrentMinValue) {
                mExtendLeftArrow.draw(canvas);
            }

            // draw the tick marks
            for (int i = min; i <= max; i++) {
                int externalProgress = getExternalProgress(i);
                if (externalProgress >= mCurrentMinValue && externalProgress <= mCurrentMaxValue) {
                    Drawable tick;
                    int shift = mZeroBasedTick ? 0 : -mMinValue;
                    int tickPosition = externalProgress / mStepValue + shift;
                    if (tickPosition % mLargeTickStepInterval == 0) {
                        tick = mLargeTickMark;
                    } else if (tickPosition % mMediumTickStepInterval == 0) {
                        tick = mMediumTickMark;
                    } else {
                        tick = mSmallTickMark;
                    }
                    tick.draw(canvas);
                }
                canvas.translate(spacing, 0);
            }

            // draw the end arrow if there is more
            if (mCurrentMaxValue < mMaxValue) {
                canvas.translate(-spacing, 0);
                mExtendRightArrow.draw(canvas);
            }

            canvas.restoreToCount(saveCount);

            // make sure the thumb is on top
            drawThumb(canvas);
        }

        // from AbsSeekBar
        private void drawThumb(Canvas canvas) {
            Drawable thumb = getThumb();
            if (thumb != null) {
                final int saveCount = canvas.save();
                // Translate the padding. For the x, we need to allow the thumb to
                // draw in its extra space
                canvas.translate(getPaddingLeft() - getThumbOffset(), getPaddingTop());
                thumb.draw(canvas);
                canvas.restoreToCount(saveCount);
            }
        }

        @Override
        public synchronized int getMin() {
            return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? super.getMin() : 0);
        }

        public synchronized void resetMin() {
            // don't bother setting the min if it didn't change in case that would trigger any
            // events
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getMin() != 0) {
                super.setMin(0);
            }
        }

        @Override
        public synchronized void setMax(int max) {
            // don't bother setting the max if it didn't change in case that would trigger any
            // events
            if (max != getMax()) {
                super.setMax(max);
            }
        }

        @Override
        public synchronized void setProgress(int progress) {
            // don't bother setting the progress if it didn't change in case that would trigger any
            // events
            if (progress != getProgress()) {
                super.setProgress(progress);
            }
        }

        public synchronized int getTotalSteps() {
            return getMax() - getMin() + 1;
        }

        private ColorStateList mTickMarkTintList = null;
        private PorterDuff.Mode mTickMarkTintMode = null;
        private boolean mHasTickMarkTint = false;
        private boolean mHasTickMarkTintMode = false;

        private void setUpTickMark(Drawable tickMark) {
            if (tickMark != null) {
                tickMark.setCallback(this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    tickMark.setLayoutDirection(getLayoutDirection());
                }
                if (tickMark.isStateful()) {
                    tickMark.setState(getDrawableState());
                }
                centerDrawable(tickMark);
                applyTickMarkTint();
            }
            invalidate();
        }

        private void setUpExtendArrow(Drawable extendArrow, boolean right) {
            if (extendArrow != null) {
                extendArrow.setCallback(this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    extendArrow.setLayoutDirection(getLayoutDirection());
                }
                if (extendArrow.isStateful()) {
                    extendArrow.setState(getDrawableState());
                }
                if (right) {
                    setDrawableBoundsLeftEdge(extendArrow);
                } else {
                    setDrawableBoundsRightEdge(extendArrow);
                }
                applyTickMarkTint();
            }
            invalidate();
        }

        /**
         * Sets the drawable displayed at the left edge of the seek bar indicating there are more
         * positions.
         *
         * @param extendLeftArrow the drawable to display at the left of the seek bar
         */
        public void setExtendLeftArrow(Drawable extendLeftArrow) {
            if (mExtendLeftArrow != null) {
                mExtendLeftArrow.setCallback(null);
            }
            mExtendLeftArrow = extendLeftArrow;
            setUpExtendArrow(extendLeftArrow, false);
        }

        /**
         * @return the drawable displayed at the left edge of the seek bar.
         */
        public Drawable getExtendLeftArrow() {
            return mExtendLeftArrow;
        }

        /**
         * Sets the drawable displayed at the right edge of the seek bar indicating there are more
         * positions.
         *
         * @param extendRightArrow the drawable to display at the right of the seek bar
         */
        public void setExtendRightArrow(Drawable extendRightArrow) {
            if (mExtendRightArrow != null) {
                mExtendRightArrow.setCallback(null);
            }
            mExtendRightArrow = extendRightArrow;
            setUpExtendArrow(extendRightArrow, true);
        }

        /**
         * @return the drawable displayed at the right edge of the seek bar.
         */
        public Drawable getExtendRightArrow() {
            return mExtendRightArrow;
        }

        /**
         * Sets the drawable displayed at each progress position, e.g. at each
         * possible thumb position.
         *
         * @param tickMark the drawable to display at each progress position
         */
        public void setSmallTickMark(Drawable tickMark) {
            if (mSmallTickMark != null) {
                mSmallTickMark.setCallback(null);
            }
            mSmallTickMark = tickMark;
            setUpTickMark(tickMark);
        }

        /**
         * @return the drawable displayed at each progress position
         */
        public Drawable getSmallTickMark() {
            return mSmallTickMark;
        }

        /**
         * Sets the drawable displayed at each medium progress interval position.
         *
         * @param tickMark the drawable to display at each progress position
         */
        public void setMediumTickMark(Drawable tickMark) {
            if (mMediumTickMark != null) {
                mMediumTickMark.setCallback(null);
            }
            mMediumTickMark = tickMark;
            setUpTickMark(tickMark);
        }
        /**
         * @return the drawable displayed at each medium progress interval position
         */
        public Drawable getMediumTickMark() {
            return mMediumTickMark;
        }

        /**
         * Sets the drawable displayed at each large progress interval position.
         *
         * @param tickMark the drawable to display at each progress position
         */
        public void setLargeTickMark(Drawable tickMark) {
            if (mLargeTickMark != null) {
                mLargeTickMark.setCallback(null);
            }
            mLargeTickMark = tickMark;
            setUpTickMark(tickMark);
        }
        /**
         * @return the drawable displayed at each large progress interval position
         */
        public Drawable getLargeTickMark() {
            return mLargeTickMark;
        }

        /**
         * Applies a tint to the tick mark drawables. Does not modify the current tint
         * mode, which is {@link PorterDuff.Mode#SRC_IN} by default.
         * <p>
         * Subsequent calls to {@link #setSmallTickMark(Drawable)},
         * {@link #setMediumTickMark(Drawable)}, and {@link #setLargeTickMark(Drawable)} will
         * automatically mutate the drawable and apply the specified tint and tint mode using
         * {@link Drawable#setTintList(ColorStateList)}.
         *
         * @param tint the tint to apply, may be {@code null} to clear tint
         *
         * @attr ref R.styleable#ExtendingSeekBar_tickMarkTint
         * @see #getTickMarkTintList()
         * @see Drawable#setTintList(ColorStateList)
         */
        public void setTickMarkTintList(@Nullable ColorStateList tint) {
            mTickMarkTintList = tint;
            mHasTickMarkTint = true;

            applyTickMarkTint();
        }

        /**
         * Returns the tint applied to the tick mark drawables, if specified.
         *
         * @return the tint applied to the tick mark drawables
         * @attr ref R.styleable#ExtendingSeekBar_tickMarkTint
         * @see #setTickMarkTintList(ColorStateList)
         */
        @Nullable
        public ColorStateList getTickMarkTintList() {
            return mTickMarkTintList;
        }

        /**
         * Specifies the blending mode used to apply the tint specified by
         * {@link #setTickMarkTintList(ColorStateList)}} to the tick mark drawables. The
         * default mode is {@link PorterDuff.Mode#SRC_IN}.
         *
         * @param tintMode the blending mode used to apply the tint, may be
         *                 {@code null} to clear tint
         *
         * @attr ref R.styleable#ExtendingSeekBar_tickMarkTintMode
         * @see #getTickMarkTintMode()
         * @see Drawable#setTintMode(PorterDuff.Mode)
         */
        public void setTickMarkTintMode(@Nullable PorterDuff.Mode tintMode) {
            mTickMarkTintMode = tintMode;
            mHasTickMarkTintMode = true;

            applyTickMarkTint();
        }

        /**
         * Returns the blending mode used to apply the tint to the tick mark drawables,
         * if specified.
         *
         * @return the blending mode used to apply the tint to the tick mark drawables
         * @attr ref R.styleable#ExtendingSeekBar_tickMarkTintMode
         * @see #setTickMarkTintMode(PorterDuff.Mode)
         */
        @Nullable
        public PorterDuff.Mode getTickMarkTintMode() {
            return mTickMarkTintMode;
        }

        private void applyTickMarkTint() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return;
            }
            Drawable[] drawables = new Drawable[] {
                    mSmallTickMark, mMediumTickMark, mLargeTickMark,
                    mExtendLeftArrow, mExtendRightArrow
            };
            for (int i = 0; i < drawables.length; i++) {
                if (drawables[i] != null && (mHasTickMarkTint || mHasTickMarkTintMode)) {
                    drawables[i] = drawables[i].mutate();
                    if (mHasTickMarkTint) {
                        drawables[i].setTintList(mTickMarkTintList);
                    }
                    if (mHasTickMarkTintMode) {
                        drawables[i].setTintMode(mTickMarkTintMode);
                    }
                    // The drawable (or one of its children) may not have been
                    // stateful before applying the tint, so let's try again.
                    if (drawables[i].isStateful()) {
                        drawables[i].setState(getDrawableState());
                    }
                }
            }
            mSmallTickMark = drawables[0];
            mMediumTickMark = drawables[1];
            mLargeTickMark = drawables[2];
            mExtendLeftArrow = drawables[3];
            mExtendRightArrow = drawables[4];
        }
    }

    private Drawable createRectangleDrawable(float widthDp, float heightDp, int color) {
        int widthPx = Math.max(0, Math.round(dpToPx(widthDp)));
        int heightPx = Math.max(0, Math.round(dpToPx(heightDp)));
        RectShape rect = new RectShape();
        rect.resize(widthPx, heightPx);
        ShapeDrawable drawable = new ShapeDrawable(rect);
        drawable.getPaint().setColor(color);
        drawable.setIntrinsicWidth(widthPx);
        drawable.setIntrinsicHeight(heightPx);
        return drawable;
    }

    private Drawable createTriangleDrawable(float widthDp, float heightDp, boolean right,
                                            int color) {
        int widthPx = Math.max(0, Math.round(dpToPx(widthDp)));
        int heightPx = Math.max(0, Math.round(dpToPx(heightDp)));
        Path path = new Path();
        if (!right) {
            path.moveTo(widthPx, 0);
        }
        path.rLineTo(0, heightPx);
        path.rLineTo(right ? widthPx : -widthPx, -heightPx / 2f);
        path.rLineTo(right ? -widthPx : widthPx, -heightPx / 2f);
        PathShape pathShape = new PathShape(path, widthPx, heightPx);
        ShapeDrawable drawable = new ShapeDrawable(pathShape);
        drawable.getPaint().setColor(color);
        drawable.setIntrinsicWidth(widthPx);
        drawable.setIntrinsicHeight(heightPx);
        return drawable;
    }

    private static void centerDrawable(Drawable d) {
        final int w = d.getIntrinsicWidth();
        final int h = d.getIntrinsicHeight();
        final int halfW = w >= 0 ? w / 2 : 1;
        final int halfH = h >= 0 ? h / 2 : 1;
        d.setBounds(-halfW, -halfH, halfW, halfH);
    }

    private static void setDrawableBoundsLeftEdge(Drawable d) {
        final int w = d.getIntrinsicWidth();
        final int h = d.getIntrinsicHeight();
        final int halfH = h >= 0 ? h / 2 : 1;
        d.setBounds(0, -halfH, w >= 0 ? w : 1, halfH);
    }

    private static void setDrawableBoundsRightEdge(Drawable d) {
        final int w = d.getIntrinsicWidth();
        final int h = d.getIntrinsicHeight();
        final int halfH = h >= 0 ? h / 2 : 1;
        d.setBounds(w >= 0 ? -w : -1, -halfH, 0, halfH);
    }

    /**
     * Sets a listener to receive notifications of changes to the ExtendingSeekBar's progress level.
     * Also provides notifications of when the user starts and stops a touch gesture within the
     * ExtendingSeekBar.
     * @param l The seek bar notification listener
     *
     * @see ExtendingSeekBar.OnExtendingSeekBarChangeListener
     */
    public void setOnSeekBarChangeListener(OnExtendingSeekBarChangeListener l) {
        mOnExtendingSeekBarChangeListener = l;
    }

    /**
     * A callback that notifies clients when the progress level has been
     * changed. This includes changes that were initiated by the user through a
     * touch gesture or arrow key/trackball as well as changes that were initiated
     * programmatically.
     */
    public interface OnExtendingSeekBarChangeListener {

        /**
         * Notification that the progress level has changed. Clients can use the fromUser parameter
         * to distinguish user-initiated changes from those that occurred programmatically.
         *
         * @param seekBar The ExtendingSeekBar whose progress has changed
         * @param progress The current progress level. This will be in the range min..max where min
         *                 and max were set by {@link ProgressBar#setMin(int)} and
         *                 {@link ProgressBar#setMax(int)}, respectively.
         * @param fromUser True if the progress change was initiated by the user.
         */
        void onProgressChanged(ExtendingSeekBar seekBar, int progress, boolean fromUser);

        /**
         * Notification that the user has started a touch gesture. Clients may want to use this
         * to disable advancing the seekbar.
         * @param seekBar The SeekBar in which the touch gesture began
         */
        void onStartTrackingTouch(ExtendingSeekBar seekBar);

        /**
         * Notification that the user has finished a touch gesture. Clients may want to use this
         * to re-enable advancing the seekbar.
         * @param seekBar The SeekBar in which the touch gesture began
         */
        void onStopTrackingTouch(ExtendingSeekBar seekBar);
    }

    private class OnSeekBarChangeListenerProxy implements SeekBar.OnSeekBarChangeListener {
        private final Handler mHandler = new Handler();
        private int mCurrentShiftSteps;
        private int mShiftCount;
        private Timer mTimer;

        private synchronized void startTimer() {
            if (mTimer != null) {
                // the timer is already running, so there isn't anything to do
                return;
            }
            mTimer = new Timer();
            mTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    int shift = getShift(mCurrentShiftSteps, mShiftCount);
                    shiftRange(shift, false);
                    mShiftCount++;

                    // do work on the UI thread
                    int progress = clipToCurrentRange(getProgress());
                    mHandler.post(new Runnable() {
                        public void run() {
                            notifyProgressChanged(progress, true);
                            mInternalSeekBar.invalidate();
                        }
                    });

                    if (progress == mMinValue || progress == mMaxValue) {
                        // we've shifted all the way to an edge, so stop shifting
                        mShiftCount = 0;
                        mCurrentShiftSteps = 0;
                        stopTimer();
                    }
                }
            }, TIMER_TIMEOUT, TIMER_TIMEOUT);
        }

        private synchronized void stopTimer() {
            if (mTimer == null) {
                // there isn't anything to stop
                return;
            }
            mTimer.cancel();
            mTimer = null;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int internalProgress, boolean fromUser) {
            synchronized (ExtendingSeekBar.this) {
                if (mIgnoreInternalProgressChanges) {
                    return;
                }

                int progress = getExternalProgress(internalProgress);

                // if the change is past the edge of visible range, shift the visible range
                int shift;
                if (progress < mCurrentMinValue) {
                    shift = progress - mCurrentMinValue;
                    if (mCurrentShiftSteps >= 0) {
                        // changing directions or starting a shift - reset
                        mShiftCount = 1;
                        stopTimer();
                    }
                } else if (progress > mCurrentMaxValue) {
                    shift = progress - mCurrentMaxValue;
                    if (mCurrentShiftSteps <= 0) {
                        // changing directions or starting a shift - reset
                        mShiftCount = 1;
                        stopTimer();
                    }
                } else {
                    shift = 0;
                    mShiftCount = 0;
                    mCurrentShiftSteps = 0;
                    stopTimer();
                }
                if (shift != 0) {
                    if (mShiftCount == 1) {
                        shiftRange(shift, false);
                        Log.w(TAG, "onProgressChanged: starting timer");
                        startTimer();
                    }
                    mCurrentShiftSteps = shift / mStepValue;
                    if (shift > 1) {
                        // the timer will handle the subsequent shifts, and even if the thumb is
                        // past our internal range, what is reported is capped, so functionally
                        // there wasn't a change in external progress yet, so listeners shouldn't be
                        // notified
                        return;
                    }
                }

                notifyProgressChanged(clipToCurrentRange(progress), fromUser);
            }
        }

        private void notifyProgressChanged(int externalProgress, boolean fromUser) {
            if (clipToCurrentRange(getProgress()) != externalProgress) {
                // this update isn't current, so don't bother notifying
                return;
            }
            if (mOnExtendingSeekBarChangeListener != null) {
                mOnExtendingSeekBarChangeListener.onProgressChanged(ExtendingSeekBar.this,
                        externalProgress, fromUser);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (mOnExtendingSeekBarChangeListener != null) {
                mOnExtendingSeekBarChangeListener.onStartTrackingTouch(ExtendingSeekBar.this);
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // reset the shifting state if the touch ended while shifting
            if (mCurrentShiftSteps != 0) {
                stopTimer();

                // the thumb shouldn't be left in the shifting extra space
                if (mCurrentShiftSteps > 0) {
                    setProgress(mCurrentMaxValue);
                } else {
                    setProgress(mCurrentMinValue);
                }

                mShiftCount = 0;
                mCurrentShiftSteps = 0;
            }

            if (mOnExtendingSeekBarChangeListener != null) {
                mOnExtendingSeekBarChangeListener.onStopTrackingTouch(ExtendingSeekBar.this);
            }
        }
    }

    private int getShift(int baseShiftSteps, int shiftCount) {
        if (shiftCount * TIMER_TIMEOUT <= SLOW_MOVEMENT_TIMEOUT) {
            // start shifting slowly to allow precise shifting (ie a scale of 1)
            return baseShiftSteps * mStepValue;
        } else if (LINEAR_STEP_MAX_EXTEND_RANGE >= getTotalSteps()) {
            // increase at a fixed linear rate since that will reach the full range in a reasonable
            // time
            return getFixedLinearIncreaseStep(baseShiftSteps, shiftCount);
        }
        // increase at a parabolic rate scaled based on the full range to ensure the whole range is
        // able to be reached in a reasonable time (still a bit slow, but reasonable given the size
        // of the range)
        return getParabolicIncreaseStep(baseShiftSteps, shiftCount);
    }

    private int getFixedLinearIncreaseStep(int baseShiftSteps, int shiftCount) {
        // simple linear formula
        //   y = a * x
        // shifted (v_x, v_y)
        //   y = a * (x - v_x) + v_y

        // determine `a` such that the sum of all consecutive shifts at the max base shift step
        // (ie: mStepsToShiftRange) is equal to LINEAR_STEP_MAX_EXTEND_RANGE at
        // LINEAR_STEP_FULL_EXTEND_MAX_TIME. this is essentially the riemann sum of the shifting
        // scale rate piecewise function (delta x = 1):
        // determine `a` such that the sum of all consecutive shift scales multiplied by the max
        // base shift step (ie: mStepsToShiftRange) is equal to LINEAR_STEP_MAX_EXTEND_RANGE at
        // LINEAR_STEP_FULL_EXTEND_MAX_TIME. this is essentially the riemann sum of the shifting
        // scale rate piecewise function (delta x = 1):
        //   f(x) = v_y | x <= v_x
        //   f(x) = a * (x - v_x) + v_y | x > v_x
        // where
        //   f(x) = scale to apply to base shift for current shift interval
        //   shift interval x = mShiftCount
        //   linear function offset (v_x, v_y) = (SLOW_MOVEMENT_TIMEOUT / TIMER_TIMEOUT, 1)
        // so
        //   (sum f(x) from 1 to n) * s = r
        // where
        //   max intervals n = LINEAR_STEP_FULL_EXTEND_MAX_TIME / TIMER_TIMEOUT
        //   max base shift steps s = mStepsToShiftRange
        //   max extended range steps r = LINEAR_STEP_MAX_EXTEND_RANGE
        // known sum of constant
        //   sum c from 1 to k = c * k
        // known sum of arithmetic sequence
        //   sum i from 1 to k = k * (k + 1) / 2
        // split the piecewise function and sum separately to simplify
        //   r = ((sum v_y from 1 to v_x) + (sum (a * (x - v_x) + v_y) from v_x + 1 to n)) * s
        //   =>
        //   sum v_y from 1 to v_x
        //     = v_y * v_x
        //   sum (a * (x - v_x)^2 + v_y) from v_x + 1 to n
        //     = a * (sum x from 1 to n - v_x) + (sum v_y from v_x + 1 to n)
        //     = a * (sum x from 1 to n - v_x) + v_y * (n - v_x)
        //     = a * ((n - v_x) * ((n - v_x) + 1) / 2) + v_y * (n - v_x)
        int maxBaseShiftStep = mStepsToShiftRange;
        int slowMovementScale = 1;
        int slowMovementIntervals = SLOW_MOVEMENT_TIMEOUT / TIMER_TIMEOUT;
        int maxIntervals = LINEAR_STEP_FULL_EXTEND_MAX_TIME / TIMER_TIMEOUT;
        int fastMovementIntervals = maxIntervals - slowMovementIntervals;

        int slowMovementSum = slowMovementScale * slowMovementIntervals;
        int linearConstantSum = slowMovementScale * fastMovementIntervals;
        double simpleLinearSum = fastMovementIntervals * (fastMovementIntervals + 1) / 2d;

        // r = (slowMovementSum + a * simpleLinearSum + linearConstantSum) * s
        double linearScale = ((double)LINEAR_STEP_MAX_EXTEND_RANGE / maxBaseShiftStep
                - slowMovementSum - linearConstantSum)
                / simpleLinearSum;

        double shiftScale = linearScale * (shiftCount - slowMovementIntervals)
                + slowMovementScale;

        return (int)(baseShiftSteps * shiftScale * mStepValue);
    }

    private int getParabolicIncreaseStep(int baseShiftSteps, int shiftCount) {
        // parabolic formula based on a vertex at (v_x, v_y):
        //   y = a * (x - v_x)^2 + v_y

        // determine `a` such that the sum of all consecutive shift scales multiplied by the max
        // base shift step (ie: mStepsToShiftRange) is equal to the max extended range steps
        // (ie: (mMaxValue - mMinValue) / mStepValue - mMaxVisibleSteps) at
        // FULL_EXTEND_MAX_TIME. this is essentially the riemann sum of the shifting scale rate
        // piecewise function (delta x = 1):
        //   f(x) = v_y | x <= v_x
        //   f(x) = a * (x - v_x)^2 + v_y | x > v_x
        // where
        //   f(x) = scale to apply to base shift for current shift interval
        //   shift interval x = mShiftCount
        //   vertex (v_x, v_y) = (SLOW_MOVEMENT_TIMEOUT / TIMER_TIMEOUT, 1)
        // so
        //   (sum f(x) from 1 to n) * s = r
        // where
        //   max intervals n = FULL_EXTEND_MAX_TIME / TIMER_TIMEOUT
        //   max base shift steps s = mStepsToShiftRange
        //   max extended range steps r = (mMaxValue - mMinValue) / mStepValue - mMaxVisibleSteps
        // known sum of constant
        //   sum c from 1 to k = c * k
        // known sum of sequence of squares
        //   sum i^2 from 1 to k = (k * (k + 1) * (2 * k + 1)) / 6
        // split the piecewise function and sum separately to simplify
        //   r = ((sum v_y from 1 to v_x) + (sum (a * (x - v_x)^2 + v_y) from v_x + 1 to n)) * s
        //   =>
        //   sum v_y from 1 to v_x
        //     = v_y * v_x
        //   sum (a * (x - v_x)^2 + v_y) from v_x + 1 to n
        //     = a * (sum x^2 from 1 to n - v_x) + (sum v_y from v_x + 1 to n)
        //     = a * (sum x^2 from 1 to n - v_x) + v_y * (n - v_x)
        //     = a * (((n - v_x) * (n - v_x + 1) * (2 * (n - v_x) + 1)) / 6) + v_y * (n - v_x)
        int maxExtendedRangeSteps = (mMaxValue - mMinValue) / mStepValue - mMaxVisibleSteps;
        int maxBaseShiftStep = mStepsToShiftRange;
        int slowMovementScale = 1;
        int slowMovementIntervals = SLOW_MOVEMENT_TIMEOUT / TIMER_TIMEOUT;
        int maxIntervals = FULL_EXTEND_MAX_TIME / TIMER_TIMEOUT;
        int fastMovementIntervals = maxIntervals - slowMovementIntervals;

        int slowMovementSum = slowMovementScale * slowMovementIntervals;
        int parabolicConstantSum = slowMovementScale * fastMovementIntervals;
        double simpleParabolicSum = fastMovementIntervals * (fastMovementIntervals + 1)
                * (2 * (fastMovementIntervals) + 1) / 6d;

        // r = (slowMovementSum + a * simpleParabolicSum + parabolicConstantSum) * s
        double parabolicScale = ((double)maxExtendedRangeSteps / maxBaseShiftStep
                - slowMovementSum - parabolicConstantSum)
                / simpleParabolicSum;

        double shiftScale = parabolicScale * Math.pow(shiftCount - slowMovementIntervals, 2)
                + slowMovementScale;

        return (int)(baseShiftSteps * shiftScale * mStepValue);
    }

    private String getDebugState() {
        return mMinValue
                + (mMinValue == mCurrentMinValue ? "" : " - - " + mCurrentMinValue)
                + " = = "
                + (mMaxValue == mCurrentMaxValue ? "" : mCurrentMaxValue + " - - ")
                + mMaxValue
                + " (" + getMin() + " - " + getMax() + ") (" + mInternalSeekBar.getProgress()
                + " / " + mInternalSeekBar.getMax() + ")";
    }
}
