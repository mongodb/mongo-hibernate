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

        EmbeddableWithBasicPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithEmbeddableWithArrayPersistentAttribute<T> {
        @Id
        int id;

        EmbeddableWithArrayPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithEmbeddableWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        EmbeddableWithCollectionPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedEmbeddableWithBasicPersistentAttribute<T> {
        @Id
        int id;

        EmbeddableNestedWithBasicPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedEmbeddableWithArrayPersistentAttribute<T> {
        @Id
        int id;

        EmbeddableNestedWithArrayPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedEmbeddableWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        EmbeddableNestedWithCollectionPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithStructWithBasicPersistentAttribute<T> {
        @Id
        int id;

        StructWithBasicPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithStructWithArrayPersistentAttribute<T> {
        @Id
        int id;

        StructWithArrayPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithStructWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        StructWithCollectionPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedStructWithBasicPersistentAttribute<T> {
        @Id
        int id;

        StructNestedWithBasicPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedStructWithArrayPersistentAttribute<T> {
        @Id
        int id;

        StructNestedWithArrayPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedStructWithCollectionPersistentAttribute<T> {
        @Id
        int id;

        StructNestedCollectionPersistenceAttribute<T> v;
    }

    @Embeddable
    static class EmbeddableWithBasicPersistentAttribute<T> {
        T v;
    }

    @Embeddable
    private static class EmbeddableWithArrayPersistentAttribute<T> {
        T[] v;
    }

    @Embeddable
    static class EmbeddableWithCollectionPersistentAttribute<T> {
        Collection<T> v;
    }

    @Embeddable
    static class EmbeddableNestedWithBasicPersistentAttribute<T> {
        EmbeddableWithBasicPersistentAttribute<T> v;
    }

    @Embeddable
    static class EmbeddableNestedWithArrayPersistentAttribute<T> {
        EmbeddableWithArrayPersistentAttribute<T> v;
    }

    @Embeddable
    static class EmbeddableNestedWithCollectionPersistentAttribute<T> {
        EmbeddableWithCollectionPersistentAttribute<T> v;
    }

    @Struct(name = "struct")
    @Embeddable
    static class StructWithBasicPersistentAttribute<T> {
        T v;
    }

    @Struct(name = "struct")
    @Embeddable
    private static class StructWithArrayPersistentAttribute<T> {
        T[] v;
    }

    @Struct(name = "struct")
    @Embeddable
    static class StructWithCollectionPersistentAttribute<T> {
        Collection<T> v;
    }

    @Struct(name = "nestedStruct")
    @Embeddable
    static class StructNestedWithBasicPersistentAttribute<T> {
        StructWithBasicPersistentAttribute<T> v;
    }

    @Struct(name = "nestedStruct")
    @Embeddable
    static class StructNestedWithArrayPersistentAttribute<T> {
        StructWithArrayPersistentAttribute<T> v;
    }

    @Struct(name = "nestedStruct")
    @Embeddable
    static class StructNestedCollectionPersistenceAttribute<T> {
        StructWithCollectionPersistentAttribute<T> v;
    }
}
