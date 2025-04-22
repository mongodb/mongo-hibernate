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
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDecimal128;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

/**
 * Provides conversion methods between {@link BsonValue}s, which our {@link PreparedStatement}/{@link ResultSet}
 * implementation uses under the hood and rarely exposes, and domain values we usually use when setting parameter values
 * on our {@link PreparedStatement}, or retrieving column values from a {@link ResultSet}.
 */
public final class ValueConversions {
    private ValueConversions() {}

    static BsonValue toBsonValue(Object value) {
        assertNotNull(value);
        if (value instanceof Boolean v) {
            return toBsonValue(v.booleanValue());
        } else if (value instanceof Integer v) {
            return toBsonValue(v.intValue());
        } else if (value instanceof Long v) {
            return toBsonValue(v.longValue());
        } else if (value instanceof Double v) {
            return toBsonValue(v.doubleValue());
        } else if (value instanceof BigDecimal v) {
            return toBsonValue(v);
        } else if (value instanceof String v) {
            return toBsonValue(v);
        } else if (value instanceof byte[] v) {
            return toBsonValue(v);
        } else if (value instanceof ObjectId v) {
            return toBsonValue(v);
        } else {
            throw new FeatureNotSupportedException(format(
                    "Value [%s] of type [%s] is not supported",
                    value, value.getClass().getTypeName()));
        }
    }

    public static BsonBoolean toBsonValue(boolean value) {
        return BsonBoolean.valueOf(value);
    }

    public static BsonInt32 toBsonValue(int value) {
        return new BsonInt32(value);
    }

    public static BsonInt64 toBsonValue(long value) {
        return new BsonInt64(value);
    }

    public static BsonDouble toBsonValue(double value) {
        return new BsonDouble(value);
    }

    public static BsonDecimal128 toBsonValue(BigDecimal value) {
        return new BsonDecimal128(new Decimal128(value));
    }

    public static BsonString toBsonValue(String value) {
        return new BsonString(value);
    }

    public static BsonBinary toBsonValue(byte[] value) {
        return new BsonBinary(value);
    }

    public static BsonObjectId toBsonValue(ObjectId value) {
        return new BsonObjectId(value);
    }

    static Object toDomainValue(BsonValue value) {
        assertNotNull(value);
        if (value instanceof BsonBoolean v) {
            return toDomainValue(v);
        } else if (value instanceof BsonInt32 v) {
            return toDomainValue(v);
        } else if (value instanceof BsonInt64 v) {
            return toDomainValue(v);
        } else if (value instanceof BsonDouble v) {
            return toDomainValue(v);
        } else if (value instanceof BsonDecimal128 v) {
            return toDomainValue(v);
        } else if (value instanceof BsonString v) {
            return toDomainValue(v);
        } else if (value instanceof BsonBinary v) {
            return toDomainValue(v);
        } else if (value instanceof BsonObjectId v) {
            return toDomainValue(v);
        } else {
            throw new FeatureNotSupportedException(format(
                    "Value [%s] of type [%s] is not supported",
                    value, value.getClass().getTypeName()));
        }
    }

    public static boolean toBooleanDomainValue(BsonValue value) {
        return toDomainValue(value.asBoolean());
    }

    private static boolean toDomainValue(BsonBoolean value) {
        return value.getValue();
    }

    public static int toIntDomainValue(BsonValue value) {
        return toDomainValue(value.asInt32());
    }

    private static int toDomainValue(BsonInt32 value) {
        return value.intValue();
    }

    public static long toLongDomainValue(BsonValue value) {
        return toDomainValue(value.asInt64());
    }

    private static long toDomainValue(BsonInt64 value) {
        return value.longValue();
    }

    public static double toDoubleDomainValue(BsonValue value) {
        return toDomainValue(value.asDouble());
    }

    private static double toDomainValue(BsonDouble value) {
        return value.getValue();
    }

    public static BigDecimal toBigDecimalDomainValue(BsonValue value) {
        return toDomainValue(value.asDecimal128());
    }

    private static BigDecimal toDomainValue(BsonDecimal128 value) {
        return value.decimal128Value().bigDecimalValue();
    }

    public static String toStringDomainValue(BsonValue value) {
        return toDomainValue(value.asString());
    }

    private static String toDomainValue(BsonString value) {
        return value.getValue();
    }

    public static byte[] toByteArrayDomainValue(BsonValue value) {
        return toDomainValue(value.asBinary());
    }

    private static byte[] toDomainValue(BsonBinary value) {
        return value.asBinary().getData();
    }

    public static ObjectId toObjectIdDomainValue(BsonValue value) {
        return toDomainValue(value.asObjectId());
    }

    private static ObjectId toDomainValue(BsonObjectId value) {
        return value.getValue();
    }
}
