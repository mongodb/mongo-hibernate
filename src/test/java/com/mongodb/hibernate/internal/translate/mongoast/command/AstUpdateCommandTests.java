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

import static com.mongodb.hibernate.internal.translate.mongoast.AstMapChildrenAssertions.assertMapsChildren;
import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRendering;
import static com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement.Kind.MULTI;
import static com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement.Kind.UPSERT;

import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstComputedFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstValueExpression;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import java.util.List;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

class AstUpdateCommandTests {

    @Test
    void testRendering() {

        var collection = "books";
        var astFieldUpdate1 = new AstFieldUpdate("title", new AstLiteral(new BsonString("War and Peace")));
        var astFieldUpdate2 = new AstFieldUpdate("author", new AstLiteral(new BsonString("Leo Tolstoy")));

        var filter = new AstFieldOperationFilter(
                "_id",
                new AstComparisonFilterOperation(
                        AstComparisonFilterOperator.EQ, new AstLiteral(new BsonInt64(12345L))));

        var updateCommand = new AstUpdateCommand(
                collection,
                List.of(new AstUpdateStatement(
                        filter, new AstDocumentUpdate(List.of(astFieldUpdate1, astFieldUpdate2)), MULTI)));

        var expectedJson =
                """
                {"update": "books", "updates": [{"q": {"_id": {"$eq": {"$numberLong": "12345"}}}, "u": {"$set": {"title": "War and Peace", "author": "Leo Tolstoy"}}, "multi": true}]}\
                """;
        assertRendering(expectedJson, updateCommand);
    }

    @Test
    void testRenderingPipelineUpdate() {
        var filter = new AstFieldOperationFilter(
                "_id",
                new AstComparisonFilterOperation(AstComparisonFilterOperator.EQ, new AstLiteral(new BsonInt32(1))));
        var computed = new AstComputedFieldUpdate(
                "publishYear",
                new AstBinaryOperatorExpression(
                        AstArithmeticExpressionOperator.ADD,
                        new AstFieldPathExpression("publishYear"),
                        new AstValueExpression(new AstLiteral(new BsonInt32(1)))));
        var updateCommand = new AstUpdateCommand(
                "books", List.of(new AstUpdateStatement(filter, new AstPipelineUpdate(List.of(computed)), MULTI)));

        var expectedJson =
                """
                {"update": "books", "updates": [{"q": {"_id": {"$eq": {"$numberInt": "1"}}}, "u": [{"$set": {"publishYear": {"$add": ["$publishYear", {"$numberInt": "1"}]}}}], "multi": true}]}\
                """;
        assertRendering(expectedJson, updateCommand);
    }

    @Test
    void testRenderingUpsert() {
        var filter = new AstFieldOperationFilter(
                "_id",
                new AstComparisonFilterOperation(AstComparisonFilterOperator.EQ, new AstLiteral(new BsonInt32(1))));
        var update = new AstDocumentUpdate(List.of(new AstFieldUpdate("v", new AstLiteral(new BsonInt32(10)))));
        var updateCommand = new AstUpdateCommand("items", List.of(new AstUpdateStatement(filter, update, UPSERT)));

        var expectedJson =
                """
                {"update": "items", "updates": [{"q": {"_id": {"$eq": {"$numberInt": "1"}}}, "u": {"$set": {"v": {"$numberInt": "10"}}}, "upsert": true, "multi": false}]}\
                """;
        assertRendering(expectedJson, updateCommand);
    }

    @Test
    void testRenderingSetOnInsert() {
        var filter = new AstFieldOperationFilter(
                "_id",
                new AstComparisonFilterOperation(AstComparisonFilterOperator.EQ, new AstLiteral(new BsonInt32(1))));
        var update = new AstDocumentUpdate(
                List.of(new AstFieldUpdate("label", new AstLiteral(new BsonString("a")))),
                List.of(new AstFieldUpdate("createdBy", new AstLiteral(new BsonString("jeff")))));
        var updateCommand = new AstUpdateCommand("items", List.of(new AstUpdateStatement(filter, update, UPSERT)));

        var expectedJson =
                """
                {"update": "items", "updates": [{"q": {"_id": {"$eq": {"$numberInt": "1"}}}, "u": {"$set": {"label": "a"}, "$setOnInsert": {"createdBy": "jeff"}}, "upsert": true, "multi": false}]}\
                """;
        assertRendering(expectedJson, updateCommand);
    }

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstUpdateCommand(
                "c",
                List.of(
                        new AstUpdateStatement(
                                new AstExprFilter(new AstFieldPathExpression("a")),
                                new AstPipelineUpdate(List.of()),
                                UPSERT),
                        new AstUpdateStatement(
                                new AstExprFilter(new AstFieldPathExpression("b")),
                                new AstPipelineUpdate(List.of()),
                                UPSERT))));
    }
}
