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

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;
import static com.mongodb.hibernate.MongoTestAssertions.assertUsingRecursiveComparison;
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
import org.hibernate.annotations.Parent;
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
            EmbeddableIntegrationTests.ItemWithOmittedEmptyValue.class
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
            item.flattenedId = new SingleValue();
            item.flattenedId.a = 1;
            item.flattened1 = new SingleValue();
            item.flattened1.a = 2;
            item.flattened2 = new MultiValueWithParent();
            item.flattened2.a = 3;
            item.flattened2.flattened = new MultiValue(4, 5);
            item.flattened2.parent = item;
        }
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    flattened1_a: 2,
                    flattened2_a: 3,
                    flattened2_flattened_a: 4,
                    flattened2_flattened_b: 5
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValues.class, item.flattenedId));
        assertEq(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithFlattenedValues.class, item.flattenedId);
            result.flattened1.a = -result.flattened1.a;
            return result;
        });
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    flattened1_a: -2,
                    flattened2_a: 3,
                    flattened2_flattened_a: 4,
                    flattened2_flattened_b: 5
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValues.class, updatedItem.flattenedId));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testFlattenedEmptyValue() {
        var item = new ItemWithOmittedEmptyValue();
        {
            item.id = 1;
            item.omitted = new EmptyValue();
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
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithOmittedEmptyValue.class, updatedItem.id));
        assertEq(updatedItem, loadedItem);
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
    static class ItemWithFlattenedValues {
        @Id
        SingleValue flattenedId;

        @AttributeOverride(name = "a", column = @Column(name = "flattened1_a"))
        SingleValue flattened1;

        @AttributeOverride(name = "a", column = @Column(name = "flattened2_a"))
        @AttributeOverride(name = "flattened.a", column = @Column(name = "flattened2_flattened_a"))
        @AttributeOverride(name = "flattened.b", column = @Column(name = "flattened2_flattened_b"))
        MultiValueWithParent flattened2;
    }

    @Embeddable
    static class SingleValue {
        int a;
    }

    @Embeddable
    static class MultiValueWithParent {
        int a;
        MultiValue flattened;

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
    record MultiValue(int a, int b) {}

    @Entity
    @Table(name = "items")
    static class ItemWithOmittedEmptyValue {
        @Id
        int id;

        EmptyValue omitted;
    }

    @Embeddable
    static class EmptyValue {}

    @Nested
    class Unsupported {
        @Test
        void testPrimaryKeySpanningMultipleFields() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithMultiValueAsId.class)
                            .buildMetadata())
                    .hasMessageContaining("does not support primary key spanning multiple columns");
        }

        @Entity
        @Table(name = "items")
        static class ItemWithMultiValueAsId {
            @Id
            MultiValue id;
        }
    }
}
