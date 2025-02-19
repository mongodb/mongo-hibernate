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

package com.mongodb.hibernate.dialect;

import static com.mongodb.hibernate.internal.MongoChecks.notNull;
import static java.lang.String.format;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.jdbc.MongoConnectionProvider;
import com.mongodb.hibernate.service.MongoDialectConfigurator;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.service.spi.Configurable;
import org.jspecify.annotations.Nullable;

/**
 * The configuration of {@link MongoDialect}, {@link MongoConnectionProvider}, the internal {@link MongoClient}.
 *
 * <table>
 *     <caption>Supported configuration properties</caption>
 *     <thead>
 *         <tr>
 *             <th>Method</th>
 *             <th>Has default</th>
 *             <th>Related {@linkplain Configurable#configure(Map) configuration property} name</th>
 *             <th>Supported value types of the configuration property</th>
 *             <th>Value, unless overridden via {@link Builder}</th>
 *         </tr>
 *     </thead>
 *     <tbody>
 *         <tr>
 *             <td>{@link #getMongoClientSettings()}</td>
 *             <td>✓</td>
 *             <td>{@value AvailableSettings#JAKARTA_JDBC_URL}</td>
 *             <td>
 *                 <ul>
 *                     <li>{@link String}</li>
 *                     <li>{@link ConnectionString}</li>
 *                 </ul>
 *             </td>
 *             <td>Is {@linkplain MongoClientSettings.Builder#applyConnectionString(ConnectionString) based} on
 *             the {@link ConnectionString} {@linkplain ConnectionString#ConnectionString(String) constructed} from
 *             {@value AvailableSettings#JAKARTA_JDBC_URL}, if the latter is configured; otherwise a {@link MongoClientSettings}
 *             instance with its defaults.</td>
 *         </tr>
 *         <tr>
 *             <td>{@link #getDatabaseName()}</td>
 *             <td>✗</td>
 *             <td>{@value AvailableSettings#JAKARTA_JDBC_URL}</td>
 *             <td>
 *                 <ul>
 *                     <li>{@link String}</li>
 *                     <li>{@link ConnectionString}</li>
 *                 </ul>
 *             </td>
 *             <td>The MongoDB database name from {@value AvailableSettings#JAKARTA_JDBC_URL},
 *             if {@linkplain ConnectionString#getDatabase() configured};
 *             otherwise there is no default value, and a value must be configured via {@link Builder#databaseName(String)}.</td>
 *         </tr>
 *     </tbody>
 * </table>
 *
 * @see MongoDialectConfigurator
 */
public final class MongoDialectSettings {
    private final MongoClientSettings mongoClientSettings;
    private final String databaseName;

    private MongoDialectSettings(Builder builder) {
        this.mongoClientSettings = builder.mongoClientSettingsBuilder.build();
        this.databaseName = notNull("databaseName", builder.databaseName);
    }

    /**
     * Gets the {@link MongoClientSettings} to be used when creating the internal {@link MongoClient}.
     *
     * @return The {@link MongoClientSettings}.
     * @see Builder#applyToMongoClientSettings(Consumer)
     */
    public MongoClientSettings getMongoClientSettings() {
        return mongoClientSettings;
    }

    /**
     * Gets the name of a MongoDB database used as the {@linkplain Connection#getSchema() JDBC schema} of a
     * {@linkplain Connection connection} {@linkplain MongoConnectionProvider#getConnection() obtained} from
     * {@link MongoConnectionProvider}.
     *
     * @return The name of the default MongoDB database.
     * @see Builder#databaseName(String)
     * @see DatabaseMetaData#getSchemaTerm()
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Creates a new {@link MongoDialectSettings.Builder} based on {@code configProperties}.
     *
     * @param configProperties The {@linkplain Configurable#configure(Map) configuration properties}.
     * @return A new {@link MongoDialectSettings.Builder}.
     */
    public static MongoDialectSettings.Builder builder(Map<String, Object> configProperties) {
        return new MongoDialectSettings.Builder(notNull("configProperties", configProperties));
    }

