/*
 * Copyright 2025-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.hibernate.jdbc;

class MongoParameterRecognizer {

    static String replace(String json) {
        StringBuilder builder = new StringBuilder(json.length());

        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i++);
            switch (c) {
                case '{':
                case '}':
                case '[':
                case ']':
                case ':':
                case ',':
                case ' ':
                    builder.append(c);
                    break;
                case '\'':
                case '"':
                    i = scanString(c, i, json, builder);
                    break;
                case '?':
                    builder.append("{$undefined: true}");
                    break;
                default:
                    if (c == '-' || Character.isDigit(c)) {
                        i = scanNumber(c, i, json, builder);
                    } else if (c == '$' || c == '_' || Character.isLetter(c)) {
                        i = scanUnquotedString(c, i, json, builder);
                    } else {
                        builder.append(c);  // or throw exception, as this isn't valid JSON
                    }
            }
        }
        return builder.toString();
    }

    private static int scanNumber(char firstCharacter, int startIndex, String json, StringBuilder builder) {
        builder.append(firstCharacter);
        int i = startIndex;
        char c = json.charAt(i++);
        while (i < json.length() && Character.isDigit(c)) {
            builder.append(c);
            c = json.charAt(i++);
        }
        return i - 1;
    }

    private static int scanUnquotedString(final char firstCharacter, final int startIndex, final String json, final StringBuilder builder) {
        builder.append(firstCharacter);
        int i = startIndex;
        char c = json.charAt(i++);
        while (i < json.length() && Character.isLetterOrDigit(c)) {
            builder.append(c);
            c = json.charAt(i++);
        }
        return i - 1;
    }

    private static int scanString(final char quoteCharacter, final int startIndex, final String json, final StringBuilder builder) {
        int i = startIndex;
        builder.append(quoteCharacter);
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\') {
                builder.append(c);
                if (i < json.length()) {
                    c = json.charAt(i++);
                    builder.append(c);
                }
            } else if (c == quoteCharacter) {
                builder.append(c);
                return i;
            } else {
                builder.append(c);
            }
        }
        return i;
    }
}