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
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MUTATION_RESULT;
import static java.util.Collections.emptyList;

import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import java.util.List;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.internal.TableUpdateNoSet;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;
import org.jspecify.annotations.Nullable;

final class ModelMutationMqlTranslator<O extends JdbcMutationOperation> extends AbstractMqlTranslator<O> {

    private final TableMutation<O> tableMutation;

    ModelMutationMqlTranslator(TableMutation<O> tableMutation, SessionFactoryImplementor sessionFactory) {
        super(sessionFactory);
        this.tableMutation = tableMutation;
    }

    @Override
    public O translate(@Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {
        assertNull(jdbcParameterBindings);
        applyQueryOptions(queryOptions);

        Result result;
        if ((TableMutation<?>) tableMutation instanceof TableUpdateNoSet) {
            result = Result.empty();
        } else {
            result = acceptAndYield(tableMutation, MUTATION_RESULT);
        }
        return result.createJdbcMutationOperation(tableMutation);
    }

    static final class Result {
        private final @Nullable AstCommand command;

        private final List<JdbcParameterBinder> parameterBinders;

        private Result(@Nullable AstCommand command, List<JdbcParameterBinder> parameterBinders) {
            this.command = command;
            this.parameterBinders = parameterBinders;
        }

        static Result create(AstCommand command, List<JdbcParameterBinder> parameterBinders) {
            return new Result(assertNotNull(command), parameterBinders);
        }

        private static Result empty() {
            return new Result(null, emptyList());
        }

        private <O extends JdbcMutationOperation> O createJdbcMutationOperation(TableMutation<O> tableMutation) {
            var mql = command == null ? "" : renderMongoAstNode(command);
            return tableMutation.createMutationOperation(mql, parameterBinders);
        }
    }
}
