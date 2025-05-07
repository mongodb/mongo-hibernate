/*
 * Copyright 2024-present MongoDB, Inc.
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

package com.mongodb.hibernate.jdbc;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.type.ValueConverter;
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLType;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MongoArray implements ArrayAdapter {
    private static final Map<String, SQLType> TYPES = Arrays.stream(JDBCType.values())
            .collect(
                    Collectors.toUnmodifiableMap(type -> type.getName().toLowerCase(Locale.ROOT), Function.identity()));

    private final SQLType baseType;
    private final Object[] elements;

    private MongoArray(SQLType baseType, Object[] elements) {
        this.baseType = baseType;
        this.elements = elements;
    }

    MongoArray(String typeName, Object[] elements) {
        this(assertNotNull(TYPES.get(typeName)), elements);
    }

    static MongoArray create(ValueConverter.ArrayWithBaseType arrayWithBaseType)
            throws SQLFeatureNotSupportedException {
        var baseDomainType = arrayWithBaseType.baseDomainType();
        JDBCType baseType;
        // the `JDBCType` we map to here matches the type name Hibernate ORM passes to `Connection.createArrayOf`
        if (baseDomainType == null) {
            baseType = JDBCType.NULL;
        } else if (baseDomainType.equals(Boolean.class)) {
            baseType = JDBCType.BOOLEAN;
        } else if (baseDomainType.equals(Integer.class)) {
            baseType = JDBCType.INTEGER;
        } else if (baseDomainType.equals(Long.class)) {
            baseType = JDBCType.BIGINT;
        } else if (baseDomainType.equals(Double.class)) {
            baseType = JDBCType.FLOAT;
        } else if (baseDomainType.equals(BigDecimal.class)) {
            baseType = JDBCType.NUMERIC;
        } else if (baseDomainType.equals(String.class)) {
            baseType = JDBCType.VARCHAR;
        } else {
            throw new SQLFeatureNotSupportedException(format(
                    "[%s] contains elements of the unsupported type [%s]",
                    arrayWithBaseType.domainValue(), baseDomainType));
        }
        // VAKOTODO try to avoid having to copy an array in `toArray`
        return new MongoArray(baseType, arrayWithBaseType.domainValue().toArray());
    }

    @Override
    public String getBaseTypeName() {
        return baseType.getName();
    }

    @Override
    public int getBaseType() {
        return baseType.getVendorTypeNumber();
    }

    @Override
    public Object getArray() {
        // Hibernate ORM does not call `Connection.getTypeMap`/`setTypeMap`, therefore we are free to ignore it
        return elements;
    }
}
