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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.dialect.MongoDialect;
import com.mongodb.hibernate.junit.InjectMongoClient;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.SQLException;
import org.bson.BsonDocument;
import org.hibernate.JDBCException;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** @see MongoDialect#buildSQLExceptionConversionDelegate() */
@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {ExceptionHandlingIntegrationTest.Item.class})
@ExtendWith(MongoExtension.class)
class ExceptionHandlingIntegrationTest implements SessionFactoryScopeAware {
    private static final String COLLECTION_NAME = "items";
    private static final String EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION = "Failed to execute operation";
    private static final String EXCEPTION_MESSAGE_TIMEOUT = "Timeout while waiting for operation to complete";

    @InjectMongoClient
    private static MongoClient mongoClient;

    @InjectMongoCollection(COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollection;

    SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 1200})
    void testGenericExceptionThrown(int errorCode) {
        configureFailPointErrorCode(errorCode);
        assertThatThrownBy(() -> {
                    sessionFactoryScope.inTransaction(session -> {
                        session.persist(new Item(1));
                    });
                })
                .isInstanceOf(JDBCException.class)
                .hasMessageContaining(EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION)
                .cause()
                .isInstanceOf(SQLException.class)
                .hasRootCauseInstanceOf(MongoException.class);
    }

    @Test
    void testTimeoutExceptionThrown() {
        configureFailPointErrorCode(50);
        assertThatThrownBy(() -> {
                    sessionFactoryScope.inTransaction(session -> {
                        session.persist(new Item(1));
                    });
                })
                .isInstanceOf(JDBCException.class)
                .hasMessageContaining(EXCEPTION_MESSAGE_TIMEOUT)
                .cause()
                .isInstanceOf(SQLException.class)
                .hasRootCauseInstanceOf(MongoException.class);
    }

    @Nested
    @ServiceRegistry(settings = @Setting(name = AvailableSettings.STATEMENT_BATCH_SIZE, value = "2"))
    @ExtendWith(MongoExtension.class)
    @SessionFactory(exportSchema = false)
    @DomainModel(annotatedClasses = {ExceptionHandlingIntegrationTest.Item.class})
    class Batch implements SessionFactoryScopeAware {
        SessionFactoryScope sessionFactoryScope;

        @Override
        public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
            this.sessionFactoryScope = sessionFactoryScope;
        }

        @ParameterizedTest
        @ValueSource(ints = {1000, 1200})
        void testGenericExceptionThrown(int errorCode) {
            configureFailPointErrorCode(errorCode);
            assertThatThrownBy(() -> {
                        sessionFactoryScope.inTransaction(session -> {
                            session.persist(new Item(1));
                            session.persist(new Item(2));
                        });
                    })
                    .isInstanceOf(JDBCException.class)
                    .hasMessageContaining(EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION)
                    .cause()
                    .isExactlyInstanceOf(SQLException.class)
                    .hasRootCauseInstanceOf(MongoException.class);
        }

        @Test
        void testTimeoutExceptionThrown() {
            configureFailPointErrorCode(50);
            assertThatThrownBy(() -> {
                        sessionFactoryScope.inTransaction(session -> {
                            session.persist(new Item(1));
                            session.persist(new Item(2));
                        });
                    })
                    .isInstanceOf(JDBCException.class)
                    .hasMessageContaining(EXCEPTION_MESSAGE_TIMEOUT)
                    .cause()
                    .isExactlyInstanceOf(SQLException.class)
                    .hasRootCauseInstanceOf(MongoException.class);
        }
    }

    private static void configureFailPointErrorCode(int errorCode) {
        BsonDocument failPointCommand = BsonDocument.parse(
                """
                {
                  configureFailPoint: "failCommand",
                  mode: { times: 1 },
                  data: {
                    failCommands: ["insert"],
                    errorCode: %d
                    errorLabels: ["TransientTransactionError"]
                  }
                }
                """
                        .formatted(errorCode));
        mongoClient.getDatabase("admin").runCommand(failPointCommand);
    }

    @Entity
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        Item() {}

        Item(int id) {
            this.id = id;
        }
    }
}
