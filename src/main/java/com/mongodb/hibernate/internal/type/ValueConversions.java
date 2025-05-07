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
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.jdbc.MongoArray;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.sql.JDBCType;
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
import org.jspecify.annotations.Nullable;

/**
 * Provides conversion methods between {@link BsonValue}s, which our {@link PreparedStatement}/{@link ResultSet}
 * implementation uses under the hood and rarely exposes, and domain values we usually use when setting parameter values
 * on our {@link PreparedStatement}, or retrieving column values from a {@link ResultSet}.
 */
public final class ValueConversions {
    private ValueConversions() {}

    public static @Nullable BsonValue toBsonValue(@Nullable Object value) throws SQLFeatureNotSupportedException {
        if (value == null) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48");
        }
        if (value instanceof BsonDocument v) {
            return v;
        } else if (value instanceof Boolean v) {
            return toBsonValue(v.booleanValue());
        } else if (value instanceof Character v) {
            return toBsonValue(v.charValue());
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
        } else if (value instanceof char[] v) {
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

    /**
     * <a
     * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#basic-character">
     * Hibernate ORM maps {@code char}/{@link Character} to {@link JDBCType#CHAR} by default</a>.
     *
     * @see #toCharDomainValue(BsonValue)
     * @see #toDomainValue(String)
     */
    private static BsonString toBsonValue(char value) {
        return new BsonString(Character.toString(value));
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

    /**
     * <a
     * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#basic-bytearray">
     * Hibernate ORM maps {@code byte[]} to {@link java.sql.JDBCType#VARBINARY} by default</a>.
     *
     * @see #toByteArrayDomainValue(BsonValue)
     * @see #toDomainValue(BsonBinary)
     */
    public static BsonBinary toBsonValue(byte[] value) {
        return new BsonBinary(value);
    }

    /**
     * <a
     * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#basic-chararray">
     * Hibernate ORM maps {@code char[]} to {@link java.sql.JDBCType#VARCHAR} by default</a>.
     *
     * @see #toDomainValue(BsonString, Type.ArrayOrCollection)
     */
    private static BsonString toBsonValue(char[] value) {
        return new BsonString(String.valueOf(value));
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
        // Hibernate ORM always passes an `java.sql.Array` with contents of the Java array type to
        // `PreparedStatement.setArray`
        assertTrue(contents.getClass().isArray());
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

    static Object toDomainValue(BsonValue value, Type domainType) throws SQLFeatureNotSupportedException {
        // TODO-HIBERNATE-48 decide if `value` is nullable and the method may return `null`
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
            if (domainType instanceof Type.Simple simpleType) {
                return toDomainValue(v, simpleType.type());
            } else if (domainType instanceof Type.ArrayOrCollection arrayOrCollectionType) {
                return toDomainValue(v, arrayOrCollectionType).getArray();
            } else {
                throw fail(domainType.toString());
            }
        } else if (value instanceof BsonBinary v) {
            return toDomainValue(v);
        } else if (value instanceof BsonObjectId v) {
            return toDomainValue(v);
        } else if (value instanceof BsonArray v) {
            if (!(domainType instanceof Type.ArrayOrCollection arrayOrCollectionDomainType)) {
                throw fail("VAKOTODO SQLException/RuntimeException about invalid data");
            }
            return toDomainValue(v, arrayOrCollectionDomainType).getArray();
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
        return toDomainValue(value.asString(), String.class);
    }

    /** @see #toBsonValue(char) */
    @SuppressWarnings("MissingSummary")
    public static char toCharDomainValue(BsonValue value) {
        return toDomainValue(value.asString(), Character.class);
    }

    private static <T> T toDomainValue(BsonString value, Class<T> domainType) {
        var v = value.getValue();
        Object result;
        if (domainType.equals(Character.class)) {
            result = toDomainValue(v);
        } else {
            result = v;
        }
        return domainType.cast(result);
    }

    /** @see #toBsonValue(char) */
    private static char toDomainValue(String value) {
        assertTrue(value.length() == 1);
        return value.charAt(0);
    }

    /** @see #toBsonValue(byte[]) */
    @SuppressWarnings("MissingSummary")
    public static byte[] toByteArrayDomainValue(BsonValue value) {
        return toDomainValue(value.asBinary());
    }

    /** @see #toBsonValue(byte[]) */
    private static byte[] toDomainValue(BsonBinary value) {
        return value.asBinary().getData();
    }

    public static ObjectId toObjectIdDomainValue(BsonValue value) {
        return toDomainValue(value.asObjectId());
    }

    private static ObjectId toDomainValue(BsonObjectId value) {
        return value.getValue();
    }

    public static MongoArray toArrayDomainValue(BsonValue value, Type.ArrayOrCollection domainArrayContentsType)
            throws SQLFeatureNotSupportedException {
        if (value instanceof BsonArray v) {
            return toDomainValue(v, domainArrayContentsType);
        } else if (value instanceof BsonString v) {
            return toDomainValue(v, domainArrayContentsType);
        } else {
            throw new RuntimeException(
                    format("Invalid data: value [%s], domainArrayContentsType [%s]", value, domainArrayContentsType));
        }
    }

    private static MongoArray toDomainValue(BsonArray value, Type.ArrayOrCollection domainArrayContentsType)
            throws SQLFeatureNotSupportedException {
        var size = value.size();
        var domainValueBuilder = MongoArray.builder(domainArrayContentsType, size);
        for (int i = 0; i < size; i++) {
            var element = toDomainValue(value.get(i), domainArrayContentsType.elementType());
            domainValueBuilder.add(i, element);
        }
        return domainValueBuilder.build();
    }

    /** @see #toBsonValue(char[]) */
    private static MongoArray toDomainValue(BsonString value, Type.ArrayOrCollection domainArrayContentsType) {
        var charsAsString = toDomainValue(value, String.class);
        var size = charsAsString.length();
        var domainValueBuilder = MongoArray.builder(domainArrayContentsType, size);
        for (int i = 0; i < size; i++) {
            var element = charsAsString.charAt(i);
            domainValueBuilder.add(i, element);
        }
        return domainValueBuilder.build();
    }

    public sealed interface Type permits Type.Simple, Type.ArrayOrCollection {
        sealed interface Simple extends Type permits SimpleType {}

        // VAKOTODO rename to Plural
        sealed interface ArrayOrCollection extends Type permits ArrayType, CollectionType {
            Simple elementType();
        }

        static Type of(java.lang.reflect.Type type) {
            if (type instanceof Class<?> klass) {
                return of(klass);
            } else if (type instanceof ParameterizedType parameterizedType) {
                if (!(parameterizedType.getRawType() instanceof Class<?> rawKlass)) {
                    throw fail(type.toString());
                }
                if (Collection.class.isAssignableFrom(rawKlass)) { // VAKOTODO equals?
                    var collectionTypeArguments = parameterizedType.getActualTypeArguments();
                    assertTrue(collectionTypeArguments.length == 1);
                    return new CollectionType(rawKlass, collectionTypeArguments[0]);
                } else {
                    return of(rawKlass);
                }
            } else {
                throw fail(type.toString());
            }
        }

        private static Type of(Class<?> type) {
            if (type.isArray() && !type.equals(byte[].class)) {
                return array(type);
            } else {
                return simple(type);
            }
        }

        private static Simple simple(Class<?> type) {
            return new SimpleType(type);
        }

        static ArrayOrCollection array(Class<?> type) {
            return new ArrayType(type);
        }

        Class<?> type();
    }

    private record SimpleType(Class<?> type) implements Type.Simple {}

    private record ArrayType(Class<?> type, Simple elementType) implements Type.ArrayOrCollection {
        ArrayType(Class<?> arrayType) {
            this(arrayType, elementType(arrayType));
        }

        private static Simple elementType(Class<?> arrayType) {
            return Type.simple(assertNotNull(arrayType.componentType()));
        }
    }

    private record CollectionType(Class<?> type, Simple elementType) implements Type.ArrayOrCollection {
        CollectionType(Class<?> rawCollectionType, java.lang.reflect.Type collectionTypeArgument) {
            this(rawCollectionType, elementType(collectionTypeArgument));
        }

        private static Simple elementType(java.lang.reflect.Type collectionTypeArgument) {
            if (!(collectionTypeArgument instanceof Class<?> collectionTypeArgumentClass)) {
                throw fail(); // VAKOTODO try Collection<?>/Collection<? extends>/Collection<? super>
            }
            return Type.simple(collectionTypeArgumentClass);
        }
    }
}
