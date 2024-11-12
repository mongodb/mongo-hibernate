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

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import org.hibernate.HibernateException;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.Stoppable;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.sql.Connection;
import java.util.Map;

import static org.hibernate.cfg.JdbcSettings.*;

/**
 * {@linkplain com.mongodb.hibernate.dialect.MongoDialect MongoDB dialect}'s customized JDBC {@link ConnectionProvider}
 * SPI implementation.
 *
 * <p>{@link MongoConnectionProvider} uses the following Hibernate properties:
 *
 * <ul>
 *   <li>{@linkplain JdbcSettings#JAKARTA_JDBC_URL jakarta.persistence.jdbc.url}
 *   <li>{@linkplain JdbcSettings#JAKARTA_JDBC_USER jakarta.persistence.jdbc.user}
 *   <li>{@linkplain JdbcSettings#JAKARTA_JDBC_PASSWORD jakarta.persistence.jdbc.password}
 * </ul>
 *
 * <p>as follows:
 *
 * <p>
 *
 * <table>
 *     <tr><th>Property</th><th>Description</th></tr>
 *     <tr>
 *         <td>{@linkplain JdbcSettings#JAKARTA_JDBC_URL jakarta.persistence.jdbc.url}</td>
 *         <td>MongoDB
 *         <a href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection string</a></td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain JdbcSettings#JAKARTA_JDBC_USER jakarta.persistence.jdbc.user}</td>
 *         <td>{@code userName} for {@link com.mongodb.MongoCredential#createCredential(String, String, char[])}</td>
 *     </tr>
 *     <tr>
 *         <td>{@linkplain JdbcSettings#JAKARTA_JDBC_PASSWORD jakarta.persistence.jdbc.password}</td>
 *         <td>{@code password} for {@link com.mongodb.MongoCredential#createCredential(String, String, char[])}</td>
 *     </tr>
 * </table>
 *
 * <p>{@value JdbcSettings#JAKARTA_JDBC_URL} property is mandatory and it maps to MongoDB's <a
 * href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection string</a>, in which database name
 * must be provided to align with JDBC URL's convention. The other two JDBC properties are optional.
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
        var database = connectionString.getDatabase();
        if (database == null) {
            throw new HibernateException(String.format(
                    "Database must be provided in ConnectionString from configuration [%s] with value [%s]",
                    JAKARTA_JDBC_URL, jdbcUrl));
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
}
