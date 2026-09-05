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

package com.mongodb.hibernate.internal.type;

import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;

import com.mongodb.hibernate.internal.jdbc.MongoArray;
import java.io.Serial;
import java.sql.JDBCType;
import java.sql.SQLException;
import org.hibernate.dialect.Dialect;
import org.hibernate.tool.schema.extract.spi.ColumnTypeInformation;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeConstructor;
import org.hibernate.type.spi.TypeConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * @hidden
 * @mongoCme Must be thread-safe.
 */
@SuppressWarnings("MissingSummary")
public final class MongoArrayJdbcType extends ArrayJdbcType {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final JDBCType JDBC_TYPE = JDBCType.ARRAY;
    public static final int HIBERNATE_SQL_TYPE = SqlTypes.STRUCT_ARRAY;

    private MongoArrayJdbcType(JdbcType elementJdbcType) {
        super(elementJdbcType);
    }

    @Override
    public int getJdbcTypeCode() {
        var result = super.getJdbcTypeCode();
        assertTrue(result == JDBC_TYPE.getVendorTypeNumber());
        return result;
    }

    @Override
    protected <X> @Nullable X getArray(
            BasicExtractor<X> extractor, java.sql.@Nullable Array array, WrapperOptions options) throws SQLException {
        if (array instanceof MongoArray mongoArray
                && getElementJdbcType() instanceof MongoStructJdbcType structElementJdbcType) {
            // ArrayJdbcType#toJavaArray feeds AggregateJdbcType#extractJdbcValues output back into the
            // physical-value walk, wrapping array components a second time; convert the elements directly
            var rawElements = (Object[]) mongoArray.getArray();
            var domainElements = new Object[rawElements.length];
            for (var i = 0; i < rawElements.length; i++) {
                domainElements[i] =
                        rawElements[i] == null ? null : structElementJdbcType.toDomain(rawElements[i], options);
            }
            return extractor.getJavaType().wrap(domainElements, options);
        }
        return super.getArray(extractor, array, options);
    }

    /**
     * @hidden
     * @mongoCme Must be thread-safe.
     */
    @SuppressWarnings("MissingSummary")
    public static final class Constructor implements JdbcTypeConstructor {
        public static final Constructor INSTANCE = new Constructor();

        private Constructor() {}

        @Override
        public JdbcType resolveType(
                TypeConfiguration typeConfiguration,
                Dialect dialect,
                JdbcType elementType,
                ColumnTypeInformation columnTypeInformation) {
            return new MongoArrayJdbcType(elementType);
        }

        @Override
        public int getDefaultSqlTypeCode() {
            return JDBC_TYPE.getVendorTypeNumber();
        }
    }
}
