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

package com.mongodb.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.hibernate.internal.cfg.MongoConfiguration;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Persistence;
import jakarta.persistence.Table;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JakartaPersistenceBootstrappingIntegrationTests {
    private static EntityManagerFactory entityManagerFactory;
    private static MongoConfiguration config;
    private static MongoClient mongoClient;

    @BeforeAll
    static void beforeAll() {
        entityManagerFactory = Persistence.createEntityManagerFactory("test-persistence-unit");
        config = new MongoConfigurationBuilder(entityManagerFactory.getProperties()).build();
        mongoClient = MongoClients.create(config.mongoClientSettings());
    }

    @BeforeEach
    void beforeEach() {
        mongoClient.getDatabase(config.databaseName()).drop();
    }

    @AfterAll
    @SuppressWarnings("try")
    static void afterAll() {
        try (var closed1 = entityManagerFactory;
                var closed2 = mongoClient) {}
    }

    @Test
    void smoke() {
        try (var entityManager = entityManagerFactory.createEntityManager()) {
            var transaction = entityManager.getTransaction();
            try {
                transaction.begin();
                var item = new Item();
                item.id = 1;
                entityManager.persist(item);
            } finally {
                transaction.commit();
            }
            assertEquals(
                    1,
                    mongoClient
                            .getDatabase(config.databaseName())
                            .getCollection("items")
                            .countDocuments());
        }
    }

    @Entity(name = "Item")
    @Table(name = "items")
    static class Item {
        @Id
        @Column(name = "_id")
        int id;
    }
}
