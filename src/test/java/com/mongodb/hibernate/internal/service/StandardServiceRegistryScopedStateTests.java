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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.mongodb.hibernate.internal.extension.service.StandardServiceRegistryScopedState;
import com.mongodb.hibernate.service.spi.MongoConfigurationContributor;
import java.util.ArrayList;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName(
            "MongoConfigurationContributor is called once per building StandardServiceRegistry, different MongoConfigurator passed")
    void mongoConfigurationContributorInvocationsAndMongoConfiguratorInstances() {
        try (var bootstrapServiceRegistry = new BootstrapServiceRegistryBuilder().build()) {
            var mongoConfigurators = new ArrayList<>();
            MongoConfigurationContributor mongoConfigurationContributor = mongoConfigurators::add;
            var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                    .addService(MongoConfigurationContributor.class, mongoConfigurationContributor);
            try (var standardServiceRegistry1 = standardServiceRegistryBuilder.build();
                    var standardServiceRegistry2 = standardServiceRegistryBuilder.build()) {
                standardServiceRegistry1.requireService(StandardServiceRegistryScopedState.class);
                standardServiceRegistry2.requireService(StandardServiceRegistryScopedState.class);
                assertEquals(2, mongoConfigurators.size());
                assertNotSame(mongoConfigurators.get(0), mongoConfigurators.get(1));
            }
        }
    }
}
