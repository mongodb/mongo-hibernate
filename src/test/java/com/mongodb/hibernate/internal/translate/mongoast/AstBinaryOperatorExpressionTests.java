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

package com.mongodb.hibernate.internal.translate.mongoast;

import static com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator.ADD;
import static com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator.MULTIPLY;
import static com.mongodb.hibernate.internal.translate.mongoast.AstMapChildrenAssertions.assertMapsChildren;
import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertExpressionRendering;
import static com.mongodb.hibernate.internal.translate.mongoast.AstStructuralKeyAssertions.assertStructuralKey;

import java.util.List;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstBinaryOperatorExpressionTests {

    @Test
    void testRenderingFieldPlusLiteral() {
        var expr = new AstBinaryOperatorExpression(
                "$add", new AstFieldPathExpression("x"), new AstValueExpression(new AstLiteral(new BsonInt32(1))));
        assertExpressionRendering(
                """
                {"": {"$add": ["$x", {"$numberInt": "1"}]}}\
                """, expr);
    }

    @Test
    void testRenderingFromComparisonOperator() {
        var expr = new AstBinaryOperatorExpression(
                AstComparisonExpressionOperator.GTE,
                new AstFieldPathExpression("x"),
                new AstValueExpression(new AstLiteral(new BsonInt32(1))));
        assertExpressionRendering(
                """
                {"": {"$gte": ["$x", {"$numberInt": "1"}]}}\
                """, expr);
    }

    @Test
    void testRenderingFromArithmeticOperator() {
        var expr = new AstBinaryOperatorExpression(
                AstArithmeticExpressionOperator.SUBTRACT,
                new AstFieldPathExpression("x"),
                new AstValueExpression(new AstLiteral(new BsonInt32(1))));
        assertExpressionRendering(
                """
                {"": {"$subtract": ["$x", {"$numberInt": "1"}]}}\
                """, expr);
    }

    @Test
    void testRenderingNested() {
        // (x * y) + 1
        var inner = new AstBinaryOperatorExpression(
                "$multiply", new AstFieldPathExpression("x"), new AstFieldPathExpression("y"));
        var outer = new AstBinaryOperatorExpression(
                "$add", inner, new AstValueExpression(new AstLiteral(new BsonInt32(1))));
        assertExpressionRendering(
                """
                {"": {"$add": [{"$multiply": ["$x", "$y"]}, {"$numberInt": "1"}]}}\
                """,
                outer);
    }

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstBinaryOperatorExpression(
                "$add", new AstFieldPathExpression("a"), new AstFieldPathExpression("b")));
    }

    @Test
    void testStructuralKey() {
        assertStructuralKey(
                new AstBinaryOperatorExpression(ADD, new AstFieldPathExpression("a"), new AstFieldPathExpression("b")),
                new StructuralKey(
                        "BinaryOperator",
                        List.of("$add", new AstFieldPathExpression("a"), new AstFieldPathExpression("b"))),
                new AstBinaryOperatorExpression(
                        MULTIPLY,
                        new AstFieldPathExpression("a"),
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(1)))),
                new AstBinaryOperatorExpression(
                        ADD,
                        new AstFieldPathExpression("b"),
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(1)))),
                new AstBinaryOperatorExpression(
                        ADD,
                        new AstFieldPathExpression("a"),
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(2)))));
    }
}
