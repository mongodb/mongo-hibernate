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

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.client.internal.MongoClientImpl;
import com.mongodb.hibernate.service.MongoClientCustomizer;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MongoClientCustomizerTests {

    @Test
    void testMongoClientCustomizerTakeEffect() {

        // given
        var applicationName = UUID.randomUUID().toString();

        MongoClientCustomizer customizer = (builder, connectionString) -> builder.applicationName(applicationName);

        // when
        try (var sessionFactory = buildSessionFactory(customizer)) {

            // then
            var mongoConnectionProvider = (MongoConnectionProvider) sessionFactory
                    .unwrap(SessionFactoryImplementor.class)
                    .getServiceRegistry()
                    .requireService(ConnectionProvider.class);

            var mongoClient = mongoConnectionProvider.getMongoClient();
            Assertions.assertNotNull(mongoClient);

            var clusterDescription =
                    ((MongoClientImpl) mongoClient).getCluster().getClusterId().getDescription();
            Assertions.assertEquals(applicationName, clusterDescription);
        }
    }

    @Test
    void testMongoClientCustomizerThrowException() {
        assertThrows(ServiceException.class, () -> {
            try (var ignored = buildSessionFactory((builder, connectionString) -> {
                throw new NullPointerException();
            })) {}
        });
    }

    private SessionFactory buildSessionFactory(MongoClientCustomizer mongoClientCustomizer) throws ServiceException {
        var cfg = new Configuration();
        cfg.getStandardServiceRegistryBuilder().addService(MongoClientCustomizer.class, mongoClientCustomizer);
        return cfg.buildSessionFactory();
    }
}
