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

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_AGGREGATE;
import static org.hibernate.sql.ast.SqlTreePrinter.logSqlAst;

import java.util.Collections;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcLockStrategy;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMappingProducerProvider;
import org.jspecify.annotations.Nullable;

final class SelectMqlTranslator extends AbstractMqlTranslator<JdbcOperationQuerySelect> {

    private final SelectStatement selectStatement;
    private final JdbcValuesMappingProducerProvider jdbcValuesMappingProducerProvider;

    SelectMqlTranslator(SessionFactoryImplementor sessionFactory, SelectStatement selectStatement) {
        super(sessionFactory);
        this.selectStatement = selectStatement;
        jdbcValuesMappingProducerProvider =
                sessionFactory.getServiceRegistry().requireService(JdbcValuesMappingProducerProvider.class);
    }

    @Override
    public JdbcOperationQuerySelect translate(
            @Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {

        logSqlAst(selectStatement);

        checkJdbcParameterBindingsSupportability(jdbcParameterBindings);
        checkQueryOptionsSupportability(queryOptions);

        setLimit(
                queryOptions.getLimit() == null ? null : queryOptions.getLimit().makeCopy());

        var aggregateCommand = acceptAndYield((Statement) selectStatement, COLLECTION_AGGREGATE);
        var jdbcValuesMappingProducer =
                jdbcValuesMappingProducerProvider.buildMappingProducer(selectStatement, getSessionFactory());

        return new JdbcOperationQuerySelect(
                renderMongoAstNode(aggregateCommand),
                getParameterBinders(),
                jdbcValuesMappingProducer,
                getAffectedTableNames(),
                0,
                Integer.MAX_VALUE,
                Collections.emptyMap(),
                JdbcLockStrategy.NONE,
                getFirstRowParameter(),
                getMaxRowsParameter());
    }
}
