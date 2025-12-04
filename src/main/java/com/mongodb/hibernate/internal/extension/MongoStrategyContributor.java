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

package com.mongodb.hibernate.internal.extension;

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_CONNECTION_PROVIDER_SHORT_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DIALECT_SHORT_NAME;

import com.mongodb.hibernate.dialect.MongoDialect;
import com.mongodb.hibernate.jdbc.MongoConnectionProvider;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.spi.ServiceContributor;

public final class MongoStrategyContributor implements ServiceContributor {

    @Override
    public void contribute(StandardServiceRegistryBuilder serviceRegistryBuilder) {
        var selector = serviceRegistryBuilder.getBootstrapServiceRegistry().requireService(StrategySelector.class);
        selector.registerStrategyImplementor(Dialect.class, MONGO_DIALECT_SHORT_NAME, MongoDialect.class);
        selector.registerStrategyImplementor(
                ConnectionProvider.class, MONGO_CONNECTION_PROVIDER_SHORT_NAME, MongoConnectionProvider.class);
    }
}