    /** A builder for {@code MongoDialectSettings}. */
    public static final class Builder {
        private final MongoClientSettings.Builder mongoClientSettingsBuilder;
        private @Nullable String databaseName;

        private Builder(Map<String, Object> configProperties) {
            mongoClientSettingsBuilder = MongoClientSettings.builder();
            var connectionString = ConfigPropertiesParser.getConnectionString(configProperties);
            if (connectionString != null) {
                mongoClientSettingsBuilder.applyConnectionString(connectionString);
                databaseName = connectionString.getDatabase();
            }
        }

        /**
         * Configures {@link MongoDialectSettings}.
         *
         * <p>Note that if you {@link MongoClientSettings.Builder#applyConnectionString(ConnectionString)} with
         * {@linkplain ConnectionString#getDatabase() database name} configured, you still must configure the database
         * name via {@link Builder#databaseName(String)}, as there is no way for that to happen automatically.
         *
         * @param configurator The {@link Consumer} of the {@link MongoClientSettings.Builder}.
         * @return {@code this}.
         * @see MongoDialectSettings#getMongoClientSettings()
         */
        public MongoDialectSettings.Builder applyToMongoClientSettings(
                final Consumer<MongoClientSettings.Builder> configurator) {
            notNull("configurator", configurator).accept(mongoClientSettingsBuilder);
            return this;
        }

        /**
         * Sets the name of a MongoDB database used as the {@linkplain Connection#getSchema() JDBC schema} of a
         * {@linkplain Connection connection} {@linkplain MongoConnectionProvider#getConnection() obtained} from
         * {@link MongoConnectionProvider}.
         *
         * @param databaseName The name of the default MongoDB database.
         * @return {@code this}.
         * @see MongoDialectSettings#getDatabaseName()
         * @see DatabaseMetaData#getSchemaTerm()
         */
        public MongoDialectSettings.Builder databaseName(String databaseName) {
            this.databaseName = notNull("databaseName", databaseName);
            return this;
        }

        /**
         * Creates a new {@link MongoDialectSettings}.
         *
         * @return A new {@link MongoDialectSettings}.
         */
        public MongoDialectSettings build() {
            return new MongoDialectSettings(this);
        }

        private static final class ConfigPropertiesParser {
            static @Nullable ConnectionString getConnectionString(Map<String, Object> configProperties) {
                var jdbcUrl = configProperties.get(JAKARTA_JDBC_URL);
                if (jdbcUrl == null) {
                    return null;
                }
                if (jdbcUrl instanceof String jdbcUrlText) {
                    return parseConnectionString(JAKARTA_JDBC_URL, jdbcUrlText);
                } else if (jdbcUrl instanceof ConnectionString jdbcUrlConnectionString) {
                    return jdbcUrlConnectionString;
                } else {
                    throw Exceptions.unsupportedType(JAKARTA_JDBC_URL, jdbcUrl, String.class, ConnectionString.class);
                }
            }

            private static ConnectionString parseConnectionString(String propertyName, String propertyValue) {
                try {
                    return new ConnectionString(propertyValue);
                } catch (RuntimeException e) {
                    throw Exceptions.failedToParse(propertyName, propertyValue, ConnectionString.class);
                }
            }

            private static final class Exceptions {
                static RuntimeException unsupportedType(
                        String propertyName, Object propertyValue, Type... expectedTypes) {
                    return new RuntimeException(format(
                            "Type %s of configuration property [%s] with value [%s] must be one of %s",
                            propertyValue.getClass().getTypeName(),
                            propertyName,
                            propertyValue,
                            Arrays.stream(expectedTypes).map(Type::getTypeName).collect(Collectors.joining(", "))));
                }

                static RuntimeException failedToParse(String propertyName, String propertyValue, Type type) {
                    return new RuntimeException(format(
                            "Failed to get %s from configuration property [%s] with value [%s]",
                            type.getTypeName(), propertyName, propertyValue));
                }
            }
        }
    }
}
