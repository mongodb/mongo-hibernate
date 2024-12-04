/*
 * Copyright 2024-present MongoDB, Inc.
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

package com.mongodb.hibernate.translate.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.hibernate.translate.mongoast.AstElement;
import com.mongodb.hibernate.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.translate.mongoast.AstNode;
import com.mongodb.hibernate.translate.mongoast.AstPlaceholder;
import com.mongodb.hibernate.translate.mongoast.aggregate.AstPipeline;
import com.mongodb.hibernate.translate.mongoast.aggregate.stages.AstMatchStage;
import com.mongodb.hibernate.translate.mongoast.aggregate.stages.AstProjectStage;
import com.mongodb.hibernate.translate.mongoast.aggregate.stages.AstProjectStageExcludeFieldSpecification;
import com.mongodb.hibernate.translate.mongoast.aggregate.stages.AstProjectStageIncludeFieldSpecification;
import com.mongodb.hibernate.translate.mongoast.command.AstAggregationCommand;
import com.mongodb.hibernate.translate.mongoast.command.AstDeleteCommand;
import com.mongodb.hibernate.translate.mongoast.command.AstFieldUpdate;
import com.mongodb.hibernate.translate.mongoast.command.AstInsertCommand;
import com.mongodb.hibernate.translate.mongoast.command.AstUpdateCommand;
import com.mongodb.hibernate.translate.mongoast.filter.AstAndFilter;
import com.mongodb.hibernate.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.translate.mongoast.filter.AstFilterField;
import com.mongodb.hibernate.translate.mongoast.filter.AstMatchesEverythingFilter;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AstNodeRenderTests {

    private BsonDocumentWriter bsonWriter;

    @BeforeEach
    void setUp() {
        bsonWriter = new BsonDocumentWriter(new BsonDocument());
    }

    private void assertJsonRendered(AstNode node, String expectedJson) {
        node.render(bsonWriter);
        var realJson = bsonWriter.getDocument().toJson();
        assertEquals(expectedJson, realJson);
    }

    @Nested
    class FilterTests {

        @Test
        void testAstComparisonFilterOperationRender() {
            // given
            var astComparisonFilterOperation = new AstComparisonFilterOperation(
                    AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(1)));

            // when && then
            var expectedJson = """
                    {"$eq": 1}""";
            assertJsonRendered(astComparisonFilterOperation, expectedJson);
        }

        @Test
        void testAstFieldOperationFilterRender() {
            // given
            var astFilterField = new AstFilterField("$field");
            var astComparisonFilterOperation = new AstComparisonFilterOperation(
                    AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(1)));
            var astFieldOperationFilter = new AstFieldOperationFilter(astFilterField, astComparisonFilterOperation);

            // when && then
            var expectedJson = """
                    {"$field": {"$eq": 1}}""";
            assertJsonRendered(astFieldOperationFilter, expectedJson);
        }

        @Test
        void testAstAndFilterRender() {
            // given
            var greatThan18 = new AstComparisonFilterOperation(
                    AstComparisonFilterOperator.GT, new AstLiteralValue(new BsonInt32(18)));
            var lessThan60 = new AstComparisonFilterOperation(
                    AstComparisonFilterOperator.LT, new AstLiteralValue(new BsonInt32(60)));
            var ageField = new AstFilterField("$age");
            var astAndFilter = new AstAndFilter(List.of(
                    new AstFieldOperationFilter(ageField, greatThan18),
                    new AstFieldOperationFilter(ageField, lessThan60)));

            // when && then
            var expectedJson = """
                    {"$and": [{"$age": {"$gt": 18}}, {"$age": {"$lt": 60}}]}""";
            assertJsonRendered(astAndFilter, expectedJson);
        }
    }

    @Nested
    class CommandTests {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testAstUpdateCommandRender(boolean filterById) {
            // given
            var collection = "books";
            var astFieldUpdate1 = new AstFieldUpdate("$title", new AstLiteralValue(new BsonString("War and Peace")));
            var astFieldUpdate2 = new AstFieldUpdate("$author", new AstLiteralValue(new BsonString("Leo Tolstoy")));

            final AstFilter filter;
            if (filterById) {
                filter = new AstFieldOperationFilter(
                        new AstFilterField("_id"),
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt64(12345L))));
            } else {
                filter = new AstFieldOperationFilter(
                        new AstFilterField("$isbn"),
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.EQ,
                                new AstLiteralValue(new BsonString("978-3-16-148410-0"))));
            }
            var updateCommand = new AstUpdateCommand(collection, filter, List.of(astFieldUpdate1, astFieldUpdate2));

            // when && then
            final String expectedJson;
            if (filterById) {
                expectedJson =
                        """
                        {"update": "books", "updates": [{"q": {"_id": {"$eq": 12345}}, "u": {"$set": {"$title": "War and Peace", "$author": "Leo Tolstoy"}}, "multi": false}]}""";
            } else {
                expectedJson =
                        """
                        {"update": "books", "updates": [{"q": {"$isbn": {"$eq": "978-3-16-148410-0"}}, "u": {"$set": {"$title": "War and Peace", "$author": "Leo Tolstoy"}}, "multi": true}]}""";
            }
            assertJsonRendered(updateCommand, expectedJson);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testAstDeleteCommandRender(boolean filterById) {
            // given
            var collection = "books";
            final AstFilter filter;
            if (filterById) {
                filter = new AstFieldOperationFilter(
                        new AstFilterField("_id"),
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt64(12345L))));
            } else {
                filter = new AstFieldOperationFilter(
                        new AstFilterField("$isbn"),
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.EQ,
                                new AstLiteralValue(new BsonString("978-3-16-148410-0"))));
            }
            var deleteCommand = new AstDeleteCommand(collection, filter);

            // when && then
            final String expectedJson;
            if (filterById) {
                expectedJson =
                        """
                        {"delete": "books", "deletes": [{"q": {"_id": {"$eq": 12345}}, "limit": 1}]}""";
            } else {
                expectedJson =
                        """
                        {"delete": "books", "deletes": [{"q": {"$isbn": {"$eq": "978-3-16-148410-0"}}, "limit": 0}]}""";
            }
            assertJsonRendered(deleteCommand, expectedJson);
        }

        @Test
        void testAstInsertionCommandRender() {
            // given
            var collection = "books";
            var elements = List.of(
                    new AstElement("$title", new AstLiteralValue(new BsonString("War and Peace"))),
                    new AstElement("$year", new AstLiteralValue(new BsonInt32(1867))),
                    new AstElement("_id", AstPlaceholder.INSTANCE));
            var insertCommand = new AstInsertCommand(collection, elements);

            // when && then
            var expectedJson =
                    """
                    {"insert": "books", "documents": [{"$title": "War and Peace", "$year": 1867, "_id": {"$undefined": true}}]}""";
            assertJsonRendered(insertCommand, expectedJson);
        }

        @Test
        void testAggregateCommandRender() {
            // given
            var collection = "books";
            var filter = new AstFieldOperationFilter(
                    new AstFilterField("_id"),
                    new AstComparisonFilterOperation(
                            AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt64(12345L))));
            var matchStage = new AstMatchStage(filter);
            var projectStage = new AstProjectStage(List.of(
                    new AstProjectStageIncludeFieldSpecification("$title"),
                    new AstProjectStageIncludeFieldSpecification("$author"),
                    new AstProjectStageExcludeFieldSpecification("$description")));
            var aggregateCommand =
                    new AstAggregationCommand(collection, new AstPipeline(List.of(matchStage, projectStage)));

            // when && then
            var expectedJson =
                    """
                    {"aggregate": "books", "pipeline": [{"$match": {"_id": {"$eq": 12345}}}, {"$project": {"$title": 1, "$author": 1, "$description": 0}}]}""";
            assertJsonRendered(aggregateCommand, expectedJson);
        }

        @Test
        void testDeleteAllRender() {
            // given
            var collection = "books";
            var deleteAllCommand = new AstDeleteCommand(collection, AstMatchesEverythingFilter.INSTANCE);

            // when && then
            var expectedJson = """
                        {"delete": "books", "deletes": [{"q": {}, "limit": 0}]}""";
            assertJsonRendered(deleteAllCommand, expectedJson);
        }
    }
}
