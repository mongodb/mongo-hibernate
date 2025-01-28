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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
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
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Nested
    class ExecuteQueryTests {
        @BeforeEach
        void setUp() {
            session.doWork(conn -> {
                var stmt = conn.createStatement();
                stmt.executeUpdate(
                        """
                            {
                                delete: "books",
                                deletes: [
                                    { q: {}, limit: 0 }
                                ]
                            }""");
                stmt.executeUpdate(
                        """
                           {
                                insert: "books",
                                documents: [
                                   {
                                       _id: 1,
                                       title: "War and Peace",
                                       author: "Leo Tolstoy",
                                       publishYear: 1867
                                   },
                                   {
                                       _id: 2,
                                       title: "Anna Karenina",
                                       author: "Leo Tolstoy",
                                       publishYear: 1878
                                   },
                                   {
                                       _id: 3,
                                       title: "Crime and Punishment",
                                       author: "Fyodor Dostoevsky",
                                       publishYear: 1866
                                   }
                               ]
                           }""");
            });
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testQuery(boolean autoCommit) throws SQLException {
            ResultSet rs = session.doReturningWork(conn -> {
                conn.setAutoCommit(autoCommit);
                try (PreparedStatement pstmt = conn.prepareStatement(
                        """
                            {
                                aggregate: "books",
                                pipeline: [
                                    { $match: { author: { $eq: { $undefined: true } } } },
                                    { $project: { author: 1, _id: 0, publishYear: 1, title: 1 } }
                                ]
                            }""")) {

                    pstmt.setString(1, "Leo Tolstoy");
                    try {
                        return pstmt.executeQuery();
                    } finally {
                        if (!autoCommit) {
                            conn.commit();
                        }
                    }
                }
            });
            assertTrue(rs.next());
            var metadata = rs.getMetaData();
            assertAll(
                    () -> assertEquals(3, metadata.getColumnCount()),
                    () -> assertEquals("author", metadata.getColumnLabel(1)),
                    () -> assertEquals("publishYear", metadata.getColumnLabel(2)),
                    () -> assertEquals("title", metadata.getColumnLabel(3)));
            assertEquals(3, metadata.getColumnCount());
            assertAll(
                    () -> assertEquals("Leo Tolstoy", rs.getString(1)),
                    () -> assertEquals(1867, rs.getInt(2)),
                    () -> assertEquals("War and Peace", rs.getString(3)));
            assertTrue(rs.next());
            assertAll(
                    () -> assertEquals("Leo Tolstoy", rs.getString(1)),
                    () -> assertEquals(1878, rs.getInt(2)),
                    () -> assertEquals("Anna Karenina", rs.getString(3)));
            assertFalse(rs.next());
        }
    }

    @Nested
    class ExecuteUpdateTests {

        @BeforeEach
        void setUp() {
            session.doWork(
                    conn -> conn.createStatement()
                            .executeUpdate(
                                    """
                                {
                                    delete: "books",
                                    deletes: [
                                        { q: {}, limit: 0 }
                                    ]
                                }"""));
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
}
