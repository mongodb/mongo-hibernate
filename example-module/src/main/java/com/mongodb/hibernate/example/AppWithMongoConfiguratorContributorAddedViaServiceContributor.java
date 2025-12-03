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

package com.mongodb.hibernate.example;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.hibernate.cfg.MongoConfigurator;
import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import com.mongodb.hibernate.example.model.Item;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import static com.mongodb.hibernate.example.AppWithMongoConfiguratorContributorAddedDirectly.useSessionFactory;

public final class AppWithMongoConfiguratorContributorAddedViaServiceContributor {
    private AppWithMongoConfiguratorContributorAddedViaServiceContributor() {}

    public static void main(String... args) {
        try (var sessionFactory = new MetadataSources()
                // add metadata sources, for example, by calling `addAnnotatedClasses`
                // ...
                .addAnnotatedClasses(Item.class)
                .getMetadataBuilder(new StandardServiceRegistryBuilder()
                        .applySetting(AvailableSettings.DIALECT, "com.mongodb.hibernate.dialect.MongoDialect")
                        .applySetting(AvailableSettings.CONNECTION_PROVIDER, "com.mongodb.hibernate.jdbc.MongoConnectionProvider")
                        .applySetting(AvailableSettings.STATEMENT_BATCH_SIZE, 2)
                        .build())
                .build()
                .buildSessionFactory()) {
            // use `sessionFactory`
            // ...
            useSessionFactory(sessionFactory);
        }
    }

    public static final class MyMongoConfigurationContributor implements MongoConfigurationContributor {
        @Serial
        private static final long serialVersionUID = 1L;

        private MyMongoConfigurationContributor() {}

        @Override
        public void configure(MongoConfigurator configurator) {
            configurator.applyToMongoClientSettings(mongoClientSettings -> mongoClientSettings
                            .applyToClusterSettings(clusterSettings -> clusterSettings
                                    .hosts(List.of(new ServerAddress("localhost", 27017)))
                                    .mode(ClusterConnectionMode.MULTIPLE))
                            .build())
                    .databaseName("example");
        }

        public static final class ServiceContributor implements org.hibernate.service.spi.ServiceContributor {
            public ServiceContributor() {}

            @Override
            public void contribute(StandardServiceRegistryBuilder serviceRegistryBuilder) {
                serviceRegistryBuilder.addInitiator(new StandardServiceInitiator<MongoConfigurationContributor>() {
                    @Override
                    public Class<MongoConfigurationContributor> getServiceInitiated() {
                        return MongoConfigurationContributor.class;
                    }

                    @Override
                    public MongoConfigurationContributor initiateService(
                            Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
                        return new MyMongoConfigurationContributor();
                    }
                });
            }
        }
    }
}
