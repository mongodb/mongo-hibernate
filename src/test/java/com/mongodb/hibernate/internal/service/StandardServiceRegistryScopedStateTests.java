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

package com.mongodb.hibernate.internal.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.DIALECT;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

class StandardServiceRegistryScopedStateTests {
    @Test
    void differentStandardServiceRegistriesHaveDifferentStates() {
        try (var bootstrapServiceRegistry = new BootstrapServiceRegistryBuilder().build()) {
            var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder(bootstrapServiceRegistry);
            try (var standardServiceRegistry1 = standardServiceRegistryBuilder.build();
                    var standardServiceRegistry2 = standardServiceRegistryBuilder.build()) {
                assertNotSame(
                        standardServiceRegistry1.requireService(StandardServiceRegistryScopedState.class),
                        standardServiceRegistry2.requireService(StandardServiceRegistryScopedState.class));
            }
        }
    }

    @Test
    void testMongoDialectNotPluggedIn() {
        var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder();
        standardServiceRegistryBuilder.clearSettings();
        try (var standardServiceRegistry = standardServiceRegistryBuilder.build()) {
            assertThatThrownBy(() -> standardServiceRegistry.requireService(StandardServiceRegistryScopedState.class))
                    .hasRootCauseMessage("com.mongodb.hibernate.dialect.MongoDialect must be plugged in"
                            + ", for example, via the [hibernate.dialect] configuration property");
        }
    }

    @Test
    void testMongoConnectionProviderNotPluggedIn() {
        var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder();
        standardServiceRegistryBuilder.clearSettings();
        try (var standardServiceRegistry = standardServiceRegistryBuilder
                .applySetting(DIALECT, "com.mongodb.hibernate.dialect.MongoDialect")
                .build()) {
            assertThatThrownBy(() -> standardServiceRegistry.requireService(StandardServiceRegistryScopedState.class))
                    .hasRootCauseMessage("com.mongodb.hibernate.jdbc.MongoConnectionProvider must be plugged in"
                            + ", for example, via the [hibernate.connection.provider_class] configuration property");
        }
    }
}
