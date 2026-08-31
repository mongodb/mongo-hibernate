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

import static com.mongodb.hibernate.internal.translate.mongoast.AstMapChildrenAssertions.assertMapsChildren;
import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertExpressionRendering;
import static com.mongodb.hibernate.internal.translate.mongoast.AstStructuralKeyAssertions.assertStructuralKey;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstLetBindingExpressionTests {

    @Test
    void testRendering() {
        var let = new AstLetBindingExpression(
                new AstVariableExpression("x"),
                new TreeMap<>(Map.of("x", new AstValueExpression(new AstLiteral(new BsonInt32(2))))));
        assertExpressionRendering(
                """
                {"": {"$let": {"vars": {"x": {"$numberInt": "2"}}, "in": "$$x"}}}\
                """,
                let);
    }

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstLetBindingExpression(
                new AstFieldPathExpression("a"),
                new TreeMap<>(Map.of(
                        "v", (AstExpression) new AstFieldPathExpression("b"), "w", new AstFieldPathExpression("c")))));
    }

    @Test
    void testStructuralKey() {
        assertStructuralKey(
                new AstLetBindingExpression(new AstFieldPathExpression("a"), new TreeMap<>(Map.of("v", (AstExpression)
                        new AstFieldPathExpression("b")))),
                new StructuralKey(
                        "LetBinding", List.of(new AstFieldPathExpression("a"), new TreeMap<>(Map.of("v", (AstExpression)
                                new AstFieldPathExpression("b"))))),
                new AstLetBindingExpression(new AstFieldPathExpression("b"), new TreeMap<>(Map.of("v", (AstExpression)
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(1)))))),
                new AstLetBindingExpression(new AstFieldPathExpression("a"), new TreeMap<>(Map.of("w", (AstExpression)
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(1)))))),
                new AstLetBindingExpression(new AstFieldPathExpression("a"), new TreeMap<>(Map.of("v", (AstExpression)
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(2)))))));
    }
}
