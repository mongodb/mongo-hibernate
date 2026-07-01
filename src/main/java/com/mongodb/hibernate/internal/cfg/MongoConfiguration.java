/*
 * Copyright 2024-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.cfg;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.cfg.MongoConfigurator;
import org.jspecify.annotations.Nullable;

/**
 * The configuration of the MongoDB Extension for Hibernate ORM.
 *
 * @param mongoClientSettings settings to create a client from, or {@code null} when {@code mongoClient} is supplied.
 * @param mongoClient an externally supplied {@link MongoClient} to use as-is (the provider does not own or close it),
 *     or {@code null} when {@code mongoClientSettings} is supplied.
 * @param databaseName {@link MongoConfigurator#databaseName(String)}.
 * @see MongoConfigurationBuilder#build()
 * @hidden
 */
public record MongoConfiguration(
        @Nullable MongoClientSettings mongoClientSettings,
        @Nullable MongoClient mongoClient,
        String databaseName) {

    public MongoConfiguration {
        if ((mongoClientSettings == null) == (mongoClient == null)) {
            throw new IllegalArgumentException("Exactly one of mongoClientSettings and mongoClient must be non-null");
        }
    }

    public MongoConfiguration(MongoClientSettings mongoClientSettings, String databaseName) {
        this(mongoClientSettings, null, databaseName);
    }

    public MongoConfiguration(MongoClient mongoClient, String databaseName) {
        this(null, mongoClient, databaseName);
    }
}
