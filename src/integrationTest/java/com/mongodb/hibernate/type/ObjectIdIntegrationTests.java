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

import static com.mongodb.hibernate.MongoTestAssertions.assertEquals;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.annotations.ObjectIdGenerator;
import com.mongodb.hibernate.internal.type.ObjectIdJavaType;
import com.mongodb.hibernate.internal.type.ObjectIdJdbcType;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcType;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            ObjectIdIntegrationTests.Item.class,
            ObjectIdIntegrationTests.ItemGenerated.class,
            ObjectIdIntegrationTests.ItemGeneratedWithPropertyAccess.class
        })
@ExtendWith(MongoExtension.class)
class ObjectIdIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void insert() {
        var item = new Item();
        item.id = 1;
        item.v = new ObjectId(1, 0);
        // TODO-HIBERNATE-74 https://jira.mongodb.org/browse/HIBERNATE-74,
        // TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 Make sure `item.vNull` is set to `null`
        // when we implement `MongoPreparedStatement.setNull` properly.
        item.vNull = new ObjectId();
        item.vExplicitlyAnnotatedNotForThePublic = new ObjectId(2, 3);
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollection.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(1))
                        .append("v", new BsonObjectId(item.v))
                        .append("vNull", new BsonObjectId(item.vNull))
                        .append(
                                "vExplicitlyAnnotatedNotForThePublic",
                                new BsonObjectId(item.vExplicitlyAnnotatedNotForThePublic)));
    }

    @Test
    void getById() {
        var item = new Item();
        item.id = 1;
        item.v = new ObjectId(2, 0);
        // TODO-HIBERNATE-74 https://jira.mongodb.org/browse/HIBERNATE-74,
        // TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 Make sure `item.vNull` is set to `null`
        // when we implement `MongoPreparedStatement.setNull` properly.
        item.vNull = new ObjectId();
        item.vExplicitlyAnnotatedNotForThePublic = new ObjectId(3, 4);
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        var loadedItem = sessionFactoryScope.fromTransaction(session -> session.get(Item.class, item.id));
        assertEquals(item, loadedItem);
    }

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Nested
    class Generated {
        @Test
        void insert() {
            var item = new ItemGenerated();
            item.id = 1;
            sessionFactoryScope.inTransaction(session -> session.persist(item));
            assertNotNull(item.v);
            assertThat(mongoCollection.find())
                    .containsExactly(new BsonDocument()
                            .append(ID_FIELD_NAME, new BsonInt32(item.id))
                            .append("v", new BsonObjectId(item.v)));
        }

        @Test
        void insertWithPropertyAccess() {
            var item = new ItemGeneratedWithPropertyAccess().setId(1);
            sessionFactoryScope.inTransaction(session -> session.persist(item));
            assertNotNull(item.getV());
            assertThat(mongoCollection.find())
                    .containsExactly(new BsonDocument()
                            .append(ID_FIELD_NAME, new BsonInt32(item.getId()))
                            .append("v", new BsonObjectId(item.getV())));
        }

        @Test
        void assignedValue() {
            var v = new ObjectId(1, 0);
            var item = new ItemGenerated();
            item.id = 1;
            item.v = v;
            sessionFactoryScope.inTransaction(session -> session.persist(item));
            assertEquals(v, item.v);
        }
    }

    @Entity
    @Table(name = "items")
    static class Item {
        @Id
        int id;

        ObjectId v;
        ObjectId vNull;

        @JavaType(ObjectIdJavaType.class)
        @JdbcType(ObjectIdJdbcType.class)
        ObjectId vExplicitlyAnnotatedNotForThePublic;
    }

    @Entity
    @Table(name = "items")
    static class ItemGenerated {
        @Id
        int id;

        @ObjectIdGenerator
        ObjectId v;
    }

    @Entity
    @Table(name = "items")
    static class ItemGeneratedWithPropertyAccess {
        private int id;
        private ObjectId v;

        @Id
        int getId() {
            return id;
        }

        ItemGeneratedWithPropertyAccess setId(int id) {
            this.id = id;
            return this;
        }

        @ObjectIdGenerator
        ObjectId getV() {
            return v;
        }

        ItemGeneratedWithPropertyAccess setV(ObjectId v) {
            this.v = v;
            return this;
        }
    }
}
