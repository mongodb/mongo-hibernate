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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
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
class MongoStatementIntegrationTests {

    static class MongoStatementIntegrationWithAutoCommitTests extends MongoStatementIntegrationTests {
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
                        { _id: 1, publishYear: 1867, title: "War and Peace", author: "Leo Tolstoy" },
                        { _id: 2, publishYear: 1878, author: "Leo Tolstoy", title: "Anna Karenina" },
                        { _id: 3, publishYear: 1866, title: "Crime and Punishment", author: "Fyodor Dostoevsky" }
                    ]
                }""");

        session.doWork(conn -> {
            conn.setAutoCommit(autoCommit());
            try (var stmt = conn.createStatement()) {
                executeInTransaction(conn, () -> {
                    var rs = stmt.executeQuery(
                            """
                            {
                                aggregate: "books",
                                pipeline: [
                                    { $match: { author: { $eq: "Leo Tolstoy" } } },
                                    { $project: { author: 1, _id: 0, publishYear: 1, title: 1 } }
                                ]
                            }""");

                    assertTrue(rs.next());
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

        @Test
        void testInsert() {
            var expectedDocs = List.of(
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
            assertExecuteUpdate(INSERT_MQL, 3, expectedDocs);
        }

        @Test
        void testUpdate() {

            insertTestData(INSERT_MQL);

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
            var expectedDocs = List.of(
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
            assertExecuteUpdate(updateMql, 2, expectedDocs);
        }

        @Test
        void testDelete() {

            insertTestData(INSERT_MQL);

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
            var expectedDocs = List.of(
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
            assertExecuteUpdate(deleteMql, 1, expectedDocs);
        }

        private void assertExecuteUpdate(
                String mql, int expectedRowCount, List<? extends BsonDocument> expectedDocuments) {
            session.doWork(connection -> {
                connection.setAutoCommit(autoCommit());
                try (var stmt = (MongoStatement) connection.createStatement()) {
                    executeInTransaction(connection, () -> assertEquals(expectedRowCount, stmt.executeUpdate(mql)));
                    assertThat(mongoCollection.find().sort(Sorts.ascending(ID_FIELD_NAME)))
                            .containsExactlyElementsOf(expectedDocuments);
                }
            });
        }
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
