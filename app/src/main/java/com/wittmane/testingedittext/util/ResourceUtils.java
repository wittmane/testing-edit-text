/*
 * Copyright (C) 2025-2026 Eli Wittman
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

package com.wittmane.testingedittext.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;

public class ResourceUtils {

    public static final int RESOURCES_ID_NULL =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Resources.ID_NULL : 0;

    public static int getResourceId(int attr, Context context) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[] { attr });
        int resId = typedArray.getResourceId(0, RESOURCES_ID_NULL);
        typedArray.recycle();
        return resId;
    }

    public static int getResourceId(int styleable, int attr, Context context) {
        TypedArray typedArray =
                context.getTheme().obtainStyledAttributes(styleable, new int[] { attr });
        int resId = typedArray.getResourceId(0, RESOURCES_ID_NULL);
        typedArray.recycle();
        return resId;
    }

    public static int getColor(int attr, Context context) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[] { attr });
        int color = typedArray.getColor(0, Color.TRANSPARENT);
        typedArray.recycle();
        return color;
    }

    public static int getDimensionPixels(int attr, Context context) {
        try (TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[] { attr })) {
            return typedArray.getDimensionPixelSize(0, 0);
        }
    }

    public static float dpToPx(float dp, Context context) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    public static float pxToDp(float px, Context context) {
        return px / context.getResources().getDisplayMetrics().density;
    }
}
