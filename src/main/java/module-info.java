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
import com.mongodb.hibernate.internal.service.StandardServiceRegistryScopedState;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.service.spi.ServiceContributor;

/** The MongoDB Extension for Hibernate ORM module. */
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
    provides AdditionalMappingContributor with
            MongoAdditionalMappingContributor;

    opens com.mongodb.hibernate.dialect to
            org.hibernate.orm.core;
    opens com.mongodb.hibernate.jdbc to
            org.hibernate.orm.core;
    opens com.mongodb.hibernate.internal.id.objectid to
            org.hibernate.orm.core;

    exports com.mongodb.hibernate.cfg;
    exports com.mongodb.hibernate.cfg.spi;
    exports com.mongodb.hibernate.annotations;
}
