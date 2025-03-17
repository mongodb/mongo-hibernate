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

import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;

import com.mongodb.hibernate.internal.MongoAssertions;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.SQLType;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.hibernate.type.SqlTypes;

public enum MqlType implements SQLType {
    OBJECT_ID(11_000);

    static {
        assertTrue(maxHibernateSqlType() < minType());
    }

    MqlType(int type) {
        this.type = type;
    }

    private final int type;

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getVendor() {
        return "MongoDB";
    }

    @Override
    public Integer getVendorTypeNumber() {
        return type;
    }

    private static int minType() {
        return Arrays.stream(MqlType.values())
                .mapToInt(MqlType::getVendorTypeNumber)
                .min()
                .orElseThrow(MongoAssertions::fail);
    }

    private static int maxHibernateSqlType() {
        Predicate<Field> publicStaticFinal = field -> {
            var modifiers = field.getModifiers();
            return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);
        };
        ToIntFunction<Field> valueExtractor = field -> {
            try {
                return field.getInt(null);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        };
        return Arrays.stream(SqlTypes.class.getDeclaredFields())
                .filter(field -> field.getType().equals(int.class))
                .filter(publicStaticFinal)
                .mapToInt(valueExtractor)
                .max()
                .orElseThrow(MongoAssertions::fail);
    }
}
