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

import static com.mongodb.hibernate.internal.MongoAssertions.assertNull;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_MUTATION;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.model.ast.TableDelete;
import org.hibernate.sql.model.ast.TableInsert;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;
import org.jspecify.annotations.Nullable;

final class TableMutationMqlTranslator<O extends JdbcMutationOperation> extends AbstractMqlTranslator<O> {

    private final TableMutation<O> tableMutation;

    TableMutationMqlTranslator(TableMutation<O> tableMutation, SessionFactoryImplementor sessionFactory) {
        super(sessionFactory);
        this.tableMutation = tableMutation;
    }

    @Override
    @SuppressWarnings("unchecked")
    public O translate(@Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {
        assertNull(jdbcParameterBindings);
        // QueryOptions class is not applicable to table mutation so a dummy value is always passed in

        if (tableMutation instanceof TableInsert || tableMutation instanceof TableDelete) {
            return translateTableMutation();
        } else {
            // TODO-HIBERNATE-19 https://jira.mongodb.org/browse/HIBERNATE-19
            return (O) new NoopJdbcMutationOperation();
        }
    }

    private O translateTableMutation() {
        var rootAstNode = acceptAndYield(tableMutation, COLLECTION_MUTATION);
        return tableMutation.createMutationOperation(renderMongoAstNode(rootAstNode), getParameterBinders());
    }
}
