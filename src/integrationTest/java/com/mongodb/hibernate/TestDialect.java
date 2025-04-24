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

package com.mongodb.hibernate;

import com.mongodb.hibernate.dialect.MongoDialect;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;

public final class TestDialect extends Dialect {
    private final AtomicInteger selectTranslatingCounter = new AtomicInteger();
    private final Dialect delegate;

    public TestDialect(DialectResolutionInfo info) {
        super(info);
        delegate = new MongoDialect(info);
    }

    @Override
    public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
        return new SqlAstTranslatorFactory() {
            @Override
            public SqlAstTranslator<JdbcOperationQuerySelect> buildSelectTranslator(
                    SessionFactoryImplementor sessionFactory, SelectStatement statement) {
                selectTranslatingCounter.incrementAndGet();
                return delegate.getSqlAstTranslatorFactory().buildSelectTranslator(sessionFactory, statement);
            }

            @Override
            public SqlAstTranslator<? extends JdbcOperationQueryMutation> buildMutationTranslator(
                    SessionFactoryImplementor sessionFactory, MutationStatement statement) {
                return delegate.getSqlAstTranslatorFactory().buildMutationTranslator(sessionFactory, statement);
            }

            @Override
            public <O extends JdbcMutationOperation> SqlAstTranslator<O> buildModelMutationTranslator(
                    TableMutation<O> mutation, SessionFactoryImplementor sessionFactory) {
                return delegate.getSqlAstTranslatorFactory().buildModelMutationTranslator(mutation, sessionFactory);
            }
        };
    }

    public int getSelectTranslatingCounter() {
        return selectTranslatingCounter.get();
    }
}
