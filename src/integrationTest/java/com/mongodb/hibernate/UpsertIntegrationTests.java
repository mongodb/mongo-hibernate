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
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.List;
import org.bson.BsonDocument;
import org.hibernate.annotations.Parent;
import org.hibernate.annotations.Struct;
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
            UpsertIntegrationTests.BareItem.class,
            UpsertIntegrationTests.AddressedItem.class,
            UpsertIntegrationTests.NestedlyAddressedItem.class,
            UpsertIntegrationTests.AuditedAddressedItem.class,
            UpsertIntegrationTests.TaggedItem.class,
            UpsertIntegrationTests.DualAddressedItem.class,
            UpsertIntegrationTests.CatalogedItem.class,
            UpsertIntegrationTests.ParentedItem.class,
            UpsertIntegrationTests.RenamedAddressItem.class,
            UpsertIntegrationTests.AddressLabeledItem.class
        })
class UpsertIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection("items")
    private MongoCollection<BsonDocument> itemCollection;

    @InjectMongoCollection("auditedItems")
    private MongoCollection<BsonDocument> auditedItemCollection;

    @InjectMongoCollection("stampedItems")
    private MongoCollection<BsonDocument> stampedItemCollection;

    @InjectMongoCollection("addressedItems")
    private MongoCollection<BsonDocument> addressedItemCollection;

    @InjectMongoCollection("nestedlyAddressedItems")
    private MongoCollection<BsonDocument> nestedlyAddressedItemCollection;

    @InjectMongoCollection("auditedAddressedItems")
    private MongoCollection<BsonDocument> auditedAddressedItemCollection;

    @InjectMongoCollection("taggedItems")
    private MongoCollection<BsonDocument> taggedItemCollection;

    @InjectMongoCollection("dualAddressedItems")
    private MongoCollection<BsonDocument> dualAddressedItemCollection;

    @InjectMongoCollection("catalogedItems")
    private MongoCollection<BsonDocument> catalogedItemCollection;

    @InjectMongoCollection("parentedItems")
    private MongoCollection<BsonDocument> parentedItemCollection;

    @InjectMongoCollection("renamedAddressItems")
    private MongoCollection<BsonDocument> renamedAddressItemCollection;

    @InjectMongoCollection("addressLabeledItems")
    private MongoCollection<BsonDocument> addressLabeledItemCollection;

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

    @Test
    void upsertInsertsStructAggregateEmbeddable() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new AddressedItem(1, "a", new Address("NYC", 10001)));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "addressedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"address": {"city": "NYC", "zipCode": 10001}, "label": "a"}}, "upsert": true}]}
                            """));
        });
        assertThat(addressedItemCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {"_id": 1, "address": {"city": "NYC", "zipCode": 10001}, "label": "a"}"""));
    }

    @Test
    void upsertReplacesWholeStructAggregateEmbeddable() {
        getSessionFactoryScope()
                .inStatelessTransaction(
                        session -> session.upsert(new AddressedItem(1, "a", new Address("NYC", 10001))));
        getSessionFactoryScope().inStatelessTransaction(session -> {
            // discard the seeding upsert's command; the extension only clears before the test body
            commandHistory.clear();
            session.upsert(new AddressedItem(1, "b", new Address(null, 20002)));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "addressedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"address": {"city": null, "zipCode": 20002}, "label": "b"}}, "upsert": true}]}
                            """));
        });
        assertThat(addressedItemCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {"_id": 1, "address": {"city": null, "zipCode": 20002}, "label": "b"}"""));
    }

    @Test
    void upsertMultipleBatchesStructAggregateEmbeddablesIntoOneCommand() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsertMultiple(List.of(
                    new AddressedItem(1, "a", new Address("NYC", 10001)),
                    new AddressedItem(2, "b", new Address("LA", 90001))));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "addressedItems", "updates": [
                              {"q": {"_id": {"$eq": 1}}, "u": {"$set": {"address": {"city": "NYC", "zipCode": 10001}, "label": "a"}}, "upsert": true},
                              {"q": {"_id": {"$eq": 2}}, "u": {"$set": {"address": {"city": "LA", "zipCode": 90001}, "label": "b"}}, "upsert": true}]}
                            """));
        });
        assertThat(addressedItemCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {"_id": 1, "address": {"city": "NYC", "zipCode": 10001}, "label": "a"}"""),
                        BsonDocument.parse(
                                """
                                {"_id": 2, "address": {"city": "LA", "zipCode": 90001}, "label": "b"}"""));
    }

    @Test
    void upsertInsertsNestedStructAggregateEmbeddable() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new NestedlyAddressedItem(1, new NestedAddress("NYC", new Zone(7))));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "nestedlyAddressedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"address": {"city": "NYC", "zone": {"code": 7}}}}, "upsert": true}]}
                            """));
        });
        assertThat(nestedlyAddressedItemCollection.find())
                .containsExactly(BsonDocument.parse(
                        """
                        {"_id": 1, "address": {"city": "NYC", "zone": {"code": 7}}}"""));
    }

    @Test
    void upsertSplitsStructAggregateEmbeddableAndNonUpdatableColumnAcrossOperators() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new AuditedAddressedItem(1, "a", new Address("NYC", 10001), "jeff"));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "auditedAddressedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"address": {"city": "NYC", "zipCode": 10001}, "label": "a"}, "$setOnInsert": {"createdBy": "jeff"}}, "upsert": true}]}
                            """));
        });
        assertThat(auditedAddressedItemCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {"_id": 1, "address": {"city": "NYC", "zipCode": 10001}, "createdBy": "jeff", "label": "a"}"""));
    }

    @Test
    void upsertWritesArraysAndPlainEmbeddables() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new TaggedItem(1, new int[] {2, 3}, new Coordinates(4, 5)));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "taggedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"x": 4, "y": 5, "tags": [2, 3]}}, "upsert": true}]}
                            """));
        });
        assertThat(taggedItemCollection.find())
                .containsExactly(BsonDocument.parse(
                        """
                                           {"_id": 1, "x": 4, "y": 5, "tags": [2, 3]}"""));
    }

    @Test
    void upsertInsertsTwoStructAggregateEmbeddables() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new DualAddressedItem(1, new Address("NYC", 10001), new Address("Boston", 20002)));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "dualAddressedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"homeAddress": {"city": "NYC", "zipCode": 10001}, "workAddress": {"city": "Boston", "zipCode": 20002}}}, "upsert": true}]}
                            """));
        });
        assertThat(dualAddressedItemCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {"_id": 1, "homeAddress": {"city": "NYC", "zipCode": 10001}, "workAddress": {"city": "Boston", "zipCode": 20002}}"""));
    }

    @Test
    void upsertUpdatesTwoStructAggregateEmbeddables() {
        getSessionFactoryScope()
                .inStatelessTransaction(session -> session.upsert(
                        new DualAddressedItem(1, new Address("NYC", 10001), new Address("Boston", 20002))));
        getSessionFactoryScope().inStatelessTransaction(session -> {
            // discard the seeding upsert's command; the extension only clears before the test body
            commandHistory.clear();
            session.upsert(new DualAddressedItem(1, new Address("LA", 90001), new Address("Miami", 33101)));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "dualAddressedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"homeAddress": {"city": "LA", "zipCode": 90001}, "workAddress": {"city": "Miami", "zipCode": 33101}}}, "upsert": true}]}
                            """));
        });
        assertThat(dualAddressedItemCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {"_id": 1, "homeAddress": {"city": "LA", "zipCode": 90001}, "workAddress": {"city": "Miami", "zipCode": 33101}}"""));
    }

    @Test
    void upsertMultipleBatchesTwoStructAggregateEmbeddablesIntoOneCommand() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsertMultiple(List.of(
                    new DualAddressedItem(1, new Address("NYC", 10001), new Address("Boston", 20002)),
                    new DualAddressedItem(2, new Address("LA", 90001), new Address("Miami", 33101))));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "dualAddressedItems", "updates": [
                              {"q": {"_id": {"$eq": 1}}, "u": {"$set": {"homeAddress": {"city": "NYC", "zipCode": 10001}, "workAddress": {"city": "Boston", "zipCode": 20002}}}, "upsert": true},
                              {"q": {"_id": {"$eq": 2}}, "u": {"$set": {"homeAddress": {"city": "LA", "zipCode": 90001}, "workAddress": {"city": "Miami", "zipCode": 33101}}}, "upsert": true}]}
                            """));
        });
        assertThat(dualAddressedItemCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {"_id": 1, "homeAddress": {"city": "NYC", "zipCode": 10001}, "workAddress": {"city": "Boston", "zipCode": 20002}}"""),
                        BsonDocument.parse(
                                """
                                {"_id": 2, "homeAddress": {"city": "LA", "zipCode": 90001}, "workAddress": {"city": "Miami", "zipCode": 33101}}"""));
    }

    @Test
    void upsertInsertsStructAggregateEmbeddableContainingAnArray() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new CatalogedItem(1, new Catalog("c", new int[] {2, 3})));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "catalogedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"catalog": {"name": "c", "codes": [2, 3]}}}, "upsert": true}]}
                            """));
        });
        assertThat(catalogedItemCollection.find())
                .containsExactly(BsonDocument.parse(
                        """
                        {"_id": 1, "catalog": {"name": "c", "codes": [2, 3]}}"""));
    }

    @Test
    void upsertInsertsStructAggregateEmbeddableWithParentBackReference() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new ParentedItem(1, new AddressWithParent("NYC")));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "parentedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"address": {"city": "NYC"}}}, "upsert": true}]}
                            """));
        });
        assertThat(parentedItemCollection.find())
                .containsExactly(BsonDocument.parse(
                        """
                                           {"_id": 1, "address": {"city": "NYC"}}"""));
    }

    @Test
    void upsertInsertsNullStructAggregateEmbeddable() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new AddressedItem(1, "a", null));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "addressedItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"address": null, "label": "a"}}, "upsert": true}]}
                            """));
        });
        assertThat(addressedItemCollection.find())
                .containsExactly(BsonDocument.parse(
                        """
                                           {"_id": 1, "address": null, "label": "a"}"""));
    }

    @Test
    void upsertInsertsStructAggregateEmbeddableMappedToRenamedColumn() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new RenamedAddressItem(1, new Address("NYC", 10001)));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "renamedAddressItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"addr": {"city": "NYC", "zipCode": 10001}}}, "upsert": true}]}
                            """));
        });
        assertThat(renamedAddressItemCollection.find())
                .containsExactly(BsonDocument.parse(
                        """
                        {"_id": 1, "addr": {"city": "NYC", "zipCode": 10001}}"""));
    }

    /**
     * {@code addressLabel} shares a prefix with the aggregate column {@code address} but is a distinct scalar column;
     * {@code findAggregate}'s dot boundary must keep the two apart, since a bare prefix match would swallow
     * {@code addressLabel}'s own binding into the aggregate and leave its parameter unmatched.
     */
    @Test
    void upsertKeepsAggregateAndPrefixedScalarColumnDistinct() {
        getSessionFactoryScope().inStatelessTransaction(session -> {
            session.upsert(new AddressLabeledItem(1, new Address("NYC", 10001), "home"));
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {"update": "addressLabeledItems", "updates": [{"q": {"_id": {"$eq": 1}}, "u": {"$set": {"address": {"city": "NYC", "zipCode": 10001}, "addressLabel": "home"}}, "upsert": true}]}
                            """));
        });
        assertThat(addressLabeledItemCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {"_id": 1, "address": {"city": "NYC", "zipCode": 10001}, "addressLabel": "home"}"""));
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

    @Entity(name = "AddressedItem")
    @Table(name = "addressedItems")
    static class AddressedItem {
        @Id
        int id;

        String label;
        Address address;

        AddressedItem() {}

        AddressedItem(int id, String label, Address address) {
            this.id = id;
            this.label = label;
            this.address = address;
        }
    }

    @Embeddable
    @Struct(name = "Address")
    record Address(String city, int zipCode) {}

    @Entity(name = "NestedlyAddressedItem")
    @Table(name = "nestedlyAddressedItems")
    static class NestedlyAddressedItem {
        @Id
        int id;

        NestedAddress address;

        NestedlyAddressedItem() {}

        NestedlyAddressedItem(int id, NestedAddress address) {
            this.id = id;
            this.address = address;
        }
    }

    @Embeddable
    @Struct(name = "NestedAddress")
    record NestedAddress(String city, Zone zone) {}

    @Embeddable
    @Struct(name = "Zone")
    record Zone(int code) {}

    @Entity(name = "AuditedAddressedItem")
    @Table(name = "auditedAddressedItems")
    static class AuditedAddressedItem {
        @Id
        int id;

        String label;
        Address address;

        @Column(updatable = false)
        String createdBy;

        AuditedAddressedItem() {}

        AuditedAddressedItem(int id, String label, Address address, String createdBy) {
            this.id = id;
            this.label = label;
            this.address = address;
            this.createdBy = createdBy;
        }
    }

    @Entity(name = "TaggedItem")
    @Table(name = "taggedItems")
    static class TaggedItem {
        @Id
        int id;

        int[] tags;
        Coordinates coordinates;

        TaggedItem() {}

        TaggedItem(int id, int[] tags, Coordinates coordinates) {
            this.id = id;
            this.tags = tags;
            this.coordinates = coordinates;
        }
    }

    @Embeddable
    record Coordinates(int x, int y) {}

    @Entity(name = "DualAddressedItem")
    @Table(name = "dualAddressedItems")
    static class DualAddressedItem {
        @Id
        int id;

        Address homeAddress;
        Address workAddress;

        DualAddressedItem() {}

        DualAddressedItem(int id, Address homeAddress, Address workAddress) {
            this.id = id;
            this.homeAddress = homeAddress;
            this.workAddress = workAddress;
        }
    }

    @Entity(name = "CatalogedItem")
    @Table(name = "catalogedItems")
    static class CatalogedItem {
        @Id
        int id;

        Catalog catalog;

        CatalogedItem() {}

        CatalogedItem(int id, Catalog catalog) {
            this.id = id;
            this.catalog = catalog;
        }
    }

    @Embeddable
    @Struct(name = "Catalog")
    record Catalog(String name, int[] codes) {}

    @Entity(name = "ParentedItem")
    @Table(name = "parentedItems")
    static class ParentedItem {
        @Id
        int id;

        AddressWithParent address;

        ParentedItem() {}

        ParentedItem(int id, AddressWithParent address) {
            this.id = id;
            this.address = address;
        }
    }

    @Embeddable
    @Struct(name = "AddressWithParent")
    static class AddressWithParent {
        String city;

        @Parent ParentedItem parent;

        AddressWithParent() {}

        AddressWithParent(String city) {
            this.city = city;
        }

        /**
         * Hibernate ORM requires a setter for a {@link Parent} field, despite us using {@linkplain AccessType#FIELD
         * field-based access}.
         */
        void setParent(ParentedItem parent) {
            this.parent = parent;
        }

        /**
         * Hibernate ORM requires a getter for a {@link Parent} field, despite us using {@linkplain AccessType#FIELD
         * field-based access}.
         */
        ParentedItem getParent() {
            return parent;
        }
    }

    @Entity(name = "RenamedAddressItem")
    @Table(name = "renamedAddressItems")
    static class RenamedAddressItem {
        @Id
        int id;

        @Column(name = "addr")
        Address address;

        RenamedAddressItem() {}

        RenamedAddressItem(int id, Address address) {
            this.id = id;
            this.address = address;
        }
    }

    @Entity(name = "AddressLabeledItem")
    @Table(name = "addressLabeledItems")
    static class AddressLabeledItem {
        @Id
        int id;

        Address address;
        String addressLabel;

        AddressLabeledItem() {}

        AddressLabeledItem(int id, Address address, String addressLabel) {
            this.id = id;
            this.address = address;
            this.addressLabel = addressLabel;
        }
    }
}
