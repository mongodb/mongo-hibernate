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
        sessionFactory = new Configuration().buildSessionFactory();
        var mongoConfig = new MongoConfigurationBuilder(sessionFactory.getProperties()).build();
        mongoClient = MongoClients.create(mongoConfig.mongoClientSettings());
        mongoDatabase = mongoClient.getDatabase(mongoConfig.databaseName());

        var testClass = context.getRequiredTestClass();

        if (MongoDatabaseAware.class.isAssignableFrom(testClass)) {
            ((MongoDatabaseAware) context.getRequiredTestInstance()).injectMongoDatabase(mongoDatabase);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        mongoDatabase.drop();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        mongoClient.close();
        sessionFactory.close();
    }
}
