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

package com.mongodb.hibernate.example;

import com.mongodb.ServerAddress;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import com.mongodb.hibernate.example.model.Item;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AppWithMongoConfiguratorContributorAddedDirectly {
    private static final Logger LOGGER = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    private AppWithMongoConfiguratorContributorAddedDirectly() {}

    public static void main(String... args) {
        try (var sessionFactory = new MetadataSources()
                // add metadata sources, for example, by calling `addAnnotatedClasses`
                // ...
                .addAnnotatedClasses(Item.class)
                .getMetadataBuilder(new StandardServiceRegistryBuilder()
                        .applySetting(AvailableSettings.DIALECT, "com.mongodb.hibernate.dialect.MongoDialect")
                        .applySetting(AvailableSettings.CONNECTION_PROVIDER, "com.mongodb.hibernate.jdbc.MongoConnectionProvider")
                        .applySetting(AvailableSettings.STATEMENT_BATCH_SIZE, 2)
                        .addService(MongoConfigurationContributor.class, configurator ->
                                configurator.applyToMongoClientSettings(mongoClientSettings -> mongoClientSettings
                                                .applyToClusterSettings(clusterSettings -> clusterSettings
                                                        .hosts(List.of(new ServerAddress("localhost", 27017)))
                                                        .mode(ClusterConnectionMode.MULTIPLE))
                                                .build())
                                        .databaseName("example"))
                        .build())
                .build()
                .buildSessionFactory()) {
            // use `sessionFactory`
            // ...
            useSessionFactory(sessionFactory);
        }
    }

    static void useSessionFactory(SessionFactory sessionFactory) {
        sessionFactory.inTransaction(session -> session.createMutationQuery("delete from Item").executeUpdate());
        LOGGER.info("Deleted using HQL all `{}`s", Item.class.getSimpleName());
        var ids = sessionFactory.fromTransaction(session -> {
            var result = new ArrayList<ObjectId>();
            var itemWithExplicitId = new Item(new ObjectId())
                    .addToStructsList(new Item.MyStruct(Instant.now(), Set.of(4, 2)))
                    .addToStructsList(new Item.MyStruct(null, null));
            {
                session.persist(itemWithExplicitId);
                result.add(itemWithExplicitId.getId());
            }
            var itemWithGeneratedId = new Item().setString("with auto-generated ID");
            {
                session.persist(itemWithGeneratedId);
                result.add(itemWithGeneratedId.getId());
            }
            return result;
        });
        LOGGER.info("Persisted using API `{}`s with identifiers {}", Item.class.getSimpleName(), ids);
        sessionFactory.inTransaction(session -> {
            var mql = new BsonDocument("aggregate", new BsonString(Item.COLLECTION_NAME))
                    .append("pipeline", new BsonArray(List.of(
                            Aggregates.match(Filters.in("_id", ids)).toBsonDocument(),
                            Item.projectAll())))
                    .toJson(JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build());
            var items = session.createNativeQuery(mql, Item.class).getResultList();
            LOGGER.info("Found using MQL {}", items);
        });
    }
}
