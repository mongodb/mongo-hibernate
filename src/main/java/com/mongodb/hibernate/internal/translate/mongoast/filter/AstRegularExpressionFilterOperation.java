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

package com.mongodb.hibernate.internal.translate.mongoast.filter;

import org.bson.BsonRegularExpression;
import org.bson.BsonWriter;
import org.jspecify.annotations.Nullable;

/**
 * See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/regex/#mongodb-query-op.-regex">regular
 * expression query predicate</a>, <a href="https://www.mongodb.com/docs/manual/tutorial/query-documents/">Query
 * Documents</a>.
 *
 * @hidden
 */
public record AstRegularExpressionFilterOperation(String pattern, String options) implements AstFilterOperation {
    private static final String REGEX_METACHAR = "[]()|^-+*?{}$\\.";
    /**
     * Converts a SQL LIKE pattern to a regular expression
     *
     * @param pattern the pattern to convert to an equivalent regular expression
     * @param escape the escape character, if any
     * @return a string builder that will hold the regular expression that matches the supplied text
     */
    public static String quoteMeta(String pattern, @Nullable Character escape) {
        var result = new StringBuilder(pattern.length());
        result.append("^");
        for (var index = 0; index < pattern.length(); index++) {
            var current = pattern.charAt(index);
            if (escape != null && current == escape) {
                if (index == pattern.length() - 1) {
                    throw new IllegalArgumentException(
                            "Escape character %s found at end of LIKE pattern: %s".formatted(escape, pattern));
                }
                var next = pattern.charAt(index + 1);
                if (next == '_') {
                    result.append("_");
                } else if (next == '%') {
                    result.append("%");
                } else if (next == escape) {
                    if (REGEX_METACHAR.indexOf(escape) != -1) {
                        result.append("\\");
                    }
                    result.append(escape);
                } else {
                    throw new IllegalArgumentException(
                            "Character %1$s is escaped by %2$s, but only %%, _, and %2$s should be escaped"
                                    .formatted(next, escape));
                }
                index++;
            } else if (current == '_') {
                result.append(".");
            } else if (current == '%') {
                result.append(".*");
            } else {
                if (REGEX_METACHAR.indexOf(current) != -1) {
                    result.append("\\");
                }
                result.append(current);
            }
        }
        result.append("$");
        return result.toString();
    }

    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeName("$regex");
            writer.writeRegularExpression(new BsonRegularExpression(pattern, options));
        }
        writer.writeEndDocument();
    }
}
