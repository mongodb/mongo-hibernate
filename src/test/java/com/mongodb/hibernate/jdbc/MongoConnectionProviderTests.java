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

import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.event.ClusterClosedEvent;
import com.mongodb.event.ClusterListener;
import com.mongodb.event.ClusterOpeningEvent;
import com.mongodb.hibernate.BuildConfig;
import com.mongodb.hibernate.service.MongoClientCustomizer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MongoConnectionProviderTests {

    @Nested
    class MongoClientCustomizerTests {

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testMongoClientCustomizerTakeEffect(boolean customizerAppliesConnectionString) {

            // given

            var hostInConnectionString = "host";
            var connectionString = "mongodb://" + hostInConnectionString;

            var clusterListener = new TestClusterListener();

            MongoClientCustomizer customizer = (builder, cs) -> {
                builder.applyToClusterSettings(
                        clusterSettingsBuilder -> clusterSettingsBuilder.addClusterListener(clusterListener));
                if (customizerAppliesConnectionString) {
                    builder.applyConnectionString(cs);
                }
            };

            // when
            verifyMongoClient(customizer, connectionString, mongoClient -> {
                var hosts = mongoClient.getClusterDescription().getClusterSettings().getHosts().stream()
                        .map(ServerAddress::getHost)
                        .collect(toSet());

                // then
                if (customizerAppliesConnectionString) {
                    assertEquals(singleton(hostInConnectionString), hosts);
                } else {
                    assertNotEquals(singleton(hostInConnectionString), hosts);
                }
                clusterListener.assertClusterOpening();
            });
            clusterListener.assertClusterClosed();
        }

        @Test
        @SuppressWarnings("try")
        void testMongoClientCustomizerThrowException() {
            assertThrows(NullPointerException.class, () -> {
                try (var ignored = buildSessionFactory(
                        (builder, cs) -> {
                            throw new NullPointerException();
                        },
                        null)) {}
            });
        }

        private static class TestClusterListener implements ClusterListener {
            private static final Duration TIMEOUT = Duration.ofSeconds(5);

            private final CompletableFuture<ClusterOpeningEvent> openingEvent;
            private final CompletableFuture<ClusterClosedEvent> closedEvent;

            TestClusterListener() {
                openingEvent = new CompletableFuture<>();
                closedEvent = new CompletableFuture<>();
            }

            @Override
            public void clusterOpening(ClusterOpeningEvent event) {
                assertTrue(openingEvent.complete(event));
            }

            @Override
            public void clusterClosed(ClusterClosedEvent event) {
                assertTrue(closedEvent.complete(event));
            }

            void assertClusterOpening() {
                assertDone(openingEvent);
            }

            void assertClusterClosed() {
                assertDone(closedEvent);
            }

            private static void assertDone(Future<?> future) {
                try {
                    future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                } catch (TimeoutException e) {
                    fail(e);
                }
            }
        }
    }

    @Test
    void testMongoDriverInformationPopulated() {
        verifyMongoClient(
                null, "mongodb://host/db", MongoConnectionProviderTests.this::verifyMongoDriverInformationPopulated);
    }

    private void verifyMongoDriverInformationPopulated(MongoClient mongoClient) {
        var driverInformation = ((MongoClientImpl) mongoClient).getMongoDriverInformation();
        assertAll(
                () -> assertTrue(driverInformation.getDriverNames().contains(BuildConfig.NAME)),
                () -> assertTrue(driverInformation.getDriverVersions().contains(BuildConfig.VERSION)));
    }

    private void verifyMongoClient(
            MongoClientCustomizer mongoClientCustomizer, String connectionString, Consumer<MongoClient> verifier)
            throws ServiceException {
        try (var sessionFactory = buildSessionFactory(mongoClientCustomizer, connectionString)) {
            var mongoConnectionProvider = (MongoConnectionProvider) sessionFactory
                    .unwrap(SessionFactoryImplementor.class)
                    .getServiceRegistry()
                    .requireService(ConnectionProvider.class);

            var mongoClient = mongoConnectionProvider.getMongoClient();
            verifier.accept(mongoClient);
        }
    }

    private SessionFactory buildSessionFactory(MongoClientCustomizer mongoClientCustomizer, String connectionString) {
        var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder();
        if (mongoClientCustomizer != null) {
            standardServiceRegistryBuilder.addService(MongoClientCustomizer.class, mongoClientCustomizer);
        }
        if (connectionString != null) {
            standardServiceRegistryBuilder.applySetting(JdbcSettings.JAKARTA_JDBC_URL, connectionString);
        }
        return new MetadataSources(standardServiceRegistryBuilder.build())
                .getMetadataBuilder()
                .build()
                .getSessionFactoryBuilder()
                .build();
    }
}
