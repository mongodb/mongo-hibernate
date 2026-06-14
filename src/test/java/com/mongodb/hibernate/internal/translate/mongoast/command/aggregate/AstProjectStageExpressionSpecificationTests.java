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

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertElementRendering;

import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstValueExpression;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstProjectStageExpressionSpecificationTests {

    @Test
    void testRenderingWithAlias() {
        var spec = new AstProjectStageExpressionSpecification(
                "total",
                new AstBinaryOperatorExpression(
                        "$add",
                        new AstFieldPathExpression("x"),
                        new AstValueExpression(new AstLiteral(new BsonInt32(1)), false)));
        assertElementRendering(
                """
                {"total": {"$add": ["$x", {"$numberInt": "1"}]}}\
                """, spec);
    }

    @Test
    void testRenderingWithGeneratedKey() {
        var spec = new AstProjectStageExpressionSpecification("#c_1", new AstFieldPathExpression("x"));
        assertElementRendering("""
                {"#c_1": "$x"}\
                """, spec);
    }
}
