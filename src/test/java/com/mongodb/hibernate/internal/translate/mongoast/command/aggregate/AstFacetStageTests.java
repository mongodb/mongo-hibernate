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

import static com.mongodb.hibernate.internal.translate.mongoast.AstMapChildrenAssertions.assertMapsChildren;
import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRendering;

import com.mongodb.hibernate.internal.translate.mongoast.AstAccumulatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstAccumulatorOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import java.util.List;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstFacetStageTests {

    @Test
    void testRendering() {
        var astFacetStage = new AstFacetStage(
                "#group",
                List.of(new AstGroupStage(
                        List.of(),
                        List.of(new AstGroupStageAccumulatorSpecification(
                                "#acc_0",
                                new AstAccumulatorExpression(
                                        AstAccumulatorOperator.SUM, new AstFieldPathExpression("sales")))))));

        var expectedJson =
                """
                {"$facet": {"#group": [{"$group": {"_id": null, "#acc_0": {"$sum": "$sales"}}}]}}\
                """;
        assertRendering(expectedJson, astFacetStage);
    }

    @Test
    void testRenderingMultipleStages() {
        var astFacetStage = new AstFacetStage(
                "f",
                List.of(
                        new AstSkipStage(new AstLiteral(new BsonInt32(1))),
                        new AstLimitStage(new AstLiteral(new BsonInt32(2)))));

        var expectedJson =
                """
                {"$facet": {"f": [{"$skip": {"$numberInt": "1"}}, {"$limit": {"$numberInt": "2"}}]}}\
                """;
        assertRendering(expectedJson, astFacetStage);
    }

    @Test
    void testMapChildren() {
        // Two differing stages, so a stage put back in the wrong position is detectable.
        assertMapsChildren(new AstFacetStage(
                "f",
                List.of(
                        new AstSkipStage(new AstLiteral(new BsonInt32(1))),
                        new AstLimitStage(new AstLiteral(new BsonInt32(2))))));
    }
}
