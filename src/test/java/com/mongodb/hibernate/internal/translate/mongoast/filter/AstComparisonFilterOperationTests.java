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

package com.mongodb.hibernate.internal.translate.mongoast.filter;

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRender;

import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import org.bson.BsonInt32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AstComparisonFilterOperationTests {

    @ParameterizedTest
    @CsvSource({
        "EQ,$eq",
        "GT,$gt",
        "GTE,$gte",
        "LT,$lt",
        "LTE,$lte",
        "NE,$ne",
    })
    void testRendering(String operatorName, String operatorRendered) {
        var operator = AstComparisonFilterOperator.valueOf(operatorName);
        var operation = new AstComparisonFilterOperation(operator, new AstLiteralValue(new BsonInt32(1)));

        var expectedJson = """
                {"%s": 1}\
                """.formatted(operatorRendered);
        assertRender(expectedJson, operation);
    }
}
