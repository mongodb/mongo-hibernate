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

package com.mongodb.hibernate.internal.translate;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.fail;

import com.mongodb.hibernate.internal.translate.mongoast.AstValue;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortField;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.sql.ast.tree.expression.Expression;

@SuppressWarnings("UnusedTypeParameter")
final class AstVisitorValueDescriptor<T> {

    static final AstVisitorValueDescriptor<ModelMutationMqlTranslator.Result> MUTATION_RESULT =
            new AstVisitorValueDescriptor<>();
    static final AstVisitorValueDescriptor<SelectMqlTranslator.Result> SELECT_RESULT =
            new AstVisitorValueDescriptor<>();

    static final AstVisitorValueDescriptor<String> COLLECTION_NAME = new AstVisitorValueDescriptor<>();

    static final AstVisitorValueDescriptor<String> FIELD_PATH = new AstVisitorValueDescriptor<>();
    static final AstVisitorValueDescriptor<AstValue> VALUE = new AstVisitorValueDescriptor<>();

    static final AstVisitorValueDescriptor<List<AstProjectStageSpecification>> PROJECT_STAGE_SPECIFICATIONS =
            new AstVisitorValueDescriptor<>();
    static final AstVisitorValueDescriptor<AstFilter> FILTER = new AstVisitorValueDescriptor<>();

    static final AstVisitorValueDescriptor<List<AstSortField>> SORT_FIELDS = new AstVisitorValueDescriptor<>();

    static final AstVisitorValueDescriptor<List<Expression>> TUPLE = new AstVisitorValueDescriptor<>();

    private static final Map<AstVisitorValueDescriptor<?>, String> CONSTANT_TOSTRING_CONTENT_MAP;

    static {
        var fields = AstVisitorValueDescriptor.class.getDeclaredFields();
        var map = new IdentityHashMap<AstVisitorValueDescriptor<?>, String>(fields.length);
        for (var field : fields) {
            var modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && AstVisitorValueDescriptor.class == field.getType()) {
                try {
                    map.put((AstVisitorValueDescriptor<?>) field.get(null), field.getName());
                } catch (IllegalAccessException e) {
                    fail(e.toString());
                }
            }
        }
        CONSTANT_TOSTRING_CONTENT_MAP = Collections.unmodifiableMap(map);
    }

    private AstVisitorValueDescriptor() {}

    @Override
    public String toString() {
        return assertNotNull(CONSTANT_TOSTRING_CONTENT_MAP.get(this));
    }
}
