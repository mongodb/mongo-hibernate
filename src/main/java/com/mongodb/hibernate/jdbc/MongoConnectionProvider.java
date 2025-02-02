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
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoDriverInformation;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.BuildConfig;
import com.mongodb.hibernate.internal.ConfigurationHelper;
import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.service.MongoClientCustomizer;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.hibernate.HibernateException;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.UnknownServiceException;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.service.spi.Stoppable;
import org.jspecify.annotations.Nullable;

/**
 * {@linkplain com.mongodb.hibernate.dialect.MongoDialect MongoDB dialect}'s customized JDBC {@link ConnectionProvider}
 * SPI implementation.
 *
 * <p>{@link MongoConnectionProvider} uses the {@value JdbcSettings#JAKARTA_JDBC_URL} configuration property to create a
 * MongoDB <a href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection string</a>. The
 * configuration property is mandatory if {@link MongoClientCustomizer} service is not used. Otherwise, a
 * {@link ConnectionString} instance will be provided to the
 * {@link MongoClientCustomizer#customize(MongoClientSettings.Builder, ConnectionString)} method as a reference
 * ({@code null} if {@value JdbcSettings#JAKARTA_JDBC_URL} is not configured), and it is up to the
 * {@code MongoClientCustomizer} to use it or not.
 *
 * @see ConnectionProvider
 * @see JdbcSettings#JAKARTA_JDBC_URL
 * @see MongoClientCustomizer
 * @see <a href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection string</a>
 */
public final class MongoConnectionProvider
        implements ConnectionProvider, Configurable, Stoppable, ServiceRegistryAwareService {

    @Serial
    private static final long serialVersionUID = 1L;

    private @Nullable MongoClient mongoClient;

    private @Nullable MongoClientCustomizer mongoClientCustomizer;

    private int batchSize;

    @Override
    public Connection getConnection() throws SQLException {
        try {
            var client = assertNotNull(mongoClient);
            var clientSession = client.startSession();
            return new MongoConnection(client, clientSession, batchSize != 0);
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
    public void configure(Map<String, Object> configurationValues) {

        batchSize = ConfigurationHelper.getInt(AvailableSettings.STATEMENT_BATCH_SIZE, configurationValues, 0);

        var jdbcUrl = configurationValues.get(JAKARTA_JDBC_URL);

        if (mongoClientCustomizer == null && jdbcUrl == null) {
            throw new HibernateException(format(
                    "Configuration property [%s] is required unless %s is provided",
                    JAKARTA_JDBC_URL, MongoClientCustomizer.class.getName()));
        }

        var connectionString = jdbcUrl == null ? null : getConnectionString(jdbcUrl);

        final MongoClientSettings.Builder clientSettingsBuilder;
        if (mongoClientCustomizer != null) {
            clientSettingsBuilder = MongoClientSettings.builder();
            mongoClientCustomizer.customize(clientSettingsBuilder, connectionString);
        } else {
            clientSettingsBuilder = MongoClientSettings.builder().applyConnectionString(connectionString);
        }

        var clientSettings = clientSettingsBuilder.build();

        var driverInfo = MongoDriverInformation.builder()
                .driverName(assertNotNull(BuildConfig.NAME))
                .driverVersion(assertNotNull(BuildConfig.VERSION))
                .build();

        mongoClient = MongoClients.create(clientSettings, driverInfo);
    }

    private static ConnectionString getConnectionString(Object jdbcUrl) {
        if (!(jdbcUrl instanceof String)) {
            throw new HibernateException(
                    format("Configuration property [%s] value [%s] not of string type", JAKARTA_JDBC_URL, jdbcUrl));
        }
        try {
            return new ConnectionString((String) jdbcUrl);
        } catch (RuntimeException e) {
            throw new HibernateException(
                    format(
                            "Failed to create ConnectionString from configuration property [%s] with value [%s]",
                            JAKARTA_JDBC_URL, jdbcUrl),
                    e);
        }
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
            mongoClientCustomizer = serviceRegistry.getService(MongoClientCustomizer.class);
        } catch (UnknownServiceException ignored) {
            // no-op
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
