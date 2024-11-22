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

import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.service.MongoClientCustomizer;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.sql.Connection;
import java.util.Map;
import org.hibernate.HibernateException;
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
 * <p>{@link MongoConnectionProvider} uses the {@value JdbcSettings#JAKARTA_JDBC_URL} to create a MongoDB <a
 * href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection string</a>. The configuration is
 * mandatory if {@link MongoClientCustomizer} service is not used. Otherwise, a {@link ConnectionString} instance will
 * be provided to {@link MongoClientCustomizer#customize(MongoClientSettings.Builder, ConnectionString)} method as a
 * reference ({@code null} if {@value JdbcSettings#JAKARTA_JDBC_URL} is not configured), and it is up to the
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

    @Override
    public Connection getConnection() {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-29");
    }

    @Override
    public void closeConnection(Connection connection) {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-29");
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
    public void configure(Map<String, Object> configValues) {
        var jdbcUrl = configValues.get(JAKARTA_JDBC_URL);

        if (mongoClientCustomizer == null && jdbcUrl == null) {
            throw new HibernateException(format(
                    "Configuration [%s] is required unless MongoClientCustomizer is provided", JAKARTA_JDBC_URL));
        }

        var connectionString = jdbcUrl == null ? null : getConnectionString(jdbcUrl);

        final MongoClientSettings.Builder clientSettingsBuilder;
        if (mongoClientCustomizer != null) {
            clientSettingsBuilder = MongoClientSettings.builder();
            mongoClientCustomizer.customize(clientSettingsBuilder, connectionString);
        } else {
            clientSettingsBuilder = MongoClientSettings.builder().applyConnectionString(connectionString);
        }
        mongoClient = MongoClients.create(clientSettingsBuilder.build());
    }

    private ConnectionString getConnectionString(Object jdbcUrl) {
        if (!(jdbcUrl instanceof String)) {
            throw new HibernateException(
                    format("Configuration [%s] value [%s] not of string type", JAKARTA_JDBC_URL, jdbcUrl));
        }
        try {
            return new ConnectionString((String) jdbcUrl);
        } catch (RuntimeException e) {
            throw new HibernateException(
                    format(
                            "Failed to create ConnectionString from configuration [%s] with value [%s]",
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
