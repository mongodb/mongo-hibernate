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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.List;
import org.bson.BsonDocument;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            UpsertIntegrationTests.Item.class,
            UpsertIntegrationTests.AuditedItem.class,
            UpsertIntegrationTests.TalliedItem.class,
            UpsertIntegrationTests.VersionedItem.class,
            UpsertIntegrationTests.StampedItem.class,
            UpsertIntegrationTests.BareItem.class
        })
class UpsertIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection("items")
    private MongoCollection<BsonDocument> itemCollection;

    @InjectMongoCollection("auditedItems")
    private MongoCollection<BsonDocument> auditedItemCollection;

    @InjectMongoCollection("stampedItems")
    private MongoCollection<BsonDocument> stampedItemCollection;

    @Test
    void upsertInsertsWhenNoDocumentMatches() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new Item(1, 10, "a"));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "items", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"label": "a", "v": 10}}, "upsert": true}]}
                            """));
        });
        assertThat(itemCollection.find())
                .containsExactly(BsonDocument.parse("""
                        {"_id": 1, "label": "a", "v": 10}"""));
    }

    @Test
    void upsertUpdatesWhenDocumentMatches() {
        getSessionFactoryScope().inStatelessTransaction(session -> session.upsert(new Item(1, 10, "a")));
        getSessionFactoryScope().inStatelessTransaction(session -> {
            // discard the seeding upsert's command; the extension only clears before the test body
            commandHistory.clear();
            session.upsert(new Item(1, 20, "b"));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "items", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"label": "b", "v": 20}}, "upsert": true}]}
                            """));
        });
        assertThat(itemCollection.find())
                .containsExactly(BsonDocument.parse("""
                        {"_id": 1, "label": "b", "v": 20}"""));
    }

    @Test
    void upsertMultipleBatchesIntoOneCommand() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsertMultiple(List.of(new Item(1, 10, "a"), new Item(2, 20, "b")));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "items", "updates": [
                              {"q": {"_id": {"$eq": 1}}, "u": {"$set": {"label": "a", "v": 10}}, "upsert": true},
                              {"q": {"_id": {"$eq": 2}}, "u": {"$set": {"label": "b", "v": 20}}, "upsert": true}]}
                            """));
        });
        assertThat(itemCollection.find())
                .containsExactly(
                        BsonDocument.parse("""
                                {"_id": 1, "label": "a", "v": 10}"""),
                        BsonDocument.parse("""
                                {"_id": 2, "label": "b", "v": 20}"""));
    }

    @Test
    void nonUpdatableAttributeGoesToSetOnInsert() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new AuditedItem(1, "first", "jeff"));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "auditedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"label": "first"}, "$setOnInsert": {"createdBy": "jeff"}}, "upsert": true}]}
                            """));
        });
        assertThat(auditedItemCollection.find())
                .containsExactly(BsonDocument.parse(
                        """
                        {"_id": 1, "createdBy": "jeff", "label": "first"}"""));

        getSessionFactoryScope()
                .inStatelessTransaction(session -> session.upsert(new AuditedItem(1, "second", "intruder")));
        assertThat(auditedItemCollection.find())
                .containsExactly(BsonDocument.parse(
                        """
                        {"_id": 1, "createdBy": "jeff", "label": "second"}"""));
    }

    @Test
    void upsertIsIdempotent() {
        getSessionFactoryScope().inStatelessTransaction(session -> session.upsert(new Item(1, 10, "a")));
        getSessionFactoryScope().inStatelessTransaction(session -> session.upsert(new Item(1, 10, "a")));
        assertThat(itemCollection.find())
                .containsExactly(BsonDocument.parse("""
                        {"_id": 1, "label": "a", "v": 10}"""));
    }

    @Test
    void upsertMatchingWithoutModifyingSucceeds() {
        getSessionFactoryScope().inStatelessTransaction(session -> session.upsert(new StampedItem(1, "jeff")));
        getSessionFactoryScope().inStatelessTransaction(session -> session.upsert(new StampedItem(1, "intruder")));
        assertThat(stampedItemCollection.find())
                .containsExactly(BsonDocument.parse("""
                        {"_id": 1, "createdBy": "jeff"}"""));
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        @Test
        void upsertOnVersionedEntityIsNotSupported() {
            getSessionFactoryScope().inStatelessTransaction(session -> assertThatThrownBy(
                            () -> session.upsert(new VersionedItem(1, "a")))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage("TODO-HIBERNATE-216 https://jira.mongodb.org/browse/HIBERNATE-216"));
        }

        @Test
        void upsertOnNonInsertableAttributeIsNotSupported() {
            getSessionFactoryScope().inStatelessTransaction(session -> assertThatThrownBy(
                            () -> session.upsert(new TalliedItem(1, "a", 5)))
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage("MongoDB does not support upserting a column that is updatable but not insertable:"
                            + " [tally]"));
        }

        @Test
        void upsertOnIdOnlyEntityIsNotSupported() {
            getSessionFactoryScope()
                    .inStatelessTransaction(
                            session -> assertThatThrownBy(() -> session.upsert(new BareItem(1)))
                                    .isInstanceOf(FeatureNotSupportedException.class)
                                    .hasMessage(
                                            "MongoDB does not support upserting an entity whose only persistent attribute is its identifier"));
        }
    }

    @Entity(name = "Item")
    @Table(name = "items")
    static class Item {
        @Id
        int id;

        int v;
        String label;

        Item() {}

        Item(int id, int v, String label) {
            this.id = id;
            this.v = v;
            this.label = label;
        }
    }

    @Entity(name = "AuditedItem")
    @Table(name = "auditedItems")
    static class AuditedItem {
        @Id
        int id;

        String label;

        @Column(updatable = false)
        String createdBy;

        AuditedItem() {}

        AuditedItem(int id, String label, String createdBy) {
            this.id = id;
            this.label = label;
            this.createdBy = createdBy;
        }
    }

    @Entity(name = "TalliedItem")
    @Table(name = "talliedItems")
    static class TalliedItem {
        @Id
        int id;

        String label;

        @Column(insertable = false)
        Integer tally;

        TalliedItem() {}

        TalliedItem(int id, String label, Integer tally) {
            this.id = id;
            this.label = label;
            this.tally = tally;
        }
    }

    @Entity(name = "VersionedItem")
    @Table(name = "versionedItems")
    static class VersionedItem {
        @Id
        int id;

        String label;

        @Version
        int version;

        VersionedItem() {}

        VersionedItem(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    @Entity(name = "StampedItem")
    @Table(name = "stampedItems")
    static class StampedItem {
        @Id
        int id;

        @Column(updatable = false)
        String createdBy;

        StampedItem() {}

        StampedItem(int id, String createdBy) {
            this.id = id;
            this.createdBy = createdBy;
        }
    }

    @Entity(name = "BareItem")
    @Table(name = "bareItems")
    static class BareItem {
        @Id
        int id;

        BareItem() {}

        BareItem(int id) {
            this.id = id;
        }
    }
}
