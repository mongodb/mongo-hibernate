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

import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.NotYetImplementedException;
import com.mongodb.hibernate.cfg.ConfigurationException;
import com.mongodb.hibernate.cfg.ConfigurationHelper;
import java.sql.Connection;
import java.util.Map;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.Stoppable;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB dialect's customized JDBC {@link ConnectionProvider} SPI implementation, whose class name could be provided
 * using configuration property {@value org.hibernate.cfg.JdbcSettings#CONNECTION_PROVIDER}.
 *
 * <p>The following Hibernate JDBC properties will be relied upon by Hibernate's {@link Configurable} SPI mechanism:
 *
 * <ul>
 *   <li>{@linkplain org.hibernate.cfg.JdbcSettings#JAKARTA_JDBC_URL jakarta.persistence.jdbc.url}
 *   <li>{@linkplain org.hibernate.cfg.JdbcSettings#JAKARTA_JDBC_USER jakarta.persistence.jdbc.user}
 *   <li>{@linkplain org.hibernate.cfg.JdbcSettings#JAKARTA_JDBC_PASSWORD jakarta.persistence.jdbc.password}
 * </ul>
 *
 * <p>{@value org.hibernate.cfg.JdbcSettings#JAKARTA_JDBC_URL} property is mandatory and it maps to MongoDB's <a
 * href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection string</a>, in which database name
 * must be provided to align with JDBC URL's convention. The other two JDBC properties are optional.
 *
 * @see ConnectionProvider
 * @see Configurable
 * @see JdbcSettings#JAKARTA_JDBC_URL
 * @see JdbcSettings#JAKARTA_JDBC_USER
 * @see JdbcSettings#JAKARTA_JDBC_PASSWORD
 */
public class MongoConnectionProvider implements ConnectionProvider, Configurable, Stoppable {

    private @Nullable MongoClient mongoClient;

    @Override
    public Connection getConnection() {
        throw new NotYetImplementedException("to be implemented at https://jira.mongodb.org/browse/HIBERNATE-29");
    }

    @Override
    public void closeConnection(Connection connection) {
        throw new NotYetImplementedException("to be implemented at https://jira.mongodb.org/browse/HIBERNATE-29");
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
        var jdbcUrl = ConfigurationHelper.getRequiredConfiguration(configurationValues, JAKARTA_JDBC_URL);
        ConnectionString connectionString;
        try {
            connectionString = new ConnectionString(jdbcUrl);
        } catch (IllegalArgumentException iae) {
            throw new ConfigurationException(JAKARTA_JDBC_URL, "invalid MongoDB connection string", iae);
        }
        var database = connectionString.getDatabase();
        if (database == null) {
            throw new ConfigurationException(JAKARTA_JDBC_URL, "database must be provided");
        }

        var clientSettingsBuilder = MongoClientSettings.builder().applyConnectionString(connectionString);

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