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

package com.mongodb.hibernate;

import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.Test;

class SessionFactoryTests {

    @Test
    void testSuccess() {
        buildSessionFactory().close();
    }

    @Test
    void testInvalidConnectionString() {
        var exception = assertThrows(ServiceException.class, () -> buildSessionFactory(
                        Map.of(JAKARTA_JDBC_URL, "jdbc:postgresql://localhost/test"))
                .close());
        assertInstanceOf(HibernateException.class, exception.getCause());
    }

    @Test
    void testOpenSession() {
        try (var sessionFactory = buildSessionFactory()) {
            assertDoesNotThrow(() -> sessionFactory.openSession().close());
        }
    }

    @Test
    void testInTransaction() {
        try (var sessionFactory = buildSessionFactory()) {
            assertDoesNotThrow(() -> sessionFactory.inTransaction(session -> {}));
        }
    }

    private static SessionFactory buildSessionFactory() throws ServiceException {
        return buildSessionFactory(Map.of());
    }

    private static SessionFactory buildSessionFactory(Map<String, Object> settings) throws ServiceException {
        var standardServiceRegistry =
                new StandardServiceRegistryBuilder().applySettings(settings).build();
        return new MetadataSources(standardServiceRegistry)
                .getMetadataBuilder()
                .build()
                .getSessionFactoryBuilder()
                .build();
    }
}
