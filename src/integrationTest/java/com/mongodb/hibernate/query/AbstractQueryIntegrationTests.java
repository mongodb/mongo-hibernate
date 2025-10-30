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

package com.mongodb.hibernate.query;

import static com.mongodb.hibernate.MongoTestAssertions.assertIterableEq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.DIALECT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.TestCommandListener;
import com.mongodb.hibernate.dialect.MongoDialect;
import com.mongodb.hibernate.junit.MongoExtension;
import java.util.Set;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bson.BsonDocument;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.SelectionQuery;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.AbstractJdbcOperationQuery;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcOperationQuerySelect;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.hibernate.testing.orm.junit.ServiceRegistryScopeAware;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;

@SessionFactory(exportSchema = false)
@ServiceRegistry(
        settings =
                @Setting(
                        name = DIALECT,
                        value =
                                "com.mongodb.hibernate.query.AbstractQueryIntegrationTests$TranslateResultAwareDialect"))
@ExtendWith(MongoExtension.class)
public abstract class AbstractQueryIntegrationTests implements SessionFactoryScopeAware, ServiceRegistryScopeAware {

    private SessionFactoryScope sessionFactoryScope;

    private TestCommandListener testCommandListener;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Override
    public void injectServiceRegistryScope(ServiceRegistryScope serviceRegistryScope) {
        this.testCommandListener = serviceRegistryScope.getRegistry().requireService(TestCommandListener.class);
    }

    protected SessionFactoryScope getSessionFactoryScope() {
        return sessionFactoryScope;
    }

    protected TestCommandListener getTestCommandListener() {
        return testCommandListener;
    }

    protected <T> void assertSelectionQuery(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            String expectedMql,
            Iterable<T> expectedResultList,
            Set<String> expectedAffectedCollections) {
        assertSelectionQuery(
                hql,
                resultType,
                queryPostProcessor,
                expectedMql,
                resultList -> assertIterableEq(expectedResultList, resultList),
                expectedAffectedCollections);
    }

    protected <T> void assertSelectionQuery(
            String hql,
            Class<T> resultType,
            String expectedMql,
            Iterable<T> expectedResultList,
            Set<String> expectedAffectedCollections) {
        assertSelectionQuery(hql, resultType, null, expectedMql, expectedResultList, expectedAffectedCollections);
    }

    protected <T> void assertSelectionQuery(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            String expectedMql,
            Consumer<Iterable<? extends T>> resultListVerifier,
            Set<String> expectedAffectedCollections) {
        sessionFactoryScope.inTransaction(session -> {
            var selectionQuery = session.createSelectionQuery(hql, resultType);
            if (queryPostProcessor != null) {
                queryPostProcessor.accept(selectionQuery);
            }
            var resultList = selectionQuery.getResultList();

            assertActualCommandsInOrder(BsonDocument.parse(expectedMql));

            resultListVerifier.accept(resultList);

            assertAffectedCollections(expectedAffectedCollections);
        });
    }

    protected <T> void assertSelectionQuery(
            String hql,
            Class<T> resultType,
            String expectedMql,
            Consumer<Iterable<? extends T>> resultListVerifier,
            Set<String> expectedAffectedCollections) {
        assertSelectionQuery(hql, resultType, null, expectedMql, resultListVerifier, expectedAffectedCollections);
    }

    protected <T> AbstractThrowableAssert<?, ? extends Throwable> assertSelectQueryFailure(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            Class<? extends Exception> expectedExceptionType,
            String expectedExceptionMessageSubstring,
            Object... expectedExceptionMessageParameters) {
        return sessionFactoryScope.fromTransaction(session -> assertThatThrownBy(() -> {
                    var selectionQuery = session.createSelectionQuery(hql, resultType);
                    if (queryPostProcessor != null) {
                        queryPostProcessor.accept(selectionQuery);
                    }
                    selectionQuery.getResultList();
                })
                .isInstanceOf(expectedExceptionType)
                .hasMessageContaining(expectedExceptionMessageSubstring, expectedExceptionMessageParameters));
    }

    protected void assertSelectQueryFailure(
            String hql,
            Class<?> resultType,
            Class<? extends Exception> expectedExceptionType,
            String expectedExceptionMessage,
            Object... expectedExceptionMessageParameters) {
        assertSelectQueryFailure(
                hql,
                resultType,
                null,
                expectedExceptionType,
                expectedExceptionMessage,
                expectedExceptionMessageParameters);
    }

