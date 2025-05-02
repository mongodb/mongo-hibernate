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

package com.mongodb.hibernate.embeddable;

import static com.mongodb.hibernate.MongoTestAssertions.assertEquals;
import static com.mongodb.hibernate.MongoTestAssertions.assertUsingRecursiveComparison;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
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
            StructAggregateEmbeddableIntegrationTests.ItemWithNestedValues.class,
            StructAggregateEmbeddableIntegrationTests.ItemWithOmittedEmptyValue.class,
            StructAggregateEmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingNonInsertable.class,
            StructAggregateEmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingAllNonInsertable.class,
            StructAggregateEmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingNonUpdatable.class
        })
@ExtendWith(MongoExtension.class)
class StructAggregateEmbeddableIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void testNestedValues() {
        var item = new ItemWithNestedValues();
        {
            item.nestedId = new StructAggregateEmbeddableValue();
            item.nestedId.a = 1;
            item.nested1 = new StructAggregateEmbeddableValue();
            item.nested1.a = 2;
            item.nested2 = new StructAggregateEmbeddablePairValue1();
            item.nested2.a = 3;
            item.nested2.nested = new StructAggregateEmbeddablePairValue2(4, 5);
            item.nested2.parent = item;
        }
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                // Hibernate ORM flattens `item.id` despite it being of an aggregate type
                """
                {
                    _id: 1,
                    nested1: {
                        a: 2
                    },
                    nested2: {
                        a: 3,
                        nested: {
                            a: 4,
                            b: 5
                        }
                    }
                }
                """);
        var loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithNestedValues.class, item.nestedId));
        assertEquals(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithNestedValues.class, item.nestedId);
            result.nested1.a = -result.nested1.a;
            return result;
        });
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    nested1: {
                        a: -2
                    },
                    nested2: {
                        a: 3,
                        nested: {
                            a: 4,
                            b: 5
                        }
                    }
                }
                """);
        loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithNestedValues.class, item.nestedId));
        assertEquals(updatedItem, loadedItem);
    }

    @Test
    void testNestedEmptyValue() {
        var item = new ItemWithOmittedEmptyValue();
        {
            item.id = 1;
            item.omitted = new StructAggregateEmbeddableEmptyValue();
        }
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                // Hibernate ORM does not store/read the empty `item.omitted` value.
                // See https://hibernate.atlassian.net/browse/HHH-11936 for more details.
                """
                {
                    _id: 1
                }
                """);
        var loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithOmittedEmptyValue.class, item.id));
        assertUsingRecursiveComparison(item, loadedItem, (assertion, actual) -> assertion
                .ignoringFields("omitted")
                .isEqualTo(actual));
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithOmittedEmptyValue.class, item.id);
            result.omitted = null;
            return result;
        });
        assertCollectionContainsExactly(
                """
                {
                    _id: 1
                }
                """);
        loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithOmittedEmptyValue.class, item.id));
        assertEquals(updatedItem, loadedItem);
    }

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    private static void assertCollectionContainsExactly(String json) {
        assertThat(mongoCollection.find()).containsExactly(BsonDocument.parse(json));
    }

    @Entity
    @Table(name = "items")
    static class ItemWithNestedValues {
        @Id
        StructAggregateEmbeddableValue nestedId;

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
    static class ItemWithOmittedEmptyValue {
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
                            .addAnnotatedClass(
                                    StructAggregateEmbeddableIntegrationTests.Unsupported.ItemWithPairValueAsId.class)
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
            assertCollectionContainsExactly(
                    // `item.omitted` is considered empty because all its persistent attributes are non-insertable.
                    // Hibernate ORM does not store/read the empty `item.omitted` value.
                    // See https://hibernate.atlassian.net/browse/HHH-11936 for more details.
                    """
                    {
                        _id: 1
                    }
                    """);
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
        static class ItemWithPairValueAsId {
            @Id
            StructAggregateEmbeddableIntegrationTests.StructAggregateEmbeddablePairValue2 id;
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
