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
import java.math.BigDecimal;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDecimal128;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;

public final class BsonTypeUtils {

    private BsonTypeUtils() {}

    public static BsonValue toBsonValue(@Nullable Object value) {
        if (value == null) {
            return BsonNull.VALUE;
        }
        if (value instanceof Boolean boolValue) {
            return BsonBoolean.valueOf(boolValue);
        }
        if (value instanceof Integer intValue) {
            return new BsonInt32(intValue);
        }
        if (value instanceof Long longValue) {
            return new BsonInt64(longValue);
        }
        if (value instanceof Double doubleValue) {
            return new BsonDouble(doubleValue);
        }
        if (value instanceof BigDecimal bigDecimalValue) {
            return new BsonDecimal128(new Decimal128(bigDecimalValue));
        }
        if (value instanceof String stringValue) {
            return new BsonString(stringValue);
        }
        if (value instanceof byte[] bytesValue) {
            return new BsonBinary(bytesValue);
        }
        if (value instanceof ObjectId objectIdValue) {
            return new BsonObjectId(objectIdValue);
        }
        throw new FeatureNotSupportedException("Unsupported Java type: " + value.getClass());
    }
}
