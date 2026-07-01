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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.hibernate.cfg.AvailableSettings.DIALECT;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;

import com.mongodb.hibernate.internal.boot.MongoAdditionalMappingContributor;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class NativeBootstrappingTests {
    private static final String COLLECTION_NAME = "items";

    /**
     * Verify that {@link MongoAdditionalMappingContributor} skips its logic when bootstrapping is unrelated to the
     * MongoDB Extension for Hibernate ORM.
     */
    @Test
    void testMongoAdditionalMappingContributorIsSkipped() {
        // clearSettings() prevents hibernate.properties from injecting MongoConnectionProvider
        var builder = new StandardServiceRegistryBuilder();
        builder.clearSettings();
        assertThatNoException().isThrownBy(() -> {
            try (var standardServiceRegistry = builder.applySetting(DIALECT, "org.hibernate.dialect.H2Dialect")
                    .applySetting(JAKARTA_JDBC_URL, "jdbc:h2:mem:")
                    .build()) {
                new MetadataSources()
                        .addAnnotatedClass(ItemWithUnsupportedId.class)
                        .buildMetadata(standardServiceRegistry)
                        .buildSessionFactory()
                        .close();
            }
        });
    }

    @Entity
    @Table(name = COLLECTION_NAME)
    record Item(@Id int id) {}

    @Entity
    @Table(name = COLLECTION_NAME)
    static class ItemWithUnsupportedId {
        @Id
        MultipleColumns id;

        ItemWithUnsupportedId(MultipleColumns id) {
            this.id = id;
        }

        @Embeddable
        record MultipleColumns(int a, int b) {}
    }
}
