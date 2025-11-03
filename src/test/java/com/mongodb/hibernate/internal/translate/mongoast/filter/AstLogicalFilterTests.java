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
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.EQ;

import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import java.util.List;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AstLogicalFilterTests {
    @ParameterizedTest
    @EnumSource(AstLogicalFilterOperator.class)
    void testRendering(AstLogicalFilterOperator operator) {
        var astLogicalFilter = new AstLogicalFilter(
                operator,
                List.of(
                        new AstFieldOperationFilter(
                                "field1", new AstComparisonFilterOperation(EQ, new AstLiteral(new BsonInt32(1)))),
                        new AstFieldOperationFilter(
                                "field2", new AstComparisonFilterOperation(EQ, new AstLiteral(new BsonString("1"))))));

        var expectedJson =
                """
                {"%s": [{"field1": {"$eq": {"$numberInt": "1"}}}, {"field2": {"$eq": "1"}}]}\
                """
                        .formatted(operator.getOperatorName());
        assertRendering(expectedJson, astLogicalFilter);
    }
}
