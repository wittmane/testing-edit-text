/*
 * Copyright (C) 2024 Eli Wittman
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

package com.wittmane.testingedittext.settings;

import androidx.annotation.NonNull;

import java.util.Arrays;

/* package */ class TestGroup {
    public final int mId;

    public String mName;
    public int[] mFieldIds;

    public TestGroup(int id, String name, int[] fieldIds) {
        if (id < 0) {
            throw new IllegalArgumentException("The group id can't be negative (" + id + ")");
        }
        mId = id;
        mName = name;
        mFieldIds = fieldIds;
    }

    @NonNull
    @Override
    public String toString() {
        return "{ mId=" + mId + ", mName=" + (mName == null ? "null" : "\"" + mName + "\"")
                + ", mFieldIds=" + Arrays.toString(mFieldIds) + " }";
    }
}
