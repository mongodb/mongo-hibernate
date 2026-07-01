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

import static com.mongodb.hibernate.internal.MongoAssertions.fail;

import java.util.List;
import org.hibernate.dialect.function.UnnestSetReturningFunctionTypeResolver;
import org.hibernate.dialect.function.array.ArrayArgumentValidator;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingSetReturningFunctionDescriptor;
import org.hibernate.query.sqm.tuple.internal.AnonymousTupleTableGroupProducer;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;

/**
 * Registers {@code unnest} as a set-returning function so Hibernate's HQL parser can resolve {@code FROM c.collection
 * alias} inside EXISTS subqueries. Rendering is never reached — the MQL translator intercepts the
 * {@code FunctionTableReference} directly in {@code recognizeExistsOverUnnest}.
 *
 * @hidden
 * @mongoCme Must be thread-safe.
 */
public final class MongoUnnestFunction extends AbstractSqmSelfRenderingSetReturningFunctionDescriptor {

    public static final String FUNCTION_NAME = "unnest";

    public MongoUnnestFunction() {
        super(
                FUNCTION_NAME,
                ArrayArgumentValidator.DEFAULT_INSTANCE,
                new UnnestSetReturningFunctionTypeResolver("value", "ordinality"),
                null);
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> sqlAstArguments,
            AnonymousTupleTableGroupProducer tupleType,
            String tableIdentifierVariable,
            SqlAstTranslator<?> walker) {
        throw fail("render should never be called for MongoUnnestFunction");
    }
}
