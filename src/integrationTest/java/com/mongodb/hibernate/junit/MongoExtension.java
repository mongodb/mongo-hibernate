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

import static org.junit.platform.commons.support.AnnotationSupport.findAnnotatedFields;
import static org.junit.platform.commons.util.ReflectionUtils.isStatic;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import java.util.Map;
import org.bson.BsonDocument;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstancePreConstructCallback;
import org.junit.jupiter.api.extension.TestInstancePreDestroyCallback;

public final class MongoExtension
        implements TestInstancePreConstructCallback,
                TestInstancePreDestroyCallback,
                BeforeAllCallback,
                BeforeEachCallback {

    private ExtensionContext outermostContext;

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    @Override
    public void preConstructTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext context) {
        if (factoryContext.getOuterInstance().isEmpty()) {
            outermostContext = context;

            @SuppressWarnings("unchecked")
            Map<String, Object> hibernateProperties =
                    (Map<String, Object>) (Map<?, Object>) new Configuration().getProperties();

            var mongoConfig = new MongoConfigurationBuilder(hibernateProperties).build();
            mongoClient = MongoClients.create(mongoConfig.mongoClientSettings());
            mongoDatabase = mongoClient.getDatabase(mongoConfig.databaseName());
        }
    }

    @Override
    public void preDestroyTestInstance(ExtensionContext context) {
        if (context.equals(outermostContext)) {
            if (mongoClient != null) {
                mongoClient.close();
            }
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (mongoDatabase != null) {
            findAnnotatedFields(context.getRequiredTestClass(), InjectMongoCollection.class)
                    .forEach(field -> {
                        var annotation = field.getDeclaredAnnotation(InjectMongoCollection.class);
                        var collectionName = annotation.value();
                        var mongoCollection = mongoDatabase.getCollection(collectionName, BsonDocument.class);
                        try {
                            field.setAccessible(true);
                            field.set(isStatic(field) ? null : context.getRequiredTestInstance(), mongoCollection);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (mongoDatabase != null) {
            mongoDatabase.drop();
        }
    }
}
