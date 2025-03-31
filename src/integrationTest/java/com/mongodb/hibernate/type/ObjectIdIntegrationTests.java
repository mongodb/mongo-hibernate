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

package com.mongodb.hibernate.type;

import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {ObjectIdIntegrationTests.Item.class})
@ExtendWith(MongoExtension.class)
class ObjectIdIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void insert() {
        var item = new Item();
        item.id = 1;
        item.v = new ObjectId(2, 0);
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollection.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(1))
                        .append("v", new BsonObjectId(item.v)));
    }

    @Test
    void getById() {
        var item = new Item();
        item.id = 1;
        item.v = new ObjectId(2, 0);
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedItem = sessionFactoryScope.fromTransaction(session -> session.get(Item.class, item.id));
        assertThat(loadedItem)
                .usingRecursiveComparison()
                .withStrictTypeChecking()
                .isEqualTo(item);
    }

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Entity
    @Table(name = "items")
    static class Item {
        @Id
        int id;

        ObjectId v;
    }
}
