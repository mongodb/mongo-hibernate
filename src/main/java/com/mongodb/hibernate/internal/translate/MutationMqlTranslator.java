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

import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MUTATION_RESULT;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static org.hibernate.sql.ast.SqlTreePrinter.logSqlAst;

import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstDeleteCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateCommand;
import java.util.List;
import java.util.Set;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.exec.internal.JdbcOperationQueryInsertImpl;
import org.hibernate.sql.exec.spi.JdbcOperationQueryDelete;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcOperationQueryUpdate;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.jspecify.annotations.Nullable;

final class MutationMqlTranslator extends AbstractMqlTranslator<JdbcOperationQueryMutation> {

    private final MutationStatement mutationStatement;

    MutationMqlTranslator(SessionFactoryImplementor sessionFactory, MutationStatement mutationStatement) {
        super(sessionFactory);
        this.mutationStatement = mutationStatement;
    }

    @Override
    public JdbcOperationQueryMutation translate(
            @Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {

        logSqlAst(mutationStatement);

        checkJdbcParameterBindingsSupportability(jdbcParameterBindings);
        applyQueryOptions(queryOptions);

        var result = acceptAndYield(mutationStatement, MUTATION_RESULT);
        return result.createJdbcOperationQueryMutation();
    }

    static final class Result {
        private final AstCommand command;
        private final List<JdbcParameterBinder> parameterBinders;
        private final Set<String> affectedTableNames;

        Result(AstCommand command, List<JdbcParameterBinder> parameterBinders, Set<String> affectedTableNames) {
            this.command = command;
            this.parameterBinders = parameterBinders;
            this.affectedTableNames = affectedTableNames;
        }

        private JdbcOperationQueryMutation createJdbcOperationQueryMutation() {
            var mql = renderMongoAstNode(command);
            if (command instanceof AstInsertCommand) {
                return new JdbcOperationQueryInsertImpl(mql, parameterBinders, affectedTableNames);
            } else if (command instanceof AstUpdateCommand) {
                return new JdbcOperationQueryUpdate(mql, parameterBinders, affectedTableNames, emptyMap());
            } else if (command instanceof AstDeleteCommand) {
                return new JdbcOperationQueryDelete(mql, parameterBinders, affectedTableNames, emptyMap());
            } else {
                throw fail(format(
                        "Unexpected mutation command type: %s",
                        command.getClass().getName()));
            }
        }
    }
}
