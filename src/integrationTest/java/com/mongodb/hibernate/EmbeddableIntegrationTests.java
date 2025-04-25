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

import static com.mongodb.hibernate.MongoTestAssertions.assertEquals;
import static com.mongodb.hibernate.MongoTestAssertions.assertNotEquals;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.AccessType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.hibernate.annotations.Parent;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.MetadataSources;
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
            EmbeddableIntegrationTests.ItemWithFlattenedValues.class,
            EmbeddableIntegrationTests.ItemWithNestedValues.class,
            EmbeddableIntegrationTests.ItemWithNestedEmptyValue.class,
            EmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingNonInsertable.class,
            EmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingAllNonInsertable.class,
            EmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingNonUpdatable.class
        })
@ExtendWith(MongoExtension.class)
class EmbeddableIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void testFlattenedValues() {
        var item = new ItemWithFlattenedValues();
        {
            item.id = new EmbeddableValue();
            item.id.a = 1;
            item.flattened1 = new EmbeddableValue();
            item.flattened1.a = 2;
            item.flattened2 = new EmbeddablePairValue1();
            item.flattened2.a = 3;
            item.flattened2.flattened = new EmbeddablePairValue2(4, 5);
            item.flattened2.parent = item;
        }
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollection.find())
                .containsExactly(new BsonDocument()
                        .append(ID_FIELD_NAME, new BsonInt32(item.id.a))
                        .append("flattened1_a", new BsonInt32(2))
                        .append("flattened2_a", new BsonInt32(3))
                        .append("flattened2_flattened_a", new BsonInt32(4))
                        .append("flattened2_flattened_b", new BsonInt32(5)));
        var loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithFlattenedValues.class, item.id));
        assertEquals(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithFlattenedValues.class, item.id);
            result.flattened1.a = -result.flattened1.a;
            return result;
        });
        loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithFlattenedValues.class, item.id));
        assertEquals(updatedItem, loadedItem);
    }

    @Test
    void testNestedValues() {
        var item = new ItemWithNestedValues();
        {
            item.id = new StructAggregateEmbeddableValue();
            item.id.a = 1;
            item.nested1 = new StructAggregateEmbeddableValue();
            item.nested1.a = 2;
            item.nested2 = new StructAggregateEmbeddablePairValue1();
            item.nested2.a = 3;
            item.nested2.nested = new StructAggregateEmbeddablePairValue2(4, 5);
            item.nested2.parent = item;
        }
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollection.find())
                .containsExactly(new BsonDocument()
                        // Hibernate ORM flattens `item.id` despite it being of an aggregate type
                        .append(ID_FIELD_NAME, new BsonInt32(item.id.a))
                        .append("nested1", new BsonDocument("a", new BsonInt32(2)))
                        .append(
                                "nested2",
                                new BsonDocument()
                                        .append("a", new BsonInt32(3))
                                        .append(
                                                "nested",
                                                new BsonDocument()
                                                        .append("a", new BsonInt32(4))
                                                        .append("b", new BsonInt32(5)))));
        var loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithNestedValues.class, item.id));
        assertEquals(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithNestedValues.class, item.id);
            result.nested1.a = -result.nested1.a;
            return result;
        });
        loadedItem = sessionFactoryScope.fromTransaction(session -> session.find(ItemWithNestedValues.class, item.id));
        assertEquals(updatedItem, loadedItem);
    }

    @Test
    void testNestedEmptyValue() {
        var item = new ItemWithNestedEmptyValue();
        {
            item.id = 1;
            item.omitted = new StructAggregateEmbeddableEmptyValue();
        }
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertThat(mongoCollection.find())
                .containsExactly(
                        // Hibernate ORM does not store `item.omitted` despite it being non-`null`
                        new BsonDocument(ID_FIELD_NAME, new BsonInt32(item.id)));
        var loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithNestedEmptyValue.class, item.id));
        // the entity we stored and the entity we loaded are not equal because Hibernate ORM omits `item.omitted`
        assertNotEquals(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithNestedEmptyValue.class, item.id);
            result.omitted = null;
            return result;
        });
        assertThat(mongoCollection.find()).containsExactly(new BsonDocument(ID_FIELD_NAME, new BsonInt32(item.id)));
        loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithNestedEmptyValue.class, item.id));
        assertEquals(updatedItem, loadedItem);
    }

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Entity
    @Table(name = "items")
    static class ItemWithFlattenedValues {
        @Id
        EmbeddableValue id;

        @AttributeOverride(name = "a", column = @Column(name = "flattened1_a"))
        EmbeddableValue flattened1;

        @AttributeOverride(name = "a", column = @Column(name = "flattened2_a"))
        @AttributeOverride(name = "flattened.a", column = @Column(name = "flattened2_flattened_a"))
        @AttributeOverride(name = "flattened.b", column = @Column(name = "flattened2_flattened_b"))
        EmbeddablePairValue1 flattened2;
    }

    @Embeddable
    static class EmbeddableValue {
        int a;
    }

    @Embeddable
    static class EmbeddablePairValue1 {
        int a;
        EmbeddablePairValue2 flattened;

        @Parent ItemWithFlattenedValues parent;

        /**
         * Hibernate ORM requires a getter for a {@link Parent} field, despite us using {@linkplain AccessType#FIELD
         * field-based access}.
         */
        void setParent(ItemWithFlattenedValues parent) {
            this.parent = parent;
        }

        /**
         * Hibernate ORM requires a getter for a {@link Parent} field, despite us using {@linkplain AccessType#FIELD
         * field-based access}.
         */
        ItemWithFlattenedValues getParent() {
            return parent;
        }
    }

    @Embeddable
    record EmbeddablePairValue2(int a, int b) {}

    @Entity
    @Table(name = "items")
    static class ItemWithNestedValues {
        @Id
        StructAggregateEmbeddableValue id;

        StructAggregateEmbeddableValue nested1;

        StructAggregateEmbeddablePairValue1 nested2;
    }

    @Embeddable
    @Struct(name = "StructAggregateEmbeddableValue")
    static class StructAggregateEmbeddableValue {
        int a;
    }

    @Embeddable
    @Struct(name = "StructAggregateEmbeddablePairValue1")
    static class StructAggregateEmbeddablePairValue1 {
        int a;
        StructAggregateEmbeddablePairValue2 nested;

        @Parent ItemWithNestedValues parent;

        /**
         * Hibernate ORM requires a getter for a {@link Parent} field, despite us using {@linkplain AccessType#FIELD
         * field-based access}.
         */
        void setParent(ItemWithNestedValues parent) {
            this.parent = parent;
        }

        /**
         * Hibernate ORM requires a getter for a {@link Parent} field, despite us using {@linkplain AccessType#FIELD
         * field-based access}.
         */
        ItemWithNestedValues getParent() {
            return parent;
        }
    }

    @Embeddable
    @Struct(name = "StructAggregateEmbeddablePairValue2")
    record StructAggregateEmbeddablePairValue2(int a, int b) {}

    @Entity
    @Table(name = "items")
    static class ItemWithNestedEmptyValue {
        @Id
        int id;

        StructAggregateEmbeddableEmptyValue omitted;
    }

    @Embeddable
    @Struct(name = "StructAggregateEmbeddableEmptyValue")
    static class StructAggregateEmbeddableEmptyValue {}

    @Nested
    class Unsupported {
        @Test
        void testPrimaryKeySpanningMultipleFields() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithFlattenedPairValueAsId.class)
                            .buildMetadata())
                    .hasMessageContaining("does not support primary key spanning multiple columns");
        }

        @Test
        void testNonInsertable() {
            var item = new ItemWithNestedValueHavingNonInsertable(
                    1, new StructAggregateEmbeddablePairValueHavingNonInsertable(2, 3));
            assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                    .hasMessageContaining("must be insertable");
        }

        @Test
        void testAllNonInsertable() {
            var item = new ItemWithNestedValueHavingAllNonInsertable();
            {
                item.id = 1;
                item.omitted = new StructAggregateEmbeddablePairValueAllNonInsertable(2, 3);
            }
            sessionFactoryScope.inTransaction(session -> session.persist(item));
            assertThat(mongoCollection.find())
                    .containsExactly(
                            // Hibernate ORM does not persist `item.omitted`, because all its persistent attributes are
                            // non-insertable.
                            new BsonDocument(ID_FIELD_NAME, new BsonInt32(item.id)));
            assertThatThrownBy(() -> sessionFactoryScope.fromTransaction(
                            session -> session.find(ItemWithNestedValueHavingAllNonInsertable.class, item.id)))
                    .isInstanceOf(Exception.class);
        }

        @Test
        void testNonUpdatable() {
            sessionFactoryScope.inTransaction(session -> {
                var nested = new StructAggregateEmbeddablePairValueHavingNonUpdatable();
                {
                    nested.a = 2;
                    nested.b = 3;
                }
                var item = new ItemWithNestedValueHavingNonUpdatable(1, nested);
                session.persist(item);
                assertThatThrownBy(session::flush).hasMessageContaining("must be updatable");
            });
        }

        @Entity
        @Table(name = "items")
        static class ItemWithFlattenedPairValueAsId {
            @Id
            EmbeddablePairValue2 id;
        }

        @Entity
        @Table(name = "items")
        record ItemWithNestedValueHavingNonInsertable(
                @Id int id, StructAggregateEmbeddablePairValueHavingNonInsertable nested) {}

        @Embeddable
        @Struct(name = "StructAggregateEmbeddablePairValueHavingNonInsertable")
        record StructAggregateEmbeddablePairValueHavingNonInsertable(@Column(insertable = false) int a, int b) {}

        @Entity
        @Table(name = "items")
        record ItemWithNestedValueHavingNonUpdatable(
                @Id int id, StructAggregateEmbeddablePairValueHavingNonUpdatable nested) {}

        @Embeddable
        @Struct(name = "StructAggregateEmbeddablePairValueHavingNonUpdatable")
        static class StructAggregateEmbeddablePairValueHavingNonUpdatable {
            @Column(updatable = false)
            int a;

            int b;
        }

        @Entity
        @Table(name = "items")
        static class ItemWithNestedValueHavingAllNonInsertable {
            @Id
            int id;

            StructAggregateEmbeddablePairValueAllNonInsertable omitted;
        }

        @Embeddable
        @Struct(name = "StructAggregateEmbeddablePairValueAllNonInsertable")
        record StructAggregateEmbeddablePairValueAllNonInsertable(
                @Column(insertable = false) int a, @Column(insertable = false) int b) {}
    }
}
