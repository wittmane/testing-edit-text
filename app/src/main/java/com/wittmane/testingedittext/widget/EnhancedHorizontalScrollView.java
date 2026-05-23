/*
 * Copyright (C) 2026 Eli Wittman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.wittmane.testingedittext.widget;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

import androidx.annotation.RequiresApi;

import com.wittmane.testingedittext.aosp.com.android.internal.graphics.ColorUtils;
import com.wittmane.testingedittext.util.DrawableUtils;
import com.wittmane.testingedittext.util.ResourceUtils;

/**
 * An enhanced version of {@link HorizontalScrollView} that provides extra functionality. Currently
 * this just adds support for showing a fading edge.
 */
public class EnhancedHorizontalScrollView extends HorizontalScrollView {
    private static final String TAG = EnhancedHorizontalScrollView.class.getSimpleName();

    public EnhancedHorizontalScrollView(Context context) {
        super(context);
    }

    public EnhancedHorizontalScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EnhancedHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public EnhancedHorizontalScrollView(Context context, AttributeSet attrs, int defStyleAttr,
                                        int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public int getSolidColor() {
        // get a color that will be used for the fading edge. modify the color of the background to
        // reasonably match while still showing contrast to indicate there is more content out of
        // view.
        Drawable background = DrawableUtils.getNearestBackground(this);
        int backgroundColor;
        if (background == null) {
            backgroundColor = ResourceUtils.getColor(android.R.attr.colorBackground,
                    this.getContext());
        } else if (background instanceof ColorDrawable) {
            backgroundColor = ((ColorDrawable) background).getColor();
        } else {
            // we probably could try to calculate something like the average color from the complex
            // background, but would be more complicated and it still may not produce great results.
            // we'll simply fall back to the base background color, but we'll drop the saturation to
            // make it grayscale to be more neutral to place on top of the complex background.
            float[] hsl = new float[3];
            ColorUtils.colorToHSL(
                    ResourceUtils.getColor(android.R.attr.colorBackground, this.getContext()),
                    hsl);
            hsl[1] = 0;
            backgroundColor = ColorUtils.HSLToColor(hsl);;
        }

        double[] lab = new double[3];
        ColorUtils.colorToLAB(backgroundColor, lab);
        // shift the perceptual lightness - move to the other end of the spectrum for contrast
        if (lab[0] < 50) {
            lab[0] += 15;
        } else {
            lab[0] -= 15;
        }
        return ColorUtils.LABToColor(lab[0], lab[1], lab[2]);
    }
}
