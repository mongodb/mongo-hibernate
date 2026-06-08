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

package com.mongodb.hibernate.cfg;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import java.util.Map;
import java.util.function.Consumer;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.service.spi.Configurable;

/**
 * The configurator of the MongoDB Extension for Hibernate ORM.
 *
 * <table>
 *     <caption>Supported configuration properties</caption>
 *     <thead>
 *         <tr>
 *             <th>Method</th>
 *             <th>Has default</th>
 *             <th>Related {@linkplain Configurable#configure(Map) configuration property} name</th>
 *             <th>Supported value types of the configuration property</th>
 *             <th>Value, unless overridden via {@link MongoConfigurator}</th>
 *         </tr>
 *     </thead>
 *     <tbody>
 *         <tr>
 *             <td>{@link #applyToMongoClientSettings(Consumer)}</td>
 *             <td>✓</td>
 *             <td>{@value AvailableSettings#JAKARTA_JDBC_URL}</td>
 *             <td>
 *                 <ul>
 *                     <li>{@link String}</li>
 *                     <li>{@link ConnectionString}</li>
 *                 </ul>
 *             </td>
 *             <td>
 *                 Is {@linkplain MongoClientSettings.Builder#applyConnectionString(ConnectionString) based} on
 *                 the {@link ConnectionString} {@linkplain ConnectionString#ConnectionString(String) constructed} from
 *                 {@value AvailableSettings#JAKARTA_JDBC_URL}, if the latter is configured; otherwise a {@link MongoClientSettings}
 *                 instance with its defaults.
 *
 *                 <p><strong>Warning:</strong> Do not include sensitive information in {@value AvailableSettings#JAKARTA_JDBC_URL},
 *                 use {@link #applyToMongoClientSettings(Consumer)} instead.
 *             </td>
 *         </tr>
 *         <tr>
 *             <td>{@link #databaseName(String)}</td>
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
 *             otherwise a value must be configured via {@link MongoConfigurator#databaseName(String)}.</td>
 *         </tr>
 *         <tr>
 *             <td>{@link #mongoClient(MongoClient)}</td>
 *             <td>✓</td>
 *             <td>&mdash;</td>
 *             <td>&mdash;</td>
 *             <td>No externally managed client; the extension creates one from the configured
 *             {@link MongoClientSettings}. When a client is supplied, it is used as-is and not closed, and
 *             {@link #applyToMongoClientSettings(Consumer)} and the {@value AvailableSettings#JAKARTA_JDBC_URL}
 *             connection string are not used to build a connection (the database name must still be configured via
 *             {@link #databaseName(String)} or come from {@value AvailableSettings#JAKARTA_JDBC_URL}).</td>
 *         </tr>
 *     </tbody>
 * </table>
 *
 * @see MongoConfigurationContributor
 */
public sealed interface MongoConfigurator permits MongoConfigurationBuilder {
    /**
     * Configures {@link MongoClientSettings}.
     *
     * <p>Note that if you {@linkplain MongoClientSettings.Builder#applyConnectionString(ConnectionString) apply} a
     * {@link ConnectionString} with the {@linkplain ConnectionString#getDatabase() database name} configured, you still
     * must configure the database name via {@link MongoConfigurator#databaseName(String)}, as there is no way for that
     * to happen automatically.
     *
     * <p>These settings are not used if an externally managed client is supplied via {@link #mongoClient(MongoClient)}.
     *
     * @param configurator The {@link Consumer} of the {@link MongoClientSettings.Builder}.
     * @return {@code this}.
     */
    MongoConfigurator applyToMongoClientSettings(Consumer<MongoClientSettings.Builder> configurator);

    /**
     * Sets the name of a MongoDB database to use.
     *
     * @param databaseName The name of a MongoDB database to use.
     * @return {@code this}.
     */
    MongoConfigurator databaseName(String databaseName);

    /**
     * Supplies an externally managed {@link MongoClient} to use instead of creating one from the configured
     * {@link MongoClientSettings}. When supplied, the client's lifecycle is owned by the caller: the extension uses it
     * as-is and does not close it, and {@link #applyToMongoClientSettings(Consumer)} and the
     * {@value AvailableSettings#JAKARTA_JDBC_URL} connection string are not used to build a connection.
     *
     * @param mongoClient the externally managed client to use.
     * @return {@code this}.
     */
    MongoConfigurator mongoClient(MongoClient mongoClient);
}
