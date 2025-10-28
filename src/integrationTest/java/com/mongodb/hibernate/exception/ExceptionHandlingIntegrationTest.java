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

package com.mongodb.hibernate.exception;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.bson.BsonDocument;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {ExceptionHandlingIntegrationTest.Item.class})
class ExceptionHandlingIntegrationTest extends AbstractExceptionHandlingIntegrationTest {
    private static final String COLLECTION_NAME = "items";
    private static final String EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION = "Failed to execute operation";
    private static final String EXCEPTION_MESSAGE_TIMEOUT = "Timeout while waiting for operation to complete";

    @InjectMongoCollection(COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollection;

    @Test
    void testConstraintViolationExceptionThrown() {
        mongoCollection.insertOne(BsonDocument.parse("{_id: 1}"));
        assertThatThrownBy(() -> {
                    sessionFactoryScope.inTransaction(session -> {
                        session.persist(new Item(1));
                    });
                })
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining(EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION)
                .cause()
                .isInstanceOf(SQLIntegrityConstraintViolationException.class)
                .hasRootCauseInstanceOf(MongoException.class);
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
                .isInstanceOf(GenericJDBCException.class)
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
                .isInstanceOf(GenericJDBCException.class)
                .hasMessageContaining(EXCEPTION_MESSAGE_TIMEOUT)
                .cause()
                .isExactlyInstanceOf(SQLException.class)
                .hasRootCauseInstanceOf(MongoException.class);
    }

    @Nested
    @ServiceRegistry(settings = @Setting(name = AvailableSettings.STATEMENT_BATCH_SIZE, value = "2"))
    @ExtendWith(MongoExtension.class)
    @SessionFactory(exportSchema = false)
    @DomainModel(annotatedClasses = {ExceptionHandlingIntegrationTest.Item.class})
    class Batch extends AbstractExceptionHandlingIntegrationTest {

        @Test
        void testConstraintViolationExceptionThrown() {
            mongoCollection.insertOne(BsonDocument.parse("{_id: 1}"));
            assertThatThrownBy(() -> {
                        sessionFactoryScope.inTransaction(session -> {
                            session.persist(new Item(1));
                            session.persist(new Item(2));
                        });
                    })
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining(EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION)
                    .cause()
                    .isInstanceOf(BatchUpdateException.class)
                    .cause()
                    .isInstanceOf(SQLIntegrityConstraintViolationException.class)
                    .hasRootCauseInstanceOf(MongoException.class);
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
                    .isInstanceOf(GenericJDBCException.class)
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
                    .isInstanceOf(GenericJDBCException.class)
                    .hasMessageContaining(EXCEPTION_MESSAGE_TIMEOUT)
                    .cause()
                    .isExactlyInstanceOf(SQLException.class)
                    .hasRootCauseInstanceOf(MongoException.class);
        }
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
