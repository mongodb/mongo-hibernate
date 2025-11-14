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

package com.mongodb.hibernate.internal.dialect.function.array;

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.VALUE;

import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.AstArray;
import java.util.List;
import java.util.Set;
import org.hibernate.dialect.function.array.ArrayConstructorFunction;
import org.hibernate.query.ReturnableType;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;

/**
 * Implements <a
 * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-array-constructor-functions">
 * {@code array}, {@code array_list}</a>.
 *
 * @mongoCme Must be thread-safe.
 */
public final class MongoArrayConstructorFunction extends ArrayConstructorFunction {
    static final Set<String> NAMES = Set.of("array", "array_list");

    public MongoArrayConstructorFunction(boolean list) {
        super(list, false);
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        var translatedArguments = arguments.stream()
                .map(argument -> translator.acceptAndYield(argument, VALUE))
                .toList();
        translator.yield(VALUE, new AstArray(translatedArguments));
    }
}
