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

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.SELECT_RESULT;

import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import java.util.ArrayList;
import java.util.Set;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslationRequest;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.spi.Statement;
import org.hibernate.sql.ast.spi.query.expression.JdbcParameter;
import org.hibernate.sql.ast.spi.query.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcOperations;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.exec.spi.JdbcSelect;
import org.jspecify.annotations.Nullable;

/**
 * @mongoCme Does not have to be thread-safe because it is
 *     {@linkplain MongoTranslatorFactory#buildTranslator(SqlAstTranslationRequest) single-use}.
 */
final class SelectMqlTranslator extends AbstractMqlTranslator<JdbcSelect> {

    private final SqlAstTranslationRequest.Select request;
    private final SelectStatement selectStatement;

    SelectMqlTranslator(SqlAstTranslationRequest.Select request) {
        super(request.sessionFactory());
        this.request = request;
        this.selectStatement = request.statement();
    }

    @Override
    public JdbcSelect translate(@Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {

        applyQueryOptions(queryOptions);

        var result = acceptAndYield((Statement) selectStatement, SELECT_RESULT);
        return result.createJdbcSelect(this.request);
    }

    static final class Result {
        private final AstCommand command;
        private final Set<String> affectedTableNames;
        private final @Nullable JdbcParameter offsetParameter;
        private final @Nullable JdbcParameter limitParameter;

        Result(
                AstCommand command,
                Set<String> affectedTableNames,
                @Nullable JdbcParameter offsetParameter,
                @Nullable JdbcParameter limitParameter) {
            this.command = command;
            this.affectedTableNames = affectedTableNames;
            this.offsetParameter = offsetParameter;
            this.limitParameter = limitParameter;
        }

        private JdbcSelect createJdbcSelect(SqlAstTranslationRequest.Select request) {
            var parameterBinders = new ArrayList<JdbcParameterBinder>();
            var mql = renderMongoAstNode(command, parameterBinders::add);
            // The offset and limit parameters are provided for query plan cache purposes.
            // Not setting them could result in reusing the wrong query plan and subsequently the wrong MQL.
            return JdbcOperations.select(request)
                    .command(mql)
                    .parameterBinders(parameterBinders)
                    .affectedQuerySpaces(affectedTableNames)
                    .offsetParameter(offsetParameter)
                    .limitParameter(limitParameter)
                    .build();
        }
    }
}
