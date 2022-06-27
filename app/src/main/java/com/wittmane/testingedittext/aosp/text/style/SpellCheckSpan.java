package com.wittmane.testingedittext.aosp.text.style;

import android.os.Parcel;
import android.os.Parcelable;

// (EW) This had to be copied since the AOSP framework version is marked with @hide. the AOSP
// version implemented ParcelableSpan (which extends Parcelable), but the documentation for
// ParcelableSpan says it "can only be used by code in the framework" and "it is not intended for
// applications to implement their own Parcelable spans." it only adds getSpanTypeId,
// getSpanTypeIdInternal (hidden), and writeToParcelInternal(hidden). it's not very clear what those
// span type IDs are supposed to be, and the only place I found that used them in AOSP code was
// TextUtils#writeToParcel. the AOSP version didn't have CREATOR, and I think the type ID might be
// some alternative to that somehow. it seems more appropriate to just implement Parcelable.
/**
 * A SpellCheckSpan is an internal data structure created by the TextView's SpellChecker to
 * annotate portions of the text that are about to or currently being spell checked. They are
 * automatically removed once the spell check is completed.
 */
public class SpellCheckSpan implements Parcelable {

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
        dest.writeInt(mSpellCheckInProgress ? 1 : 0);
    }

    public static final Creator<SpellCheckSpan> CREATOR = new Creator<SpellCheckSpan>() {
        @Override
        public SpellCheckSpan createFromParcel(Parcel in) {
            return new SpellCheckSpan(in);
        }

        @Override
        public SpellCheckSpan[] newArray(int size) {
            return new SpellCheckSpan[size];
        }
    };
}
