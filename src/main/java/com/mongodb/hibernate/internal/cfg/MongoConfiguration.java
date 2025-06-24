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
import com.mongodb.hibernate.cfg.MongoConfigurator;
import java.util.function.Consumer;

/**
 * The configuration of the MongoDB Hibernate Extension.
 *
 * @param mongoClientSettings {@link MongoConfigurator#applyToMongoClientSettings(Consumer)}.
 * @param databaseName {@link MongoConfigurator#databaseName(String)}.
 * @see MongoConfigurationBuilder#build()
 */
public record MongoConfiguration(MongoClientSettings mongoClientSettings, String databaseName) {}
