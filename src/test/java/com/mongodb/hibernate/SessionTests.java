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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionTests {

    private static SessionFactory sessionFactory;

    private Session session;

    @BeforeAll
    static void beforeAll() {
        sessionFactory = new Configuration().buildSessionFactory();
    }

    @AfterAll
    static void afterAll() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        session = sessionFactory.openSession();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void testDoWork() {
        assertDoesNotThrow(() -> session.doWork(connection -> {}));
    }

    @Test
    void testBeginTransaction() {
        assertDoesNotThrow(() -> session.beginTransaction().commit());
    }
}
