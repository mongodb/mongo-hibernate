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

import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;

@ExtendWith(MongoExtension.class)
class MongoPreparedStatementIntegrationTests {

    static class MongoPreparedStatementIntegrationWithAutoCommitTests extends MongoPreparedStatementIntegrationTests {
        @Override
        boolean autoCommit() {
            return true;
        }

        void executeInTransaction(Connection connection, Executable executable) {
            try {
                executable.execute();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    boolean autoCommit() {
        return false;
    }

    void executeInTransaction(Connection connection, Executable executable) throws SQLException {
        try {
            executable.execute();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            connection.commit();
        }
    }

    @AutoClose
    private static SessionFactory sessionFactory;

    @InjectMongoCollection("books")
    private static MongoCollection<BsonDocument> mongoCollection;

    @AutoClose
    private Session session;

    @BeforeAll
    static void beforeAll() {
        sessionFactory = new Configuration().buildSessionFactory();
    }

    @BeforeEach
    void beforeEach() {
        session = sessionFactory.openSession();
    }

    @Test
    void testExecuteQuery() {

        insertTestData(
                """
                {
                     insert: "books",
                     documents: [
                        { _id: 1, publishYear: 1867, title: "War and Peace", author: "Leo Tolstoy", comment: "reference only" },
                        { _id: 2, publishYear: 1878, title: "Anna Karenina", author: "Leo Tolstoy",  vintage: true},
                        { _id: 3, publishYear: 1866, author: "Fyodor Dostoevsky", title: "Crime and Punishment", vintage: false },
                    ]
                }""");

        session.doWork(conn -> {
            conn.setAutoCommit(autoCommit());
            try (var pstmt = conn.prepareStatement(
                    """
                    {
                        aggregate: "books",
                        pipeline: [
                            { $match: { author: { $eq: { $undefined: true } } } },
                            { $project: { author: 1, _id: 0, vintage: 1, publishYear: 1, comment: 1, title: 1 } }
                        ]
                    }""")) {

                pstmt.setString(1, "Leo Tolstoy");

                executeInTransaction(conn, () -> {
                    var rs = pstmt.executeQuery();

                    assertTrue(rs.next());
                    assertAll(
                            () -> assertEquals("Leo Tolstoy", rs.getString(1)),
                            () -> assertFalse(rs.getBoolean(2)),
                            () -> assertEquals(1867, rs.getInt(3)),
                            () -> assertEquals("reference only", rs.getString(4)),
                            () -> assertEquals("War and Peace", rs.getString(5)));
                    assertTrue(rs.next());
                    assertAll(
                            () -> assertEquals("Leo Tolstoy", rs.getString(1)),
                            () -> assertTrue(rs.getBoolean(2)),
                            () -> assertEquals(1878, rs.getInt(3)),
                            () -> assertNull(rs.getString(4)),
                            () -> assertEquals("Anna Karenina", rs.getString(5)));
                    assertFalse(rs.next());
                });
            }
        });
    }

    @Test
    void testPreparedStatementAndResultSetRoundTrip() {

        var random = new Random();

        boolean booleanValue = random.nextBoolean();
        double doubleValue = random.nextDouble();
        int intValue = random.nextInt();
        long longValue = random.nextLong();

        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        String stringValue = new String(bytes);

        BigDecimal bigDecimalValue = new BigDecimal(random.nextInt());

        session.doWork(conn -> {
            conn.setAutoCommit(autoCommit());
            try (var pstmt = conn.prepareStatement(
                    """
                    {
                        insert: "books",
                        documents: [
                            {
                                _id: 1,
                                booleanField: { $undefined: true },
                                doubleField: { $undefined: true },
                                intField: { $undefined: true },
                                longField: { $undefined: true },
                                stringField: { $undefined: true },
                                bigDecimalField: { $undefined: true },
                                bytesField: { $undefined: true }
                            }
                        ]
                    }""")) {

                pstmt.setBoolean(1, booleanValue);
                pstmt.setDouble(2, doubleValue);
                pstmt.setInt(3, intValue);
                pstmt.setLong(4, longValue);
                pstmt.setString(5, stringValue);
                pstmt.setBigDecimal(6, bigDecimalValue);
                pstmt.setBytes(7, bytes);

                executeInTransaction(conn, pstmt::executeUpdate);
            }
        });

        session.doWork(conn -> {
            conn.setAutoCommit(autoCommit());
            try (var pstmt = conn.prepareStatement(
                    """
                    {
                        aggregate: "books",
                        pipeline: [
                            { $match: { _id: { $eq: { $undefined: true } } } },
                            { $project:
                                {
                                    _id: 0,
                                    booleanField: 1,
                                    doubleField: 1,
                                    intField: 1,
                                    longField: 1,
                                    stringField: 1,
                                    bigDecimalField: 1,
                                    bytesField: 1
                                }
                            }
                        ]
                    }""")) {

                pstmt.setInt(1, 1);
                executeInTransaction(conn, () -> {
                    var rs = pstmt.executeQuery();

                    assertTrue(rs.next());
                    assertAll(
                            () -> assertEquals(booleanValue, rs.getBoolean(1)),
                            () -> assertEquals(doubleValue, rs.getDouble(2)),
                            () -> assertEquals(intValue, rs.getInt(3)),
                            () -> assertEquals(longValue, rs.getLong(4)),
                            () -> assertEquals(stringValue, rs.getString(5)),
                            () -> assertEquals(bigDecimalValue, rs.getBigDecimal(6)),
                            () -> assertArrayEquals(bytes, rs.getBytes(7)));
                    assertFalse(rs.next());
                });
            }
        });
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
                            tags: [ "classic", "dostoevsky", "literature" ]
                        }
                    ]
                }""";

        @Test
        void testUpdate() {

            insertTestData(INSERT_MQL);

            var expectedDocs = List.of(
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
                                tags: [ "classic", "dostoevsky", "literature" ]
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
            assertExecuteUpdate(pstmtProvider, 2, expectedDocs);
        }

        private void assertExecuteUpdate(
                Function<Connection, MongoPreparedStatement> pstmtProvider,
                int expectedUpdatedRowCount,
                List<? extends BsonDocument> expectedDocuments) {
            session.doWork(connection -> {
                connection.setAutoCommit(autoCommit());
                try (var pstmt = pstmtProvider.apply(connection)) {
                    executeInTransaction(
                            connection, () -> assertEquals(expectedUpdatedRowCount, pstmt.executeUpdate()));
                    assertThat(mongoCollection.find().sort(Sorts.ascending(ID_FIELD_NAME)))
                            .containsExactlyElementsOf(expectedDocuments);
                }
            });
        }
    }

    private void deleteCollection() {
        session.doWork(conn -> {
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.executeUpdate(
                        """
                        {
                            delete: "books",
                            deletes: [
                                { q: {}, limit: 0 }
                            ]
                        }""");
            } finally {
                conn.commit();
            }
        });
    }

    private void insertTestData(String insertMql) {
        session.doWork(connection -> {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(insertMql);
            } finally {
                connection.commit();
            }
        });
    }
}
