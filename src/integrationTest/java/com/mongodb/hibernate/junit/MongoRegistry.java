/*
 * Copyright 2026-present MongoDB, Inc.
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

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_CONFIGURATION_CONTRIBUTOR_KEY;
import static com.mongodb.hibernate.junit.MongoExtension.configurationContributorForClass;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bson.BsonDocument;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

/**
 * Boots a {@code SessionFactory} for an integration test programmatically rather than through Hibernate's testing
 * framework, so it can apply the contributor that points the {@code SessionFactory} at the test class's own database
 * and install that database's command listener.
 */
public final class MongoRegistry {

    /** What one booted {@code SessionFactory} produced: the commands sent, and whatever the action observed. */
    public record Run<T>(List<BsonDocument> commands, T observed) {}

    /** Shared base settings for schema generation tests. */
    public static final Map<String, Object> SCHEMA_GENERATION_BASE_SETTINGS = Map.of(
            "jakarta.persistence.schema-generation.database.action",
            "create-drop",
            "hibernate.hbm2ddl.halt_on_error",
            "true");

    private MongoRegistry() {}

    /**
     * Boots a {@code SessionFactory} for {@code entityClasses} with {@code settings}, runs {@code action} against an
     * open session while it is up, and returns the commands sent along with the action's result.
     */
    public static <T> Run<T> inRegistry(
            CommandHistory commandHistory,
            Class<?> testClass,
            Map<String, Object> settings,
            Function<Session, T> action,
            Class<?>... entityClasses) {
        try (var registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .applySetting(MONGO_CONFIGURATION_CONTRIBUTOR_KEY, configurationContributorForClass(testClass))
                .build()) {
            T observed;
            var metadataSources = new MetadataSources();
            for (var entityClass : entityClasses) {
                metadataSources.addAnnotatedClass(entityClass);
            }
            try (var sessionFactory = metadataSources.buildMetadata(registry).buildSessionFactory();
                    var session = sessionFactory.openSession()) {
                observed = action.apply(session);
            }
            return new Run<>(commandHistory.getCommands(), observed);
        }
    }
}
