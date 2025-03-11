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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MongoPreparedStatementIntegrationTests {

    private static final String INIT_INSERT_MQL =
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

    @AutoClose
    private static SessionFactory sessionFactory;

    @AutoClose
    private static MongoClient mongoClient;

    private static MongoCollection<BsonDocument> mongoCollection;

    @AutoClose
    private Session session;

    @BeforeAll
    static void beforeAll() {
        sessionFactory = new Configuration().buildSessionFactory();
        var config = new MongoConfigurationBuilder(sessionFactory.getProperties()).build();
        mongoClient = MongoClients.create(config.mongoClientSettings());
        mongoCollection = mongoClient.getDatabase(config.databaseName()).getCollection("books", BsonDocument.class);
    }

    @BeforeEach
    void beforeEach() {
        session = sessionFactory.openSession();
    }

    @Nested
    class ExecuteUpdateTests {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testUpdate(boolean autoCommit) {

            clearData();
            prepareData();

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
            assertExecuteUpdate(pstmtProvider, autoCommit, 2, expectedDocs);
        }

        private void assertExecuteUpdate(
                Function<Connection, MongoPreparedStatement> pstmtProvider,
                boolean autoCommit,
                int expectedUpdatedRowCount,
                List<? extends BsonDocument> expectedDocuments) {
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
                    var actualDocuments =
                            mongoCollection.find().sort(Sorts.ascending("_id")).into(new ArrayList<>());
                    assertEquals(expectedDocuments, actualDocuments);
                }
            });
        }
    }

    @Nested
    class BatchTests {
        private static final int BATCH_SIZE = 2;

        @AutoClose
        private static SessionFactory batchableSessionFactory;

        @AutoClose
        private Session batchableSession;

        @BeforeAll
        static void beforeAll() {
            batchableSessionFactory = new Configuration()
                    .setProperty(AvailableSettings.STATEMENT_BATCH_SIZE, BATCH_SIZE)
                    .buildSessionFactory();
        }

        @BeforeEach
        void beforeEach() {
            batchableSession = batchableSessionFactory.openSession();
        }

        @Nested
        class InsertTests {

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

            @BeforeEach
            void beforeEach() {
                clearData();
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void test(boolean autoCommit) {
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

                        var expectedDocuments = List.of(
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
                                .sort(Sorts.ascending("_id"))
                                .into(new ArrayList<>());
                        assertEquals(expectedDocuments, realDocuments);
                    }
                });
            }
        }

        @Nested
        class UpdateTests {
            private static final String UPDATE_ONE_MQL =
                    """
                    {
                        update: "books",
                        updates: [
                            {
                                q: { _id: { $eq: { $undefined: true } } },
                                u: { $set: { title: { $undefined: true } } },
                                multi: false
                            }
                        ]
                    }""";

            private static final String UPDATE_MANY_MQL =
                    """
                    {
                        update: "books",
                        updates: [
                            {
                                q: { author: { $eq: { $undefined: true } } },
                                u: { $push: { tags: { $undefined: true } } },
                                multi: true
                            }
                        ]
                    }""";

            @BeforeEach
            void beforeEach() {
                clearData();
                prepareData();
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void testUpdateOne(boolean autoCommit) {
                batchableSession.doWork(connection -> {
                    connection.setAutoCommit(autoCommit);
                    try (var pstmt = connection.prepareStatement(UPDATE_ONE_MQL)) {
                        try {
                            pstmt.setInt(1, 1);
                            pstmt.setString(2, "Insurrection");
                            pstmt.addBatch();

                            pstmt.setInt(1, 2);
                            pstmt.setString(2, "Hadji Murat");
                            pstmt.addBatch();

                            pstmt.executeBatch();

                            pstmt.setInt(1, 3);
                            pstmt.setString(2, "The Brothers Karamazov");
                            pstmt.addBatch();

                            pstmt.executeBatch();

                        } finally {
                            if (!autoCommit) {
                                connection.commit();
                            }
                            pstmt.clearBatch();
                        }

                        var expectedDocuments = List.of(
                                BsonDocument.parse(
                                        """
                                        {
                                            _id: 1,
                                            title: "Insurrection",
                                            author: "Leo Tolstoy",
                                            outOfStock: false,
                                            tags: [ "classic", "tolstoy" ]
                                        }"""),
                                BsonDocument.parse(
                                        """
                                        {
                                            _id: 2,
                                            title: "Hadji Murat",
                                            author: "Leo Tolstoy",
                                            outOfStock: false,
                                            tags: [ "classic", "tolstoy" ]
                                        }"""),
                                BsonDocument.parse(
                                        """
                                        {
                                            _id: 3,
                                           title: "The Brothers Karamazov",
                                           author: "Fyodor Dostoevsky",
                                           outOfStock: false,
                                           tags: [ "classic", "dostoevsky", "literature" ]
                                        }"""));

                        var realDocuments = ((MongoPreparedStatement) pstmt)
                                .getMongoDatabase()
                                .getCollection("books", BsonDocument.class)
                                .find()
                                .sort(Sorts.ascending("_id"))
                                .into(new ArrayList<>());
                        assertEquals(expectedDocuments, realDocuments);
                    }
                });
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void testUpdateMany(boolean autoCommit) {
                batchableSession.doWork(connection -> {
                    connection.setAutoCommit(autoCommit);
                    try (var pstmt = connection.prepareStatement(UPDATE_MANY_MQL)) {
                        try {
                            pstmt.setString(1, "Leo Tolstoy");
                            pstmt.setString(2, "russian");
                            pstmt.addBatch();

                            pstmt.setString(1, "Fyodor Dostoevsky");
                            pstmt.setString(2, "russian");
                            pstmt.addBatch();

                            pstmt.executeBatch();

                            pstmt.setString(1, "Leo Tolstoy");
                            pstmt.setString(2, "literature");
                            pstmt.addBatch();

                            pstmt.executeBatch();

                        } finally {
                            if (!autoCommit) {
                                connection.commit();
                            }
                            pstmt.clearBatch();
                        }

                        var expectedDocuments = List.of(
                                BsonDocument.parse(
                                        """
                                        {
                                            _id: 1,
                                            title: "War and Peace",
                                            author: "Leo Tolstoy",
                                            outOfStock: false,
                                            tags: [ "classic", "tolstoy", "russian", "literature" ]
                                        }"""),
                                BsonDocument.parse(
                                        """
                                        {
                                            _id: 2,
                                            title: "Anna Karenina",
                                            author: "Leo Tolstoy",
                                            outOfStock: false,
                                            tags: [ "classic", "tolstoy", "russian", "literature" ]
                                        }"""),
                                BsonDocument.parse(
                                        """
                                        {
                                            _id: 3,
                                           title: "Crime and Punishment",
                                           author: "Fyodor Dostoevsky",
                                           outOfStock: false,
                                           tags: [ "classic", "dostoevsky", "literature", "russian" ]
                                        }"""));

                        var realDocuments = ((MongoPreparedStatement) pstmt)
                                .getMongoDatabase()
                                .getCollection("books", BsonDocument.class)
                                .find()
                                .sort(Sorts.ascending("_id"))
                                .into(new ArrayList<>());
                        assertEquals(expectedDocuments, realDocuments);
                    }
                });
            }
        }

        @Nested
        class DeleteTests {
            private static final String DELETE_ONE_MQL =
                    """
                    {
                        delete: "books",
                        deletes: [
                            {
                                q: { _id: { $eq: { $undefined: true } } },
                                limit: 1
                            }
                        ]
                    }""";

            private static final String DELETE_MANY_MQL =
                    """
                    {
                        delete: "books",
                        deletes: [
                            {
                                q: { author: { $eq: { $undefined: true } } },
                                limit: 0
                            }
                        ]
                    }""";

            @BeforeEach
            void beforeEach() {
                clearData();
                prepareData();
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void testDeleteOne(boolean autoCommit) {
                batchableSession.doWork(connection -> {
                    connection.setAutoCommit(autoCommit);
                    try (var pstmt = connection.prepareStatement(DELETE_ONE_MQL)) {
                        try {
                            pstmt.setInt(1, 1);
                            pstmt.addBatch();

                            pstmt.setInt(1, 2);
                            pstmt.addBatch();

                            pstmt.executeBatch();

                            pstmt.setInt(1, 3);
                            pstmt.addBatch();

                            pstmt.executeBatch();

                        } finally {
                            if (!autoCommit) {
                                connection.commit();
                            }
                            pstmt.clearBatch();
                        }

                        var realDocuments = ((MongoPreparedStatement) pstmt)
                                .getMongoDatabase()
                                .getCollection("books", BsonDocument.class)
                                .find()
                                .sort(Sorts.ascending("_id"))
                                .into(new ArrayList<>());
                        assertEquals(Collections.emptyList(), realDocuments);
                    }
                });
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void testDeleteMany(boolean autoCommit) {
                batchableSession.doWork(connection -> {
                    connection.setAutoCommit(autoCommit);
                    try (var pstmt = connection.prepareStatement(DELETE_MANY_MQL)) {
                        try {
                            pstmt.setString(1, "Leo Tolstoy");
                            pstmt.addBatch();

                            pstmt.setString(1, "Fyodor Dostoevsky");
                            pstmt.addBatch();

                            pstmt.executeBatch();

                        } finally {
                            if (!autoCommit) {
                                connection.commit();
                            }
                            pstmt.clearBatch();
                        }

                        var realDocuments = ((MongoPreparedStatement) pstmt)
                                .getMongoDatabase()
                                .getCollection("books", BsonDocument.class)
                                .find()
                                .sort(Sorts.ascending("_id"))
                                .into(new ArrayList<>());
                        assertEquals(Collections.emptyList(), realDocuments);
                    }
                });
            }
        }
    }

    private void prepareData() {
        session.doWork(connection -> {
            connection.setAutoCommit(true);
            var statement = connection.createStatement();
            statement.executeUpdate(INIT_INSERT_MQL);
        });
    }

    private void clearData() {
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
}
