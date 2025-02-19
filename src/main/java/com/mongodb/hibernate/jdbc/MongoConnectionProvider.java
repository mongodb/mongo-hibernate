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

package com.mongodb.hibernate.jdbc;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;

import com.mongodb.MongoDriverInformation;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.BuildConfig;
import com.mongodb.hibernate.dialect.MongoDialect;
import com.mongodb.hibernate.dialect.MongoDialectSettings;
import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.service.MongoDialectConfigurator;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.hibernate.HibernateException;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.UnknownServiceException;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.service.spi.Stoppable;
import org.jspecify.annotations.Nullable;

/**
 * {@linkplain MongoDialect MongoDB dialect}'s customized JDBC {@link ConnectionProvider} SPI implementation.
 *
 * <p>All the work done via a {@link Connection} {@linkplain MongoConnectionProvider#getConnection() obtained} from this
 * {@linkplain ConnectionProvider} is done within the same {@link ClientSession}.
 * {@linkplain ClientSession#startTransaction() MongoDB transactions} are used only if
 * {@linkplain Connection#getAutoCommit() auto-commit} is disabled.
 *
 * <p>This {@link ConnectionProvider} does not respect the {@value org.hibernate.cfg.AvailableSettings#AUTOCOMMIT}
 * configuration property, and {@linkplain MongoConnectionProvider#getConnection() provides} {@link Connection}s with
 * {@linkplain Connection#getAutoCommit() auto-commit} enabled.
 *
 * @see MongoDialectSettings
 */
public final class MongoConnectionProvider
        implements ConnectionProvider, Configurable, Stoppable, ServiceRegistryAwareService {
    @Serial
    private static final long serialVersionUID = 1L;

    private @Nullable MongoDialectSettings config;
    private @Nullable MongoClient mongoClient;

    private boolean servicesWereInjected = false;
    private @Nullable MongoDialectConfigurator mongoDialectConfigurator;

    @Override
    public Connection getConnection() throws SQLException {
        try {
            var client = assertNotNull(mongoClient);
            var clientSession = client.startSession();
            var initializedConfig = assertNotNull(config);
            return new MongoConnection(initializedConfig, client, clientSession);
        } catch (RuntimeException e) {
            throw new SQLException("Failed to get connection", e);
        }
    }

    @Override
    public void closeConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnknownUnwrapTypeException(unwrapType);
    }

    @Override
    public void configure(Map<String, Object> configProperties) {
        assertTrue(servicesWereInjected);
        var jdbcUrl = configProperties.get(JAKARTA_JDBC_URL);

        if (mongoDialectConfigurator == null && jdbcUrl == null) {
            throw new HibernateException(format(
                    "Configuration property [%s] is required unless %s is provided",
                    JAKARTA_JDBC_URL, MongoDialectConfigurator.class.getName()));
        }

        try {
            MongoDialectSettings.Builder mongoDialectSettingsBuilder = MongoDialectSettings.builder(configProperties);
            if (mongoDialectConfigurator != null) {
                mongoDialectConfigurator.configure(mongoDialectSettingsBuilder);
            }
            config = mongoDialectSettingsBuilder.build();
        } catch (RuntimeException e) {
            throw new HibernateException(
                    format("Failed to construct %s", MongoDialectSettings.class.getSimpleName()), e);
        }

        var driverInfo = MongoDriverInformation.builder()
                .driverName(assertNotNull(BuildConfig.NAME))
                .driverVersion(assertNotNull(BuildConfig.VERSION))
                .build();

        mongoClient = MongoClients.create(config.getMongoClientSettings(), driverInfo);
    }

    @Override
    public void stop() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Override
    public void injectServices(ServiceRegistryImplementor serviceRegistry) {
        try {
            mongoDialectConfigurator = serviceRegistry.getService(MongoDialectConfigurator.class);
        } catch (UnknownServiceException e) {
            // TODO-HIBERNATE-43 `LOGGER.debug("{} is not detected", MongoDialectConfigurator.class.getName(), e)`
        } finally {
            servicesWereInjected = true;
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        throw new NotSerializableException(
                "This class is not designed to be serialized despite it having to implement `Serializable`");
    }

    @VisibleForTesting(otherwise = PRIVATE)
    @Nullable MongoClient getMongoClient() {
        return mongoClient;
    }
}
