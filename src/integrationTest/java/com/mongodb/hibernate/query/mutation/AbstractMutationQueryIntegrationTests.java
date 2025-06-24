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

package com.mongodb.hibernate.query.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.cfg.JdbcSettings.DIALECT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.mongodb.hibernate.dialect.MongoDialect;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.AbstractJdbcOperationQuery;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.Setting;
import org.mockito.stubbing.Answer;

@ServiceRegistry(
        settings =
                @Setting(
                        name = DIALECT,
                        value =
                                "com.mongodb.hibernate.query.mutation.AbstractMutationQueryIntegrationTests$MutationTranslateResultAwareDialect"))
public abstract class AbstractMutationQueryIntegrationTests extends AbstractQueryIntegrationTests {

    protected void assertExpectedAffectedCollections(String... expectedAffectedCollections) {
        assertThat(((MutationTranslateResultAwareDialect) getSessionFactoryScope()
                                .getSessionFactory()
                                .getJdbcServices()
                                .getDialect())
                        .capturedTranslateResult.getAffectedTableNames())
                .containsExactlyInAnyOrder(expectedAffectedfCollections);
    }

    public static final class MutationTranslateResultAwareDialect extends Dialect {
        private final Dialect delegate;
        private AbstractJdbcOperationQuery capturedTranslateResult;

        public MutationTranslateResultAwareDialect(DialectResolutionInfo info) {
            super(info);
            delegate = new MongoDialect(info);
        }

        @Override
        public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
            return new SqlAstTranslatorFactory() {
                @Override
                public SqlAstTranslator<JdbcOperationQuerySelect> buildSelectTranslator(
                        SessionFactoryImplementor sessionFactory, SelectStatement statement) {
                    return delegate.getSqlAstTranslatorFactory().buildSelectTranslator(sessionFactory, statement);
                }

                @Override
                public SqlAstTranslator<? extends JdbcOperationQueryMutation> buildMutationTranslator(
                        SessionFactoryImplementor sessionFactory, MutationStatement statement) {
                    var originalTranslator =
                            delegate.getSqlAstTranslatorFactory().buildMutationTranslator(sessionFactory, statement);
                    var translatorSpy = spy(originalTranslator);
                    doAnswer((Answer<AbstractJdbcOperationQuery>) invocation -> {
                                capturedTranslateResult = (AbstractJdbcOperationQuery) invocation.callRealMethod();
                                return capturedTranslateResult;
                            })
                            .when(translatorSpy)
                            .translate(any(), any());
                    return translatorSpy;
                }

                @Override
                public <O extends JdbcMutationOperation> SqlAstTranslator<O> buildModelMutationTranslator(
                        TableMutation<O> mutation, SessionFactoryImplementor sessionFactory) {
                    return delegate.getSqlAstTranslatorFactory().buildModelMutationTranslator(mutation, sessionFactory);
                }
            };
        }
    }
}
