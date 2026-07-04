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

import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.type.MongoArrayJdbcType;
import com.mongodb.hibernate.internal.type.MongoStructJdbcType;
import org.hibernate.dialect.aggregate.AggregateSupportImpl;
import org.hibernate.mapping.Column;
import org.hibernate.metamodel.mapping.SqlTypedMapping;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * @hidden
 * @mongoCme It is unclear whether this class must be thread-safe.
 */
@SuppressWarnings("MissingSummary")
public final class MongoAggregateSupport extends AggregateSupportImpl {
    public static final MongoAggregateSupport INSTANCE = new MongoAggregateSupport();

    private MongoAggregateSupport() {}

    @Override
    public String aggregateComponentAssignmentExpression(
            String aggregateParentAssignmentExpression,
            String columnExpression,
            int aggregateColumnTypeCode,
            Column column) {
        assertStructOrArrayType(aggregateColumnTypeCode);
        return aggregateParentAssignmentExpression + "." + columnExpression;
    }

    @Override
    public String aggregateComponentCustomReadExpression(
            String template,
            String placeholder,
            String aggregateParentReadExpression,
            String columnExpression,
            int aggregateColumnTypeCode,
            SqlTypedMapping column,
            TypeConfiguration typeConfiguration) {
        assertStructOrArrayType(aggregateColumnTypeCode);
        // `template` is non-empty when the field declares its own read fragment via @ColumnTransformer(read = ...).
        // (A @Formula component is rejected by Hibernate before reaching this method.) The MQL translator never renders
        // read expressions (visitColumnReference resolves field paths from getColumnExpression(), the assignment
        // expression), so such a request cannot be honored; reject it rather than silently dropping it.
        if (!template.isEmpty()) {
            throw new FeatureNotSupportedException(
                    "Custom read expression on an aggregate embeddable field is not supported");
        }
        // Otherwise there is no custom read: MongoDB reads the struct as a whole column (preferSelectAggregateMapping()
        // returns true) and the per-component read is never used. Returning "" makes Hibernate record no custom read
        // (Column.setCustomRead runs it through nullIfEmpty), instead of a fabricated, incorrect value.
        return "";
    }

    @Override
    public boolean requiresAggregateCustomWriteExpressionRenderer(int aggregateSqlTypeCode) {
        assertStructOrArrayType(aggregateSqlTypeCode);
        return false;
    }

    private static void assertStructOrArrayType(int aggregateColumnTypeCode) {
        if (!isStructOrArrayType(aggregateColumnTypeCode)) {
            throw new FeatureNotSupportedException(
                    format("The SQL type code [%d] is not supported", aggregateColumnTypeCode));
        }
    }

    private static boolean isStructOrArrayType(int aggregateColumnTypeCode) {
        return aggregateColumnTypeCode == MongoStructJdbcType.HIBERNATE_SQL_TYPE
                || aggregateColumnTypeCode == MongoArrayJdbcType.HIBERNATE_SQL_TYPE;
    }
}
