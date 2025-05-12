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
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.EQ;

import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterFieldPath;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

class AstMatchStageTests {

    @Test
    void testRendering() {
        var astFilter = new AstFieldOperationFilter(
                new AstFilterFieldPath("title"),
                new AstComparisonFilterOperation(EQ, new AstLiteralValue(new BsonString("Jane Eyre"))));
        var astMatchStage = new AstMatchStage(astFilter);

        var expectedJson = """
                {"$match": {"title": {"$eq": "Jane Eyre"}}}\
                """;
        assertRendering(expectedJson, astMatchStage);
    }
}
