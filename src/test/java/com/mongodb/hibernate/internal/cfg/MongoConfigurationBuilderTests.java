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

package com.mongodb.hibernate.internal.cfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MongoConfigurationBuilderTests {
    @Test
    void requiresPropertiesThatHaveNoDefaults() {
        var e = assertThrows(NullPointerException.class, () -> new MongoConfigurationBuilder().build());
        assertEquals("databaseName must not be null", e.getMessage());
    }

    @Test
    void defaults() {
        var config = new MongoConfigurationBuilder().databaseName("testDbName").build();
        assertEquals(MongoClientSettings.builder().build(), config.mongoClientSettings());
    }

    @Test
    @DisplayName(
            "defaults with invalid connection string that contains mongodb+srv with port number should throw an exception")
    void defaultsWithInvalidConnectionString() {
        var mongoSrvUrlWithPort = "mongodb+srv://mongo:27017/mongo";
        var e = assertThrows(RuntimeException.class, () -> new MongoConfigurationBuilder(
                        Map.of(JAKARTA_JDBC_URL, mongoSrvUrlWithPort))
                .databaseName("testDbName")
                .build());
        assertThat(e).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(e).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName(
            "overrides defaults with invalid connection string that contains mongodb+srv with port number should throw an exception")
    void overridesDefaultsWithInvalidConnectionString() {
        var mongoSrvUrlWithPort = "mongodb+srv://mongo:27017/mongo";
        var e = assertThrows(RuntimeException.class, () -> new MongoConfigurationBuilder()
                .applyToMongoClientSettings(
                        builder -> builder.applyConnectionString(new ConnectionString(mongoSrvUrlWithPort)))
                .databaseName("testDbName")
                .build());
        assertThat(e).isInstanceOf(IllegalArgumentException.class);
        // unlike #defaultsWithInvalidConnectionString,
        // the exception is not wrapped in a RuntimeException
        assertNull(e.getCause());
    }

    @Test
    void overridesDefaults() {
        var config = new MongoConfigurationBuilder()
                .applyToMongoClientSettings(builder -> builder.applyConnectionString(
                        new ConnectionString("mongodb://host?replicaSet=testReplicaSetName")))
                .databaseName("testDbName")
                .build();
        assertEquals(
                "testReplicaSetName",
                config.mongoClientSettings().getClusterSettings().getRequiredReplicaSetName());
        assertEquals("testDbName", config.databaseName());
    }

    @Test
    void overrides() {
        var config = new MongoConfigurationBuilder(Map.of(JAKARTA_JDBC_URL, "mongodb://host/testDbName"))
                .applyToMongoClientSettings(builder -> builder.applyConnectionString(
                        new ConnectionString("mongodb://host?replicaSet=testReplicaSetName")))
                .databaseName("testDbName2")
                .build();
        assertEquals(
                "testReplicaSetName",
                config.mongoClientSettings().getClusterSettings().getRequiredReplicaSetName());
        assertEquals("testDbName2", config.databaseName());
    }

    @Nested
    class IndividualTests {
        @Test
        void jakartaJdbcUrl() {
            String connectionStringText = "mongodb://host/testDbName?replicaSet=testReplicaSetName";
            assertAll(
                    () -> assertJakartaJdbcUrl("testReplicaSetName", "testDbName", connectionStringText),
                    () -> assertJakartaJdbcUrl(
                            "testReplicaSetName", "testDbName", new ConnectionString(connectionStringText)),
                    () -> assertFailedToParse(JAKARTA_JDBC_URL, "invalid connection string"),
                    () -> assertUnsupportedType(JAKARTA_JDBC_URL, new StringBuilder()));
        }

        private static void assertJakartaJdbcUrl(
                String expectedRequiredReplicaSetName, String expectedDatabaseName, Object propertyValue) {
            var config = new MongoConfigurationBuilder(Map.of(JAKARTA_JDBC_URL, propertyValue)).build();
            assertAll(
                    () -> assertEquals(
                            expectedRequiredReplicaSetName,
                            config.mongoClientSettings().getClusterSettings().getRequiredReplicaSetName()),
                    () -> assertEquals(expectedDatabaseName, config.databaseName()));
        }

        private static void assertFailedToParse(String propertyName, Object propertyValue) {
            var e = assertThrows(
                    RuntimeException.class,
                    () -> new MongoConfigurationBuilder(Map.of(propertyName, propertyValue)).build());
            assertThat(e.getMessage()).matches("Failed to get .* from configuration property .*");
            assertThat(e).hasCauseInstanceOf(RuntimeException.class);
        }

        private static void assertUnsupportedType(String propertyName, Object propertyValue) {
            var e = assertThrows(
                    RuntimeException.class,
                    () -> new MongoConfigurationBuilder(Map.of(propertyName, propertyValue)).build());
            assertThat(e.getMessage()).matches("Type .* must be one of .*");
        }
    }
}
