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

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRendering;

import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterFieldPath;
import java.util.List;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

class AstUpdateCommandTests {

    @Test
    void testRendering() {

        var collection = "books";
        var astFieldUpdate1 = new AstFieldUpdate("title", new AstLiteralValue(new BsonString("War and Peace")));
        var astFieldUpdate2 = new AstFieldUpdate("author", new AstLiteralValue(new BsonString("Leo Tolstoy")));

        final AstFilter filter;
        filter = new AstFieldOperationFilter(
                new AstFilterFieldPath("_id"),
                new AstComparisonFilterOperation(
                        AstComparisonFilterOperator.EQ, new AstLiteralValue(new BsonInt64(12345L))));

        var updateCommand = new AstUpdateCommand(collection, filter, List.of(astFieldUpdate1, astFieldUpdate2));

        final String expectedJson =
                """
                {"update": "books", "updates": [{"q": {"_id": {"$eq": {"$numberLong": "12345"}}}, "u": {"$set": {"title": "War and Peace", "author": "Leo Tolstoy"}}, "multi": true}]}\
                """;
        assertRendering(expectedJson, updateCommand);
    }
}
