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
import com.mongodb.hibernate.cfg.spi.MongoConfigurationContributor;
import com.mongodb.hibernate.internal.Sealed;
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
 *             <td>Is {@linkplain MongoClientSettings.Builder#applyConnectionString(ConnectionString) based} on
 *             the {@link ConnectionString} {@linkplain ConnectionString#ConnectionString(String) constructed} from
 *             {@value AvailableSettings#JAKARTA_JDBC_URL}, if the latter is configured; otherwise a {@link MongoClientSettings}
 *             instance with its defaults.</td>
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
 *     </tbody>
 * </table>
 *
 * @see MongoConfigurationContributor
 */
@Sealed
public interface MongoConfigurator {
    /**
     * Configures {@link MongoClientSettings}.
     *
     * <p>Note that if you {@linkplain MongoClientSettings.Builder#applyConnectionString(ConnectionString) apply} a
     * {@link ConnectionString} with the {@linkplain ConnectionString#getDatabase() database name} configured, you still
     * must configure the database name via {@link MongoConfigurator#databaseName(String)}, as there is no way for that
     * to happen automatically.
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
}
