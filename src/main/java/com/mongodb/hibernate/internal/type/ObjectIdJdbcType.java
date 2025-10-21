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

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.io.Serial;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLType;
import org.bson.types.ObjectId;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.jspecify.annotations.Nullable;

/** Thread-safe. */
public final class ObjectIdJdbcType implements JdbcType {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final ObjectIdJdbcType INSTANCE = new ObjectIdJdbcType();
    public static final SQLType SQL_TYPE = MqlType.OBJECT_ID;
    private static final ObjectIdJavaType JAVA_TYPE = ObjectIdJavaType.INSTANCE;

    private ObjectIdJdbcType() {}

    @Override
    public int getJdbcTypeCode() {
        return SQL_TYPE.getVendorTypeNumber();
    }

    @Override
    public String getFriendlyName() {
        return SQL_TYPE.getName();
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        if (!javaType.equals(JAVA_TYPE)) {
            throw new FeatureNotSupportedException();
        }
        @SuppressWarnings("unchecked")
        var result = (ValueBinder<X>) new Binder(JAVA_TYPE);
        return result;
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        if (!javaType.equals(JAVA_TYPE)) {
            throw new FeatureNotSupportedException();
        }
        @SuppressWarnings("unchecked")
        var result = (ValueExtractor<X>) new Extractor(JAVA_TYPE);
        return result;
    }

    /** Thread-safe. */
    private final class Binder extends BasicBinder<ObjectId> {
        @Serial
        private static final long serialVersionUID = 1L;

        private Binder(JavaType<ObjectId> javaType) {
            super(javaType, ObjectIdJdbcType.this);
        }

        @Override
        protected void doBind(PreparedStatement st, ObjectId value, int index, WrapperOptions options)
                throws SQLException {
            st.setObject(index, value, getJdbcType().getJdbcTypeCode());
        }

        @Override
        protected void doBind(CallableStatement st, ObjectId value, String name, WrapperOptions options)
                throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    /** Thread-safe. */
    private final class Extractor extends BasicExtractor<ObjectId> {
        @Serial
        private static final long serialVersionUID = 1L;

        private Extractor(JavaType<ObjectId> javaType) {
            super(javaType, ObjectIdJdbcType.this);
        }

        @Override
        protected @Nullable ObjectId doExtract(ResultSet rs, int paramIndex, WrapperOptions options)
                throws SQLException {
            return rs.getObject(paramIndex, getJavaType().getJavaTypeClass());
        }

        @Override
        protected ObjectId doExtract(CallableStatement statement, int index, WrapperOptions options)
                throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        protected ObjectId doExtract(CallableStatement statement, String name, WrapperOptions options)
                throws SQLException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
