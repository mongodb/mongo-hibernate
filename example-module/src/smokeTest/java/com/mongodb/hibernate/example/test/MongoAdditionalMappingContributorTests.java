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

package com.mongodb.hibernate.example.test;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoAdditionalMappingContributorTests {
    @Test
    void testContribute() {
        assertThatThrownBy(() -> new MetadataSources()
                .addAnnotatedClass(Item.class)
                .buildMetadata(new StandardServiceRegistryBuilder()
                        .applySetting(AvailableSettings.DIALECT, "com.mongodb.hibernate.dialect.MongoDialect")
                        .applySetting(AvailableSettings.CONNECTION_PROVIDER, "com.mongodb.hibernate.jdbc.MongoConnectionProvider")
                        .applySetting(AvailableSettings.ALLOW_METADATA_ON_BOOT, false)
                        .applySetting(AvailableSettings.JAKARTA_JDBC_URL, "mongodb://host/db")
                        .build()))
                .hasMessageContaining("does not support primary key spanning multiple columns");
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
