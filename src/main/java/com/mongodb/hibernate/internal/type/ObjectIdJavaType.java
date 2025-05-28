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
import java.util.concurrent.ThreadLocalRandom;
import org.bson.types.ObjectId;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeIndicators;

/** Thread-safe. */
public final class ObjectIdJavaType extends AbstractClassJavaType<ObjectId> {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int hashCode = ThreadLocalRandom.current().nextInt();

    public static final ObjectIdJavaType INSTANCE = new ObjectIdJavaType();

    private ObjectIdJavaType() {
        super(ObjectId.class);
    }

    @Override
    public JdbcType getRecommendedJdbcType(JdbcTypeIndicators indicators) {
        return ObjectIdJdbcType.INSTANCE;
    }

    @Override
    public <X> X unwrap(ObjectId value, Class<X> type, WrapperOptions options) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public <X> ObjectId wrap(X value, WrapperOptions options) {
        if (!(value instanceof ObjectId wrapped)) {
            throw new FeatureNotSupportedException();
        }
        return wrapped;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
