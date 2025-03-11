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

import static java.lang.String.format;
import static org.junit.Assert.assertTrue;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Assumes that all tests that use this {@linkplain ExtendWith#value() extension} run <a
 * href="https://junit.org/junit5/docs/current/user-guide/#writing-tests-parallel-execution">sequentially</a>.
 */
public final class MongoExtension implements BeforeAllCallback, BeforeEachCallback {

    private static final State STATE = State.create();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        for (var field : findAnnotatedFields(context.getRequiredTestClass(), InjectMongoCollection.class)) {
            assertTrue(format("The field [%s] must be static", field), isStatic(field));
            var annotation = field.getDeclaredAnnotation(InjectMongoCollection.class);
            var collectionName = annotation.value();
            var mongoCollection = STATE.mongoDatabase().getCollection(collectionName, BsonDocument.class);
            field.setAccessible(true);
            field.set(null, mongoCollection);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        STATE.mongoDatabase().drop();
    }

    private record State(MongoClient mongoClient, MongoDatabase mongoDatabase) {
        static State create() {
            @SuppressWarnings("unchecked")
            var hibernateProperties = (Map<String, Object>) (Map<?, Object>) new Configuration().getProperties();
            var mongoConfig = new MongoConfigurationBuilder(hibernateProperties).build();
            var mongoClient = MongoClients.create(mongoConfig.mongoClientSettings());
            var state = new State(mongoClient, mongoClient.getDatabase(mongoConfig.databaseName()));
            Runtime.getRuntime().addShutdownHook(new Thread(state::close));
            return state;
        }

        private void close() {
            mongoClient.close();
        }
    }
}
