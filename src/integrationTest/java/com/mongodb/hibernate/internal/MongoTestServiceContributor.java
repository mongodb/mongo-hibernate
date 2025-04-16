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

package com.mongodb.hibernate.internal;

import com.mongodb.hibernate.service.spi.MongoConfigurationContributor;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.spi.ServiceContributor;

public final class MongoTestServiceContributor implements ServiceContributor {

    public MongoTestServiceContributor() {}

    @Override
    public void contribute(StandardServiceRegistryBuilder serviceRegistryBuilder) {
        serviceRegistryBuilder.addService(MongoConfigurationContributor.class, (MongoConfigurationContributor)
                configurator -> configurator.applyToMongoClientSettings(
                        builder -> builder.addCommandListener(MongoTestCommandListener.INSTANCE)));
        serviceRegistryBuilder.addService(MongoTestCommandListener.class, MongoTestCommandListener.INSTANCE);
    }
}
