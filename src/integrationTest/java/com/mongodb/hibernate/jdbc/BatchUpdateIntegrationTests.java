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

package com.mongodb.hibernate.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.hibernate.cfg.MongoConfigurator;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import com.mongodb.hibernate.service.spi.MongoConfigurationContributor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {BatchUpdateIntegrationTests.Movie.class})
@ServiceRegistry(
        settings = @Setting(name = AvailableSettings.STATEMENT_BATCH_SIZE, value = "3"),
        services =
                @ServiceRegistry.Service(
                        role = MongoConfigurationContributor.class,
                        impl = BatchUpdateIntegrationTests.TestingMongoConfigurationContributor.class))
class BatchUpdateIntegrationTests implements SessionFactoryScopeAware {

    private static class TestingCommandListener implements CommandListener {
        List<BsonDocument> successfulCommands = new ArrayList<>();
        List<CommandFailedEvent> failedCommandEvents = new ArrayList<>(0);

        @Override
        public void commandStarted(CommandStartedEvent event) {
            successfulCommands.add(event.getCommand().clone());
        }

        @Override
        public void commandFailed(CommandFailedEvent event) {
            failedCommandEvents.add(event);
        }

        void clear() {
            successfulCommands.clear();
            failedCommandEvents.clear();
        }

        List<BsonDocument> getSuccessfulCommands() {
            return Collections.unmodifiableList(successfulCommands);
        }

        List<CommandFailedEvent> getFailedCommandEvents() {
            return Collections.unmodifiableList(failedCommandEvents);
        }
    }

    private static final TestingCommandListener TESTING_COMMAND_LISTENER = new TestingCommandListener();

    public static class TestingMongoConfigurationContributor implements MongoConfigurationContributor {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public void configure(MongoConfigurator configurator) {
            configurator.applyToMongoClientSettings(builder -> builder.addCommandListener(TESTING_COMMAND_LISTENER));
        }
    }

    @AutoClose
    private MongoClient mongoClient;

    private MongoCollection<BsonDocument> collection;

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @BeforeAll
    void beforeAll() {
        var config = new MongoConfigurationBuilder(
                        sessionFactoryScope.getSessionFactory().getProperties())
                .build();
        mongoClient = MongoClients.create(config.mongoClientSettings());
        collection = mongoClient.getDatabase(config.databaseName()).getCollection("movies", BsonDocument.class);
    }

    @BeforeEach
    void beforeEach() {
        collection.drop();
        TESTING_COMMAND_LISTENER.clear();
    }

    @Test
    void batchInsertTest() {
        var movies = new ArrayList<>();
        for (var i = 1; i <= 8; i++) {
            var movie = new Movie();
            movie.id = i;
            movie.title = "title_" + i;
            movies.add(movie);
        }
        sessionFactoryScope.inTransaction(session -> {
            movies.forEach(session::persist);
        });

        assertThat(TESTING_COMMAND_LISTENER.getFailedCommandEvents()).isEmpty();
        assertThat(TESTING_COMMAND_LISTENER.getSuccessfulCommands())
                .satisfiesExactly(
                        command1 -> {
                            assertThat(command1.entrySet()).contains(Map.entry("insert", new BsonString("movies")));
                            assertThat(command1.getArray("documents").getValues())
                                    .containsExactly(
                                            BsonDocument.parse(
                                                    """
                                                    {_id: 1, title: "title_1"}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {_id: 2, title: "title_2"}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {_id: 3, title: "title_3"}
                                                    """));
                        },
                        command2 -> {
                            assertThat(command2.entrySet()).contains(Map.entry("insert", new BsonString("movies")));
                            assertThat(command2.getArray("documents").getValues())
                                    .containsExactly(
                                            BsonDocument.parse(
                                                    """
                                                    {_id: 4, title: "title_4"}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {_id: 5, title: "title_5"}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {_id: 6, title: "title_6"}
                                                    """));
                        },
                        command3 -> {
                            assertThat(command3.entrySet()).contains(Map.entry("insert", new BsonString("movies")));
                            assertThat(command3.getArray("documents").getValues())
                                    .containsExactly(
                                            BsonDocument.parse(
                                                    """
                                                    {_id: 7, title: "title_7"}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {_id: 8, title: "title_8"}
                                                    """));
                        },
                        command4 -> assertThat(command4.getFirstKey()).isEqualTo("commitTransaction"));
    }

    @Test
    void batchDeleteTest() {
        var movies = new ArrayList<>();
        for (var i = 1; i <= 8; i++) {
            var movie = new Movie();
            movie.id = i;
            movie.title = "title_" + i;
            movies.add(movie);
        }
        sessionFactoryScope.inTransaction(session -> {
            movies.forEach(session::persist);
            session.flush();
            TESTING_COMMAND_LISTENER.clear();
            movies.forEach(session::remove);
        });

        assertThat(TESTING_COMMAND_LISTENER.getFailedCommandEvents()).isEmpty();
        assertThat(TESTING_COMMAND_LISTENER.getSuccessfulCommands())
                .satisfiesExactly(
                        command1 -> {
                            assertThat(command1.entrySet()).contains(Map.entry("delete", new BsonString("movies")));
                            assertThat(command1.getArray("deletes").getValues())
                                    .containsExactly(
                                            BsonDocument.parse(
                                                    """
                                                    {q: {_id: {$eq: 1}}, limit: 0}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {q: {_id: {$eq: 2}}, limit: 0}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {q: {_id: {$eq: 3}}, limit: 0}
                                                    """));
                        },
                        command2 -> {
                            assertThat(command2.entrySet()).contains(Map.entry("delete", new BsonString("movies")));
                            assertThat(command2.getArray("deletes").getValues())
                                    .containsExactly(
                                            BsonDocument.parse(
                                                    """
                                                    {q: {_id: {$eq: 4}}, limit: 0}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {q: {_id: {$eq: 5}}, limit: 0}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {q: {_id: {$eq: 6}}, limit: 0}
                                                    """));
                        },
                        command3 -> {
                            assertThat(command3.entrySet()).contains(Map.entry("delete", new BsonString("movies")));
                            assertThat(command3.getArray("deletes").getValues())
                                    .containsExactly(
                                            BsonDocument.parse(
                                                    """
                                                    {q: {_id: {$eq: 7}}, limit: 0}
                                                    """),
                                            BsonDocument.parse(
                                                    """
                                                    {q: {_id: {$eq: 8}}, limit: 0}
                                                    """));
                        },
                        command4 -> assertThat(command4.getFirstKey()).isEqualTo("commitTransaction"));
    }

    // TODO-HIBERNATE-19 https://jira.mongodb.org/browse/HIBERNATE-19
    // add batch update test case

    @Entity(name = "Movie")
    @Table(name = "movies")
    static class Movie {
        @Id
        @Column(name = "_id")
        int id;

        String title;
    }
}
