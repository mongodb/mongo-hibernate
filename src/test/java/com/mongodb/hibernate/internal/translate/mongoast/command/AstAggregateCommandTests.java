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

package com.mongodb.hibernate.internal.translate.mongoast.command;

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRender;
import static com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.stage.AstProjectStageSpecification.include;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.EQ;

import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstAggregateCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.stage.AstMatchStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.stage.AstProjectStage;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterFieldPath;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstMatchesEverythingFilter;
import java.util.List;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

class AstAggregateCommandTests {

    @Test
    void testRenderingWithExplicitFilter() {
        var collection = "books";
        var filter = new AstFieldOperationFilter(
                new AstFilterFieldPath("title"),
                new AstComparisonFilterOperation(EQ, new AstLiteralValue(new BsonString("In Search of Lost Time"))));

        var projectStageSpecifications =
                List.of(include("_id"), include("author"), include("title"), include("publishYear"));

        var stages = List.<AstStage>of(new AstMatchStage(filter), new AstProjectStage(projectStageSpecifications));
        var aggregateCommand = new AstAggregateCommand(collection, stages);

        var expectedJson =
                """
                {"aggregate": "books", "pipeline": [{"$match": {"title": {"$eq": "In Search of Lost Time"}}}, {"$project": {"_id": 1, "author": 1, "title": 1, "publishYear": 1}}]}\
                """;

        assertRender(expectedJson, aggregateCommand);
    }

    @Test
    void testRenderingWithoutFilter() {
        var collection = "movies";

        var projectStageSpecifications = List.of(include("_id"), include("title"));

        var stages = List.<AstStage>of(
                new AstMatchStage(AstMatchesEverythingFilter.INSTANCE),
                new AstProjectStage(projectStageSpecifications));
        var aggregateCommand = new AstAggregateCommand(collection, stages);

        var expectedJson =
                """
                {"aggregate": "movies", "pipeline": [{"$match": {}}, {"$project": {"_id": 1, "title": 1}}]}\
                """;

        assertRender(expectedJson, aggregateCommand);
    }
}
