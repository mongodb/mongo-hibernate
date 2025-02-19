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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.hibernate.internal.translate.mongoast.command.AstDeleteCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterField;

import java.io.StringWriter;
import java.util.List;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.json.JsonWriter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AstNodeRenderTests {

    private void assertJsonRendered(AstNode node, String expectedJson) {
        var stringWriter = new StringWriter();
        var jsonWriter = new JsonWriter(stringWriter);
        node.render(jsonWriter);
        jsonWriter.flush();
        var realJson = stringWriter.toString();
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
            var expectedJson =
                    """
                    {"$eq": 1}\
                    """;
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
            var expectedJson =
                    """
                    {"$field": {"$eq": 1}}\
                    """;
            assertJsonRendered(astFieldOperationFilter, expectedJson);
        }
    }

    @Nested
    class CommandTests {

        @Test
        void testAstInsertionCommandRender() {
            // given
            var collection = "books";
            var elements = List.of(
                    new AstElement("$title", new AstLiteralValue(new BsonString("War and Peace"))),
                    new AstElement("$year", new AstLiteralValue(new BsonInt32(1867))),
                    new AstElement("_id", AstPlaceholder.INSTANCE));
            var insertCommand = new AstInsertCommand(collection, new AstDocument(elements));

            // when && then
            var expectedJson =
                    """
                    {"insert": "books", "documents": [{"$title": "War and Peace", "$year": 1867, "_id": {"$undefined": true}}]}\
                    """;
            assertJsonRendered(insertCommand, expectedJson);
        }

        @Test
        void testAstDeleteCommandRender() {
            // given
            var collection = "books";
            var filter = new AstFieldOperationFilter(
                    new AstFilterField("$isbn"),
                    new AstComparisonFilterOperation(
                            AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonString("978-3-16-148410-0"))));

            var deleteCommand = new AstDeleteCommand(collection, filter);

            // when && then
            var expectedJson =
                    """
                    {"delete": "books", "deletes": [{"q": {"$isbn": {"$eq": "978-3-16-148410-0"}}, "limit": 0}]}\
                    """;
            assertJsonRendered(deleteCommand, expectedJson);
        }
    }
}
