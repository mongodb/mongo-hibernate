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

import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_PASSWORD;
import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;
import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_USER;
import static org.hibernate.internal.util.NullnessUtil.castNonNull;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.hibernate.HibernateException;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.Stoppable;
import org.jspecify.annotations.Nullable;

/**
 * {@linkplain com.mongodb.hibernate.dialect.MongoDialect MongoDB dialect}'s customized JDBC {@link ConnectionProvider}
 * SPI implementation.
 *
 * <p>{@link MongoConnectionProvider} uses the following Hibernate properties:
 *
 * <table>
 *     <tr><th>Property</th><th>Description</th><th>Required</th></tr>
 *     <tr>
 *         <td>{@value JdbcSettings#JAKARTA_JDBC_URL}</td>
 *         <td>MongoDB
 *         <a href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection string</a>,
 *         which must specify the database name for authentication
 *         if {@value JdbcSettings#JAKARTA_JDBC_USER} is specified.</td>
 *         <td>✓</td>
 *     </tr>
 *     <tr>
 *         <td>{@value JdbcSettings#JAKARTA_JDBC_USER}</td>
 *         <td>{@code userName} for {@link com.mongodb.MongoCredential#createCredential(String, String, char[])}</td>
 *         <td></td>
 *     </tr>
 *     <tr>
 *         <td>{@value JdbcSettings#JAKARTA_JDBC_PASSWORD}</td>
 *         <td>{@code password} for {@link com.mongodb.MongoCredential#createCredential(String, String, char[])}</td>
 *         <td></td>
 *     </tr>
 * </table>
 *
 * @see ConnectionProvider
 * @see JdbcSettings#JAKARTA_JDBC_URL
 * @see JdbcSettings#JAKARTA_JDBC_USER
 * @see JdbcSettings#JAKARTA_JDBC_PASSWORD
 * @see <a href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection string</a>
 */
public final class MongoConnectionProvider implements ConnectionProvider, Configurable, Stoppable {

    @Serial
    private static final long serialVersionUID = 1L;

    private @Nullable MongoClient mongoClient;

    @Override
    public Connection getConnection() throws SQLException {
        try {
            // mongoClient should have been set in configure(Map<String,Object>)
            ClientSession clientSession = castNonNull(mongoClient).startSession();
            return new MongoConnection(clientSession);
        } catch (RuntimeException e) {
            throw new SQLException("Failed to start session", e);
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
    public void configure(Map<String, Object> configValues) {
        var jdbcUrl = configValues.get(JAKARTA_JDBC_URL);
        if (jdbcUrl == null) {
            throw new HibernateException("Configuration is required: " + JAKARTA_JDBC_URL);
        }
        if (!(jdbcUrl instanceof String)) {
            throw new HibernateException(
                    String.format("Configuration [%s] value [%s] not of string type", JAKARTA_JDBC_URL, jdbcUrl));
        }
        ConnectionString connectionString;
        try {
            connectionString = new ConnectionString((String) jdbcUrl);
        } catch (RuntimeException e) {
            throw new HibernateException(
                    String.format(
                            "Failed to create ConnectionString from configuration [%s] with value [%s]",
                            JAKARTA_JDBC_URL, jdbcUrl),
                    e);
        }

        var clientSettingsBuilder = MongoClientSettings.builder().applyConnectionString(connectionString);

        if (configValues.get(JAKARTA_JDBC_USER) != null || configValues.get(JAKARTA_JDBC_PASSWORD) != null) {
            throw new NotYetImplementedException("To be implemented after auth could be tested in CI");
        }

        var clientSettings = clientSettingsBuilder.build();
        this.mongoClient = MongoClients.create(clientSettings);
    }

    @Override
    public void stop() {
        if (this.mongoClient != null) {
            this.mongoClient.close();
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        throw new NotSerializableException(
                "This class is not designed to be serialized despite it having to implement `Serializable`");
    }
}
