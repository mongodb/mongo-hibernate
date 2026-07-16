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

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertExpressionRendering;

import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstUnaryOperatorExpressionTests {

    @Test
    void testRendering() {
        // $toInt($divide(["$x", 2]))
        var divide = new AstBinaryOperatorExpression(
                "$divide", new AstFieldPathExpression("x"), new AstValueExpression(new AstLiteral(new BsonInt32(2))));
        var toInt = new AstUnaryOperatorExpression("$toInt", divide);
        assertExpressionRendering(
                """
                {"": {"$toInt": {"$divide": ["$x", {"$numberInt": "2"}]}}}\
                """,
                toInt);
    }

    @Test
    void testRenderingFromConversionOperator() {
        var divide = new AstBinaryOperatorExpression(
                "$divide", new AstFieldPathExpression("x"), new AstValueExpression(new AstLiteral(new BsonInt32(2))));
        var toLong = new AstUnaryOperatorExpression(AstConversionExpressionOperator.TO_LONG, divide);
        assertExpressionRendering(
                """
                {"": {"$toLong": {"$divide": ["$x", {"$numberInt": "2"}]}}}\
                """,
                toLong);
    }
}
