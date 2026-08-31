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

import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import java.util.List;
import org.junit.jupiter.api.Test;

class AstGroupStageTests {

    @Test
    void testRenderingSingleKey() {
        var astGroupStage = new AstGroupStage(
                List.of(new AstGroupStageSpecification("country", new AstFieldPathExpression("country"))));

        var expectedJson = """
                {"$group": {"_id": {"country": "$country"}}}\
                """;
        assertRendering(expectedJson, astGroupStage);
    }

    @Test
    void testRenderingMultipleKeys() {
        var astGroupStage = new AstGroupStage(List.of(
                new AstGroupStageSpecification("country", new AstFieldPathExpression("country")),
                new AstGroupStageSpecification("city", new AstFieldPathExpression("city"))));

        var expectedJson =
                """
                {"$group": {"_id": {"country": "$country", "city": "$city"}}}\
                """;
        assertRendering(expectedJson, astGroupStage);
    }

    @Test
    void testRenderingNestedFieldKey() {
        var astGroupStage = new AstGroupStage(
                List.of(new AstGroupStageSpecification("address#city", new AstFieldPathExpression("address.city"))));

        var expectedJson =
                """
                {"$group": {"_id": {"address#city": "$address.city"}}}\
                """;
        assertRendering(expectedJson, astGroupStage);
    }

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstGroupStage(List.of(
                new AstGroupStageSpecification("k", new AstFieldPathExpression("a")),
                new AstGroupStageSpecification("l", new AstFieldPathExpression("b")))));
    }
}
