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

package com.mongodb.hibernate.internal.translate.mongoast.command.aggregate;

import static com.mongodb.hibernate.internal.translate.mongoast.AstMapChildrenAssertions.assertMapsChildren;
import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRendering;

import com.mongodb.hibernate.internal.translate.mongoast.AstArrayExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstValueExpression;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstReplaceWithStageTests {

    @Test
    void testRendering() {
        var astReplaceWithStage = new AstReplaceWithStage(new AstFieldPathExpression("nested"));

        var expectedJson = """
                           {"$replaceWith": "$nested"}\
                           """;
        assertRendering(expectedJson, astReplaceWithStage);
    }

    @Test
    void testRenderingComputedExpression() {
        var astReplaceWithStage = new AstReplaceWithStage(new AstBinaryOperatorExpression(
                AstArrayExpressionOperator.ARRAY_ELEM_AT,
                new AstFieldPathExpression("f"),
                new AstValueExpression(new AstLiteral(new BsonInt32(0)))));

        var expectedJson =
                """
                {"$replaceWith": {"$arrayElemAt": ["$f", {"$numberInt": "0"}]}}\
                """;
        assertRendering(expectedJson, astReplaceWithStage);
    }

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstReplaceWithStage(new AstFieldPathExpression("nested")));
    }
}
