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
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.Test;

class SessionFactoryTests {

    @Test
    void test_success() {
        buildSessionFactory(Map.of(JAKARTA_JDBC_URL, "mongodb://localhost/test"));
    }

    @Test
    void test_invalid_connection_String() {
        var exception = assertThrows(
                ServiceException.class,
                () -> buildSessionFactory(Map.of(JAKARTA_JDBC_URL, "jdbc:postgresql://localhost/test")));
        assertInstanceOf(HibernateException.class, exception.getCause());
    }

    @Test
    void test_when_database_absent() {
        var exception = assertThrows(
                ServiceException.class, () -> buildSessionFactory(Map.of(JAKARTA_JDBC_URL, "mongodb://localhost")));
        assertInstanceOf(HibernateException.class, exception.getCause());
    }

    private void buildSessionFactory(Map<String, String> configurationValues) throws ServiceException {
        var cfg = new Configuration(); // default properties will be loaded from conventional resource
        configurationValues.forEach(cfg::setProperty); // override
        try (SessionFactory ignored = cfg.buildSessionFactory()) {
            // no-op
        }
    }
}
