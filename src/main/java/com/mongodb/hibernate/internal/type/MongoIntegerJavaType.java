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

import static com.mongodb.hibernate.internal.type.ValueConversions.toIntDomainValue;

import java.io.Serial;
import org.bson.BsonValue;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.IntegerJavaType;
import org.jspecify.annotations.Nullable;

/** Thread-safe. */
public final class MongoIntegerJavaType extends IntegerJavaType {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final MongoIntegerJavaType INSTANCE = new MongoIntegerJavaType();

    private MongoIntegerJavaType() {}

    @Override
    public <X> @Nullable Integer wrap(@Nullable X value, WrapperOptions options) {
        if (value instanceof BsonValue v) {
            return toIntDomainValue(v);
        }
        return super.wrap(value, options);
    }
}
