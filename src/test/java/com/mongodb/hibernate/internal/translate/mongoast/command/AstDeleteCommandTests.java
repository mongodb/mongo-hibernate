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
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.EQ;

import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

class AstDeleteCommandTests {

    @Test
    void testRendering() {

        var collection = "books";
        var filter = new AstFieldOperationFilter(
                "isbn", new AstComparisonFilterOperation(EQ, new AstLiteralValue(new BsonString("978-3-16-148410-0"))));

        var deleteCommand = new AstDeleteCommand(collection, filter);

        var expectedJson =
                """
                {"delete": "books", "deletes": [{"q": {"isbn": {"$eq": "978-3-16-148410-0"}}, "limit": {"$numberInt": "0"}}]}\
                """;

        assertRendering(expectedJson, deleteCommand);
    }
}
