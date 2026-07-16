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

package com.mongodb.hibernate;

import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import com.mongodb.hibernate.junit.MongoExtension;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.spi.ServiceContributor;

public final class TestServiceContributor implements ServiceContributor {

    public TestServiceContributor() {}

    @Override
    public void contribute(StandardServiceRegistryBuilder serviceRegistryBuilder) {
        serviceRegistryBuilder.addService(
                MongoConfigurationContributor.class,
                // Point every test SessionFactory at the current fork's database so parallel Gradle test forks stay
                // isolated. Registered here (rather than in MongoServiceRegistryProducer) so it also reaches tests that
                // build their SessionFactory directly via `new Configuration()`, bypassing the testing framework.
                configurator -> configurator
                        .databaseName(MongoExtension.databaseName())
                        .applyToMongoClientSettings(
                                builder -> builder.addCommandListener(TestCommandListener.INSTANCE)));
        serviceRegistryBuilder.addService(TestCommandListener.class, TestCommandListener.INSTANCE);
    }
}
