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
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprComparisonFilterOperator.LT;

import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathValue;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import java.util.List;
import org.junit.jupiter.api.Test;

class AstLookupStageWithPipelineTests {

    @Test
    void testRendering() {
        var stage = new AstLookupStageWithPipeline(
                "Order",
                List.of(new AstElement("v0", new AstFieldPathValue("$_id"))),
                List.of(new AstMatchStage(
                        new AstExprFilter(LT, new AstFieldPathValue("$$v0"), new AstFieldPathValue("$total")))),
                "#o1_0");

        assertRendering(
                """
                {"$lookup": {"from": "Order", "let": {"v0": "$_id"}, \
                "pipeline": [{"$match": {"$expr": {"$lt": ["$$v0", "$total"]}}}], "as": "#o1_0"}}\
                """,
                stage);
    }
}
