/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.wittmane.testingedittext.aosp.os;

import android.os.Parcel;
import android.os.Parcelable;

// (EW) the AOSP version of this is hidden from apps, so it had to be copied here
/**
 * Parcelable containing a raw Parcel of data.
 */
public class ParcelableParcel implements Parcelable {
    final Parcel mParcel;
    final ClassLoader mClassLoader;

    public ParcelableParcel(ClassLoader loader) {
        mParcel = Parcel.obtain();
        mClassLoader = loader;
    }

    public ParcelableParcel(Parcel src, ClassLoader loader) {
        mParcel = Parcel.obtain();
        mClassLoader = loader;
        int size = src.readInt();
        if (size < 0) {
            throw new IllegalArgumentException("Negative size read from parcel");
        }

        int pos = src.dataPosition();
        src.setDataPosition(addOrThrow(pos, size));
        mParcel.appendFrom(src, pos, size);
    }

    public Parcel getParcel() {
        mParcel.setDataPosition(0);
        return mParcel;
    }

    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mParcel.dataSize());
        dest.appendFrom(mParcel, 0, mParcel.dataSize());
    }

    public static final Parcelable.ClassLoaderCreator<ParcelableParcel> CREATOR
            = new Parcelable.ClassLoaderCreator<ParcelableParcel>() {
        public ParcelableParcel createFromParcel(Parcel in) {
            return new ParcelableParcel(in, null);
        }

        public ParcelableParcel createFromParcel(Parcel in, ClassLoader loader) {
            return new ParcelableParcel(in, loader);
        }

        public ParcelableParcel[] newArray(int size) {
            return new ParcelableParcel[size];
        }
    };

    // (EW) from android.util.MathUtils
    /**
     * Returns the sum of the two parameters, or throws an exception if the resulting sum would
     * cause an overflow or underflow.
     * @throws IllegalArgumentException when overflow or underflow would occur.
     */
    private static int addOrThrow(int a, int b) throws IllegalArgumentException {
        if (b == 0) {
            return a;
        }

        if (b > 0 && a <= (Integer.MAX_VALUE - b)) {
            return a + b;
        }

        if (b < 0 && a >= (Integer.MIN_VALUE - b)) {
            return a + b;
        }
        throw new IllegalArgumentException("Addition overflow: " + a + " + " + b);
    }
}
