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

package com.mongodb.hibernate.query.select;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.hibernate.TestCommandListener;
import com.mongodb.hibernate.junit.MongoExtension;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.bson.BsonDocument;
import org.hibernate.query.SelectionQuery;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.hibernate.testing.orm.junit.ServiceRegistryScopeAware;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@ExtendWith(MongoExtension.class)
abstract class AbstractSelectionQueryIntegrationTests implements SessionFactoryScopeAware, ServiceRegistryScopeAware {

    SessionFactoryScope sessionFactoryScope;

    TestCommandListener testCommandListener;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Override
    public void injectServiceRegistryScope(ServiceRegistryScope serviceRegistryScope) {
        this.testCommandListener = serviceRegistryScope.getRegistry().requireService(TestCommandListener.class);
    }

    <T> void assertSelectionQuery(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            String expectedMql,
            List<T> expectedResultList) {
        assertSelectionQuery(hql, resultType, queryPostProcessor, expectedMql, resultList -> assertThat(resultList)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(expectedResultList));
    }

    <T> void assertSelectionQuery(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            String expectedMql,
            Consumer<List<T>> resultListVerifier) {
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

    <T> void assertSelectQueryFailure(
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

    void assertActualCommand(BsonDocument expectedCommand) {
        var capturedCommands = testCommandListener.getStartedCommands();

        assertThat(capturedCommands)
                .singleElement()
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsAllEntriesOf(expectedCommand);
    }
}
