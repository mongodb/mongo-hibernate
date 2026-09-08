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
import static com.mongodb.hibernate.internal.MongoAssertions.assertNull;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MODEL_MUTATION_RESULT;
import static java.util.Collections.emptyList;

import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import java.util.ArrayList;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.spi.model.TableMutation;
import org.hibernate.sql.ast.spi.model.TableUpdate;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.spi.mutation.jdbc.JdbcMutationOperation;
import org.jspecify.annotations.Nullable;

/**
 * @mongoCme Does not have to be thread-safe because it is {@linkplain MongoTranslatorFactory#buildTranslator
 *     single-use}, as is {@link MongoTranslatorFactory#buildUpsertModelMutationTranslator}.
 */
final class ModelMutationMqlTranslator<O extends JdbcMutationOperation> extends AbstractMqlTranslator<O> {

    private final TableMutation<O> tableMutation;
    private final AstVisitorValueDescriptor<Result> resultDescriptor;

    ModelMutationMqlTranslator(TableMutation<O> tableMutation, SessionFactoryImplementor sessionFactory) {
        this(tableMutation, sessionFactory, MODEL_MUTATION_RESULT);
    }

    ModelMutationMqlTranslator(
            TableMutation<O> tableMutation,
            SessionFactoryImplementor sessionFactory,
            AstVisitorValueDescriptor<Result> resultDescriptor) {
        super(sessionFactory);
        this.tableMutation = tableMutation;
        this.resultDescriptor = resultDescriptor;
    }

    @Override
    public O translate(@Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {
        assertNull(jdbcParameterBindings);
        applyQueryOptions(queryOptions);

        Result result;
        // a no-op update: TableUpdateBuilderStandard produces one exactly when there is nothing to write,
        // so detect that through the spi accessors rather than the internal TableUpdateNoSet marker class
        if (tableMutation instanceof TableUpdate<?> tableUpdate
                && tableUpdate.getValueBindings().isEmpty()
                && tableUpdate.getKeyBindings().isEmpty()) {
            result = Result.empty();
        } else {
            result = acceptAndYield(tableMutation, resultDescriptor);
        }
        return result.createJdbcMutationOperation(tableMutation);
    }

    static final class Result {
        private final @Nullable AstCommand command;

        private Result(@Nullable AstCommand command) {
            this.command = command;
        }

        static Result create(AstCommand command) {
            return new Result(assertNotNull(command));
        }

        private static Result empty() {
            return new Result(null);
        }

        private <O extends JdbcMutationOperation> O createJdbcMutationOperation(TableMutation<O> tableMutation) {
            if (command == null) {
                return tableMutation.createMutationOperation("", emptyList());
            }
            var parameterBinders = new ArrayList<JdbcParameterBinder>();
            var mql = renderMongoAstNode(command, parameterBinders::add);
            return tableMutation.createMutationOperation(mql, parameterBinders);
        }
    }
}
