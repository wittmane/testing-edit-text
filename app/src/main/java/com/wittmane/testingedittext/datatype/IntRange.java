/*
 * Copyright (C) 2022-2024 Eli Wittman
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

package com.wittmane.testingedittext.datatype;

import java.util.Objects;

public class IntRange {
    private final int mStart;
    private final int mEnd;

    public IntRange(final int start, final int end) {
        mStart = start;
        mEnd = end;
    }

    public int getStart() {
        return mStart;
    }

    public int getEnd() {
        return mEnd;
    }

    @Override
    public String toString() {
        return "[" + mStart + ", " + mEnd + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IntRange)) {
            return false;
        }
        IntRange other = (IntRange) o;
        return mStart == other.mStart && mEnd == other.mEnd;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStart, mEnd);
    }
}
