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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Pattern;

public class StringArraySerializer {
    public static String[] deserialize(String serializedValue)
            throws InvalidSerializedDataException {
        if (TextUtils.isEmpty(serializedValue)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int delimiterStart = 0;
        for (int i = 0; i < serializedValue.length(); i++) {
            char c = serializedValue.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            } else {
                delimiterStart = i;
                break;
            }
        }
        int delimiterLength;
        try {
            delimiterLength = Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            throw new InvalidSerializedDataException("Failed to parse delimiter length from "
                    + serializedValue);
        }
        if (delimiterStart + delimiterLength > serializedValue.length()) {
            throw new InvalidSerializedDataException("Invalid delimiter length length ("
                    + delimiterLength + ") from " + serializedValue);
        }
        String[] pieces = serializedValue.split(Pattern.quote(
                serializedValue.substring(delimiterStart, delimiterStart + delimiterLength)), -1);

        String[] deserializedValue = new String[pieces.length - 1];
        if (pieces.length > 1) {
            System.arraycopy(pieces, 1, deserializedValue, 0, deserializedValue.length);
        }

        return deserializedValue;
    }

    public static String serialize(String[] stringArray) {
        String delimiter = determineDelimiter(stringArray);
        StringBuilder sb = new StringBuilder();
        sb.append(delimiter.length());
        for (String s : stringArray) {
            sb.append(delimiter).append(s);
        }
        return sb.toString();
    }

    private static String determineDelimiter(final String[] pieces) {
        int delimiterLength = 0;
        String delimiter = "";
        char[] delimiterCharArray;
        HashSet<String> usedText = new HashSet<>();
        while (true) {
            if (delimiterLength > 0) {
                // get a list of all of the text combinations that need to be delimited with a
                // length of the delimiter to be able to exclude them as the delimiter
                for (String piece : pieces) {
                    for (int i = 0; i + delimiterLength <= piece.length(); i++) {
                        usedText.add(piece.substring(i, i + delimiterLength));
                    }
                }

                // find a delimiter with the current length that isn't in the text that needs to be
                // delimited
                while (true) {
                    if (!usedText.contains(delimiter)) {
                        return delimiter;
                    }
                    delimiterCharArray = delimiter.toCharArray();

                    if (!incrementDelimiter(delimiterCharArray)) {
                        // ran out of options - need to increase the length of the delimiter
                        break;
                    }
                    delimiter = new String(delimiterCharArray);
                }

                // all valid delimiters were found in the text to delimit - try a longer delimiter
                usedText.clear();
            }
            delimiterLength++;
            delimiterCharArray = new char[delimiterLength];
            // don't use \0 as a delimiter as that might cause issues
            Arrays.fill(delimiterCharArray, '\u0001');
            if (delimiterLength > 1) {
                // a multi-character delimiter with all of the same character isn't a valid, so we
                // need to get the next valid delimiter to start with
                incrementDelimiter(delimiterCharArray);
            }
            delimiter = new String(delimiterCharArray);
        }
    }

    private static boolean incrementDelimiter(char[] delimiterCharArray) {
        boolean incremented = false;
        while (true) {
            for (int i = delimiterCharArray.length - 1; i >= 0; i--) {
                if (delimiterCharArray[i] < Character.MAX_VALUE) {
                    delimiterCharArray[i]++;
                    incremented = true;
                    break;
                } else {
                    // overflow - move to next char
                    delimiterCharArray[i] = '\0';
                }
            }
            if (!incremented) {
                // ran out of options - need to increase the length of the delimiter
                return false;
            }
            if (!isValidDelimiter(new String(delimiterCharArray))) {
                // the new delimiter can't be used, so try incrementing more
                incremented = false;
            } else {
                // found the next valid delimiter
                return true;
            }
        }
    }

    private static boolean isValidDelimiter(@NonNull String delimiter) {
        // the first character can't be a number because the first piece will be a number to
        // determine the length of the delimiter when parsing the saved preference
        char firstChar = delimiter.charAt(0);
        if (firstChar >= '0' && firstChar <= '9') {
            return false;
        }

        // a delimiter is invalid if any sequence of characters at the beginning matches a sequence
        // at the end. for example:
        // 111: foo11 111 bar == foo1 111 1bar == foo 111 11bar
        // 12321: foo1232 1231 bar == foo 12321 231bar
        // 12312: foo1232 12321 bar == foo 12321 2321bar
        for (int sequenceLength = 1; sequenceLength < delimiter.length(); sequenceLength++) {
            String start = delimiter.substring(0, sequenceLength);
            String end = delimiter.substring(delimiter.length() - sequenceLength);
            if (start.equals(end)) {
                return false;
            }
        }
        return true;
    }

    public static class InvalidSerializedDataException extends Exception {
        public InvalidSerializedDataException(String message) {
            super(message);
        }
    }
}
