package com.wittmane.testingedittext.aosp.os;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable containing a raw Parcel of data.
 * @hide
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







    // from android.util.MathUtils
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