    protected void assertActualCommandsInOrder(BsonDocument... expectedCommands) {
        var capturedCommands = testCommandListener.getStartedCommands();
        assertThat(capturedCommands).hasSize(expectedCommands.length);
        for (int i = 0; i < expectedCommands.length; i++) {
            BsonDocument actual = capturedCommands.get(i);
            assertThat(actual).asInstanceOf(InstanceOfAssertFactories.MAP).containsAllEntriesOf(expectedCommands[i]);
        }
    }

    protected void assertMutationQuery(
            String hql,
            int expectedMutationCount,
            String expectedMql,
            MongoCollection<BsonDocument> collection,
            Iterable<? extends BsonDocument> expectedDocuments,
            Set<String> expectedAffectedCollections) {
        assertMutationQuery(
                hql,
                null,
                expectedMutationCount,
                expectedMql,
                collection,
                expectedDocuments,
                expectedAffectedCollections);
    }

    protected void assertMutationQuery(
            String hql,
            Consumer<MutationQuery> queryPostProcessor,
            int expectedMutationCount,
            String expectedMql,
            MongoCollection<BsonDocument> collection,
            Iterable<? extends BsonDocument> expectedDocuments,
            Set<String> expectedAffectedCollections) {
        sessionFactoryScope.inTransaction(session -> {
            var query = session.createMutationQuery(hql);
            if (queryPostProcessor != null) {
                queryPostProcessor.accept(query);
            }
            var mutationCount = query.executeUpdate();
            assertActualCommandsInOrder(BsonDocument.parse(expectedMql));
            assertThat(mutationCount).isEqualTo(expectedMutationCount);
        });
        assertThat(collection.find()).containsExactlyElementsOf(expectedDocuments);
        assertAffectedCollections(expectedAffectedCollections);
    }

    protected void assertMutationQueryFailure(
            String hql,
            Consumer<MutationQuery> queryPostProcessor,
            Class<? extends Exception> expectedExceptionType,
            String expectedExceptionMessage,
            Object... expectedExceptionMessageParameters) {
        sessionFactoryScope.inTransaction(session -> assertThatThrownBy(() -> {
                    var query = session.createMutationQuery(hql);
                    if (queryPostProcessor != null) {
                        queryPostProcessor.accept(query);
                    }
                    query.executeUpdate();
                })
                .isInstanceOf(expectedExceptionType)
                .hasMessage(expectedExceptionMessage, expectedExceptionMessageParameters));
    }

    private void assertAffectedCollections(Set<String> expectedAffectedCollections) {
        assertThat(((TranslateResultAwareDialect) getSessionFactoryScope()
                                .getSessionFactory()
                                .getJdbcServices()
                                .getDialect())
                        .capturedTranslateResult.getAffectedTableNames())
                .containsExactlyInAnyOrderElementsOf(expectedAffectedCollections);
    }

    protected static final class TranslateResultAwareDialect extends MongoDialect {
        private AbstractJdbcOperationQuery capturedTranslateResult;

        public TranslateResultAwareDialect(DialectResolutionInfo info) {
            super(info);
        }

        @Override
        public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
            return new SqlAstTranslatorFactory() {
                @Override
                public SqlAstTranslator<JdbcOperationQuerySelect> buildSelectTranslator(
                        SessionFactoryImplementor sessionFactory, SelectStatement statement) {
                    return createCapturingTranslator(TranslateResultAwareDialect.super
                            .getSqlAstTranslatorFactory()
                            .buildSelectTranslator(sessionFactory, statement));
                }

                @Override
                public SqlAstTranslator<? extends JdbcOperationQueryMutation> buildMutationTranslator(
                        SessionFactoryImplementor sessionFactory, MutationStatement statement) {
                    return createCapturingTranslator(TranslateResultAwareDialect.super
                            .getSqlAstTranslatorFactory()
                            .buildMutationTranslator(sessionFactory, statement));
                }

                @Override
                public <O extends JdbcMutationOperation> SqlAstTranslator<O> buildModelMutationTranslator(
                        TableMutation<O> mutation, SessionFactoryImplementor sessionFactory) {
                    return TranslateResultAwareDialect.super
                            .getSqlAstTranslatorFactory()
                            .buildModelMutationTranslator(mutation, sessionFactory);
                }

                private <T extends JdbcOperation> SqlAstTranslator<T> createCapturingTranslator(
                        SqlAstTranslator<T> originalTranslator) {
                    var translatorSpy = spy(originalTranslator);
                    doAnswer((Answer<AbstractJdbcOperationQuery>) invocation -> {
                                capturedTranslateResult = (AbstractJdbcOperationQuery) invocation.callRealMethod();
                                return capturedTranslateResult;
                            })
                            .when(translatorSpy)
                            .translate(any(), any());
                    return translatorSpy;
                }
            };
        }
    }
}
