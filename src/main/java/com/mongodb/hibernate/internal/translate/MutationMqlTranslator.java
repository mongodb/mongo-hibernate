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

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MUTATION_RESULT;

import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import java.util.ArrayList;
import java.util.Set;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslationRequest;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.spi.query.MutationStatement;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcOperations;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.jspecify.annotations.Nullable;

/**
 * @mongoCme Does not have to be thread-safe because it is
 *     {@linkplain MongoTranslatorFactory#buildTranslator(SqlAstTranslationRequest) single-use}.
 */
final class MutationMqlTranslator extends AbstractMqlTranslator<JdbcOperationQueryMutation> {

    private final SqlAstTranslationRequest.QueryMutation request;
    private final MutationStatement mutationStatement;

    MutationMqlTranslator(SqlAstTranslationRequest.QueryMutation request) {
        super(request.sessionFactory());
        this.request = request;
        this.mutationStatement = request.statement();
    }

    @Override
    public JdbcOperationQueryMutation translate(
            @Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {

        applyQueryOptions(queryOptions);

        var result = acceptAndYield(mutationStatement, MUTATION_RESULT);
        return result.createJdbcOperationQueryMutation(request);
    }

    static final class Result {
        private final AstCommand command;
        private final Set<String> affectedTableNames;

        Result(AstCommand command, Set<String> affectedTableNames) {
            this.command = command;
            this.affectedTableNames = affectedTableNames;
        }

        private JdbcOperationQueryMutation createJdbcOperationQueryMutation(
                SqlAstTranslationRequest.QueryMutation request) {
            var parameterBinders = new ArrayList<JdbcParameterBinder>();
            var mql = renderMongoAstNode(command, parameterBinders::add);
            return JdbcOperations.queryMutation(request)
                    .command(mql)
                    .parameterBinders(parameterBinders)
                    .affectedQuerySpaces(affectedTableNames)
                    .build();
        }
    }
}
