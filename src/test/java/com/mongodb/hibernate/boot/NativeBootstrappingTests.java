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

package com.mongodb.hibernate.boot;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.ALLOW_METADATA_ON_BOOT;
import static org.hibernate.cfg.AvailableSettings.DIALECT;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;

import com.mongodb.hibernate.internal.boot.MongoAdditionalMappingContributor;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.InstantiationException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl;
import org.junit.jupiter.api.Test;

class NativeBootstrappingTests {
    @Test
    void testMongoDialectNotPluggedIn() {
        try (var standardServiceRegistry = new StandardServiceRegistryBuilder()
                .applySetting(DIALECT, "org.hibernate.dialect.PostgreSQLDialect")
                .build()) {
            assertThatThrownBy(() -> new MetadataSources()
                            .buildMetadata(standardServiceRegistry)
                            .buildSessionFactory()
                            .close())
                    .hasRootCauseMessage("com.mongodb.hibernate.dialect.MongoDialect must be plugged in"
                            + ", for example, via the [hibernate.dialect] configuration property");
        }
    }

    /**
     * Verify that {@link MongoAdditionalMappingContributor} skips its logic when bootstrapping is unrelated to the
     * MongoDB Extension for Hibernate ORM.
     */
    @Test
    void testMongoAdditionalMappingContributorIsSkipped() {
        var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder();
        standardServiceRegistryBuilder.clearSettings();
        try (var sessionFactory = new MetadataSources()
                .addAnnotatedClass(Item.class)
                .buildMetadata(standardServiceRegistryBuilder
                        .applySetting(DIALECT, "org.hibernate.dialect.PostgreSQLDialect")
                        .applySetting(JAKARTA_JDBC_URL, "jdbc:postgresql://host/")
                        .applySetting(ALLOW_METADATA_ON_BOOT, false)
                        .applySetting(DriverManagerConnectionProviderImpl.INITIAL_SIZE, 0)
                        .build())
                .buildSessionFactory()) {
            assertThatThrownBy(() -> sessionFactory.inSession(session -> session.persist(new Item(null))))
                    .hasMessageNotContaining("does not support primary key spanning multiple columns")
                    .isInstanceOf(InstantiationException.class)
                    .hasMessageMatching("Could not instantiate entity .* due to: null");
        }
    }

    @Entity
    @Table(name = Item.COLLECTION_NAME)
    static class Item {
        static final String COLLECTION_NAME = "items";

        @Id
        MultipleColumns id;

        Item(MultipleColumns id) {
            this.id = id;
        }

        @Embeddable
        record MultipleColumns(int a, int b) {}
    }
}
