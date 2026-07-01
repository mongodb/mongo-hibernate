/*
 * Copyright 2024-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MongoAggregateSupportTests {

    private static final MongoAggregateSupport SUPPORT = MongoAggregateSupport.INSTANCE;

    // An aggregate SQL type code the dialect does not support (anything other than STRUCT / STRUCT_ARRAY).
    private static final int UNSUPPORTED_TYPE_CODE = SqlTypes.JSON;

    @ParameterizedTest
    @ValueSource(ints = {SqlTypes.STRUCT, SqlTypes.STRUCT_ARRAY})
    void testAssignmentExpressionIsDotNotationPath(int aggregateColumnTypeCode) {
        assertThat(SUPPORT.aggregateComponentAssignmentExpression("nested", "a", aggregateColumnTypeCode, null))
                .isEqualTo("nested.a");
    }

    @ParameterizedTest
    @ValueSource(ints = {SqlTypes.STRUCT, SqlTypes.STRUCT_ARRAY})
    void testReadExpressionIsEmptyWhenNoCustomReadIsDeclared(int aggregateColumnTypeCode) {
        // An empty `template` means the field declares no read fragment. MongoDB has no SQL read expression for a
        // struct component and the MQL translator never uses one. Returning "" makes Hibernate record a null custom
        // read (Column.setCustomRead runs it through nullIfEmpty), rather than a fabricated path. This asserts that.
        assertThat(SUPPORT.aggregateComponentCustomReadExpression(
                        "", "", "nested", "a", aggregateColumnTypeCode, null, null))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {SqlTypes.STRUCT, SqlTypes.STRUCT_ARRAY})
    void testReadExpressionRejectsDeclaredCustomRead(int aggregateColumnTypeCode) {
        // A non-empty `template` carries a @ColumnTransformer(read = ...) / @Formula fragment. The MQL translator
        // cannot honor it, so it must fail rather than silently drop it.
        assertThatExceptionOfType(FeatureNotSupportedException.class)
                .isThrownBy(() -> SUPPORT.aggregateComponentCustomReadExpression(
                        "a * 5", "{@}.", "nested", "a", aggregateColumnTypeCode, null, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {SqlTypes.STRUCT, SqlTypes.STRUCT_ARRAY})
    void testRequiresAggregateCustomWriteExpressionRendererIsFalse(int aggregateColumnTypeCode) {
        assertThat(SUPPORT.requiresAggregateCustomWriteExpressionRenderer(aggregateColumnTypeCode))
                .isFalse();
    }

    @Test
    void testAssignmentExpressionRejectsUnsupportedType() {
        assertThatExceptionOfType(FeatureNotSupportedException.class)
                .isThrownBy(() ->
                        SUPPORT.aggregateComponentAssignmentExpression("nested", "a", UNSUPPORTED_TYPE_CODE, null));
    }

    @Test
    void testRequiresAggregateCustomWriteExpressionRendererRejectsUnsupportedType() {
        assertThatExceptionOfType(FeatureNotSupportedException.class)
                .isThrownBy(() -> SUPPORT.requiresAggregateCustomWriteExpressionRenderer(UNSUPPORTED_TYPE_CODE));
    }

    @Test
    void testReadExpressionRejectsUnsupportedType() {
        assertThatExceptionOfType(FeatureNotSupportedException.class)
                .isThrownBy(() -> SUPPORT.aggregateComponentCustomReadExpression(
                        "", "", "nested", "a", UNSUPPORTED_TYPE_CODE, null, null));
    }
}
