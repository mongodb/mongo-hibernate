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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@NullUnmarked
class MongoPreparedStatementIntegrationTests {
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

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Nested
    class ExecuteUpdateTests {

        private static final String INSERT_MQL =
                """
                {
                    insert: "books",
                    documents: [
                        {
                            _id: 1,
                            title: "War and Peace",
                            author: "Leo Tolstoy",
                            outOfStock: false,
                            tags: [ "classic", "tolstoy" ]
                        },
                        {
                            _id: 2,
                            title: "Anna Karenina",
                            author: "Leo Tolstoy",
                            outOfStock: false,
                            tags: [ "classic", "tolstoy" ]
                        },
                        {
                            _id: 3,
                            title: "Crime and Punishment",
                            author: "Fyodor Dostoevsky",
                            outOfStock: false,
                            tags: [ "classic", "Dostoevsky", "literature" ]
                        }
                    ]
                }""";

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testUpdate(boolean autoCommit) {
            // given
            prepareData();

            // when && then
            var expectedDocs = Set.of(
                    BsonDocument.parse(
                            """
                                {
                                    _id: 1,
                                    title: "War and Peace",
                                    author: "Leo Tolstoy",
                                    outOfStock: true,
                                    tags: [ "classic", "tolstoy", "literature" ]
                                }"""),
                    BsonDocument.parse(
                            """
                                {
                                    _id: 2,
                                    title: "Anna Karenina",
                                    author: "Leo Tolstoy",
                                    outOfStock: true,
                                    tags: [ "classic", "tolstoy", "literature" ]
                                }"""),
                    BsonDocument.parse(
                            """
                               {
                                   _id: 3,
                                   title: "Crime and Punishment",
                                   author: "Fyodor Dostoevsky",
                                   outOfStock: false,
                                   tags: [ "classic", "Dostoevsky", "literature" ]
                               }"""));
            Function<Connection, MongoPreparedStatement> pstmtProvider = connection -> {
                try {
                    var pstmt = (MongoPreparedStatement)
                            connection.prepareStatement(
                                    """
                                        {
                                            update: "books",
                                            updates: [
                                                {
                                                    q: { author: { $undefined: true } },
                                                    u: {
                                                        $set: {
                                                            outOfStock: { $undefined: true }
                                                        },
                                                        $push: { tags: { $undefined: true } }
                                                    },
                                                    multi: true
                                                }
                                            ]
                                        }""");
                    pstmt.setString(1, "Leo Tolstoy");
                    pstmt.setBoolean(2, true);
                    pstmt.setString(3, "literature");
                    return pstmt;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            };
            assertExecuteUpdate(pstmtProvider, autoCommit, 2, expectedDocs);
        }

        private void prepareData() {
            session.doWork(connection -> {
                connection.setAutoCommit(true);
                var statement = connection.createStatement();
                statement.executeUpdate(INSERT_MQL);
            });
        }

        private void assertExecuteUpdate(
                Function<Connection, MongoPreparedStatement> pstmtProvider,
                boolean autoCommit,
                int expectedUpdatedRowCount,
                Set<? extends BsonDocument> expectedDocuments) {
            session.doWork(connection -> {
                connection.setAutoCommit(autoCommit);
                try (var pstmt = pstmtProvider.apply(connection)) {
                    try {
                        assertEquals(expectedUpdatedRowCount, pstmt.executeUpdate());
                    } finally {
                        if (!autoCommit) {
                            connection.commit();
                        }
                    }
                    var realDocuments = pstmt.getMongoDatabase()
                            .getCollection("books", BsonDocument.class)
                            .find()
                            .into(new HashSet<>());
                    assertEquals(expectedDocuments, realDocuments);
                }
            });
        }
    }

    @Nested
    class BatchTests {
        private static final int BATCH_SIZE = 2;

        private static SessionFactory batchableSessionFactory;

        private Session batchableSession;

        private static final String MQL =
                """
                {
                    insert: "books",
                    documents: [
                        {
                            _id: { $undefined: true },
                            title: { $undefined: true }
                        }
                    ]
                }""";

        @BeforeAll
        static void beforeAll() {
            batchableSessionFactory = new Configuration()
                    .setProperty(AvailableSettings.STATEMENT_BATCH_SIZE, BATCH_SIZE)
                    .buildSessionFactory();
        }

        @AfterAll
        static void afterAll() {
            if (batchableSessionFactory != null) {
                batchableSessionFactory.close();
            }
        }

        @BeforeEach
        void setUp() {
            batchableSession = batchableSessionFactory.openSession();
        }

        @AfterEach
        void tearDown() {
            if (batchableSession != null) {
                batchableSession.close();
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testExecuteBatch(boolean autoCommit) {
            batchableSession.doWork(connection -> {
                connection.setAutoCommit(autoCommit);
                try (var pstmt = connection.prepareStatement(MQL)) {
                    try {
                        pstmt.setInt(1, 1);
                        pstmt.setString(2, "War and Peace");
                        pstmt.addBatch();

                        pstmt.setInt(1, 2);
                        pstmt.setString(2, "Anna Karenina");
                        pstmt.addBatch();

                        pstmt.executeBatch();

                        pstmt.setInt(1, 3);
                        pstmt.setString(2, "Crime and Punishment");
                        pstmt.addBatch();

                        pstmt.setInt(1, 4);
                        pstmt.setString(2, "Notes from Underground");
                        pstmt.addBatch();

                        pstmt.executeBatch();

                        pstmt.setInt(1, 5);
                        pstmt.setString(2, "Fathers and Sons");

                        pstmt.addBatch();

                        pstmt.executeBatch();
                    } finally {
                        if (!autoCommit) {
                            connection.commit();
                        }
                        pstmt.clearBatch();
                    }

                    var expectedDocuments = Set.of(
                            BsonDocument.parse(
                                    """
                                        {
                                            _id: 1,
                                            title: "War and Peace"
                                        }"""),
                            BsonDocument.parse(
                                    """
                                        {
                                            _id: 2,
                                            title: "Anna Karenina"
                                        }"""),
                            BsonDocument.parse(
                                    """
                                        {
                                            _id: 3,
                                            title: "Crime and Punishment"
                                        }"""),
                            BsonDocument.parse(
                                    """
                                        {
                                            _id: 4,
                                            title: "Notes from Underground"
                                        }"""),
                            BsonDocument.parse(
                                    """
                                        {
                                            _id: 5,
                                            title: "Fathers and Sons"
                                        }"""));

                    var realDocuments = ((MongoPreparedStatement) pstmt)
                            .getMongoDatabase()
                            .getCollection("books", BsonDocument.class)
                            .find()
                            .into(new HashSet<>());
                    assertEquals(expectedDocuments, realDocuments);
                }
            });
        }
    }
}
