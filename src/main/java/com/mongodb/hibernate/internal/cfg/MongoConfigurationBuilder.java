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

package com.mongodb.hibernate.internal.cfg;

import static com.mongodb.hibernate.internal.MongoChecks.notNull;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.hibernate.cfg.MongoConfigurator;
import com.mongodb.hibernate.internal.VisibleForTesting;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public final class MongoConfigurationBuilder implements MongoConfigurator {
    private final MongoClientSettings.Builder mongoClientSettingsBuilder;
    private @Nullable String databaseName;

    public MongoConfigurationBuilder(Map<String, Object> configurationValues) {
        mongoClientSettingsBuilder = MongoClientSettings.builder();
        var connectionString =
                MongoConfigurationBuilder.ConfigPropertiesParser.getConnectionString(configurationValues);
        if (connectionString != null) {
            mongoClientSettingsBuilder.applyConnectionString(connectionString);
            databaseName = connectionString.getDatabase();
        }
    }

    @VisibleForTesting(otherwise = PRIVATE)
    MongoConfigurationBuilder() {
        this(Collections.emptyMap());
    }

    @Override
    public MongoConfigurationBuilder applyToMongoClientSettings(Consumer<MongoClientSettings.Builder> configurator) {
        notNull("configurator", configurator).accept(mongoClientSettingsBuilder);
        return this;
    }

    @Override
    public MongoConfigurationBuilder databaseName(String databaseName) {
        this.databaseName = notNull("databaseName", databaseName);
        return this;
    }

    public MongoConfiguration build() {
        return new MongoConfiguration(mongoClientSettingsBuilder.build(), notNull("databaseName", databaseName));
    }

    private static final class ConfigPropertiesParser {
        static @Nullable ConnectionString getConnectionString(Map<String, Object> configurationValues) {
            var jdbcUrl = configurationValues.get(JAKARTA_JDBC_URL);
            if (jdbcUrl == null) {
                return null;
            }
            if (jdbcUrl instanceof String jdbcUrlText) {
                return parseConnectionString(JAKARTA_JDBC_URL, jdbcUrlText);
            } else if (jdbcUrl instanceof ConnectionString jdbcUrlConnectionString) {
                return jdbcUrlConnectionString;
            } else {
                throw MongoConfigurationBuilder.ConfigPropertiesParser.Exceptions.unsupportedType(
                        JAKARTA_JDBC_URL, jdbcUrl, String.class, ConnectionString.class);
            }
        }

        private static ConnectionString parseConnectionString(String propertyName, String propertyValue) {
            try {
                return new ConnectionString(propertyValue);
            } catch (RuntimeException e) {
                throw MongoConfigurationBuilder.ConfigPropertiesParser.Exceptions.failedToParse(
                        propertyName, propertyValue, ConnectionString.class);
            }
        }

        private static final class Exceptions {
            static RuntimeException unsupportedType(String propertyName, Object propertyValue, Type... expectedTypes) {
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
