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

import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
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

    private static final String COLLECTION_NAME = "books";

    @Nested
    class ExecuteUpdateTests {

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

        private static final String INSERT_MQL =
                """
                 {
                            insert: "%s",
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
                                    title: "Resurrection",
                                    author: "Leo Tolstoy",
                                    outOfStock: false
                                },
                                {
                                    _id: 4,
                                    title: "Crime and Punishment",
                                    author: "Fyodor Dostoevsky",
                                    outOfStock: false
                                },
                                {
                                    _id: 5,
                                    title: "The Brothers Karamazov",
                                    author: "Fyodor Dostoevsky",
                                    outOfStock: false
                                },
                                {
                                    _id: 6,
                                    title: "Fathers and Sons",
                                    author: "Ivan Turgenev",
                                    outOfStock: false
                                }
                            ]
                        }"""
                        .formatted(COLLECTION_NAME);

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testInsert(boolean autoCommit) {
            List<Document> expectedDocs = List.of(
                    Document.parse(
                            """
                            {
                                    _id: 1,
                                    title: "War and Peace",
                                    author: "Leo Tolstoy",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                            {
                                    _id: 2,
                                    title: "Anna Karenina",
                                    author: "Leo Tolstoy",
                                    outOfStock: false
                                 }"""),
                    Document.parse(
                            """
                            {
                                    _id: 3,
                                    title: "Resurrection",
                                    author: "Leo Tolstoy",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 4,
                                    title: "Crime and Punishment",
                                    author: "Fyodor Dostoevsky",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 5,
                                    title: "The Brothers Karamazov",
                                    author: "Fyodor Dostoevsky",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 6
                                    title: "Fathers and Sons",
                                    author: "Ivan Turgenev",
                                    outOfStock: false
                                }"""));
            assertExecuteUpdate(INSERT_MQL, autoCommit, 6, expectedDocs);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testUpdate(boolean autoCommit) {
            // given
            session.doWork(connection -> {
                connection.setAutoCommit(true);
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
            var expectedDocs = List.of(
                    Document.parse(
                            """
                            {
                                    _id: 1,
                                    title: "War and Peace",
                                    author: "Leo Tolstoy",
                                    outOfStock: true
                                }"""),
                    Document.parse(
                            """
                            {
                                    _id: 2,
                                    title: "Anna Karenina",
                                    author: "Leo Tolstoy",
                                    outOfStock: true
                                 }"""),
                    Document.parse(
                            """
                            {
                                    _id: 3,
                                    title: "Resurrection",
                                    author: "Leo Tolstoy",
                                    outOfStock: true
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 4,
                                    title: "Crime and Punishment",
                                    author: "Fyodor Dostoevsky",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 5,
                                    title: "The Brothers Karamazov",
                                    author: "Fyodor Dostoevsky",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 6
                                    title: "Fathers and Sons",
                                    author: "Ivan Turgenev",
                                    outOfStock: false
                                }"""));
            assertExecuteUpdate(updateMql, autoCommit, 3, expectedDocs);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testDelete(boolean autoCommit) {
            // given
            session.doWork(connection -> {
                connection.setAutoCommit(true);
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
                                q: { author: "Leo Tolstoy" },
                                limit: 1
                            }
                        ]
                    }"""
                            .formatted(COLLECTION_NAME);
            var expectedDocs = List.of(
                    Document.parse(
                            """
                            {
                                    _id: 2,
                                    title: "Anna Karenina",
                                    author: "Leo Tolstoy",
                                    outOfStock: false
                                 }"""),
                    Document.parse(
                            """
                            {
                                    _id: 3,
                                    title: "Resurrection",
                                    author: "Leo Tolstoy",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 4,
                                    title: "Crime and Punishment",
                                    author: "Fyodor Dostoevsky",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 5,
                                    title: "The Brothers Karamazov",
                                    author: "Fyodor Dostoevsky",
                                    outOfStock: false
                                }"""),
                    Document.parse(
                            """
                           {
                                    _id: 6
                                    title: "Fathers and Sons",
                                    author: "Ivan Turgenev",
                                    outOfStock: false
                                }"""));
            assertExecuteUpdate(deleteMql, autoCommit, 1, expectedDocs);
        }

        private void assertExecuteUpdate(
                String mql, boolean autoCommit, int expectedRowCount, List<Document> expectedCollection) {
            session.doWork(connection -> {
                connection.setAutoCommit(autoCommit);
                var statement = (MongoStatement) connection.createStatement();
                assertEquals(expectedRowCount, statement.executeUpdate(mql));
                if (!autoCommit) {
                    connection.commit();
                }
                var documents = statement
                        .getMongoDatabase()
                        .getCollection(COLLECTION_NAME)
                        .find();
                var docs = new ArrayList<>();
                documents.forEach(docs::add);
                assertEquals(expectedCollection, docs);
            });
        }
    }
}
