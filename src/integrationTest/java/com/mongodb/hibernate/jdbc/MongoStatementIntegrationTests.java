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

package com.mongodb.hibernate.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MongoStatementIntegrationTests {

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

    @Nested
    class ExecuteUpdateTests {

        @BeforeEach
        void setUp() {
            session.doWork(conn -> {
                conn.createStatement()
                        .executeUpdate(
                                """
                                {
                                    delete: "books",
                                    deletes: [
                                        { q: {}, limit: 0 }
                                    ]
                                }""");
            });
        }

        private static final String INSERT_MQL =
                """
                {
                    insert: "books",
                    documents: [
                        {
                            _id: 1,
                            title: "War and Peace",
                            author: "Leo Tolstoy",
                            outOfStock: false
                        },
                        {
                            _id: 2,
                            title: "Anna Karenina",
                            author: "Leo Tolstoy",
                            outOfStock: false
                        },
                        {
                            _id: 3,
                            title: "Crime and Punishment",
                            author: "Fyodor Dostoevsky",
                            outOfStock: false
                        }
                    ]
                }""";

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testInsert(boolean autoCommit) {
            var expectedDocs = Set.of(
                    BsonDocument.parse(
                            """
                            {
                                _id: 1,
                                title: "War and Peace",
                                author: "Leo Tolstoy",
                                outOfStock: false
                            }"""),
                    BsonDocument.parse(
                            """
                            {
                                _id: 2,
                                title: "Anna Karenina",
                                author: "Leo Tolstoy",
                                outOfStock: false
                            }"""),
                    BsonDocument.parse(
                            """
                            {
                                _id: 3,
                                title: "Crime and Punishment",
                                author: "Fyodor Dostoevsky",
                                outOfStock: false
                            }"""));
            assertExecuteUpdate(INSERT_MQL, autoCommit, 3, expectedDocs);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testUpdate(boolean autoCommit) {
            // given
            prepareData();

            // when && then
            var updateMql =
                    """
                    {
                        update: "books",
                        updates: [
                            {
                                q: { author: "Leo Tolstoy" },
                                u: {
                                    $set: { outOfStock: true }
                                },
                                multi: true
                            }
                        ]
                    }""";
            var expectedDocs = Set.of(
                    BsonDocument.parse(
                            """
                            {
                                _id: 1,
                                title: "War and Peace",
                                author: "Leo Tolstoy",
                                outOfStock: true
                            }"""),
                    BsonDocument.parse(
                            """
                            {
                                _id: 2,
                                title: "Anna Karenina",
                                author: "Leo Tolstoy",
                                outOfStock: true
                            }"""),
                    BsonDocument.parse(
                            """
                            {
                                _id: 3,
                                title: "Crime and Punishment",
                                author: "Fyodor Dostoevsky",
                                outOfStock: false
                            }"""));
            assertExecuteUpdate(updateMql, autoCommit, 2, expectedDocs);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testDelete(boolean autoCommit) {
            // given
            prepareData();

            // when && then
            var deleteMql =
                    """
                    {
                        delete: "books",
                        deletes: [
                            {
                                q: { author: "Leo Tolstoy" },
                                limit: 1
                            }
                        ]
                    }""";
            var expectedDocs = Set.of(
                    BsonDocument.parse(
                            """
                            {
                                _id: 2,
                                title: "Anna Karenina",
                                author: "Leo Tolstoy",
                                outOfStock: false
                            }"""),
                    BsonDocument.parse(
                            """
                            {
                                 _id: 3,
                                 title: "Crime and Punishment",
                                 author: "Fyodor Dostoevsky",
                                 outOfStock: false
                            }"""));
            assertExecuteUpdate(deleteMql, autoCommit, 1, expectedDocs);
        }

        private void prepareData() {
            session.doWork(connection -> {
                connection.setAutoCommit(true);
                var statement = connection.createStatement();
                statement.executeUpdate(INSERT_MQL);
            });
        }

        private void assertExecuteUpdate(
                String mql, boolean autoCommit, int expectedRowCount, Set<? extends BsonDocument> expectedDocuments) {
            session.doWork(connection -> {
                connection.setAutoCommit(autoCommit);
                try (var stmt = (MongoStatement) connection.createStatement()) {
                    try {
                        assertEquals(expectedRowCount, stmt.executeUpdate(mql));
                    } finally {
                        if (!autoCommit) {
                            connection.commit();
                        }
                    }
                    var realDocuments = stmt.getMongoDatabase()
                            .getCollection("books", BsonDocument.class)
                            .find()
                            .into(new HashSet<>());
                    assertEquals(expectedDocuments, realDocuments);
                }
            });
        }
    }
}
