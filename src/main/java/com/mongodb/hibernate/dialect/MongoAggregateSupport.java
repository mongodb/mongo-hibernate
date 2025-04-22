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

package com.mongodb.hibernate.dialect;

import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.sql.JDBCType;
import org.hibernate.dialect.aggregate.AggregateSupportImpl;
import org.hibernate.mapping.AggregateColumn;
import org.hibernate.mapping.Column;

final class MongoAggregateSupport extends AggregateSupportImpl {
    static final MongoAggregateSupport INSTANCE = new MongoAggregateSupport();

    private MongoAggregateSupport() {}

    @Override
    public String aggregateComponentCustomReadExpression(
            String template,
            String placeholder,
            String aggregateParentReadExpression,
            String columnExpression,
            AggregateColumn aggregateColumn,
            Column column) {
        var aggregateColumnType = aggregateColumn.getTypeCode();
        if (aggregateColumnType == JDBCType.STRUCT.getVendorTypeNumber()) {
            return format(
                    "unused from %s.aggregateComponentCustomReadExpression",
                    MongoAggregateSupport.class.getSimpleName());
        }
        throw new FeatureNotSupportedException(format("The SQL type code [%d] is not supported", aggregateColumnType));
    }

    @Override
    public String aggregateComponentAssignmentExpression(
            String aggregateParentAssignmentExpression,
            String columnExpression,
            AggregateColumn aggregateColumn,
            Column column) {
        var aggregateColumnType = aggregateColumn.getTypeCode();
        if (aggregateColumnType == JDBCType.STRUCT.getVendorTypeNumber()) {
            return format(
                    "unused from %s.aggregateComponentAssignmentExpression",
                    MongoAggregateSupport.class.getSimpleName());
        }
        throw new FeatureNotSupportedException(format("The SQL type code [%d] is not supported", aggregateColumnType));
    }

    @Override
    public boolean requiresAggregateCustomWriteExpressionRenderer(int aggregateSqlTypeCode) {
        if (aggregateSqlTypeCode == JDBCType.STRUCT.getVendorTypeNumber()) {
            return false;
        }
        throw new FeatureNotSupportedException(format("The SQL type code [%d] is not supported", aggregateSqlTypeCode));
    }
}
