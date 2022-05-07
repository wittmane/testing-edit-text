package com.wittmane.testingedittext.aosp.text.style;

import android.os.Parcel;
import android.text.ParcelableSpan;
//import android.text.TextUtils;

/**
 * A SpellCheckSpan is an internal data structure created by the TextView's SpellChecker to
 * annotate portions of the text that are about to or currently being spell checked. They are
 * automatically removed once the spell check is completed.
 *
 * @hide
 */
public class SpellCheckSpan implements ParcelableSpan {

    private boolean mSpellCheckInProgress;

    public SpellCheckSpan() {
        mSpellCheckInProgress = false;
    }

    public SpellCheckSpan(Parcel src) {
        mSpellCheckInProgress = (src.readInt() != 0);
    }

    public void setSpellCheckInProgress(boolean inProgress) {
        mSpellCheckInProgress = inProgress;
    }

    public boolean isSpellCheckInProgress() {
        return mSpellCheckInProgress;
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
        dest.writeInt(mSpellCheckInProgress ? 1 : 0);
    }

    @Override
    public int getSpanTypeId() {
        return getSpanTypeIdInternal();
    }

    /** @hide */
    public int getSpanTypeIdInternal() {
        //TODO: (EW) probably don't do this
        return /*TextUtils.SPELL_CHECK_SPAN*/20;
    }
}
