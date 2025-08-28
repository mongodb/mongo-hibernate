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
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(MongoExtension.class)
@ParameterizedClass
@ValueSource(booleans = {true, false})
class MongoStatementIntegrationTests {

    @AutoClose
    private static SessionFactory sessionFactory;

    @InjectMongoCollection("books")
    private static MongoCollection<BsonDocument> mongoCollection;

    @AutoClose
    private Session session;

    @Parameter
    private boolean autoCommit;

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
                session,
                """
                {
                    insert: "books",
                    documents: [
                        { _id: 1, publishYear: 1867, title: "War and Peace", author: "Leo Tolstoy" },
                        { _id: 2, publishYear: 1878, author: "Leo Tolstoy", title: "Anna Karenina" },
                        { _id: 3, publishYear: 1866, title: "Crime and Punishment", author: "Fyodor Dostoevsky" }
                    ]
                }""");

        doWorkAwareOfAutoCommit(connection -> {
            try (var stmt = connection.createStatement()) {
                try (var rs = stmt.executeQuery(
                        """
                        {
                            aggregate: "books",
                            pipeline: [
                                { $match: { author: { $eq: "Leo Tolstoy" } } },
                                { $project: { author: 1, _id: 0, publishYear: 1, title: 1 } }
                            ]
                        }""")) {
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
                }
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

            insertTestData(session, INSERT_MQL);

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

            insertTestData(session, INSERT_MQL);

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
            doWorkAwareOfAutoCommit(connection -> {
                try (var stmt = (MongoStatement) connection.createStatement()) {
                    assertEquals(expectedRowCount, stmt.executeUpdate(mql));
                }
            });
            assertThat(mongoCollection.find().sort(Sorts.ascending(ID_FIELD_NAME)))
                    .containsExactlyElementsOf(expectedDocuments);
        }
    }

    static void insertTestData(Session session, String insertMql) {
        session.doWork(connection -> doWithSpecifiedAutoCommit(
                false,
                connection,
                () -> doAndTerminateTransaction(connection, () -> {
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate(insertMql);
                    }
                })));
    }

    private void doWorkAwareOfAutoCommit(Work work) {
        doWorkWithSpecifiedAutoCommit(autoCommit, session, work);
    }

    static void doWorkWithSpecifiedAutoCommit(boolean autoCommit, Session session, Work work) {
        session.doWork(connection -> {
            SqlExecutable executable = () -> work.execute(connection);
            doWithSpecifiedAutoCommit(
                    autoCommit,
                    connection,
                    autoCommit ? executable : () -> doAndTerminateTransaction(connection, executable));
        });
    }

    private static void doWithSpecifiedAutoCommit(boolean autoCommit, Connection connection, SqlExecutable work)
            throws SQLException {
        var originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(autoCommit);
        try {
            work.execute();
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static void doAndTerminateTransaction(Connection connectionNoAutoCommit, SqlExecutable work)
            throws SQLException {
        Throwable primaryException = null;
        try {
            work.execute();
            connectionNoAutoCommit.commit();
        } catch (Throwable e) {
            primaryException = e;
            throw e;
        } finally {
            if (primaryException != null) {
                try {
                    connectionNoAutoCommit.rollback();
                } catch (Throwable suppressedException) {
                    primaryException.addSuppressed(suppressedException);
                }
            }
        }
    }

    private interface SqlExecutable {
        void execute() throws SQLException;
    }
}
