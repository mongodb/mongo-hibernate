/*
 * Copyright 2024-present MongoDB, Inc.
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionTests {

    private static SessionFactory sessionFactory;

    private Session session;

    @BeforeAll
    static void beforeAll() {
        sessionFactory = new Configuration().buildSessionFactory();
    }

    @AfterAll
    static void afterAll() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        session = sessionFactory.openSession();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void testDoWork() {
        assertDoesNotThrow(() -> session.doWork(connection -> {}));
    }

    @Test
    void testBeginTransaction() {
        assertDoesNotThrow(() -> session.beginTransaction().commit());
        assertDoesNotThrow(() -> session.doWork(connection -> {}));
    }

    @Test
    void testCreateStatement() {
        assertDoesNotThrow(() -> session.doWork(Connection::createStatement));
    }

    @Nested
    class MongoStatementTests {

        private static final String COLLECTION_NAME = "books";

        @BeforeEach
        void setUp() {
            session.doWork(conn -> {
                conn.createStatement()
                        .executeUpdate(
                                """
                        {
                            delete: "%s",
                            deletes: [
                                { q: {}, limit: 0 }
                            ]
                        }"""
                                        .formatted(COLLECTION_NAME));
            });
        }

        @Nested
        class ExecuteUpdateTests {

            private static final String INSERT_MQL =
                    """
                     {
                                insert: "%s",
                                documents: [
                                    {
                                        title: "War and Peace",
                                        author: "Leo Tolstoy",
                                        outOfStock: false
                                    },
                                    {
                                        title: "Anna Karenina",
                                        author: "Leo Tolstoy",
                                        outOfStock: false
                                    },
                                    {
                                        title: "Resurrection",
                                        author: "Leo Tolstoy",
                                        outOfStock: false
                                    },
                                    {
                                        title: "Crime and Punishment",
                                        author: "Fyodor Dostoevsky",
                                        outOfStock: false
                                    },
                                    {
                                        title: "The Brothers Karamazov",
                                        author: "Fyodor Dostoevsky",
                                        outOfStock: false
                                    },
                                    {
                                        title: "Fathers and Sons",
                                        author: "Ivan Turgenev",
                                        outOfStock: false
                                    }
                                ]
                            }"""
                            .formatted(COLLECTION_NAME);

            @Test
            void testInsert() {
                session.doWork(connection -> {
                    assertExecuteUpdate(INSERT_MQL, 6);
                });
            }

            @Test
            void testUpdate() {
                // given
                session.doWork(connection -> {
                    var statement = connection.createStatement();
                    statement.executeUpdate(INSERT_MQL);
                });

                // when && then
                var updateMql =
                        """
                            {
                                update: "%s",
                                updates: [
                                    {
                                        q: { author: "Leo Tolstoy" },
                                        u: {
                                            $set: { outOfStock: true }
                                        },
                                        multi: true
                                    }
                                ]
                            }"""
                                .formatted(COLLECTION_NAME);
                assertExecuteUpdate(updateMql, 3);
                updateMql =
                        """
                            {
                                update: "%s",
                                updates: [
                                    {
                                        q: { author: "Fyodor Dostoevsky" },
                                        u: {
                                            $set: { outOfStock: true }
                                        },
                                        multi: false
                                    },
                                    {
                                        q: { author: "Ivan Turgenev" },
                                        u: {
                                            $set: { outOfStock: true }
                                        }
                                        multi: true
                                    }
                                ]
                            }"""
                                .formatted(COLLECTION_NAME);
                assertExecuteUpdate(updateMql, 2);
            }

            @Test
            void testDelete() {
                // given
                session.doWork(connection -> {
                    var statement = connection.createStatement();
                    statement.executeUpdate(INSERT_MQL);
                });

                // when && then
                var deleteMql =
                        """
                        {
                            delete: "%s",
                            deletes: [
                                {
                                    q: { author: "Fyodor Dostoevsky" },
                                    limit: 0
                                },
                                {
                                    q: { author: "Ivan Turgenev" },
                                    limit: 0
                                }
                            ]
                        }"""
                                .formatted(COLLECTION_NAME);
                assertExecuteUpdate(deleteMql, 3);
                deleteMql =
                        """
                        {
                            delete: "%s",
                            deletes: [
                                {
                                    q: { author: "Leo Tolstoy" },
                                    limit: 1
                                }
                            ]
                        }"""
                                .formatted(COLLECTION_NAME);
                assertExecuteUpdate(deleteMql, 1);
            }

            private void assertExecuteUpdate(String mql, int expectedRowCount) {
                session.doWork(connection -> {
                    var statement = connection.createStatement();
                    assertEquals(expectedRowCount, statement.executeUpdate(mql));
                });
            }
        }
    }
}
