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

package com.mongodb.hibernate.internal.service;

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DIALECT_SHORT_NAME;

import com.mongodb.hibernate.internal.dialect.MongoDialect;
import org.hibernate.boot.registry.selector.spi.NamedStrategyContributions;
import org.hibernate.boot.registry.selector.spi.NamedStrategyContributor;
import org.hibernate.dialect.Dialect;

/** @hidden */
@SuppressWarnings("MissingSummary")
public final class MongoNamedStrategyContributor implements NamedStrategyContributor {

    public MongoNamedStrategyContributor() {}

    @Override
    public void contributeStrategyImplementations(NamedStrategyContributions contributions) {
        contributions.contributeStrategyImplementor(Dialect.class, MongoDialect.class, MONGO_DIALECT_SHORT_NAME);
    }

    @Override
    public void clearStrategyImplementations(NamedStrategyContributions contributions) {
        contributions.removeStrategyImplementor(Dialect.class, MongoDialect.class);
    }
}
