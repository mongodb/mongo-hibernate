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
    public String aggregateComponentCustomReadExpression(
            String template,
            String placeholder,
            String aggregateParentReadExpression,
            String columnExpression,
            int aggregateColumnTypeCode,
            SqlTypedMapping column,
            TypeConfiguration typeConfiguration) {
        return aggregateComponentExpression(aggregateParentReadExpression, columnExpression, aggregateColumnTypeCode);
    }

    @Override
    public String aggregateComponentAssignmentExpression(
            String aggregateParentAssignmentExpression,
            String columnExpression,
            int aggregateColumnTypeCode,
            Column column) {
        return aggregateComponentExpression(
                aggregateParentAssignmentExpression, columnExpression, aggregateColumnTypeCode);
    }

    @Override
    public boolean requiresAggregateCustomWriteExpressionRenderer(int aggregateSqlTypeCode) {
        if (isStructOrArrayType(aggregateSqlTypeCode)) {
            return false;
        }
        throw new FeatureNotSupportedException(format("The SQL type code [%d] is not supported", aggregateSqlTypeCode));
    }

    private static String aggregateComponentExpression(
            String aggregateParentExpression, String columnExpression, int aggregateColumnTypeCode) {
        if (isStructOrArrayType(aggregateColumnTypeCode)) {
            return aggregateParentExpression + "." + columnExpression;
        }
        throw new FeatureNotSupportedException(
                format("The SQL type code [%d] is not supported", aggregateColumnTypeCode));
    }

    private static boolean isStructOrArrayType(int aggregateColumnTypeCode) {
        return aggregateColumnTypeCode == MongoStructJdbcType.HIBERNATE_SQL_TYPE
                || aggregateColumnTypeCode == MongoArrayJdbcType.HIBERNATE_SQL_TYPE;
    }
}
