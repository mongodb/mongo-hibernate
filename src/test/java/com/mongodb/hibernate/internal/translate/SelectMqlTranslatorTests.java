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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import com.mongodb.hibernate.internal.extension.service.StandardServiceRegistryScopedState;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.spi.SqlAliasBaseImpl;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.from.NamedTableReference;
import org.hibernate.sql.ast.tree.from.StandardTableGroup;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.results.internal.SqlSelectionImpl;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMappingProducerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelectMqlTranslatorTests {

    @Test
    void testAffectedTableNames(
            @Mock EntityPersister entityPersister,
            @Mock(mockMaker = MockMakers.PROXY) SessionFactoryImplementor sessionFactory,
            @Mock JdbcValuesMappingProducerProvider jdbcValuesMappingProducerProvider,
            @Mock(mockMaker = MockMakers.PROXY) ServiceRegistryImplementor serviceRegistry,
            @Mock StandardServiceRegistryScopedState standardServiceRegistryScopedState,
            @Mock SelectableMapping selectableMapping) {

        var tableName = "books";
        SelectStatement selectFromTableName;
        { // prepare `selectFromTableName`
            doReturn(new String[] {tableName}).when(entityPersister).getQuerySpaces();

            var namedTableReference = new NamedTableReference(tableName, "b1_0");

            var querySpec = new QuerySpec(true);
            var tableGroup = new StandardTableGroup(
                    false,
                    new NavigablePath("Book"),
                    entityPersister,
                    null,
                    namedTableReference,
                    new SqlAliasBaseImpl("b1"),
                    sessionFactory);
            querySpec.getFromClause().addRoot(tableGroup);
            querySpec
                    .getSelectClause()
                    .addSqlSelection(new SqlSelectionImpl(
                            new ColumnReference(tableGroup.getPrimaryTableReference(), selectableMapping)));
            selectFromTableName = new SelectStatement(querySpec);
        }
        { // prepare `sessionFactory`
            doReturn(serviceRegistry).when(sessionFactory).getServiceRegistry();
            doReturn(jdbcValuesMappingProducerProvider)
                    .when(serviceRegistry)
                    .requireService(eq(JdbcValuesMappingProducerProvider.class));
            doReturn(standardServiceRegistryScopedState)
                    .when(serviceRegistry)
                    .requireService(eq(StandardServiceRegistryScopedState.class));
        }

        var translator = new SelectMqlTranslator(sessionFactory, selectFromTableName);

        assertThat(translator.translate(null, QueryOptions.NONE).getAffectedTableNames())
                .containsExactly(tableName);
    }
}
