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

package com.mongodb.hibernate.internal;

import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

/** @hidden */
@SuppressWarnings("MissingSummary")
public final class MongoConstants {

    private MongoConstants() {}

    /**
     * We model a query parameter marker as the BSON {@code undefined} value (see {@code AstParameterMarker}) and render
     * it here as the JDBC standard {@code ?} marker (unquoted, hence {@link org.bson.json.StrictJsonWriter#writeRaw}).
     * That way parameterized queries look the same in Hibernate ORM's SQL logs whether they originate from HQL or from
     * a native query. The JDBC adapter turns {@code ?} back into the BSON {@code undefined} marker before binding, so
     * {@code {"$undefined": true}} never escapes the driver.
     */
    public static final JsonWriterSettings EXTENDED_JSON_WRITER_SETTINGS = JsonWriterSettings.builder()
            .outputMode(JsonMode.EXTENDED)
            .undefinedConverter((value, writer) -> writer.writeRaw("?"))
            .build();

    public static final String MONGO_DBMS_NAME = "MongoDB";
    public static final String MONGO_JDBC_DRIVER_NAME = MONGO_DBMS_NAME + " Java Driver JDBC Adapter";
    public static final String ID_FIELD_NAME = "_id";

    public static final String MONGO_DIALECT_SHORT_NAME = "MongoDB";

    /**
     * JPA property key used to pass a {@code MongoConfigurationContributor} object via the JPA properties map. Read by
     * {@code StandardServiceRegistryScopedState}, written by {@code MongoHibernateAutoConfiguration} in the Spring Boot
     * autoconfigure module (which duplicates this string as a private constant since it cannot access this internal
     * class).
     */
    public static final String MONGO_CONFIGURATION_CONTRIBUTOR_KEY = "com.mongodb.hibernate.configurationContributor";
}
