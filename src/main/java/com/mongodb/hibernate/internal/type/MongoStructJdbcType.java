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

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.internal.type.ValueConversions.isNull;
import static com.mongodb.hibernate.internal.type.ValueConversions.toBsonValue;
import static com.mongodb.hibernate.internal.type.ValueConversions.toDomainValue;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.jdbc.MongoArray;
import java.io.Serial;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Struct;
import org.bson.BsonDocument;
import org.bson.BsonValue;
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
    public static final JDBCType JDBC_TYPE = JDBCType.STRUCT;

    private final @Nullable EmbeddableMappingType embeddableMappingType;
    private final @Nullable String structTypeName;

    private MongoStructJdbcType() {
        this(null, null);
    }

    private MongoStructJdbcType(
            @Nullable EmbeddableMappingType embeddableMappingType, @Nullable String structTypeName) {
        if (embeddableMappingType != null && embeddableMappingType.isPolymorphic()) {
            throw new FeatureNotSupportedException("Polymorphic mapping is not supported");
        }
        this.embeddableMappingType = embeddableMappingType;
        this.structTypeName = structTypeName;
    }

    @Override
    public int getJdbcTypeCode() {
        return JDBC_TYPE.getVendorTypeNumber();
    }

    @Override
    public String getStructTypeName() {
        return assertNotNull(structTypeName);
    }

    /**
     * This method may be called multiple times with equal {@code sqlType} and different {@code mappingType}.
     *
     * @param sqlType The {@link org.hibernate.annotations.Struct#name()}.
     */
    @Override
    public AggregateJdbcType resolveAggregateJdbcType(
            EmbeddableMappingType mappingType, String sqlType, RuntimeModelCreationContext creationContext) {
        return new MongoStructJdbcType(assertNotNull(mappingType), assertNotNull(sqlType));
    }

    @Override
    public EmbeddableMappingType getEmbeddableMappingType() {
        return assertNotNull(embeddableMappingType);
    }

    /**
     * We replaced this method with {@link #createBsonValue(Object, WrapperOptions)} to make it clear that
     * {@link #createJdbcValue(Object, WrapperOptions)} is not called by Hibernate ORM.
     */
    @Override
    public BsonValue createJdbcValue(@Nullable Object domainValue, WrapperOptions options) {
        throw fail();
    }

    private BsonValue createBsonValue(@Nullable Object domainValue, WrapperOptions options) throws SQLException {
        if (domainValue == null) {
            throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 return toBsonValue(domainValue)");
        }
        var embeddableMappingType = getEmbeddableMappingType();
        var result = new BsonDocument();
        var jdbcValueCount = embeddableMappingType.getJdbcValueCount();
        for (int columnIndex = 0; columnIndex < jdbcValueCount; columnIndex++) {
            var jdbcValueSelectable = embeddableMappingType.getJdbcValueSelectable(columnIndex);
            assertFalse(jdbcValueSelectable.isFormula());
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
            var jdbcTypeCode = jdbcMapping.getJdbcType().getJdbcTypeCode();
            if (jdbcTypeCode == getJdbcTypeCode()) {
                if (!(jdbcMapping.getJdbcValueBinder() instanceof Binder<?> structValueBinder)) {
                    throw fail();
                }
                bsonValue = structValueBinder.getJdbcType().createBsonValue(value, options);
            } else if (jdbcTypeCode == MongoArrayJdbcType.JDBC_TYPE.getVendorTypeNumber()) {
                @SuppressWarnings("unchecked")
                ValueBinder<Object> valueBinder = jdbcMapping.getJdbcValueBinder();
                bsonValue = toBsonValue(valueBinder.getBindValue(value, options));
            } else {
                bsonValue = toBsonValue(value);
            }
            result.append(fieldName, bsonValue);
        }
        return result;
    }

    /**
     * @return The {@linkplain Struct#getAttributes() struct attributes}. Though, the way we support {@link Struct} in
     *     {@link MongoStructJdbcType} does not involve Hibernate ORM ever {@linkplain Connection#createStruct(String,
     *     Object[]) creating} one. If we extended {@link org.hibernate.dialect.StructJdbcType}, this could have been
     *     different.
     */
    @Override
    public Object @Nullable [] extractJdbcValues(@Nullable Object rawJdbcValue, WrapperOptions options)
            throws SQLException {
        if (isNull(rawJdbcValue)) {
            throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 return null");
        }
        if (!(rawJdbcValue instanceof BsonDocument bsonDocument)) {
            throw fail();
        }
        var embeddableMappingType = getEmbeddableMappingType();
        var result = new Object[bsonDocument.size()];
        var elementIdx = 0;
        for (var value : bsonDocument.values()) {
            var jdbcMapping =
                    embeddableMappingType.getJdbcValueSelectable(elementIdx).getJdbcMapping();
            var jdbcTypeCode = jdbcMapping.getJdbcType().getJdbcTypeCode();
            Object domainValue;
            if (isNull(value)) {
                throw new FeatureNotSupportedException("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48"
                        + " domainValue = ValueConversions.toNullDomainValue, where toNullDomainValue returns null");
            } else if (jdbcTypeCode == getJdbcTypeCode()) {
                if (!(jdbcMapping.getJdbcValueExtractor() instanceof Extractor<?> structValueExtractor)) {
                    throw fail();
                }
                domainValue = structValueExtractor.getJdbcType().extractJdbcValues(value, options);
            } else if (jdbcTypeCode == MongoArrayJdbcType.JDBC_TYPE.getVendorTypeNumber()) {
                if (!(jdbcMapping.getJdbcType() instanceof MongoArrayJdbcType arrayJdbcType)) {
                    throw fail();
                }
                if (!(jdbcMapping.getJdbcValueExtractor() instanceof BasicExtractor<?> jdbcValueExtractor)) {
                    throw fail();
                }
                domainValue = arrayJdbcType.getArray(
                        jdbcValueExtractor,
                        // `org.hibernate.type.descriptor.jdbc.ArrayJdbcType` expects `java.sql.Array.getArray`
                        // to return `Object[]`, we cannot put a `BsonArray`, which is a `List<BsonValue>`, inside.
                        new MongoArray(value.asArray().toArray()),
                        options);
            } else {
                domainValue =
                        toDomainValue(value, jdbcMapping.getMappedJavaType().getJavaTypeClass());
            }
            result[elementIdx++] = domainValue;
        }
        return result;
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
        public MongoStructJdbcType getJdbcType() {
            if (!(super.getJdbcType() instanceof MongoStructJdbcType structJdbcType)) {
                throw fail();
            }
            return structJdbcType;
        }

        @Override
        public Object getBindValue(@Nullable X value, WrapperOptions options) throws SQLException {
            return getJdbcType().createBsonValue(value, options);
        }

        @Override
        protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
            st.setObject(index, getBindValue(value, options), getJdbcType().getJdbcTypeCode());
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
        public MongoStructJdbcType getJdbcType() {
            if (!(super.getJdbcType() instanceof MongoStructJdbcType structJdbcType)) {
                throw fail();
            }
            return structJdbcType;
        }

        @Override
        protected @Nullable X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
            var classX = getJavaType().getJavaTypeClass();
            assertTrue(classX.equals(Object[].class));
            var bsonDocument = rs.getObject(paramIndex, BsonDocument.class);
            return classX.cast(getJdbcType().extractJdbcValues(bsonDocument, options));
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
