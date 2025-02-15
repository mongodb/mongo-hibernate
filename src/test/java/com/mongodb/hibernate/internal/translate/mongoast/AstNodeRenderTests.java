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
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstAndFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterField;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriter;
import org.bson.json.JsonWriterSettings;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AstNodeRenderTests {

    private static final JsonWriterSettings EXTENDED_JSON_WRITER_SETTINGS =
            JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    private void assertExtendedJsonRendered(AstNode node, String expectedExtendedJson) {
        var writer = new StringWriter();
        node.render(new JsonWriter(writer, EXTENDED_JSON_WRITER_SETTINGS));
        var realJson = writer.toString();
        assertEquals(expectedExtendedJson, realJson);
    }

    @Nested
    class FilterTests {

        @Test
        void testAstComparisonFilterOperationRender() {
            // given
            var astComparisonFilterOperation = new AstComparisonFilterOperation(
                    AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(1)));

            // when && then
            var expectedExtendedJson =
                    """
                    {"$eq": {"$numberInt": "1"}}\
                    """;
            assertExtendedJsonRendered(astComparisonFilterOperation, expectedExtendedJson);
        }

        @Test
        void testAstFieldOperationFilterRender() {
            // given
            var astFilterField = new AstFilterField("$field");
            var astComparisonFilterOperation = new AstComparisonFilterOperation(
                    AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(1)));
            var astFieldOperationFilter = new AstFieldOperationFilter(astFilterField, astComparisonFilterOperation);

            // when && then
            var expectedExtendedJson =
                    """
                    {"$field": {"$eq": {"$numberInt": "1"}}}\
                    """;
            assertExtendedJsonRendered(astFieldOperationFilter, expectedExtendedJson);
        }

        @Nested
        class testAstAndFilterRender {

            @Test
            void testNoFilter() {
                var expectedExtendedJson =
                        """
                        {}\
                        """;
                assertExtendedJsonRendered(new AstAndFilter(Collections.emptyList()), expectedExtendedJson);
            }

            @Test
            void testSingleFilter() {
                // given
                var astFilterField = new AstFilterField("$field");
                var astComparisonFilterOperation = new AstComparisonFilterOperation(
                        AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(1)));
                var astFieldOperationFilter = new AstFieldOperationFilter(astFilterField, astComparisonFilterOperation);

                // when && then
                var expectedExtendedJson =
                        """
                        {"$field": {"$eq": {"$numberInt": "1"}}}\
                        """;
                assertExtendedJsonRendered(
                        new AstAndFilter(Collections.singletonList(astFieldOperationFilter)), expectedExtendedJson);
            }

            @Test
            void testMultipleFilters() {
                // given
                var astFilterField1 = new AstFilterField("$field1");
                var astComparisonFilterOperation1 = new AstComparisonFilterOperation(
                        AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(1)));
                var astFieldOperationFilter1 =
                        new AstFieldOperationFilter(astFilterField1, astComparisonFilterOperation1);

                var astFilterField2 = new AstFilterField("$field2");
                var astComparisonFilterOperation2 = new AstComparisonFilterOperation(
                        AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt32(2)));
                var astFieldOperationFilter2 =
                        new AstFieldOperationFilter(astFilterField2, astComparisonFilterOperation2);

                // when && then
                var expectedExtendedJson =
                        """
                        {"$and": [{"$field1": {"$eq": {"$numberInt": "1"}}}, {"$field2": {"$eq": {"$numberInt": "2"}}}]}\
                        """;
                assertExtendedJsonRendered(
                        new AstAndFilter(List.of(astFieldOperationFilter1, astFieldOperationFilter2)),
                        expectedExtendedJson);
            }
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
            var expectedExtendedJson =
                    """
                    {"insert": "books", "documents": [{"$title": "War and Peace", "$year": {"$numberInt": "1867"}, "_id": {"$undefined": true}}]}\
                    """;
            assertExtendedJsonRendered(insertCommand, expectedExtendedJson);
        }

        @ParameterizedTest(name = "filterById: {0}")
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
            final String expectedExtendedJson;
            if (filterById) {
                expectedExtendedJson =
                        """
                        {"delete": "books", "deletes": [{"q": {"_id": {"$eq": {"$numberLong": "12345"}}}, "limit": {"$numberInt": "1"}}]}\
                        """;
            } else {
                expectedExtendedJson =
                        """
                        {"delete": "books", "deletes": [{"q": {"$isbn": {"$eq": "978-3-16-148410-0"}}, "limit": {"$numberInt": "0"}}]}\
                        """;
            }
            assertExtendedJsonRendered(deleteCommand, expectedExtendedJson);
        }
    }
}
