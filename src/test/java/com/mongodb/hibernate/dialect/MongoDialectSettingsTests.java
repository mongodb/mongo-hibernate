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

package com.mongodb.hibernate.dialect;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.cfg.JdbcSettings.AUTOCOMMIT;
import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MongoDialectSettingsTests {
    @Test
    void requiresPropertiesThatHaveNoDefaults() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> MongoDialectSettings.builder(emptyMap())
                .build());
        assertThat(e.getMessage()).matches("databaseName must not be null");
    }

    @Test
    void defaults() {
        MongoDialectSettings config = MongoDialectSettings.builder(emptyMap())
                .databaseName("testDbName")
                .build();
        assertEquals(MongoClientSettings.builder().build(), config.getMongoClientSettings());
        assertFalse(config.isAutoCommit());
    }

    @Test
    void builderOverridesDefaults() {
        MongoDialectSettings config = MongoDialectSettings.builder(emptyMap())
                .applyToMongoClientSettings(builder -> builder.applyConnectionString(
                        new ConnectionString("mongodb://localhost?replicaSet=testReplicaSetName")))
                .databaseName("testDbName")
                .autoCommit(true)
                .build();
        assertEquals(
                "testReplicaSetName",
                config.getMongoClientSettings().getClusterSettings().getRequiredReplicaSetName());
        assertEquals("testDbName", config.getDatabaseName());
        assertTrue(config.isAutoCommit());
    }

    @Test
    void builderOverrides() {
        MongoDialectSettings config = MongoDialectSettings.builder(
                        Map.of(JAKARTA_JDBC_URL, "mongodb://localhost/testDbName", AUTOCOMMIT, true))
                .applyToMongoClientSettings(builder -> builder.applyConnectionString(
                        new ConnectionString("mongodb://localhost?replicaSet=testReplicaSetName")))
                .databaseName("testDbName2")
                .autoCommit(false)
                .build();
        assertEquals(
                "testReplicaSetName",
                config.getMongoClientSettings().getClusterSettings().getRequiredReplicaSetName());
        assertEquals("testDbName2", config.getDatabaseName());
        assertFalse(config.isAutoCommit());
    }

    @Nested
    class IndividualTests {
        @Test
        void jakartaJdbcUrl() {
            String connectionStringText = "mongodb://localhost/testDbName?replicaSet=testReplicaSetName";
            assertAll(
                    () -> assertJakartaJdbcUrl("testReplicaSetName", "testDbName", connectionStringText),
                    () -> assertJakartaJdbcUrl(
                            "testReplicaSetName", "testDbName", new ConnectionString(connectionStringText)),
                    () -> assertFailedToParse(JAKARTA_JDBC_URL, "invalid connection string"),
                    () -> assertUnsupportedType(JAKARTA_JDBC_URL, new StringBuilder()));
        }

        private static void assertJakartaJdbcUrl(
                String expectedRequiredReplicaSetName, String expectedDatabaseName, Object propertyValue) {
            MongoDialectSettings config = MongoDialectSettings.builder(Map.of(JAKARTA_JDBC_URL, propertyValue))
                    .build();
            assertEquals(
                    expectedRequiredReplicaSetName,
                    config.getMongoClientSettings().getClusterSettings().getRequiredReplicaSetName());
            assertEquals(expectedDatabaseName, config.getDatabaseName());
        }

        @Test
        void autocommit() {
            assertAll(
                    () -> assertAutocommit(true, "true"),
                    () -> assertAutocommit(true, Boolean.TRUE),
                    () -> assertAutocommit(false, "false"),
                    () -> assertAutocommit(false, Boolean.FALSE),
                    () -> assertFailedToParse(AUTOCOMMIT, "yes"),
                    () -> assertUnsupportedType(AUTOCOMMIT, 1));
        }

        private static void assertAutocommit(boolean expectedPropertyValue, Object propertyValue) {
            MongoDialectSettings config = MongoDialectSettings.builder(Map.of(AUTOCOMMIT, propertyValue))
                    .databaseName("testDbName")
                    .build();
            assertEquals(expectedPropertyValue, config.isAutoCommit());
        }

        private static void assertFailedToParse(String propertyName, Object propertyValue) {
            RuntimeException e = assertThrows(
                    RuntimeException.class, () -> MongoDialectSettings.builder(Map.of(propertyName, propertyValue))
                            .build());
            assertThat(e.getMessage()).matches("Failed to get .* from configuration property .*");
        }

        private static void assertUnsupportedType(String propertyName, Object propertyValue) {
            RuntimeException e = assertThrows(
                    RuntimeException.class, () -> MongoDialectSettings.builder(Map.of(propertyName, propertyValue))
                            .build());
            assertThat(e.getMessage()).matches("Type .* must be one of .*");
        }
    }
}
