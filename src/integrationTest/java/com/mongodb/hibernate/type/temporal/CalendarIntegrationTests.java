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

package com.mongodb.hibernate.type.temporal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Calendar;
import java.util.Collection;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.MetadataSources;
import org.junit.jupiter.api.Test;

class CalendarIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new UnsupportedItems.ItemWithId<Calendar>() {}.getClass()),
                () -> assertNotSupported(new UnsupportedItems.ItemWithFlattenedEmbeddableId<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithBasicPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithArrayPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithCollectionPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithBasicPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithArrayPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithCollectionPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithBasicPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithArrayPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithCollectionPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithBasicPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithArrayPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                Calendar>() {}.getClass()));
    }

    static void assertNotSupported(Class<?> entityClass) {
        assertThatThrownBy(() ->
                        new MetadataSources().addAnnotatedClass(entityClass).buildMetadata())
                .isInstanceOf(FeatureNotSupportedException.class)
                .hasMessageMatching(".*persistent attribute .* has .*type .* that is not supported");
    }

    @Entity
    record ItemWithUnsupportedId(@Id Calendar id) {}

    @Entity
    record ItemWithUnsupportedBasicPersistentAttribute(@Id int id, Calendar v) {}

    @Entity
    record ItemWithUnsupportedArrayPersistentAttribute(@Id int id, Calendar[] v) {}

    @Entity
    record ItemWithUnsupportedCollectionPersistentAttribute(@Id int id, Collection<Calendar> v) {}

    @Entity
    record ItemWithEmbeddableWithBasicPersistentAttribute(@Id int id, EmbeddableWithBasicPersistentAttribute v) {}

    @Entity
    record ItemWithEmbeddableWithArrayPersistentAttribute(@Id int id, EmbeddableWithArrayPersistentAttribute v) {}

    @Entity
    record ItemWithEmbeddableWithCollectionPersistentAttribute(
            @Id int id, EmbeddableWithCollectionPersistentAttribute v) {}

    @Entity
    record ItemWithNestedEmbeddableWithBasicPersistentAttribute(
            @Id int id, EmbeddableNestedWithBasicPersistentAttribute v) {}

    @Entity
    record ItemWithNestedEmbeddableWithArrayPersistentAttribute(
            @Id int id, EmbeddableNestedWithArrayPersistentAttribute v) {}

    @Entity
    record ItemWithNestedEmbeddableWithCollectionPersistentAttribute(
            @Id int id, EmbeddableNestedWithCollectionPersistentAttribute v) {}

    @Entity
    record ItemWithAggregateEmbeddableWithBasicPersistentAttribute(@Id int id, StructWithBasicPersistentAttribute v) {}

    @Entity
    record ItemWithAggregateEmbeddableWithArrayPersistentAttribute(@Id int id, StructWithArrayPersistentAttribute v) {}

    @Entity
    record ItemWithAggregateEmbeddableWithCollectionPersistentAttribute(
            @Id int id, StructWithCollectionPersistentAttribute v) {}

    @Entity
    record ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute(
            @Id int id, StructNestedWithBasicPersistentAttribute v) {}

    @Entity
    record ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute(
            @Id int id, StructNestedWithArrayPersistentAttribute v) {}

    @Entity
    record ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute(
            @Id int id, StructNestedCollectionPersistenceAttribute v) {}

    @Embeddable
    record EmbeddableWithBasicPersistentAttribute(Calendar v) {}

    @Embeddable
    private record EmbeddableWithArrayPersistentAttribute(Calendar[] v) {}

    @Embeddable
    record EmbeddableWithCollectionPersistentAttribute(Collection<Calendar> v) {}

    @Embeddable
    record EmbeddableNestedWithBasicPersistentAttribute(EmbeddableWithBasicPersistentAttribute v) {}

    @Embeddable
    record EmbeddableNestedWithArrayPersistentAttribute(EmbeddableWithArrayPersistentAttribute v) {}

    @Embeddable
    record EmbeddableNestedWithCollectionPersistentAttribute(EmbeddableWithCollectionPersistentAttribute v) {}

    @Struct(name = "struct")
    @Embeddable
    record StructWithBasicPersistentAttribute(Calendar v) {}

    @Struct(name = "struct")
    @Embeddable
    private record StructWithArrayPersistentAttribute(Calendar[] v) {}

    @Struct(name = "struct")
    @Embeddable
    record StructWithCollectionPersistentAttribute(Collection<Calendar> v) {}

    @Struct(name = "nestedStruct")
    @Embeddable
    record StructNestedWithBasicPersistentAttribute(StructWithBasicPersistentAttribute v) {}

    @Struct(name = "nestedStruct")
    @Embeddable
    record StructNestedWithArrayPersistentAttribute(StructWithArrayPersistentAttribute v) {}

    @Struct(name = "nestedStruct")
    @Embeddable
    record StructNestedCollectionPersistenceAttribute(StructWithCollectionPersistentAttribute v) {}
}
