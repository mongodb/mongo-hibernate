/*
 * Copyright 2026-present MongoDB, Inc.
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

import org.junit.jupiter.api.Test;

class AstUnwindStageTests {

    @Test
    void testRenderingInner() {
        var stage = new AstUnwindStage("o1_0", false);

        var expectedJson = """
                           {"$unwind": "$o1_0"}\
                           """;
        assertRendering(expectedJson, stage);
    }

    @Test
    void testRenderingLeft() {
        var stage = new AstUnwindStage("o1_0", true);

        var expectedJson =
                """
                {"$unwind": {"path": "$o1_0", "preserveNullAndEmptyArrays": true}}\
                """;
        assertRendering(expectedJson, stage);
    }
}
