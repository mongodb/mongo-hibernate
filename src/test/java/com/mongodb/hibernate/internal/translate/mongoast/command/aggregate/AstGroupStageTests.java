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

import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldReferenceValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class AstGroupStageTests {

    @Test
    void testRenderingSingleKey() {
        var groupKey = new AstDocument(
                List.of(new AstElement("country", new AstFieldReferenceValue(new AstFieldPathExpression("country")))));
        var astGroupStage = new AstGroupStage(groupKey);

        var expectedJson = """
                {"$group": {"_id": {"country": "$country"}}}\
                """;
        assertRendering(expectedJson, astGroupStage);
    }

    @Test
    void testRenderingMultipleKeys() {
        var groupKey = new AstDocument(List.of(
                new AstElement("country", new AstFieldReferenceValue(new AstFieldPathExpression("country"))),
                new AstElement("city", new AstFieldReferenceValue(new AstFieldPathExpression("city")))));
        var astGroupStage = new AstGroupStage(groupKey);

        var expectedJson =
                """
                {"$group": {"_id": {"country": "$country", "city": "$city"}}}\
                """;
        assertRendering(expectedJson, astGroupStage);
    }

    @Test
    void testRenderingNestedFieldKey() {
        var groupKey = new AstDocument(List.of(new AstElement(
                "address#city", new AstFieldReferenceValue(new AstFieldPathExpression("address.city")))));
        var astGroupStage = new AstGroupStage(groupKey);

        var expectedJson =
                """
                {"$group": {"_id": {"address#city": "$address.city"}}}\
                """;
        assertRendering(expectedJson, astGroupStage);
    }
}
