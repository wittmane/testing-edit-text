/*
 * Copyright (C) 2022 Eli Wittman
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

/**
 * An InputFilter that only allows 0-9.
 */
public class NumericFilter extends CharFilter {

    @Override
    protected boolean isValidChar(char c) {
        return c >= '0' && c <= '9';
    }

    @Override
    protected char convertChar(char c) {
        return c;
    }
}
