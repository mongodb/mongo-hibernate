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
import org.bson.types.ObjectId;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

public final class ObjectIdJdbcType implements JdbcType {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final MqlType MQL_TYPE = MqlType.OBJECT_ID;
    private static final ObjectIdJavaType JAVA_TYPE = ObjectIdJavaType.INSTANCE;
    public static final ObjectIdJdbcType INSTANCE = new ObjectIdJdbcType();

    private ObjectIdJdbcType() {}

    @Override
    public int getJdbcTypeCode() {
        return MQL_TYPE.getVendorTypeNumber();
    }

    @Override
    public String getFriendlyName() {
        return MQL_TYPE.getName();
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        if (!javaType.equals(JAVA_TYPE)) {
            throw new FeatureNotSupportedException();
        }
        @SuppressWarnings("unchecked")
        var result = (ValueBinder<X>) Binder.INSTANCE;
        return result;
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        if (!javaType.equals(JAVA_TYPE)) {
            throw new FeatureNotSupportedException();
        }
        @SuppressWarnings("unchecked")
        var result = (ValueExtractor<X>) Extractor.INSTANCE;
        return result;
    }

    /**
     * Thread-safe.
     */
    private static final class Binder extends BasicBinder<ObjectId> {
        @Serial
        private static final long serialVersionUID = 1L;

        static Binder INSTANCE = new Binder();

        private Binder() {
            super(JAVA_TYPE, ObjectIdJdbcType.INSTANCE);
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

    /**
     * Thread-safe.
     */
    private static final class Extractor extends BasicExtractor<ObjectId> {
        @Serial
        private static final long serialVersionUID = 1L;

        static Extractor INSTANCE = new Extractor();

        private Extractor() {
            super(JAVA_TYPE, ObjectIdJdbcType.INSTANCE);
        }

        @Override
        protected ObjectId doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
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
