package com.wittmane.testingedittext.aosp.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;

import com.wittmane.testingedittext.aosp.text.HiddenTextUtils;

/**
 * A SuggestionRangeSpan is used to show which part of an EditText is affected by a suggestion
 * popup window.
 *
 * @hide
 */
public class SuggestionRangeSpan extends CharacterStyle implements ParcelableSpan {
    private int mBackgroundColor;

    public SuggestionRangeSpan() {
        // 0 is a fully transparent black. Has to be set using #setBackgroundColor
        mBackgroundColor = 0;
    }

    public SuggestionRangeSpan(Parcel src) {
        mBackgroundColor = src.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }

    /** @hide */
    public void writeToParcelInternal(Parcel dest, int flags) {
        dest.writeInt(mBackgroundColor);
    }

    @Override
    public int getSpanTypeId() {
        return getSpanTypeIdInternal();
    }

    /** @hide */
    public int getSpanTypeIdInternal() {
        //TODO: (EW) maybe don't reuse a hidden id
        return HiddenTextUtils.SUGGESTION_RANGE_SPAN;
    }

    public void setBackgroundColor(int backgroundColor) {
        mBackgroundColor = backgroundColor;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        tp.bgColor = mBackgroundColor;
    }
}
