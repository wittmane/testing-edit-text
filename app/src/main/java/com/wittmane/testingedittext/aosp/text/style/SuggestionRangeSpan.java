/*
 * Copyright (C) 2022 Eli Wittman
 * Copyright (C) 2011 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;
import android.text.style.CharacterStyle;

// (EW) This had to be copied since the AOSP framework version is marked with @hide. the AOSP
// version implemented ParcelableSpan (which extends Parcelable), but the documentation for
// ParcelableSpan says it "can only be used by code in the framework" and "it is not intended for
// applications to implement their own Parcelable spans." it only adds getSpanTypeId,
// getSpanTypeIdInternal (hidden), and writeToParcelInternal(hidden). it's not very clear what those
// span type IDs are supposed to be, and the only place I found that used them in AOSP code was
// TextUtils#writeToParcel. the AOSP version didn't have CREATOR, and I think the type ID might be
// some alternative to that somehow. it seems more appropriate to just implement Parcelable.
/**
 * A SuggestionRangeSpan is used to show which part of an EditText is affected by a suggestion
 * popup window.
 */
public class SuggestionRangeSpan extends CharacterStyle implements Parcelable {
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
        dest.writeInt(mBackgroundColor);
    }

    public void setBackgroundColor(int backgroundColor) {
        mBackgroundColor = backgroundColor;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        tp.bgColor = mBackgroundColor;
    }

    public static final Creator<SuggestionRangeSpan> CREATOR = new Creator<SuggestionRangeSpan>() {
        @Override
        public SuggestionRangeSpan createFromParcel(Parcel in) {
            return new SuggestionRangeSpan(in);
        }

        @Override
        public SuggestionRangeSpan[] newArray(int size) {
            return new SuggestionRangeSpan[size];
        }
    };
}
