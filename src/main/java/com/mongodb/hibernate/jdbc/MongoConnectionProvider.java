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

import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.hibernate.cfg.JdbcSettings.*;
import static org.hibernate.internal.util.NullnessUtil.castNonNull;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.cfg.ConfigurationHelper;
import com.mongodb.hibernate.exception.ConfigurationException;
import com.mongodb.hibernate.exception.NotYetImplementedException;
import java.sql.Connection;
import java.util.Map;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.Configurable;
import org.hibernate.service.spi.Startable;
import org.hibernate.service.spi.Stoppable;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB dialect's customized JDBC {@link ConnectionProvider} SPI implementation, whose class name is supposed to be
 * provided as the following Hibernate property to kick off MongoDB dialect's JDBC flow:
 *
 * <ul>
 *   <li>{@linkplain org.hibernate.cfg.JdbcSettings#CONNECTION_PROVIDER hibernate.connection.provider_class}
 * </ul>
 *
 * <p>The following Hibernate JDBC properties will be relied upon by Hibernate's {@link Configurable} SPI mechanism:
 *
 * <ul>
 *   <li>{@linkplain org.hibernate.cfg.JdbcSettings#JAKARTA_JDBC_URL jakarta.persistence.jdbc.url}
 *   <li>{@linkplain org.hibernate.cfg.JdbcSettings#JAKARTA_JDBC_USER jakarta.persistence.jdbc.user}
 *   <li>{@linkplain org.hibernate.cfg.JdbcSettings#JAKARTA_JDBC_PASSWORD jakarta.persistence.jdbc.password}
 * </ul>
 *
 * <p>{@linkplain org.hibernate.cfg.JdbcSettings#JAKARTA_JDBC_URL jakarta.persistence.jdbc.url} property is mandatory
 * and it maps to MongoDB's <a href="https://www.mongodb.com/docs/manual/reference/connection-string/">connection
 * string</a>, in which database name must be provided to align with JDBC URL's convention. The other two JDBC
 * properties are optional.
 *
 * @see ConnectionProvider
 * @see Configurable
 */
public class MongoConnectionProvider implements ConnectionProvider, Configurable, Startable, Stoppable {

    // non-null after configure(Map<String, Object>) method is invoked successfully
    private @Nullable ConnectionString connectionString;
    private @Nullable String database;

    // non-null after start() method is invoked successfully
    private @Nullable MongoClient mongoClient;

    private @Nullable String user;
    private @Nullable String password;

    @Override
    public Connection getConnection() {
        throw new NotYetImplementedException();
    }

    @Override
    public void closeConnection(Connection connection) {
        throw new NotYetImplementedException();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false; // won't be used in container
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return ConnectionProvider.class.equals(unwrapType)
                || MongoConnectionProvider.class.isAssignableFrom(unwrapType);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return unwrapType.cast(this);
        } else {
            throw new UnknownUnwrapTypeException(unwrapType);
        }
    }

    @Override
    public void configure(Map<String, Object> configurationValues) {
        var jdbcUrl = ConfigurationHelper.getRequiredConfiguration(configurationValues, JAKARTA_JDBC_URL);
        try {
            this.connectionString = new ConnectionString(jdbcUrl);
        } catch (IllegalArgumentException iae) {
            throw new ConfigurationException(JAKARTA_JDBC_URL, "invalid MongoDB connection string", iae);
        }
        var database = this.connectionString.getDatabase();
        if (database == null) {
            throw new ConfigurationException(JAKARTA_JDBC_URL, "database must be provided");
        }
        this.database = database;
        this.user = ConfigurationHelper.getOptionalConfiguration(configurationValues, JAKARTA_JDBC_USER);
        this.password = ConfigurationHelper.getOptionalConfiguration(configurationValues, JAKARTA_JDBC_PASSWORD);
    }

    @Override
    public void start() {
        // connectionString and database are set as mandatory values in the above configure method
        // if either is unset, exception would have been thrown and this method invocation would have be skipped
        castNonNull(this.connectionString);
        castNonNull(this.database);

        var clientSettingsBuilder = MongoClientSettings.builder().applyConnectionString(connectionString);
        if (this.user != null) {
            var password = this.password == null ? null : this.password.toCharArray();
            var credential = MongoCredential.createCredential(this.user, this.database, password);
            clientSettingsBuilder.credential(credential);
        }

        var codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry());
        clientSettingsBuilder.codecRegistry(codecRegistry);

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
