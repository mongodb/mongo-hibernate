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

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_CONFIGURATION_CONTRIBUTOR_KEY;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DIALECT_SHORT_NAME;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static org.hibernate.cfg.AvailableSettings.DIALECT_RESOLVERS;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.hibernate.cfg.AvailableSettings.JAVA_TIME_USE_DIRECT_JDBC;
import static org.hibernate.cfg.AvailableSettings.PREFERRED_INSTANT_JDBC_TYPE;

import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.internal.cfg.MongoConfiguration;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import com.mongodb.hibernate.internal.dialect.TestMongoDialect;
import com.mongodb.hibernate.internal.jdbc.MongoConnectionProvider;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.Map;
import java.util.Set;
import org.hibernate.HibernateException;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
import org.hibernate.service.Service;
import org.hibernate.service.UnknownServiceException;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.jspecify.annotations.Nullable;

/**
 * @hidden
 * @mongoCme Thread-safe.
 */
@SuppressWarnings("MissingSummary")
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

    /**
     * @hidden
     * @mongoCme The instance methods of {@link org.hibernate.service.spi.ServiceContributor} are called multiple times
     *     if multiple {@link StandardServiceRegistry} instances are {@linkplain StandardServiceRegistryBuilder#build()
     *     built} using the same {@link BootstrapServiceRegistry}.
     */
    public static final class ServiceContributor implements org.hibernate.service.spi.ServiceContributor {
        public ServiceContributor() {}

        @Override
        public void contribute(StandardServiceRegistryBuilder serviceRegistryBuilder) {
            var settings = serviceRegistryBuilder.getSettings();
            var url = settings.get(JAKARTA_JDBC_URL);
            if (settings.get(AvailableSettings.DIALECT) == null
                    && url instanceof String urlString
                    && (urlString.startsWith("mongodb://") || urlString.startsWith("mongodb+srv://"))) {
                serviceRegistryBuilder.applySetting(AvailableSettings.DIALECT, MONGO_DIALECT_SHORT_NAME);
            }
            if (isMongoDialect(settings.get(AvailableSettings.DIALECT))) {
                var connectionProvider = settings.get(AvailableSettings.CONNECTION_PROVIDER);
                if (connectionProvider != null) {
                    if (!MongoConnectionProvider.class.getName().equals(connectionProvider)) {
                        throw new RuntimeException("[%s] is automatically configured and must not be set explicitly"
                                .formatted(AvailableSettings.CONNECTION_PROVIDER));
                    }
                } else {
                    // Autoconfigure: set the class name directly so ConnectionProviderInitiator
                    // resolves it without registering it as a named strategy (which would trigger
                    // Hibernate's single-registered-provider auto-selection for non-MongoDB sessions).
                    serviceRegistryBuilder.applySetting(
                            AvailableSettings.CONNECTION_PROVIDER, MongoConnectionProvider.class.getName());
                }
            }
            // The initiator is registered unconditionally so that checkMongoDialectIsPluggedIn provides
            // a helpful error whenever the service is requested from a misconfigured session.
            serviceRegistryBuilder.addInitiator(new StandardServiceInitiator<StandardServiceRegistryScopedState>() {
                /**
                 * @mongoCme This method may be called multiple times when
                 *     {@linkplain StandardServiceRegistryBuilder#build() building} a single
                 *     {@link StandardServiceRegistry} instance.
                 */
                @Override
                public Class<StandardServiceRegistryScopedState> getServiceInitiated() {
                    return StandardServiceRegistryScopedState.class;
                }

                /**
                 * @mongoCme This method is called not more than once per instance of {@link StandardServiceInitiator}.
                 */
                @Override
                public StandardServiceRegistryScopedState initiateService(
                        Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
                    checkMongoDialectIsPluggedIn(configurationValues, serviceRegistry);
                    return new StandardServiceRegistryScopedState(
                            createMongoConfiguration(configurationValues, serviceRegistry));
                }
            });
        }

        private static boolean isMongoDialect(@Nullable Object dialect) {
            return dialect instanceof String dialectName
                    && (dialectName.equals(MONGO_DIALECT_SHORT_NAME) || isTestMongoDialectSubclass(dialectName));
        }

        private static void checkMongoDialectIsPluggedIn(
                Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
            var dialectFactory = serviceRegistry.getService(DialectFactory.class);
            if ((dialectFactory == null
                            || dialectFactory.getClass().getPackageName().startsWith("org.hibernate"))
                    && configurationValues.get(DIALECT_RESOLVERS) == null) {
                // If `DialectFactory` is different from the ones Hibernate ORM provides, or if
                // `DIALECT_RESOLVERS` is specified, then we cannot detect whether `MongoDialect` is plugged in.
                // Otherwise, we know that `DIALECT` is the only way to plug `MongoDialect` in,
                // and we can detect whether it is plugged in.
                if (!isMongoDialect(configurationValues.get(AvailableSettings.DIALECT))) {
                    throw new RuntimeException(
                            "[%s] must be set to [%s]".formatted(AvailableSettings.DIALECT, MONGO_DIALECT_SHORT_NAME));
                }
            }
        }

        // Only subclasses of TestMongoDialect pass — intentional test dialects, not a misconfiguration.
        // (MongoDialect itself is rejected here, enforcing the short name for production use.)
        private static boolean isTestMongoDialectSubclass(String className) {
            try {
                return TestMongoDialect.class.isAssignableFrom(Class.forName(className));
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        private static MongoConfiguration createMongoConfiguration(
                Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
            var jdbcUrl = configurationValues.get(JAKARTA_JDBC_URL);
            var mongoConfigurationContributor = getMongoConfigurationContributor(configurationValues, serviceRegistry);
            if (jdbcUrl == null && mongoConfigurationContributor == null) {
                throw new HibernateException(format(
                        "Configuration property [%s] is required unless %s is provided",
                        JAKARTA_JDBC_URL, MongoConfigurationContributor.class.getName()));
            }
            forbidTemporalConfiguration(configurationValues);
            checkNullSemantics(configurationValues);
            var mongoConfigurationBuilder = new MongoConfigurationBuilder(configurationValues);
            if (mongoConfigurationContributor != null) {
                mongoConfigurationContributor.configure(mongoConfigurationBuilder);
            }
            return mongoConfigurationBuilder.build();
        }

        private static @Nullable MongoConfigurationContributor getMongoConfigurationContributor(
                Map<String, Object> configurationValues, ServiceRegistryImplementor serviceRegistry) {
            // The JPA properties map is checked first — before the service registry —
            // so Spring Boot auto-configuration can bridge a contributor without touching Hibernate internals.
            if (configurationValues.get(MONGO_CONFIGURATION_CONTRIBUTOR_KEY)
                    instanceof MongoConfigurationContributor contributor) {
                return contributor;
            }
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

        private static void checkNullSemantics(Map<String, Object> configurationValues) {
            var nullSemantics = configurationValues.get("com.mongodb.hibernate.semantics.nulls");
            if (nullSemantics == null) {
                throw new HibernateException(
                        "Configuration property [com.mongodb.hibernate.semantics.nulls] is required");
            }
            if (!"MQL".equals(nullSemantics)) {
                throw new HibernateException(format(
                        "Configuration property [com.mongodb.hibernate.semantics.nulls] with value [%s] must be [MQL]",
                        nullSemantics));
            }
        }
    }
}
