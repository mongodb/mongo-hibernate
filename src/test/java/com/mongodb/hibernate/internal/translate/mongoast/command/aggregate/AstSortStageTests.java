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

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRender;

import java.util.List;
import org.junit.jupiter.api.Test;

class AstSortStageTests {

    @Test
    void testRendering() {
        var astSortField1 = new AstSortField("field1", AstSortOrder.ASC);
        var astSortField2 = new AstSortField("field2", AstSortOrder.DESC);
        var astSortStage = new AstSortStage(List.of(astSortField1, astSortField2));

        var expectedJson = """
                {"$sort": {"field1": 1, "field2": -1}}\
                """;
        assertRender(expectedJson, astSortStage);
    }
}
