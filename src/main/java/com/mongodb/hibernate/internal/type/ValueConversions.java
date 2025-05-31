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
import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static java.lang.String.format;

import com.mongodb.hibernate.jdbc.MongoArray;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collection;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
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

    public static BsonValue toBsonValue(Object value) throws SQLFeatureNotSupportedException {
        assertNotNull(value);
        if (value instanceof BsonDocument v) {
            return v;
        } else if (value instanceof Boolean v) {
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
        } else if (value instanceof MongoArray v) {
            return toBsonValue(v);
        } else if (value.getClass().isArray()) {
            return arrayToBsonValue(value);
        } else if (value instanceof Collection<?> v) {
            return toBsonValue(v);
        } else {
            throw new SQLFeatureNotSupportedException(format(
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

    public static BsonArray toBsonValue(java.sql.Array value) throws SQLFeatureNotSupportedException {
        Object contents;
        try {
            contents = value.getArray();
        } catch (SQLException e) {
            throw fail(e.toString());
        }
        return arrayToBsonValue(contents);
    }

    private static BsonArray arrayToBsonValue(Object value) throws SQLFeatureNotSupportedException {
        var length = Array.getLength(value);
        var elements = new ArrayList<BsonValue>(length);
        for (int i = 0; i < length; i++) {
            elements.add(toBsonValue(Array.get(value, i)));
        }
        return new BsonArray(elements);
    }

    private static BsonArray toBsonValue(Collection<?> value) throws SQLFeatureNotSupportedException {
        var elements = new ArrayList<BsonValue>(value.size());
        for (var e : value) {
            elements.add(toBsonValue(e));
        }
        return new BsonArray(elements);
    }

    static Object toDomainValue(BsonValue value) throws SQLFeatureNotSupportedException {
        assertNotNull(value);
        if (value instanceof BsonDocument v) {
            return v;
        } else if (value instanceof BsonBoolean v) {
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
        } else if (value instanceof BsonArray v) {
            throw fail(v.toString());
        } else {
            throw new SQLFeatureNotSupportedException(format(
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

    public static MongoArray toArrayDomainValue(BsonValue value, Class<?> arrayContentsType)
            throws SQLFeatureNotSupportedException {
        return toDomainValue(value.asArray(), arrayContentsType);
    }

    private static MongoArray toDomainValue(BsonArray value, Class<?> arrayContentsType)
            throws SQLFeatureNotSupportedException {
        var domainValueBuilder = MongoArray.builder(arrayContentsType, value.size());
        for (int i = 0; i < value.size(); i++) {
            var element = toDomainValue(value.get(i));
            domainValueBuilder.add(i, element);
        }
        return domainValueBuilder.build();
    }
}
