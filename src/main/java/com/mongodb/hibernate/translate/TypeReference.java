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

package com.mongodb.hibernate.translate;

import com.mongodb.hibernate.internal.mongoast.AstNode;
import com.mongodb.hibernate.internal.mongoast.AstValue;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * An enum class denoting the possible types of the value in {@link AstVisitorValueHolder}, so the setter and getter
 * sides could ensure data exchange safety by sharing the same type reference in this class.
 *
 * <p>Note that Java Enum does not support generics. This class combines benefits of both {@code Enum} and type safety
 * together.
 *
 * @param <T> generics type
 * @see AstVisitorValueHolder
 */
abstract class TypeReference<T> {

    public static final TypeReference<AstNode> COLLECTION_MUTATION = new TypeReference<>() {};
    public static final TypeReference<AstValue> FIELD_VALUE = new TypeReference<>() {};

    private static final Map<TypeReference<?>, String> CONSTANT_TOSTRING_CONTENT_MAP;

    static {
        var fields = TypeReference.class.getDeclaredFields();
        var map = new IdentityHashMap<TypeReference<?>, String>(fields.length);
        for (var field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && TypeReference.class == field.getType()) {
                try {
                    map.put((TypeReference<?>) field.get(null), field.getName());
                } catch (IllegalAccessException ignored) {
                    // ignored
                }
            }
        }
        CONSTANT_TOSTRING_CONTENT_MAP = Collections.unmodifiableMap(map);
    }

    private TypeReference() {}

    @Override
    public String toString() {
        return assertNotNull(CONSTANT_TOSTRING_CONTENT_MAP.get(this));
    }
}
