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

package com.wittmane.testingedittext.util;

import android.os.Build;
import android.transition.Transition;
import android.transition.TransitionSet;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class TransitionUtils {
    private static final String TAG = TransitionUtils.class.getSimpleName();

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static void setTotalDuration(Transition transition, long totalDuration) {
        long originalTotalDuration = getTotalDuration(transition, true);
        if (originalTotalDuration < 0) {
            // we can't tell the exact duration, so assume no start delay and set the duration, even
            // if that inappropriately evenly distributes to all children
            Log.w(TAG, "Can't determine total duration for " + transition
                    + ", so it can't be scaled properly");
            transition.setStartDelay(0);
            transition.setDuration(totalDuration);
            return;
        }

        long startDelay = transition.getStartDelay();
        long duration = transition.getDuration();
        if (duration >= 0) {
            if (startDelay < 0) {
                // assume there should be no start delay and explicitly set that
                Log.w(TAG, "Explicitly setting no start delay for " + transition);
                transition.setStartDelay(0);
            }
            if (originalTotalDuration > 0) {
                // scale the start delay and duration
                transition.setDuration(totalDuration * duration / originalTotalDuration);
                transition.setStartDelay(totalDuration - transition.getDuration());
            } else {
                // start delay and duration are both 0, so just set the duration and leave no start
                // delay
                transition.setDuration(totalDuration);
            }
        } else if (transition instanceof TransitionSet) {
            long newDuration;
            if (startDelay > 0) {
                // scale the start delay and duration
                newDuration = totalDuration * (originalTotalDuration - startDelay)
                        / originalTotalDuration;
            } else {
                // start delay and duration are both 0, so just set the duration and leave no start
                // delay
                newDuration = totalDuration;
            }
            transition.setStartDelay(totalDuration - newDuration);
            TransitionSet transitionSet = (TransitionSet) transition;
            long originalMaxChildTotalDuration =
                    originalTotalDuration - (startDelay >= 0 ? startDelay : 0);
            for (int i = 0; i < transitionSet.getTransitionCount(); i++) {
                Transition childTransition = transitionSet.getTransitionAt(i);
                long childTotalDuration = getTotalDuration(childTransition, true);
                setTotalDuration(childTransition,
                        newDuration * childTotalDuration / originalMaxChildTotalDuration);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static long getTotalDuration(Transition transition, boolean assumeZeroStartOffsets) {
        long startDelay = transition.getStartDelay();
        long duration = transition.getDuration();
        if (duration >= 0) {
            if (startDelay >= 0) {
                return startDelay + duration;
            }
            if (assumeZeroStartOffsets) {
                return duration;
            }
        }
        if (transition instanceof TransitionSet) {
            TransitionSet transitionSet = (TransitionSet) transition;
            long maxTotalDuration = 0;
            for (int i = 0; i < transitionSet.getTransitionCount(); i++) {
                Transition childTransition = transitionSet.getTransitionAt(i);
                long totalDuration = getTotalDuration(childTransition, assumeZeroStartOffsets);
                if (totalDuration < 0) {
                    return -1;
                }
                if (totalDuration > maxTotalDuration) {
                    maxTotalDuration = totalDuration;
                }
            }
            return (startDelay >= 0 ? startDelay : 0) + maxTotalDuration;
        }
        // duration comes from animator, so we can't cleanly get that
        return -1;
    }
}
