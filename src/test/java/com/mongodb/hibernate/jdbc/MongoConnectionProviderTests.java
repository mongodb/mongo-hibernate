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
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import com.mongodb.hibernate.service.MongoDialectConfigurator;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.service.Service;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.ServiceBinding;
import org.hibernate.service.spi.ServiceException;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MongoConnectionProviderTests {

    @Nested
    class MongoDialectConfiguratorTests {

        @Test
        void testMongoClientCustomizerTakeEffect() {

            // given

            var hostInConnectionString = "host";
            var connectionString = "mongodb://" + hostInConnectionString + "/db";

            var clusterListener = new TestClusterListener();

            MongoDialectConfigurator configurator =
                    dialectSettingsBuilder -> dialectSettingsBuilder.applyToMongoClientSettings(clientSettingsBuilder ->
                            clientSettingsBuilder.applyToClusterSettings(clusterSettingsBuilder ->
                                    clusterSettingsBuilder.addClusterListener(clusterListener)));

            // when
            verifyMongoClient(configurator, connectionString, mongoClient -> {
                var hosts = mongoClient.getClusterDescription().getClusterSettings().getHosts().stream()
                        .map(ServerAddress::getHost)
                        .collect(toSet());

                // then
                assertEquals(singleton(hostInConnectionString), hosts);
                clusterListener.assertClusterOpening();
            });
            clusterListener.assertClusterClosed();
        }

        @Test
        void testMongoClientCustomizerThrowException() {
            RuntimeException e = new RuntimeException();
            HibernateException actual = assertThrows(HibernateException.class, () -> {
                try (var ignored = buildSessionFactory(
                        dialectSettingsBuilder -> {
                            throw e;
                        },
                        null)) {}
            });
            assertSame(e, actual.getCause());
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
    void defaultAutoCommit() throws SQLException {
        MongoConnectionProvider connectionProvider = new MongoConnectionProvider();
        connectionProvider.injectServices(NoopServiceRegistryImplementor.INSTANCE);
        connectionProvider.configure(Map.of(JAKARTA_JDBC_URL, "mongodb://host/db"));
        try (Connection connection = connectionProvider.getConnection()) {
            assertTrue(connection.getAutoCommit());
        } finally {
            connectionProvider.stop();
        }
    }

    @Test
    void testMongoDriverInformationPopulated() {
        verifyMongoClient(
                null,
                "mongodb://host/db",
                MongoConnectionProviderTests.this::verifyMongoDriverInformationPopulated);
    }

    private void verifyMongoDriverInformationPopulated(MongoClient mongoClient) {
        var driverInformation = ((MongoClientImpl) mongoClient).getMongoDriverInformation();
        assertAll(
                () -> assertTrue(driverInformation.getDriverNames().contains(BuildConfig.NAME)),
                () -> assertTrue(driverInformation.getDriverVersions().contains(BuildConfig.VERSION)));
    }

    private void verifyMongoClient(
            MongoDialectConfigurator mongoDialectConfigurator, String connectionString, Consumer<MongoClient> verifier)
            throws ServiceException {
        try (var sessionFactory = buildSessionFactory(mongoDialectConfigurator, connectionString)) {
            var mongoConnectionProvider = (MongoConnectionProvider) sessionFactory
                    .unwrap(SessionFactoryImplementor.class)
                    .getServiceRegistry()
                    .requireService(ConnectionProvider.class);

            var mongoClient = mongoConnectionProvider.getMongoClient();
            verifier.accept(mongoClient);
        }
    }

    private SessionFactory buildSessionFactory(
            MongoDialectConfigurator mongoDialectConfigurator, String connectionString) {
        var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder();
        if (mongoDialectConfigurator != null) {
            standardServiceRegistryBuilder.addService(MongoDialectConfigurator.class, mongoDialectConfigurator);
        }
        if (connectionString != null) {
            standardServiceRegistryBuilder.applySetting(JAKARTA_JDBC_URL, connectionString);
        }
        return new MetadataSources(standardServiceRegistryBuilder.build())
                .getMetadataBuilder()
                .build()
                .getSessionFactoryBuilder()
                .build();
    }

    private static final class NoopServiceRegistryImplementor implements ServiceRegistryImplementor {
        static final NoopServiceRegistryImplementor INSTANCE = new NoopServiceRegistryImplementor();

        private NoopServiceRegistryImplementor() {}

        @Override
        public <R extends Service> ServiceBinding<R> locateServiceBinding(Class<R> serviceRole) {
            return null;
        }

        @Override
        public void destroy() {}

        @Override
        public void registerChild(ServiceRegistryImplementor child) {}

        @Override
        public void deRegisterChild(ServiceRegistryImplementor child) {}

        @Override
        public <T extends Service> T fromRegistryOrChildren(Class<T> serviceRole) {
            return null;
        }

        @Override
        public ServiceRegistry getParentServiceRegistry() {
            return null;
        }

        @Override
        public <R extends Service> R getService(Class<R> serviceRole) {
            return null;
        }
    }
}
