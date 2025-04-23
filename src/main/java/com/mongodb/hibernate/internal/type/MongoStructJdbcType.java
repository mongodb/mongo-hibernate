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

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.internal.type.ValueConversions.toBsonValue;
import static com.mongodb.hibernate.internal.type.ValueConversions.toDomainValue;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.MongoAssertions;
import java.io.Serial;
import java.sql.CallableStatement;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.hibernate.annotations.Struct;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.AggregateJdbcType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.StructJdbcType;
import org.jspecify.annotations.Nullable;

/** Thread-safe. */
public final class MongoStructJdbcType implements StructJdbcType {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final MongoStructJdbcType INSTANCE = new MongoStructJdbcType();
    private static final JDBCType JDBC_TYPE = JDBCType.STRUCT;

    @Nullable private final EmbeddableMappingType embeddableMappingType;

    @Nullable private final String structTypeName;

    private MongoStructJdbcType() {
        this(null, null);
    }

    private MongoStructJdbcType(
            @Nullable EmbeddableMappingType embeddableMappingType, @Nullable String structTypeName) {
        this.embeddableMappingType = embeddableMappingType;
        this.structTypeName = structTypeName;
    }

    @Override
    public String getStructTypeName() {
        return assertNotNull(structTypeName);
    }

    /**
     * This method may be called multiple times with equal {@code sqlType} and different {@code mappingType}.
     *
     * @param sqlType The {@link Struct#name()}.
     */
    @Override
    public AggregateJdbcType resolveAggregateJdbcType(
            EmbeddableMappingType mappingType, String sqlType, RuntimeModelCreationContext creationContext) {
        return new MongoStructJdbcType(mappingType, sqlType);
    }

    @Override
    public EmbeddableMappingType getEmbeddableMappingType() {
        return assertNotNull(embeddableMappingType);
    }

    @Override
    public BsonDocument createJdbcValue(Object domainValue, WrapperOptions options) {
        var embeddableMappingType = assertNotNull(this.embeddableMappingType);
        var result = new BsonDocument();
        var jdbcValueCount = embeddableMappingType.getJdbcValueCount();
        for (int columnIndex = 0; columnIndex < jdbcValueCount; columnIndex++) {
            var jdbcValueSelectable = embeddableMappingType.getJdbcValueSelectable(columnIndex);
            if (jdbcValueSelectable.isFormula()) {
                throw new FeatureNotSupportedException("Formulas are not supported");
            }
            if (!jdbcValueSelectable.isInsertable()) {
                throw new FeatureNotSupportedException(
                        "Persistent attributes of a `@Struct @Embeddable` must be insertable");
            }
            if (!jdbcValueSelectable.isUpdateable()) {
                throw new FeatureNotSupportedException(
                        "Persistent attributes of a `@Struct @Embeddable` must be updatable");
            }
            var fieldName = jdbcValueSelectable.getSelectableName();
            var value = embeddableMappingType.getValue(domainValue, columnIndex);
            if (value == null) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48");
            }
            BsonValue bsonValue;
            var jdbcMapping = jdbcValueSelectable.getJdbcMapping();
            if (jdbcMapping.getJdbcType().getJdbcTypeCode() == JDBC_TYPE.getVendorTypeNumber()) {
                if (!(jdbcMapping.getJdbcValueBinder() instanceof Binder<?> structValueBinder)) {
                    throw fail();
                }
                if (!(structValueBinder.getJdbcType() instanceof MongoStructJdbcType structJdbcType)) {
                    throw fail();
                }
                bsonValue = structJdbcType.createJdbcValue(value, options);
            } else {
                bsonValue = toBsonValue(value);
            }
            result.append(fieldName, bsonValue);
        }
        return result;
    }

    @Override
    public Object[] extractJdbcValues(Object rawJdbcValue, WrapperOptions options) {
        if (!(rawJdbcValue instanceof BsonDocument bsonDocument)) {
            throw fail();
        }
        return bsonDocument.values().stream()
                .peek(MongoAssertions::assertNotNull)
                .map(value -> value instanceof BsonDocument ? extractJdbcValues(value, options) : toDomainValue(value))
                .toArray();
    }

    @Override
    public int getJdbcTypeCode() {
        return JDBC_TYPE.getVendorTypeNumber();
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return new Binder<>(javaType);
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new Extractor<>(javaType);
    }

    /** Thread-safe. */
    private final class Binder<X> extends BasicBinder<X> {
        @Serial
        private static final long serialVersionUID = 1L;

        Binder(JavaType<X> javaType) {
            super(javaType, MongoStructJdbcType.this);
        }

        @Override
        protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
            if (!(getJdbcType() instanceof MongoStructJdbcType structJdbcType)) {
                throw fail();
            }
            st.setObject(index, structJdbcType.createJdbcValue(value, options), structJdbcType.getJdbcTypeCode());
        }

        @Override
        protected void doBind(CallableStatement st, X value, String name, WrapperOptions options) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    /** Thread-safe. */
    private final class Extractor<X> extends BasicExtractor<X> {
        @Serial
        private static final long serialVersionUID = 1L;

        Extractor(JavaType<X> javaType) {
            super(javaType, MongoStructJdbcType.this);
        }

        @Override
        protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
            if (!(getJdbcType() instanceof MongoStructJdbcType structJdbcType)) {
                throw fail();
            }
            var classX = getJavaType().getJavaTypeClass();
            assertTrue(classX.equals(Object[].class));
            var bsonDocument = rs.getObject(paramIndex, BsonDocument.class);
            return classX.cast(structJdbcType.extractJdbcValues(bsonDocument, options));
        }

        @Override
        protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
