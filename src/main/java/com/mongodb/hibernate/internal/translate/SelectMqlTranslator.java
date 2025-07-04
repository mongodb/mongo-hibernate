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
import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.emptyMap;
import static org.hibernate.sql.ast.SqlTreePrinter.logSqlAst;
import static org.hibernate.sql.exec.spi.JdbcLockStrategy.NONE;

import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import java.util.List;
import java.util.Set;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMappingProducerProvider;
import org.jspecify.annotations.Nullable;

final class SelectMqlTranslator extends AbstractMqlTranslator<JdbcOperationQuerySelect> {

    private final SelectStatement selectStatement;

    SelectMqlTranslator(SessionFactoryImplementor sessionFactory, SelectStatement selectStatement) {
        super(sessionFactory);
        this.selectStatement = selectStatement;
    }

    @Override
    public JdbcOperationQuerySelect translate(
            @Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {

        logSqlAst(selectStatement);

        applyQueryOptions(queryOptions);

        var result = acceptAndYield((Statement) selectStatement, SELECT_RESULT);
        return result.createJdbcOperationQuerySelect(selectStatement, getSessionFactory());
    }

    static final class Result {
        private final AstCommand command;
        private final List<JdbcParameterBinder> parameterBinders;
        private final Set<String> affectedTableNames;
        private final @Nullable JdbcParameter offsetParameter;
        private final @Nullable JdbcParameter limitParameter;

        Result(
                AstCommand command,
                List<JdbcParameterBinder> parameterBinders,
                Set<String> affectedTableNames,
                @Nullable JdbcParameter offsetParameter,
                @Nullable JdbcParameter limitParameter) {
            this.command = command;
            this.parameterBinders = parameterBinders;
            this.affectedTableNames = affectedTableNames;
            this.offsetParameter = offsetParameter;
            this.limitParameter = limitParameter;
        }

        private JdbcOperationQuerySelect createJdbcOperationQuerySelect(
                SelectStatement selectStatement, SessionFactoryImplementor sessionFactory) {
            var jdbcValuesMappingProducerProvider =
                    sessionFactory.getServiceRegistry().requireService(JdbcValuesMappingProducerProvider.class);
            var jdbcValuesMappingProducer =
                    jdbcValuesMappingProducerProvider.buildMappingProducer(selectStatement, sessionFactory);
            return new JdbcOperationQuerySelect(
                    renderMongoAstNode(command),
                    parameterBinders,
                    jdbcValuesMappingProducer,
                    affectedTableNames,
                    0,
                    MAX_VALUE,
                    emptyMap(),
                    NONE,
                    // The following parameters are provided for query plan cache purposes.
                    // Not setting them could result in reusing the wrong query plan and subsequently the wrong MQL.
                    offsetParameter,
                    limitParameter);
        }
    }
}
