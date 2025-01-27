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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.event.ClusterClosedEvent;
import com.mongodb.event.ClusterListener;
import com.mongodb.event.ClusterOpeningEvent;
import com.mongodb.hibernate.BuildConfig;
import com.mongodb.hibernate.service.MongoClientCustomizer;
import java.util.function.Consumer;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.service.spi.ServiceException;
import org.jspecify.annotations.Nullable;
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

            var hostInConnectionString = customizerAppliesConnectionString ? "my-host" : "localhost";
            var connectionString = "mongodb://" + hostInConnectionString;

            var customizedClusterListener = mock(ClusterListener.class);

            MongoClientCustomizer customizer = (builder, cs) -> {
                builder.applyToClusterSettings(
                        clusterSettingsBuilder -> clusterSettingsBuilder.addClusterListener(customizedClusterListener));
                assertNotNull(cs);
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
                verify(customizedClusterListener).clusterOpening(any(ClusterOpeningEvent.class));
            });
            verify(customizedClusterListener).clusterClosed(any(ClusterClosedEvent.class));
        }

        @Test
        void testMongoClientCustomizerThrowException() {
            assertThrows(NullPointerException.class, () -> {
                try (var ignored = buildSessionFactory(
                        (builder, cs) -> {
                            throw new NullPointerException();
                        },
                        null)) {}
            });
        }
    }

    @Test
    void testMongoDriverInformationPopulated() {
        verifyMongoClient(
                null,
                "mongodb://localhost/db",
                MongoConnectionProviderTests.this::verifyMongoDriverInformationPopulated);
    }

    private void verifyMongoDriverInformationPopulated(MongoClient mongoClient) {
        var driverInformation = ((MongoClientImpl) mongoClient).getMongoDriverInformation();
        assertAll(
                () -> assertTrue(driverInformation.getDriverNames().contains(BuildConfig.NAME)),
                () -> assertTrue(driverInformation.getDriverVersions().contains(BuildConfig.VERSION)));
    }

    private void verifyMongoClient(
            @Nullable MongoClientCustomizer mongoClientCustomizer,
            @Nullable String connectionString,
            Consumer<MongoClient> verifier)
            throws ServiceException {
        try (var sessionFactory = buildSessionFactory(mongoClientCustomizer, connectionString)) {
            var mongoConnectionProvider = (MongoConnectionProvider) sessionFactory
                    .unwrap(SessionFactoryImplementor.class)
                    .getServiceRegistry()
                    .requireService(ConnectionProvider.class);

            var mongoClient = (MongoClientImpl) mongoConnectionProvider.getMongoClient();
            assertNotNull(mongoClient);

            verifier.accept(mongoClient);
        }
    }

    private SessionFactory buildSessionFactory(
            @Nullable MongoClientCustomizer mongoClientCustomizer, @Nullable String connectionString) {
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
