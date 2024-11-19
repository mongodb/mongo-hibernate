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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionTests {

    private SessionFactory sessionFactory;

    @BeforeEach
    void setUp() {
        sessionFactory = new Configuration().buildSessionFactory();
    }

    @AfterEach
    void tearDown() {
        sessionFactory.close();
    }

    @Test
    void testSessionFactoryOpenSession() {
        assertNotNull(sessionFactory.openSession());
    }

    @Test
    void testSessionDoWork() {
        sessionFactory.inSession(session -> session.doWork(connection -> {}));
    }
}
