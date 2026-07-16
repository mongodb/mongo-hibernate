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

import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstValueExpression;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstExprFilterTests {

    @Test
    void testRenderingComparison() {
        // $expr: { $gt: ["$x", 5] }
        var filter = new AstExprFilter(new AstBinaryOperatorExpression(
                "$gt", new AstFieldPathExpression("x"), new AstValueExpression(new AstLiteral(new BsonInt32(5)))));
        assertRendering(
                """
                {"$expr": {"$gt": ["$x", {"$numberInt": "5"}]}}\
                """, filter);
    }

    @Test
    void testRenderingArithmeticInExpr() {
        // $expr: { $gt: [{ $add: ["$x", 1] }, 5] }
        var add = new AstBinaryOperatorExpression(
                "$add", new AstFieldPathExpression("x"), new AstValueExpression(new AstLiteral(new BsonInt32(1))));
        var filter = new AstExprFilter(
                new AstBinaryOperatorExpression("$gt", add, new AstValueExpression(new AstLiteral(new BsonInt32(5)))));
        assertRendering(
                """
                {"$expr": {"$gt": [{"$add": ["$x", {"$numberInt": "1"}]}, {"$numberInt": "5"}]}}\
                """,
                filter);
    }
}
