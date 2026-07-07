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

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRendering;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AstRegularExpressionOperationTests {
    @Test
    void testRendering() {
        var operation = new AstRegularExpressionFilterOperation("pa[t]t?ern*", "i");
        var expectedJson =
                """
                {"$regex": {"$regularExpression": {"pattern": "pa[t]t?ern*", "options": "i"}}}\
                """;
        assertRendering(expectedJson, operation);
    }

    @Test
    void testQuote() {
        assertEquals("^$", AstRegularExpressionFilterOperation.quoteMeta("", null));
        assertEquals("^.*$", AstRegularExpressionFilterOperation.quoteMeta("%", null));
        assertEquals("^.$", AstRegularExpressionFilterOperation.quoteMeta("_", null));
        assertEquals("^x.*x$", AstRegularExpressionFilterOperation.quoteMeta("x%x", null));
        assertEquals("^.*\\.\\*\\+\\\\\\[\\]\\|$", AstRegularExpressionFilterOperation.quoteMeta("%.*+\\[]|", null));
    }

    @Test
    void testQuoteEscaped() {
        assertEquals("^$", AstRegularExpressionFilterOperation.quoteMeta("", 'y'));
        assertEquals("^.*$", AstRegularExpressionFilterOperation.quoteMeta("%", 'y'));
        assertEquals("^%$", AstRegularExpressionFilterOperation.quoteMeta("y%", 'y'));
        assertEquals("^.$", AstRegularExpressionFilterOperation.quoteMeta("_", 'y'));
        assertEquals("^_$", AstRegularExpressionFilterOperation.quoteMeta("y_", 'y'));
        assertEquals("^x.*x%$", AstRegularExpressionFilterOperation.quoteMeta("x%xy%", 'y'));
        assertEquals("^.*y$", AstRegularExpressionFilterOperation.quoteMeta("%yy", 'y'));
        assertEquals("^.*\\.\\*\\+\\\\\\[\\]\\|$", AstRegularExpressionFilterOperation.quoteMeta("%.*+\\[]|", 'y'));
    }

    @Test
    void testQuoteEscapedSlash() {
        assertEquals("^$", AstRegularExpressionFilterOperation.quoteMeta("", '\\'));
        assertEquals("^.*$", AstRegularExpressionFilterOperation.quoteMeta("%", '\\'));
        assertEquals("^%$", AstRegularExpressionFilterOperation.quoteMeta("\\%", '\\'));
        assertEquals("^.$", AstRegularExpressionFilterOperation.quoteMeta("_", '\\'));
        assertEquals("^_$", AstRegularExpressionFilterOperation.quoteMeta("\\_", '\\'));
        assertEquals("^\\\\.*$", AstRegularExpressionFilterOperation.quoteMeta("\\\\%", '\\'));
    }
}
