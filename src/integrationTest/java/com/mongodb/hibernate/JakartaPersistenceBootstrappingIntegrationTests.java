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

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Jpa(
        exportSchema = false,
        integrationSettings =
                @Setting(
                        name = AvailableSettings.CONNECTION_PROVIDER,
                        value = "com.mongodb.hibernate.jdbc.MongoConnectionProvider"),
        annotatedClasses = JakartaPersistenceBootstrappingIntegrationTests.Item.class)
@ExtendWith(MongoExtension.class)
class JakartaPersistenceBootstrappingIntegrationTests {

    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> collection;

    @Test
    void smoke(EntityManagerFactoryScope scope) {
        scope.inTransaction(em -> {
            var item = new Item();
            item.id = 1;
            em.persist(item);
        });
        assertThat(collection.find()).containsExactly(BsonDocument.parse("{_id: 1}"));
    }

    @Entity
    @Table(name = "items")
    static class Item {
        @Id
        int id;
    }
}
