package com.wittmane.testingedittext.aosp.content;

/**
 * Representation of an owner of {@link UndoOperation} objects in an {@link UndoManager}.
 *
 * @hide
 */
public class UndoOwner {
    final String mTag;
    final UndoManager mManager;

    Object mData;
    int mOpCount;

    // For saving/restoring state.
    int mStateSeq;
    int mSavedIdx;

    UndoOwner(String tag, UndoManager manager) {
        if (tag == null) {
            throw new NullPointerException("tag can't be null");
        }
        if (manager == null) {
            throw new NullPointerException("manager can't be null");
        }
        mTag = tag;
        mManager = manager;
    }

    /**
     * Return the unique tag name identifying this owner.  This is the tag
     * supplied to {@link UndoManager#getOwner(String, Object) UndoManager.getOwner}
     * and is immutable.
     */
    public String getTag() {
        return mTag;
    }

    /**
     * Return the actual data object of the owner.  This is the data object
     * supplied to {@link UndoManager#getOwner(String, Object) UndoManager.getOwner}.  An
     * owner may have a null data if it was restored from a previously saved state with
     * no getOwner call to associate it with its data.
     */
    public Object getData() {
        return mData;
    }

    @Override
    public String toString() {
        return "UndoOwner:[mTag=" + mTag +
                " mManager=" + mManager +
                " mData=" + mData +
                " mData=" + mData +
                " mOpCount=" + mOpCount +
                " mStateSeq=" + mStateSeq +
                " mSavedIdx=" + mSavedIdx + "]";
    }
}
