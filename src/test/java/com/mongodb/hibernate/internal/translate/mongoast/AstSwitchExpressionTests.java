/*
 * Copyright 2026-present MongoDB, Inc.
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

import static com.mongodb.hibernate.internal.translate.mongoast.AstMapChildrenAssertions.assertMapsChildren;
import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertExpressionRendering;
import static com.mongodb.hibernate.internal.translate.mongoast.AstStructuralKeyAssertions.assertStructuralKey;

import java.util.List;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.junit.jupiter.api.Test;

class AstSwitchExpressionTests {

    @Test
    void testRenderingSingleBranch() {
        var expr = new AstSwitchExpression(
                List.of(new AstSwitchCase(
                        new AstBinaryOperatorExpression(
                                AstComparisonExpressionOperator.GT,
                                new AstFieldPathExpression("x"),
                                new AstValueExpression(new AstLiteral(new BsonInt32(5)))),
                        new AstValueExpression(new AstLiteral(new BsonInt32(1))))),
                new AstValueExpression(new AstLiteral(new BsonInt32(0))));
        assertExpressionRendering(
                """
                {"": {"$switch": {"branches": [{"case": {"$gt": ["$x", {"$numberInt": "5"}]}, "then": {"$numberInt": "1"}}], "default": {"$numberInt": "0"}}}}\
                """,
                expr);
    }

    @Test
    void testRenderingMultipleBranchesWithNullDefault() {
        var expr = new AstSwitchExpression(
                List.of(
                        new AstSwitchCase(
                                new AstBinaryOperatorExpression(
                                        AstComparisonExpressionOperator.EQ,
                                        new AstFieldPathExpression("x"),
                                        new AstValueExpression(new AstLiteral(new BsonInt32(1)))),
                                new AstValueExpression(new AstLiteral(new BsonInt32(10)))),
                        new AstSwitchCase(
                                new AstBinaryOperatorExpression(
                                        AstComparisonExpressionOperator.EQ,
                                        new AstFieldPathExpression("x"),
                                        new AstValueExpression(new AstLiteral(new BsonInt32(2)))),
                                new AstValueExpression(new AstLiteral(new BsonInt32(20))))),
                new AstValueExpression(new AstLiteral(BsonNull.VALUE)));
        assertExpressionRendering(
                """
                {"": {"$switch": {"branches": [{"case": {"$eq": ["$x", {"$numberInt": "1"}]}, "then": {"$numberInt": "10"}}, {"case": {"$eq": ["$x", {"$numberInt": "2"}]}, "then": {"$numberInt": "20"}}], "default": null}}}\
                """,
                expr);
    }

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstSwitchExpression(
                List.of(
                        new AstSwitchCase(new AstFieldPathExpression("a"), new AstFieldPathExpression("b")),
                        new AstSwitchCase(new AstFieldPathExpression("c"), new AstFieldPathExpression("d"))),
                new AstFieldPathExpression("f")));
    }

    @Test
    void testStructuralKey() {
        assertStructuralKey(
                new AstSwitchExpression(
                        List.of(new AstSwitchCase(new AstFieldPathExpression("a"), new AstFieldPathExpression("b"))),
                        new AstFieldPathExpression("c")),
                new StructuralKey(
                        "Switch",
                        List.of(
                                new AstFieldPathExpression("a"),
                                new AstFieldPathExpression("b"),
                                new AstFieldPathExpression("c"))),
                new AstSwitchExpression(
                        List.of(new AstSwitchCase(
                                new AstFieldPathExpression("b"),
                                new AstLiteralExpression(new AstLiteral(new BsonInt32(1))))),
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(2)))),
                new AstSwitchExpression(
                        List.of(new AstSwitchCase(
                                new AstFieldPathExpression("a"),
                                new AstLiteralExpression(new AstLiteral(new BsonInt32(2))))),
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(2)))),
                new AstSwitchExpression(
                        List.of(new AstSwitchCase(
                                new AstFieldPathExpression("a"),
                                new AstLiteralExpression(new AstLiteral(new BsonInt32(1))))),
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(1)))),
                new AstSwitchExpression(
                        List.of(
                                new AstSwitchCase(
                                        new AstFieldPathExpression("a"),
                                        new AstLiteralExpression(new AstLiteral(new BsonInt32(1)))),
                                new AstSwitchCase(
                                        new AstFieldPathExpression("b"),
                                        new AstLiteralExpression(new AstLiteral(new BsonInt32(2))))),
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(2)))));
    }
}
