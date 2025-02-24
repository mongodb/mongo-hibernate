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

import com.mongodb.hibernate.cfg.MongoConfigurator;
import java.util.Map;
import org.hibernate.service.Service;
import org.hibernate.service.spi.Configurable;

/**
 * A {@link Service} an application may use for programmatically configuring the MongoDB extension of Hibernate ORM.
 *
 * <p>An example usage is as follows:
 *
 * <pre>{@code
 * var mongoConfigContributor = configurator -> {
 *     // configure the dialect to your heart's content
 *     ...
 * };
 *
 * var standardServiceRegistryBuilder =
 *         new StandardServiceRegistryBuilder().addService(MongoConfigurationContributor.class, mongoConfigContributor);
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
public interface MongoConfigurationContributor extends Service {

    /**
     * Configures the MongoDB extension of Hibernate ORM.
     *
     * @param configurator The {@link MongoConfigurator} pre-configured with {@linkplain Configurable#configure(Map)
     *     configuration properties}.
     */
    void contribute(MongoConfigurator configurator);
}
