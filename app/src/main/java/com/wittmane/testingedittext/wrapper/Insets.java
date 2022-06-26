package com.wittmane.testingedittext.wrapper;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.lang.reflect.Field;

// wrapper for android.graphics.Insets. Although it existed since at least Kitkat, it was marked
// with @hide until Q, so older versions will use reflection, which should be at least relatively
// safe since it's only done on old versions so it shouldn't just stop working at some point in the
// future.
public class Insets {
    private static final String TAG = Insets.class.getSimpleName();

    private final android.graphics.Insets mInsets;
    private final Object mInsetsReflectionObject;

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public static final Insets NONE;
    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // (EW) despite not actually getting called, on Pie, simply having this code here
            // causes this warning to be logged:
            // Accessing hidden field Landroid/graphics/Insets;->NONE:Landroid/graphics/Insets; (light greylist, linking)
            NONE = new Insets(android.graphics.Insets.NONE);
        } else {
            Object none;
            try {
                Class<?> insetsClass = Class.forName("android.graphics.Insets");

                // (EW) this line on Pie works, but logs the warning (if it wasn't already logged
                // from above):
                // Accessing hidden field Landroid/graphics/Insets;->NONE:Landroid/graphics/Insets; (light greylist, reflection)
                Field noneField = insetsClass.getDeclaredField("NONE");

                // (EW) this line on Pie works, but logs the warnings (if it wasn't already logged
                // from Q constructor):
                // Accessing hidden field Landroid/graphics/Insets;->left:I (light greylist, reflection)
                // Accessing hidden field Landroid/graphics/Insets;->top:I (light greylist, reflection)
                // Accessing hidden field Landroid/graphics/Insets;->right:I (light greylist, reflection)
                // Accessing hidden field Landroid/graphics/Insets;->bottom:I (light greylist, reflection)
                none = noneField.get(null);
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                Log.e(TAG, "NONE: Failed to get field: " + e.getClass().getSimpleName() + ": "
                        + e.getMessage());
                none = null;
            }
            NONE = new Insets(none);
        }
    }

    // (EW) despite not actually getting called, on Pie, simply having this block of code here
    // causes these warnings to be logged:
    // Accessing hidden field Landroid/graphics/Insets;->left:I (light greylist, linking)
    // Accessing hidden field Landroid/graphics/Insets;->top:I (light greylist, linking)
    // Accessing hidden field Landroid/graphics/Insets;->right:I (light greylist, linking)
    // Accessing hidden field Landroid/graphics/Insets;->bottom:I (light greylist, linking)
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public Insets(android.graphics.Insets insets) {
        mInsets = insets;
        mInsetsReflectionObject = null;
        left = mInsets.left;
        top = mInsets.top;
        right = mInsets.right;
        bottom = mInsets.bottom;
    }

    public Insets(Object insets) {
        mInsets = null;
        if (insets != null && "android.graphics.Insets".equals(insets.getClass().getName())) {
            mInsetsReflectionObject = insets;
        } else {
            Log.e(TAG, "Insets: unexpected type: "
                    + (insets == null ? "null" : insets.getClass().getName()));
            mInsetsReflectionObject = null;
        }

        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;
        if (mInsetsReflectionObject != null) {
            try {
                Class<?> insetsClass = Class.forName("android.graphics.Insets");
                Field leftField = insetsClass.getField("left");
                Field topField = insetsClass.getField("top");
                Field rightField = insetsClass.getField("right");
                Field bottomField = insetsClass.getField("bottom");
                left = leftField.getInt(mInsetsReflectionObject);
                top = topField.getInt(mInsetsReflectionObject);
                right = rightField.getInt(mInsetsReflectionObject);
                bottom = bottomField.getInt(mInsetsReflectionObject);
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                Log.e(TAG, "Insets: Failed to get fields: " + e.getClass().getSimpleName() + ": "
                        + e.getMessage());
            }
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
