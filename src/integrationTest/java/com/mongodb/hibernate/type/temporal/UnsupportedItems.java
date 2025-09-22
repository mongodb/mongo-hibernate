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

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Collection;
import org.hibernate.annotations.Struct;

final class UnsupportedItems {

    private UnsupportedItems() {}

    @Entity
    static class ItemWithId<T> {
        @Id
        T id;
    }

    @Entity
    static class ItemWithFlattenedEmbeddableId<T> {
        @Id
        FlattenedEmbeddableWithBasicPersistentAttribute<T> id;
    }

    @Entity
    static class ItemWithArrayPersistentAttribute<T> {
        @Id
        int id;

        T[] v;
    }

    @Entity
    static class ItemWithBasicPersistentAttribute<T> {
        @Id
        int id;

        T v;
    }

    @Entity
    static class ItemWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        Collection<T> v;
    }

    @Entity
    static class ItemWithEmbeddableWithBasicPersistentAttribute<T> {
        @Id
        int id;

        FlattenedEmbeddableWithBasicPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithEmbeddableWithArrayPersistentAttribute<T> {
        @Id
        int id;

        FlattenedEmbeddableWithArrayPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithEmbeddableWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        FlattenedEmbeddableWithCollectionPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedEmbeddableWithBasicPersistentAttribute<T> {
        @Id
        int id;

        FlattenedEmbeddableNestedWithBasicPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedEmbeddableWithArrayPersistentAttribute<T> {
        @Id
        int id;

        FlattenedEmbeddableNestedWithArrayPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedEmbeddableWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        FlattenedEmbeddableNestedWithCollectionPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithAggregateEmbeddableWithBasicPersistentAttribute<T> {
        @Id
        int id;

        AggregateEmbeddableWithBasicPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithAggregateEmbeddableWithArrayPersistentAttribute<T> {
        @Id
        int id;

        AggregateEmbeddableWithArrayPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        AggregateEmbeddableWithCollectionPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithCollectionOfAggregateEmbeddableWithBasicPersistentAttribute<T> {
        @Id
        int id;

        Collection<AggregateEmbeddableWithBasicPersistentAttribute<T>> v;
    }

    @Entity
    static class ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<T> {
        @Id
        int id;

        AggregateEmbeddableNestedWithBasicPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<T> {
        @Id
        int id;

        AggregateEmbeddableNestedWithArrayPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        AggregateEmbeddableNestedCollectionPersistenceAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedCollectionOfAggregateEmbeddableWithPersistentAttribute<T> {
        @Id
        int id;

        AggregateEmbeddableWithCollectionOfAggregateEmbeddablePersistentAttribute<T> v;
    }

    @Embeddable
    static class FlattenedEmbeddableWithBasicPersistentAttribute<T> {
        T v;
    }

    @Embeddable
    private static class FlattenedEmbeddableWithArrayPersistentAttribute<T> {
        T[] v;
    }

    @Embeddable
    static class FlattenedEmbeddableWithCollectionPersistentAttribute<T> {
        Collection<T> v;
    }

    @Embeddable
    static class FlattenedEmbeddableNestedWithBasicPersistentAttribute<T> {
        FlattenedEmbeddableWithBasicPersistentAttribute<T> v;
    }

    @Embeddable
    static class FlattenedEmbeddableNestedWithArrayPersistentAttribute<T> {
        FlattenedEmbeddableWithArrayPersistentAttribute<T> v;
    }

    @Embeddable
    static class FlattenedEmbeddableNestedWithCollectionPersistentAttribute<T> {
        FlattenedEmbeddableWithCollectionPersistentAttribute<T> v;
    }

    @Struct(name = "AggregateEmbeddableWithBasicPersistentAttribute")
    @Embeddable
    static class AggregateEmbeddableWithBasicPersistentAttribute<T> {
        T v;
    }

    @Struct(name = "AggregateEmbeddableWithArrayPersistentAttribute")
    @Embeddable
    private static class AggregateEmbeddableWithArrayPersistentAttribute<T> {
        T[] v;
    }

    @Struct(name = "AggregateEmbeddableWithCollectionPersistentAttribute")
    @Embeddable
    static class AggregateEmbeddableWithCollectionPersistentAttribute<T> {
        Collection<T> v;
    }

    @Struct(name = "AggregateEmbeddableNestedWithBasicPersistentAttribute")
    @Embeddable
    static class AggregateEmbeddableNestedWithBasicPersistentAttribute<T> {
        AggregateEmbeddableWithBasicPersistentAttribute<T> v;
    }

    @Struct(name = "AggregateEmbeddableNestedWithArrayPersistentAttribute")
    @Embeddable
    static class AggregateEmbeddableNestedWithArrayPersistentAttribute<T> {
        AggregateEmbeddableWithArrayPersistentAttribute<T> v;
    }

    @Struct(name = "AggregateEmbeddableNestedCollectionPersistenceAttribute")
    @Embeddable
    static class AggregateEmbeddableNestedCollectionPersistenceAttribute<T> {
        AggregateEmbeddableWithCollectionPersistentAttribute<T> v;
    }

    @Struct(name = "AggregateEmbeddableWithCollectionOfAggregateEmbeddablePersistentAttribute")
    @Embeddable
    static class AggregateEmbeddableWithCollectionOfAggregateEmbeddablePersistentAttribute<T> {
        Collection<AggregateEmbeddableWithBasicPersistentAttribute<T>> v;
    }
}
