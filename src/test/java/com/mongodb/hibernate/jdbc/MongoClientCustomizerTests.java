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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.ServerAddress;
import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.hibernate.service.MongoClientCustomizer;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.service.spi.ServiceException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MongoClientCustomizerTests {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testMongoClientCustomizerTakeEffect(boolean customizerAppliesConnectionString) {

        // given

        var hostInConnectionString = "my-host";
        var connectionString = "mongodb://" + hostInConnectionString;

        var applicationName = "test_" + UUID.randomUUID();

        MongoClientCustomizer customizer = (builder, cs) -> {
            builder.applicationName(applicationName);

            assertNotNull(cs);
            if (customizerAppliesConnectionString) {
                builder.applyConnectionString(cs);
            }
        };

        // when
        try (var sessionFactory = buildSessionFactory(customizer, connectionString)) {

            // then
            var mongoConnectionProvider = (MongoConnectionProvider) sessionFactory
                    .unwrap(SessionFactoryImplementor.class)
                    .getServiceRegistry()
                    .requireService(ConnectionProvider.class);

            var mongoClient = (MongoClientImpl) mongoConnectionProvider.getMongoClient();
            assertNotNull(mongoClient);

            var hosts = mongoClient.getCluster().getSettings().getHosts().stream()
                    .map(ServerAddress::getHost)
                    .collect(toSet());
            if (customizerAppliesConnectionString) {
                assertEquals(singleton(hostInConnectionString), hosts);
            } else {
                assertNotEquals(singleton(hostInConnectionString), hosts);
            }

            var clusterDescription = mongoClient.getCluster().getClusterId().getDescription();
            assertEquals(applicationName, clusterDescription);
        }
    }

    @Test
    void testMongoClientCustomizerThrowException() {
        assertThrows(ServiceException.class, () -> {
            try (var ignored = buildSessionFactory(
                    (builder, connectionString) -> {
                        throw new NullPointerException();
                    },
                    null)) {}
        });
    }

    private SessionFactory buildSessionFactory(
            MongoClientCustomizer mongoClientCustomizer, @Nullable String connectionString) throws ServiceException {
        var standardServiceRegistryBuilder =
                new StandardServiceRegistryBuilder().addService(MongoClientCustomizer.class, mongoClientCustomizer);
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
