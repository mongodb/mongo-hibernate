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

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRender;

import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import java.util.List;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstNorFilterTests {
    @Test
    void testRendering() {
        var filters = List.of(
                new AstFieldOperationFilter(
                        new AstFilterFieldPath("field1"),
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(1)))),
                new AstFieldOperationFilter(
                        new AstFilterFieldPath("field2"),
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.NE, new AstLiteralValue(new BsonInt32(0)))));
        var norFilter = new AstNorFilter(filters);
        var expectedJson =
                """
                {"$nor": [{"field1": {"$eq": 1}}, {"field2": {"$ne": 0}}]}\
                """;
        assertRender(expectedJson, norFilter);
    }
}
