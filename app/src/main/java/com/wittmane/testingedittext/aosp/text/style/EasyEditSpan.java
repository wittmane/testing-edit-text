//package com.wittmane.testingedittext.aosp.text.style;
//
//import android.app.PendingIntent;
//import android.os.Parcel;
//import android.text.ParcelableSpan;
//import android.text.TextUtils;
//import android.widget.TextView;
//
//import androidx.annotation.NonNull;
//
////TODO: (EW) see if we can just use the regular EasyEditSpan (are the hidden methods necessary?)
///**
// * Provides an easy way to edit a portion of text.
// * <p>
// * The {@link TextView} uses this span to allow the user to delete a chuck of text in one click.
// * <p>
// * {@link TextView} removes the span when the user deletes the whole text or modifies it.
// * <p>
// * This span can be also used to receive notification when the user deletes or modifies the text;
// */
//public class EasyEditSpan implements ParcelableSpan {
//
//    /**
//     * The extra key field in the pending intent that describes how the text changed.
//     *
//     * @see #TEXT_DELETED
//     * @see #TEXT_MODIFIED
//     */
//    public static final String EXTRA_TEXT_CHANGED_TYPE =
//            "android.text.style.EXTRA_TEXT_CHANGED_TYPE";
//
//    /**
//     * The value of {@link #EXTRA_TEXT_CHANGED_TYPE} when the text wrapped by this span is deleted.
//     */
//    public static final int TEXT_DELETED = 1;
//
//    /**
//     * The value of {@link #EXTRA_TEXT_CHANGED_TYPE} when the text wrapped by this span is modified.
//     */
//    public static final int TEXT_MODIFIED = 2;
//
//    private final PendingIntent mPendingIntent;
//
//    private boolean mDeleteEnabled;
//
//    /**
//     * Creates the span. No intent is sent when the wrapped text is modified or
//     * deleted.
//     */
//    public EasyEditSpan() {
//        mPendingIntent = null;
//        mDeleteEnabled = true;
//    }
//
//    /**
//     * @param pendingIntent The intent will be sent when the wrapped text is deleted or modified.
//     *                      When the pending intent is sent, {@link #EXTRA_TEXT_CHANGED_TYPE} is
//     *                      added in the intent to describe how the text changed.
//     */
//    public EasyEditSpan(PendingIntent pendingIntent) {
//        mPendingIntent = pendingIntent;
//        mDeleteEnabled = true;
//    }
//
//    /**
//     * Constructor called from {@link TextUtils} to restore the span.
//     */
//    public EasyEditSpan(@NonNull Parcel source) {
//        mPendingIntent = source.readParcelable(null);
//        mDeleteEnabled = (source.readByte() == 1);
//    }
//
//    @Override
//    public int describeContents() {
//        return 0;
//    }
//
//    @Override
//    public void writeToParcel(@NonNull Parcel dest, int flags) {
//        writeToParcelInternal(dest, flags);
//    }
//
//    /** @hide */
//    public void writeToParcelInternal(@NonNull Parcel dest, int flags) {
//        dest.writeParcelable(mPendingIntent, 0);
//        dest.writeByte((byte) (mDeleteEnabled ? 1 : 0));
//    }
//
//    @Override
//    public int getSpanTypeId() {
//        return getSpanTypeIdInternal();
//    }
//
//    /** @hide */
//    public int getSpanTypeIdInternal() {
//        return /*TextUtils.EASY_EDIT_SPAN*/22;
//    }
//
//    /**
//     * @return True if the {@link TextView} should offer the ability to delete the text.
//     *
//     * @hide
//     */
////    @UnsupportedAppUsage
//    public boolean isDeleteEnabled() {
//        return mDeleteEnabled;
//    }
//
//    /**
//     * Enables or disables the deletion of the text.
//     *
//     * @hide
//     */
////    @UnsupportedAppUsage
//    public void setDeleteEnabled(boolean value) {
//        mDeleteEnabled = value;
//    }
//
//    /**
//     * @return the pending intent to send when the wrapped text is deleted or modified.
//     *
//     * @hide
//     */
////    @UnsupportedAppUsage
//    public PendingIntent getPendingIntent() {
//        return mPendingIntent;
//    }
//}
