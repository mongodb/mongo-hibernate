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

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.TestCommandListener;
import com.mongodb.hibernate.junit.MongoExtension;
import java.util.function.Consumer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bson.BsonDocument;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.SelectionQuery;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.hibernate.testing.orm.junit.ServiceRegistryScopeAware;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@ExtendWith(MongoExtension.class)
public abstract class AbstractQueryIntegrationTests implements SessionFactoryScopeAware, ServiceRegistryScopeAware {

    private SessionFactoryScope sessionFactoryScope;

    private TestCommandListener testCommandListener;

    protected SessionFactoryScope getSessionFactoryScope() {
        return sessionFactoryScope;
    }

    protected TestCommandListener getTestCommandListener() {
        return testCommandListener;
    }

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Override
    public void injectServiceRegistryScope(ServiceRegistryScope serviceRegistryScope) {
        this.testCommandListener = serviceRegistryScope.getRegistry().requireService(TestCommandListener.class);
    }

    protected <T> void assertSelectionQuery(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            String expectedMql,
            Iterable<T> expectedResultList) {
        assertSelectionQuery(
                hql,
                resultType,
                queryPostProcessor,
                expectedMql,
                resultList -> assertIterableEq(expectedResultList, resultList));
    }

    protected <T> void assertSelectionQuery(
            String hql, Class<T> resultType, String expectedMql, Iterable<T> expectedResultList) {
        assertSelectionQuery(hql, resultType, null, expectedMql, expectedResultList);
    }

    protected <T> void assertSelectionQuery(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            String expectedMql,
            Consumer<Iterable<? extends T>> resultListVerifier) {
        sessionFactoryScope.inTransaction(session -> {
            var selectionQuery = session.createSelectionQuery(hql, resultType);
            if (queryPostProcessor != null) {
                queryPostProcessor.accept(selectionQuery);
            }
            var resultList = selectionQuery.getResultList();

            assertActualCommand(BsonDocument.parse(expectedMql));

            resultListVerifier.accept(resultList);
        });
    }

    protected <T> void assertSelectionQuery(
            String hql, Class<T> resultType, String expectedMql, Consumer<Iterable<? extends T>> resultListVerifier) {
        assertSelectionQuery(hql, resultType, null, expectedMql, resultListVerifier);
    }

    protected <T> void assertSelectQueryFailure(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            Class<? extends Exception> expectedExceptionType,
            String expectedExceptionMessage,
            Object... expectedExceptionMessageParameters) {
        sessionFactoryScope.inTransaction(session -> assertThatThrownBy(() -> {
                    var selectionQuery = session.createSelectionQuery(hql, resultType);
                    if (queryPostProcessor != null) {
                        queryPostProcessor.accept(selectionQuery);
                    }
                    selectionQuery.getResultList();
                })
                .isInstanceOf(expectedExceptionType)
                .hasMessage(expectedExceptionMessage, expectedExceptionMessageParameters));
    }

    protected <T> void assertSelectQueryFailure(
            String hql,
            Class<T> resultType,
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

    protected void assertActualCommand(BsonDocument expectedCommand) {
        var capturedCommands = testCommandListener.getStartedCommands();

        assertThat(capturedCommands)
                .singleElement()
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsAllEntriesOf(expectedCommand);
    }

    protected void assertMutationQuery(
            String hql,
            Consumer<MutationQuery> queryPostProcessor,
            int expectedMutationCount,
            String expectedMql,
            MongoCollection<BsonDocument> collection,
            Iterable<? extends BsonDocument> expectedDocuments) {
        sessionFactoryScope.inTransaction(session -> {
            var query = session.createMutationQuery(hql);
            if (queryPostProcessor != null) {
                queryPostProcessor.accept(query);
            }
            var mutationCount = query.executeUpdate();
            assertActualCommand(BsonDocument.parse(expectedMql));
            assertThat(mutationCount).isEqualTo(expectedMutationCount);
        });
        assertThat(collection.find()).containsExactlyElementsOf(expectedDocuments);
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
}
