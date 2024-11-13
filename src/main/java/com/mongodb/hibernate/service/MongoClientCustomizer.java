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

import com.mongodb.MongoClientSettings;
import org.hibernate.service.Service;

/**
 * A Hibernate {@link Service} focusing on customizing {@link com.mongodb.client.MongoClient} creation programmatically
 * by invoking {@link MongoClientSettings.Builder}'s various methods prior to its
 * {@link MongoClientSettings.Builder#build()} invocation.
 *
 * <p>An example usage is as follows:
 *
 * <pre>
 *     var cfg = new Configuration();
 *     ... ... // configure cfg as you normally do (e.g. add entity classes, etc.)
 *
 *     var clientCustomizer = builder -> {
 *         ... ... // customize client settings to your heart's content
 *     };
 *
 *     var serviceRegistryBuilder = cfg.getStandardServiceRegistryBuilder();
 *     serviceRegistryBuilder.addService(MongoClientCustomizer.class, clientCustomizer);
 *
 *     var sessionFactory = cfg.buildSessionFactory();
 *     ... ... // start using sessionFactory as normal
 *
 * </pre>
 *
 * <p>Internally the {@code clientCustomizer} will be fetched and do its due diligence.
 *
 * @see com.mongodb.hibernate.jdbc.MongoConnectionProvider#configure(java.util.Map)
 */
@FunctionalInterface
public interface MongoClientCustomizer extends Service {

    void customize(MongoClientSettings.Builder builder);
}
