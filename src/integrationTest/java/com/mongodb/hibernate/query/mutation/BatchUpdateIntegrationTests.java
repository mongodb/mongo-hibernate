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

package com.mongodb.hibernate.query.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bson.RawBsonDocument.parse;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.bson.BsonDocument;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = BatchUpdateIntegrationTests.Item.class)
@ServiceRegistry(settings = @Setting(name = AvailableSettings.STATEMENT_BATCH_SIZE, value = "3"))
class BatchUpdateIntegrationTests extends AbstractQueryIntegrationTests {

    private static final String COLLECTION_NAME = "items";
    private static final int ENTITIES_TO_PERSIST_COUNT = 5;

    @InjectMongoCollection(COLLECTION_NAME)
    private static MongoCollection<BsonDocument> collection;

    @BeforeEach
    void beforeEach() {
        getTestCommandListener().clear();
    }

    @Test
    void testBatchInsert() {
        getSessionFactoryScope().inTransaction(session -> {
            for (int i = 1; i <= ENTITIES_TO_PERSIST_COUNT; i++) {
                session.persist(new Item(i, String.valueOf(i)));
            }
            session.flush();
            assertActualCommand(
                    parse(
                            """
                             {
                                 "insert": "items",
                                 "ordered": true,
                                 "documents": [
                                   { "_id": 1, "string": "1"},
                                   { "_id": 2, "string": "2"},
                                   { "_id": 3, "string": "3"}
                                 ]
                             }
                            """),
                    parse(
                            """
                             {
                                 "insert": "items",
                                 "ordered": true,
                                 "documents": [
                                    { "_id": 4, "string": "4"},
                                    { "_id": 5, "string": "5"}
                                 ]
                             }
                            """));
        });

        assertThat(collection.find())
                .containsExactlyElementsOf(List.of(
                        BsonDocument.parse("{ _id: 1, string: '1' }"),
                        BsonDocument.parse("{ _id: 2, string: '2' }"),
                        BsonDocument.parse("{ _id: 3, string: '3' }"),
                        BsonDocument.parse("{ _id: 4, string: '4' }"),
                        BsonDocument.parse("{ _id: 5, string: '5' }")));
    }

    @Test
    void testBatchUpdate() {
        getSessionFactoryScope().inTransaction(session -> {
            insertTestData(session);
            for (int i = 1; i <= ENTITIES_TO_PERSIST_COUNT; i++) {
                Item item = session.find(Item.class, i);
                item.string = "u" + i;
            }
            session.flush();
            assertActualCommand(
                    parse(
                            """
                            {
                              "update": "items",
                              "ordered": true,
                              "updates": [
                                { "q": { "_id": { "$eq": 1 } }, "u": { "$set": { "string": "u1" } }, "multi": true },
                                { "q": { "_id": { "$eq": 2 } }, "u": { "$set": { "string": "u2" } }, "multi": true },
                                { "q": { "_id": { "$eq": 3 } }, "u": { "$set": { "string": "u3" } }, "multi": true }
                              ]
                            }
                            """),
                    parse(
                            """
                            {
                              "update": "items",
                              "ordered": true,
                              "updates": [
                                { "q": { "_id": { "$eq": 4 } }, "u": { "$set": { "string": "u4" } }, "multi": true },
                                { "q": { "_id": { "$eq": 5 } }, "u": { "$set": { "string": "u5" } }, "multi": true }
                              ]
                            }
                            """));
        });

        assertThat(collection.find())
                .containsExactlyElementsOf(java.util.List.of(
                        BsonDocument.parse("{ _id: 1, string: 'u1' }"),
                        BsonDocument.parse("{ _id: 2, string: 'u2' }"),
                        BsonDocument.parse("{ _id: 3, string: 'u3' }"),
                        BsonDocument.parse("{ _id: 4, string: 'u4' }"),
                        BsonDocument.parse("{ _id: 5, string: 'u5' }")));
    }

    @Test
    void testBatchDelete() {
        getSessionFactoryScope().inTransaction(session -> {
            insertTestData(session);
            for (int i = 1; i <= ENTITIES_TO_PERSIST_COUNT; i++) {
                var item = session.find(Item.class, i);
                session.remove(item);
            }
            session.flush();
            assertActualCommand(
                    parse(
                            """
                            {
                                "delete": "items",
                                "ordered": true,
                                "deletes": [
                                    {"q": {"_id": {"$eq": 1}}, "limit": 0},
                                    {"q": {"_id": {"$eq": 2}}, "limit": 0},
                                    {"q": {"_id": {"$eq": 3}}, "limit": 0}
                                ]
                            }
                            """),
                    parse(
                            """
                            {
                                "delete": "items",
                                "ordered": true,
                                "deletes": [
                                    {"q": {"_id": {"$eq": 4}}, "limit": 0},
                                    {"q": {"_id": {"$eq": 5}}, "limit": 0}
                                ]
                            }
                            """));
        });

        assertThat(collection.find()).isEmpty();
    }

    private void insertTestData(final SessionImplementor session) {
        for (int i = 1; i <= 5; i++) {
            session.persist(new Item(i, String.valueOf(i)));
        }
        session.flush();
        getTestCommandListener().clear();
    }

    @Entity
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        String string;

        Item() {}

        Item(int id, String string) {
            this.id = id;
            this.string = string;
        }
    }
}
