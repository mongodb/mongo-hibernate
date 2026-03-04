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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MongoConfigurationBuilderTests {
    @Test
    void requiresPropertiesThatHaveNoDefaults() {
        assertThatThrownBy(() -> new MongoConfigurationBuilder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("databaseName must not be null");
    }

    @Test
    void defaults() {
        var config = new MongoConfigurationBuilder().databaseName("testDbName").build();
        assertEquals(MongoClientSettings.builder().build(), config.mongoClientSettings());
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
                    () -> assertFailedToParse(JAKARTA_JDBC_URL, ""),
                    () -> assertUnsupportedType(JAKARTA_JDBC_URL, new StringBuilder()));
        }

        @Test
        void applyToMongoClientSettingsPropagatesException() {
            var exception = new RuntimeException();
            assertThatThrownBy(() -> new MongoConfigurationBuilder()
                            .applyToMongoClientSettings(builder -> {
                                throw exception;
                            }))
                    .isEqualTo(exception);
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
            assertThatThrownBy(() -> new MongoConfigurationBuilder(Map.of(propertyName, propertyValue)).build())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageMatching("Failed to get .* from configuration property .*")
                    .hasCauseInstanceOf(RuntimeException.class);
        }

        private static void assertUnsupportedType(String propertyName, Object propertyValue) {
            assertThatThrownBy(() -> new MongoConfigurationBuilder(Map.of(propertyName, propertyValue)).build())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageMatching("Type .* must be one of .*");
        }
    }
}
