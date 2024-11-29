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

package com.mongodb.hibernate.translate;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;
import org.mockito.Mockito;

@SuppressWarnings("unchecked")
public final class MongoTranslatorFactory implements SqlAstTranslatorFactory {
    @Override
    public SqlAstTranslator<JdbcOperationQuerySelect> buildSelectTranslator(
            SessionFactoryImplementor sessionFactoryImplementor, SelectStatement selectStatement) {
        return Mockito.mock(SqlAstTranslator.class);
    }

    @Override
    public SqlAstTranslator<? extends JdbcOperationQueryMutation> buildMutationTranslator(
            SessionFactoryImplementor sessionFactoryImplementor, MutationStatement mutationStatement) {
        return Mockito.mock(SqlAstTranslator.class);
    }

    @Override
    public <O extends JdbcMutationOperation> SqlAstTranslator<O> buildModelMutationTranslator(
            TableMutation<O> tableMutation, SessionFactoryImplementor sessionFactoryImplementor) {
        return new MqlTranslator<>(sessionFactoryImplementor, tableMutation);
    }
}
