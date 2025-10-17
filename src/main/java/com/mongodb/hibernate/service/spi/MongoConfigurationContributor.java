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

package com.mongodb.hibernate.service.spi;

import com.mongodb.hibernate.cfg.MongoConfigurator;
import java.util.Map;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.Service;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.ServiceContributor;

/**
 * A {@link Service} an application may use for programmatically configuring the MongoDB Extension for Hibernate ORM.
 *
 * <p>This {@link Service} may be contributed either via a {@link ServiceContributor}, which allows access to
 * {@link StandardServiceRegistryBuilder}, or via a {@link StandardServiceRegistryBuilder} directly, as shown below:
 *
 * <pre>{@code
 * MongoConfigurationContributor mongoConfigContributor = configurator -> {
 *     // configure the extension to your heart's content
 *     ...
 * };
 * var standardServiceRegistryBuilder = new StandardServiceRegistryBuilder()
 *         .addService(MongoConfigurationContributor.class, mongoConfigContributor);
 * var metadataBuilder = new MetadataSources(standardServiceRegistryBuilder.build())
 *         .getMetadataBuilder();
 *
 * // add metadata (e.g. annotated Entity classes)
 * ...
 *
 * try (var sessionFactory = metadataBuilder.build()
 *         .getSessionFactoryBuilder().build()) {
 *     // use the session factory
 *     ...
 * }
 * }</pre>
 */
@FunctionalInterface
public interface MongoConfigurationContributor extends Service {

    /**
     * Configures the MongoDB Extension for Hibernate ORM. This method is called once per instance of
     * {@link StandardServiceRegistry} that has this {@link MongoConfigurationContributor}
     * {@linkplain StandardServiceRegistryBuilder#addService(Class, Service) added}.
     *
     * @param configurator The {@link MongoConfigurator} pre-configured with {@linkplain Configurable#configure(Map)
     *     configuration properties}.
     */
    void configure(MongoConfigurator configurator);
}
