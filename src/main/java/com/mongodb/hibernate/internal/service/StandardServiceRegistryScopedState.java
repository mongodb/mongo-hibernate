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

package com.mongodb.hibernate.internal.service;

import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.hibernate.cfg.AvailableSettings.JAVA_TIME_USE_DIRECT_JDBC;
import static org.hibernate.cfg.AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE;

import com.mongodb.hibernate.dialect.MongoDialect;
import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.internal.cfg.MongoConfiguration;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import com.mongodb.hibernate.jdbc.MongoConnectionProvider;
import com.mongodb.hibernate.service.spi.MongoConfigurationContributor;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Map;
import java.util.Set;
import org.hibernate.HibernateException;
import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
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
                    checkMongoDialectIsPluggedIn(configurationValues, serviceRegistry);
                    checkMongoConnectionProviderIsPluggedIn(configurationValues);
                    return new StandardServiceRegistryScopedState(
                            createMongoConfiguration(configurationValues, serviceRegistry));
                }
            });
        }

        private static void checkMongoDialectIsPluggedIn(
                Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
            var dialectFactory = serviceRegistry.getService(DialectFactory.class);
            if ((dialectFactory == null
                            || dialectFactory.getClass().getPackageName().startsWith("org.hibernate"))
                    && configurationValues.get(AvailableSettings.DIALECT_RESOLVERS) == null) {
                // If `DialectFactory` is different from the ones Hibernate ORM provide, or if
                // `AvailableSettings.DIALECT_RESOLVERS` is specified, then we cannot detect whether
                // `MongoDialect` is plugged in. Otherwise, we know that `AvailableSettings.DIALECT`
                // is the only way to plug `MongoDialect` in, and we can detect whether it is plugged in.
                var dialect = configurationValues.get(AvailableSettings.DIALECT);
                if (!((dialect instanceof MongoDialect)
                        || (dialect instanceof Class<?> dialectClass
                                && MongoDialect.class.isAssignableFrom(dialectClass))
                        || (dialect instanceof String dialectName
                                && dialectName.startsWith("com.mongodb.hibernate")))) {
                    throw new RuntimeException("%s must be plugged in, for example, via the [%s] configuration property"
                            .formatted(MongoDialect.class.getName(), AvailableSettings.DIALECT));
                }
            }
        }

        private static void checkMongoConnectionProviderIsPluggedIn(Map<String, Object> configurationValues) {
            var connectionProvider = configurationValues.get(AvailableSettings.CONNECTION_PROVIDER);
            if (!((connectionProvider instanceof MongoConnectionProvider)
                    || (connectionProvider instanceof Class<?> connectionProviderClass
                            && MongoConnectionProvider.class.isAssignableFrom(connectionProviderClass))
                    || (connectionProvider instanceof String connectionProviderName
                            && connectionProviderName.startsWith("com.mongodb.hibernate")))) {
                throw new RuntimeException("%s must be plugged in, for example, via the [%s] configuration property"
                        .formatted(MongoConnectionProvider.class.getName(), AvailableSettings.CONNECTION_PROVIDER));
            }
        }

        private static MongoConfiguration createMongoConfiguration(
                Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
            var jdbcUrl = configurationValues.get(JAKARTA_JDBC_URL);
            var mongoConfigurationContributor = getMongoConfigurationContributor(serviceRegistry);
            if (jdbcUrl == null && mongoConfigurationContributor == null) {
                throw new HibernateException(format(
                        "Configuration property [%s] is required unless %s is provided",
                        JAKARTA_JDBC_URL, MongoConfigurationContributor.class.getName()));
            }
            forbidTemporalConfiguration(configurationValues);
            var mongoConfigurationBuilder = new MongoConfigurationBuilder(configurationValues);
            if (mongoConfigurationContributor != null) {
                mongoConfigurationContributor.configure(mongoConfigurationBuilder);
            }
            return mongoConfigurationBuilder.build();
        }

        private static @Nullable MongoConfigurationContributor getMongoConfigurationContributor(
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

        private static void forbidTemporalConfiguration(Map<String, Object> configurationValues) {
            var forbiddenConfigurationPropertyNames = Set.of(JAVA_TIME_USE_DIRECT_JDBC, PREFERRED_INSTANT_JDBC_TYPE);
            for (var forbiddenConfigurationPropertyName : forbiddenConfigurationPropertyNames) {
                if (configurationValues.containsKey(forbiddenConfigurationPropertyName)) {
                    throw new HibernateException(
                            format("Configuration property [%s] is not supported", JAVA_TIME_USE_DIRECT_JDBC));
                }
            }
        }
    }
}
