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

import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_AGGREGATE;

import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.jspecify.annotations.Nullable;

final class SelectStatementMqlTranslator extends AbstractMqlTranslator<JdbcOperationQuerySelect> {

    private final SelectStatement selectStatement;

    SelectStatementMqlTranslator(SessionFactoryImplementor sessionFactory, SelectStatement selectStatement) {
        super(sessionFactory);
        this.selectStatement = selectStatement;
    }

    @Override
    public JdbcOperationQuerySelect translate(
            @Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {
        var aggregateCommand = getAggregateCommand();
        var mql = renderMongoAstNode(aggregateCommand);
        var sessionFactory = getSessionFactory();
        var jdbcValuesMappingProducer = sessionFactory
                .getFastSessionServices()
                .getJdbcValuesMappingProducerProvider()
                .buildMappingProducer(selectStatement, sessionFactory);

        return new JdbcOperationQuerySelect(
                mql, getParameterBinders(), jdbcValuesMappingProducer, getAffectedTableNames());
    }

    @VisibleForTesting(otherwise = PRIVATE)
    AstCommand getAggregateCommand() {
        return acceptAndYield((Statement) selectStatement, COLLECTION_AGGREGATE);
    }
}
