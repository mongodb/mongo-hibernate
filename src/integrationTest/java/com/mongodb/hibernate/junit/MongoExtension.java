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

package com.mongodb.hibernate.junit;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class MongoExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

    private SessionFactory sessionFactory;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    @Override
    public void beforeAll(ExtensionContext context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> hibernateProperties =
                (Map<String, Object>) (Map<?, Object>) new Configuration().getProperties();
        var mongoConfig = new MongoConfigurationBuilder(hibernateProperties).build();
        mongoClient = MongoClients.create(mongoConfig.mongoClientSettings());
        mongoDatabase = mongoClient.getDatabase(mongoConfig.databaseName());

        if (context.getTestInstance().orElse(null) instanceof MongoDatabaseAware mongoDatabaseAware) {
            mongoDatabaseAware.injectMongoDatabase(mongoDatabase);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (mongoDatabase != null) {
            mongoDatabase.drop();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        mongoClient.close();
        sessionFactory.close();
    }
}
