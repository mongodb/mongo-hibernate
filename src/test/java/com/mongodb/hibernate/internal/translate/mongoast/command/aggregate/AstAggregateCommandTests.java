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

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRendering;

import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterFieldPath;
import java.util.List;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstAggregateCommandTests {

    @Test
    void testRendering() {
        var matchStage = new AstMatchStage(new AstFieldOperationFilter(
                new AstFilterFieldPath("_id"),
                new AstComparisonFilterOperation(
                        AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(1)))));
        var projectStage = new AstProjectStage(List.of(new AstProjectStageIncludeSpecification("title")));
        var aggregateCommand = new AstAggregateCommand("books", List.of(matchStage, projectStage));
        var expectedJson =
                """
                {"aggregate": "books", "pipeline": [{"$match": {"_id": {"$eq": {"$numberInt": "1"}}}}, {"$project": {"title": true}}]}\
                """;
        assertRendering(expectedJson, aggregateCommand);
    }
}
