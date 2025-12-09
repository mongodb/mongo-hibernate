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
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_JDBC_DRIVER_NAME;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;

import com.mongodb.MongoDriverInformation;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.internal.BuildConfig;
import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.internal.cfg.MongoConfiguration;
import com.mongodb.hibernate.internal.extension.service.StandardServiceRegistryScopedState;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;
import org.hibernate.HibernateException;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.engine.jdbc.connections.spi.DatabaseConnectionInfo;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.InjectService;
import org.hibernate.service.spi.Stoppable;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ConnectionProvider} for the MongoDB Extension for Hibernate ORM.
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
 * @mongoCme The methods {@link #getConnection()}/{@link #closeConnection(Connection)} must be thread-safe. It is
 *     unclear about the other methods.
 */
public final class MongoConnectionProvider implements ConnectionProvider, Stoppable {
    @Serial
    private static final long serialVersionUID = 1L;

    private @Nullable StandardServiceRegistryScopedState standardServiceRegistryScopedState;
    private transient @Nullable MongoClient mongoClient;

    /** @mongoCme Must be thread-safe. */
    @Override
    public DatabaseConnectionInfo getDatabaseConnectionInfo(Dialect dialect) {
        return new MongoDatabaseConnectionInfo(
                assertNotNull(standardServiceRegistryScopedState).getConfiguration(), dialect.getVersion());
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            var client = assertNotNull(mongoClient);
            var clientSession = client.startSession();
            var config = assertNotNull(standardServiceRegistryScopedState).getConfiguration();
            return new MongoConnection(config, client, clientSession);
        } catch (HibernateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SQLException("Failed to get connection", e);
        }
    }

    /** @mongoCme Must be thread-safe. */
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
    public void stop() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @InjectService
    public void injectStandardServiceRegistryScopedState(
            StandardServiceRegistryScopedState standardServiceRegistryScopedState) {
        this.standardServiceRegistryScopedState = standardServiceRegistryScopedState;
        var mongoClientSettings =
                standardServiceRegistryScopedState.getConfiguration().mongoClientSettings();
        var driverInfo = MongoDriverInformation.builder()
                .driverName(assertNotNull(BuildConfig.NAME))
                .driverVersion(assertNotNull(BuildConfig.VERSION))
                .build();
        mongoClient = MongoClients.create(mongoClientSettings, driverInfo);
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

    private static final class MongoDatabaseConnectionInfo implements DatabaseConnectionInfo {
        private final MongoConfiguration configuration;
        private final @Nullable DatabaseVersion dbmsVersion;

        MongoDatabaseConnectionInfo(MongoConfiguration configuration, @Nullable DatabaseVersion dbmsVersion) {
            this.configuration = configuration;
            this.dbmsVersion = dbmsVersion;
        }

        @Override
        public String getJdbcUrl() {
            return configuration.mongoClientSettings().toString();
        }

        @Override
        public String getJdbcDriver() {
            return MONGO_JDBC_DRIVER_NAME + " " + assertNotNull(BuildConfig.VERSION);
        }

        @Override
        public @Nullable DatabaseVersion getDialectVersion() {
            return dbmsVersion;
        }

        @Override
        public @Nullable String getAutoCommitMode() {
            return null;
        }

        @Override
        public @Nullable String getIsolationLevel() {
            return null;
        }

        @Override
        public Integer getPoolMinSize() {
            return configuration
                    .mongoClientSettings()
                    .getConnectionPoolSettings()
                    .getMinSize();
        }

        @Override
        public Integer getPoolMaxSize() {
            return configuration
                    .mongoClientSettings()
                    .getConnectionPoolSettings()
                    .getMaxSize();
        }

        @Override
        public String toInfoString() {
            var dbmsVersion = getDialectVersion();
            return "\tDMBS: " + MONGO_DBMS_NAME + (dbmsVersion == null ? "" : " " + dbmsVersion)
                    + "\n\tDatabase name: " + configuration.databaseName()
                    + "\n\tDBMS driver: " + getJdbcDriver()
                    + "\n\tDBMS driver settings: " + getJdbcUrl();
        }
    }
}
