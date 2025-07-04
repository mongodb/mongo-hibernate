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
import static org.hibernate.dialect.StructHelper.instantiate;

import java.io.Serial;
import java.lang.reflect.Array;
import java.sql.JDBCType;
import java.sql.SQLException;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.StructHelper;
import org.hibernate.tool.schema.extract.spi.ColumnTypeInformation;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.jdbc.AggregateJdbcType;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeConstructor;
import org.hibernate.type.spi.TypeConfiguration;
import org.jspecify.annotations.Nullable;

/** Thread-safe. */
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

    /** This method is overridden to make it accessible from our code. */
    @Override
    protected <X> @Nullable X getArray(
            BasicExtractor<X> extractor, java.sql.@Nullable Array array, WrapperOptions options) throws SQLException {
        if (array != null && getElementJdbcType() instanceof AggregateJdbcType aggregateJdbcType) {
            var embeddableMappingType = aggregateJdbcType.getEmbeddableMappingType();
            var embeddableJavaType = embeddableMappingType.getMappedJavaType().getJavaTypeClass();
            var rawArray = array.getArray();
            var domainObjects = (Object[]) Array.newInstance(embeddableJavaType, Array.getLength(rawArray));
            for (var i = 0; i < domainObjects.length; i++) {
                var aggregateRawValues = aggregateJdbcType.extractJdbcValues(Array.get(rawArray, i), options);
                if (aggregateRawValues != null) {
                    var attributeValues =
                            StructHelper.getAttributeValues(embeddableMappingType, aggregateRawValues, options);
                    domainObjects[i] = instantiate(embeddableMappingType, attributeValues, options.getSessionFactory());
                }
            }
            return extractor.getJavaType().wrap(domainObjects, options);
        } else {
            return extractor.getJavaType().wrap(array, options);
        }
    }

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
