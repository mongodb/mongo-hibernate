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

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DIALECT_SHORT_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.CONNECTION_PROVIDER;
import static org.hibernate.cfg.AvailableSettings.DIALECT;
import static org.hibernate.cfg.AvailableSettings.DIALECT_NATIVE_PARAM_MARKERS;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.mongodb.hibernate.internal.jdbc.MongoConnectionProvider;
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
    void testDialectInferredFromMongoUrl() {
        var builder = new StandardServiceRegistryBuilder()
                .clearSettings()
                .applySetting(JAKARTA_JDBC_URL, "mongodb://host/db");
        new StandardServiceRegistryScopedState.ServiceContributor().contribute(builder);
        assertThat(builder.getSettings().get(DIALECT)).isEqualTo(MONGO_DIALECT_SHORT_NAME);
        assertThat(builder.getSettings().get(CONNECTION_PROVIDER)).isEqualTo(MongoConnectionProvider.class.getName());
    }

    @Test
    void testDialectInferredFromMongoSrvUrl() {
        var builder = new StandardServiceRegistryBuilder()
                .clearSettings()
                .applySetting(JAKARTA_JDBC_URL, "mongodb+srv://cluster.example.com/db");
        new StandardServiceRegistryScopedState.ServiceContributor().contribute(builder);
        assertThat(builder.getSettings().get(DIALECT)).isEqualTo(MONGO_DIALECT_SHORT_NAME);
        assertThat(builder.getSettings().get(CONNECTION_PROVIDER)).isEqualTo(MongoConnectionProvider.class.getName());
    }

    @Test
    void testExplicitDialectNotOverriddenByMongoUrl() {
        var builder = new StandardServiceRegistryBuilder()
                .clearSettings()
                .applySetting(DIALECT, MONGO_DIALECT_SHORT_NAME)
                .applySetting(JAKARTA_JDBC_URL, "mongodb://host/db");
        new StandardServiceRegistryScopedState.ServiceContributor().contribute(builder);
        assertThat(builder.getSettings().get(DIALECT)).isEqualTo(MONGO_DIALECT_SHORT_NAME);
        assertThat(builder.getSettings().get(CONNECTION_PROVIDER)).isEqualTo(MongoConnectionProvider.class.getName());
    }

    @Test
    void testNonMongoDialectWithMongoUrlIsSilent() {
        // Deliberately not calling requireService — the service is never accessed in production
        // when a non-MongoDB dialect is configured alongside a MongoDB URL.
        try (var registry = new StandardServiceRegistryBuilder()
                .clearSettings()
                .applySetting(DIALECT, "org.hibernate.dialect.H2Dialect")
                .applySetting(JAKARTA_JDBC_URL, "mongodb://host/db")
                .build()) {
            assertThat(registry).isNotNull();
        }
    }

    @Test
    void testMongoDialectNotPluggedIn() {
        var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder();
        standardServiceRegistryBuilder.clearSettings();
        try (var standardServiceRegistry = standardServiceRegistryBuilder.build()) {
            assertThatThrownBy(() -> standardServiceRegistry.requireService(StandardServiceRegistryScopedState.class))
                    .hasRootCauseMessage("[hibernate.dialect] must be set to [MongoDB]");
        }
    }

    @Test
    void testNativeParamMarkersForcedOn() {
        var builder = new StandardServiceRegistryBuilder()
                .clearSettings()
                .applySetting(DIALECT, MONGO_DIALECT_SHORT_NAME)
                .applySetting(JAKARTA_JDBC_URL, "mongodb://host/db");
        new StandardServiceRegistryScopedState.ServiceContributor().contribute(builder);
        assertThat(builder.getSettings().get(DIALECT_NATIVE_PARAM_MARKERS)).isEqualTo(true);
    }

    @Test
    void testExplicitTrueNativeParamMarkersAccepted() {
        var builder = new StandardServiceRegistryBuilder()
                .clearSettings()
                .applySetting(DIALECT, MONGO_DIALECT_SHORT_NAME)
                .applySetting(JAKARTA_JDBC_URL, "mongodb://host/db")
                .applySetting(DIALECT_NATIVE_PARAM_MARKERS, true);
        new StandardServiceRegistryScopedState.ServiceContributor().contribute(builder);
        assertThat(builder.getSettings().get(DIALECT_NATIVE_PARAM_MARKERS)).isEqualTo(true);
    }

    @Test
    void testExplicitFalseNativeParamMarkersRejected() {
        var builder = new StandardServiceRegistryBuilder()
                .clearSettings()
                .applySetting(DIALECT, MONGO_DIALECT_SHORT_NAME)
                .applySetting(DIALECT_NATIVE_PARAM_MARKERS, false);
        assertThatThrownBy(() -> new StandardServiceRegistryScopedState.ServiceContributor().contribute(builder))
                .hasMessageContaining(
                        "[hibernate.dialect.native_param_markers] is automatically configured and must not be set to [false]");
    }

    @Test
    void testIncompatibleConnectionProvider() {
        assertThatThrownBy(() -> new StandardServiceRegistryBuilder()
                        .clearSettings()
                        .applySetting(DIALECT, MONGO_DIALECT_SHORT_NAME)
                        .applySetting(CONNECTION_PROVIDER, "com.example.SomeOtherConnectionProvider")
                        .build())
                .hasMessageContaining(
                        "[hibernate.connection.provider_class] is automatically configured and must not be set explicitly");
    }
}
