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
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

class AstInExpressionTests {

    @Test
    void testRenderingNumbers() {
        var expr = new AstInExpression(
                new AstFieldPathExpression("x"),
                List.of(
                        new AstValueExpression(new AstLiteral(new BsonInt32(1))),
                        new AstValueExpression(new AstLiteral(new BsonInt32(2)))));
        assertExpressionRendering(
                """
                {"": {"$in": ["$x", [{"$numberInt": "1"}, {"$numberInt": "2"}]]}}\
                """,
                expr);
    }

    @Test
    void testRenderingWrapsDollarPrefixedOption() {
        var expr = new AstInExpression(
                new AstFieldPathExpression("name"),
                List.of(
                        new AstLiteralExpression(new AstLiteral(new BsonString("$x"))),
                        new AstValueExpression(new AstLiteral(new BsonString("a")))));
        assertExpressionRendering(
                """
                {"": {"$in": ["$name", [{"$literal": "$x"}, "a"]]}}\
                """, expr);
    }

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstInExpression(
                new AstFieldPathExpression("a"),
                List.of(new AstFieldPathExpression("b"), new AstFieldPathExpression("c"))));
    }

    @Test
    void testStructuralKey() {
        assertStructuralKey(
                new AstInExpression(new AstFieldPathExpression("a"), List.of(new AstFieldPathExpression("b"))),
                new StructuralKey(
                        "In", List.of(new AstFieldPathExpression("a"), List.of(new AstFieldPathExpression("b")))),
                new AstInExpression(
                        new AstFieldPathExpression("b"),
                        List.of(new AstLiteralExpression(new AstLiteral(new BsonInt32(1))))),
                new AstInExpression(
                        new AstFieldPathExpression("a"),
                        List.of(new AstLiteralExpression(new AstLiteral(new BsonInt32(2))))),
                new AstInExpression(
                        new AstFieldPathExpression("a"),
                        List.of(
                                new AstLiteralExpression(new AstLiteral(new BsonInt32(1))),
                                new AstLiteralExpression(new AstLiteral(new BsonInt32(2))))));
    }
}
