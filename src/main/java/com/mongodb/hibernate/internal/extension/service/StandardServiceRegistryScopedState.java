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

package com.mongodb.hibernate.internal.extension.service;

import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;

import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.internal.cfg.MongoConfiguration;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import com.mongodb.hibernate.service.spi.MongoConfigurationContributor;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Map;
import org.hibernate.HibernateException;
import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.Service;
import org.hibernate.service.UnknownServiceException;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.jspecify.annotations.Nullable;

public final class StandardServiceRegistryScopedState implements Service {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient MongoConfiguration config;

    @VisibleForTesting(otherwise = PRIVATE)
    public StandardServiceRegistryScopedState(MongoConfiguration config) {
        this.config = config;
    }

    public MongoConfiguration getConfiguration() {
        return config;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        throw new NotSerializableException(
                "This class is not designed to be serialized despite it having to implement `Serializable`");
    }

    public static final class ServiceContributor implements org.hibernate.service.spi.ServiceContributor {
        public ServiceContributor() {}

        @Override
        public void contribute(StandardServiceRegistryBuilder serviceRegistryBuilder) {
            serviceRegistryBuilder.addInitiator(new StandardServiceInitiator<StandardServiceRegistryScopedState>() {
                @Override
                public Class<StandardServiceRegistryScopedState> getServiceInitiated() {
                    return StandardServiceRegistryScopedState.class;
                }

                @Override
                public StandardServiceRegistryScopedState initiateService(
                        Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
                    return new StandardServiceRegistryScopedState(
                            createMongoConfiguration(configurationValues, serviceRegistry));
                }
            });
        }

        private MongoConfiguration createMongoConfiguration(
                Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
            var jdbcUrl = configurationValues.get(JAKARTA_JDBC_URL);
            MongoConfigurationContributor mongoConfigurationContributor =
                    getMongoConfigurationContributor(serviceRegistry);
            if (jdbcUrl == null && mongoConfigurationContributor == null) {
                throw new HibernateException(format(
                        "Configuration property [%s] is required unless %s is provided",
                        JAKARTA_JDBC_URL, MongoConfigurationContributor.class.getName()));
            }
            var mongoConfigurationBuilder = new MongoConfigurationBuilder(configurationValues);
            if (mongoConfigurationContributor != null) {
                mongoConfigurationContributor.configure(mongoConfigurationBuilder);
            }
            return mongoConfigurationBuilder.build();
        }

        private @Nullable MongoConfigurationContributor getMongoConfigurationContributor(
                ServiceRegistryImplementor serviceRegistry) {
            MongoConfigurationContributor result = null;
            try {
                result = serviceRegistry.getService(MongoConfigurationContributor.class);
                if (result == null) {
                    // TODO-HIBERNATE-43 `LOGGER.debug("{} is not detected", ...)`
                }
            } catch (UnknownServiceException e) {
                // TODO-HIBERNATE-43 `LOGGER.debug("{} is not detected", ..., e)`
            }
            return result;
        }
    }
}
