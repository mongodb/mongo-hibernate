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

package com.mongodb.hibernate.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.service.Service;
import org.jspecify.annotations.Nullable;

/**
 * A Hibernate {@link Service} focusing on customizing {@link com.mongodb.client.MongoClient} creation programmatically
 * by invoking the various methods of {@link MongoClientSettings.Builder} before its
 * {@link MongoClientSettings.Builder#build()} invocation.
 *
 * <p>An example usage is as follows:
 *
 * <pre>{@code
 * var clientCustomizer = (clientSettingsBuilder, connectionString) -> {
 *     // customize client settings to your heart's content
 *     ...
 * };
 *
 * var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder();
 * standardServiceRegistryBuilder.addService(MongoClientCustomizer.class, clientCustomizer);
 *
 * var metadataBuilder = new MetadataSources(standardServiceRegistryBuilder.build()).getMetadataBuilder();
 *
 * // add metadata (e.g. annotated Entity classes)
 * ...
 *
 * var sessionFactory = metadataBuilder().build().getSessionFactoryBuilder().build();
 *
 * // start using sessionFactory as normal
 * ...
 * }</pre>
 */
@FunctionalInterface
public interface MongoClientCustomizer extends Service {

    /**
     * Customize {@link MongoClientSettings} building.
     *
     * @param builder a {@link MongoClientSettings.Builder} instance which has been created with blank state (without
     *     {@linkplain MongoClientSettings.Builder#applyConnectionString(ConnectionString) applying}
     *     the {@code connectionString} argument)
     * @param connectionString the {@link ConnectionString} created from {@value JdbcSettings#JAKARTA_JDBC_URL}
     *     configuration property if provided; provided for reference alone; could be null
     * @see com.mongodb.hibernate.jdbc.MongoConnectionProvider#configure(java.util.Map)
     */
    void customize(MongoClientSettings.Builder builder, @Nullable ConnectionString connectionString);
}
