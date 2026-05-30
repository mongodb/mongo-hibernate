/*
 * Copyright 2025-present MongoDB, Inc.
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

import com.mongodb.hibernate.internal.boot.MongoAdditionalMappingContributor;
import com.mongodb.hibernate.internal.service.MongoNamedStrategyContributor;
import com.mongodb.hibernate.internal.service.StandardServiceRegistryScopedState;
import org.hibernate.boot.registry.selector.spi.NamedStrategyContributor;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.service.spi.ServiceContributor;

/**
 * The MongoDB Extension for Hibernate ORM module.
 *
 * <p>Configure Hibernate using the short names registered by this module:
 *
 * <ul>
 *   <li>{@code hibernate.dialect = MongoDB} — the MongoDB {@linkplain org.hibernate.dialect.Dialect Dialect} for
 *       MongoDB 7.0 and above, to be used together with the connection provider below.
 *   <li>{@code hibernate.connection.provider_class = mongodb} — the MongoDB
 *       {@linkplain org.hibernate.engine.jdbc.connections.spi.ConnectionProvider ConnectionProvider}. All work done via
 *       a connection obtained from this provider is done within the same {@link com.mongodb.client.ClientSession}.
 *       {@linkplain com.mongodb.client.ClientSession#startTransaction() MongoDB transactions} are used only if
 *       {@linkplain java.sql.Connection#getAutoCommit() auto-commit} is disabled.
 * </ul>
 *
 * <p>Example {@code hibernate.properties}:
 *
 * <pre>{@code
 * hibernate.dialect = MongoDB
 * hibernate.connection.provider_class = mongodb
 * jakarta.persistence.jdbc.url = mongodb://localhost/mydb
 * }</pre>
 *
 * <table>
 *   <caption>Default type mapping</caption>
 *   <thead>
 *     <tr>
 *       <th>Java type</th>
 *       <th><a href="https://www.mongodb.com/docs/manual/reference/bson-types/">BSON type</a></th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td><a href="https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.1">null type</a></td>
 *       <td>BSON {@code Null}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code byte[]}</td>
 *       <td>BSON {@code Binary data} with subtype 0</td>
 *     </tr>
 *     <tr>
 *       <td>{@code char}, {@link Character}, {@link String}, {@code char[]}</td>
 *       <td>BSON {@code String}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code int}, {@link Integer}</td>
 *       <td>BSON {@code 32-bit integer}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code long}, {@link Long}</td>
 *       <td>BSON {@code 64-bit integer}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code double}, {@link Double}</td>
 *       <td>BSON {@code Double}</td>
 *     </tr>
 *     <tr>
 *       <td>{@code boolean}, {@link Boolean}</td>
 *       <td>BSON {@code Boolean}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link java.math.BigDecimal}</td>
 *       <td>BSON {@code Decimal128}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link org.bson.types.ObjectId}</td>
 *       <td>BSON {@code ObjectId}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link java.time.Instant}</td>
 *       <td>BSON {@code Date}</td>
 *     </tr>
 *     <tr>
 *       <td>{@link java.sql.Struct} <a
 *           href="https://docs.hibernate.org/orm/6.6/userguide/html_single/#embeddable-mapping-aggregate">aggregate
 *           embeddable</a></td>
 *       <td>BSON {@code Object} — field values are mapped as per this table.</td>
 *     </tr>
 *     <tr>
 *       <td>array, {@link java.util.Collection} (or a subtype supported by Hibernate ORM), except for {@code
 *           byte[]}, {@code char[]}</td>
 *       <td>BSON {@code Array} — elements are mapped as per this table.</td>
 *     </tr>
 *   </tbody>
 * </table>
 */
module com.mongodb.hibernate {
    requires java.naming;
    requires java.sql;
    requires jakarta.persistence;
    requires transitive org.hibernate.orm.core;
    requires org.mongodb.bson;
    requires transitive org.mongodb.driver.core;
    requires org.mongodb.driver.sync.client;
    requires org.jspecify;

    provides ServiceContributor with
            StandardServiceRegistryScopedState.ServiceContributor;
    provides NamedStrategyContributor with
            MongoNamedStrategyContributor;
    provides AdditionalMappingContributor with
            MongoAdditionalMappingContributor;

    opens com.mongodb.hibernate.internal.dialect to
            org.hibernate.orm.core;
    opens com.mongodb.hibernate.internal.jdbc to
            org.hibernate.orm.core;
    opens com.mongodb.hibernate.internal.id.objectid to
            org.hibernate.orm.core;

    exports com.mongodb.hibernate.cfg;
    exports com.mongodb.hibernate.cfg.spi;
    exports com.mongodb.hibernate.annotations;
}
