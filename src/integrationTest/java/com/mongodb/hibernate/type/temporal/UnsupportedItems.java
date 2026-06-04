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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import org.hibernate.annotations.Struct;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * Generic entity/embeddable templates used by the unsupported-type tests. Each unsupported type has a corresponding
 * {@code TypeItems} nested class (e.g. {@link CalendarItems}, {@link DateItems}) that contains fully concrete,
 * non-generic copies of these templates specialized for that type. The copies exist because Hibernate ORM 7 no longer
 * resolves type parameters from the anonymous-subclass super-type token pattern ({@code new ItemWithId<T>(){}}) that
 * worked in Hibernate 6: it reports "unbound type" when processing the generic superclass, so each test type needs its
 * own concrete entity and embeddable classes.
 *
 * <p>NOTE: An entity with a field that is an array of aggregate embeddables (and the same case with nested aggregate
 * embeddables) is not included in the unsupported-items test set. Hibernate ORM currently maps such arrays as
 * {@link org.hibernate.type.BasicType} instead of {@link org.hibernate.type.BasicArrayType} when the component type of
 * the array is a parameterized type, which causes validation not to catch the unsupported {@link JavaType} usage.
 */
final class UnsupportedItems {

    private UnsupportedItems() {}

    static final class CalendarItems {
        private CalendarItems() {}

        // Embeddable helpers — concrete Calendar versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            Calendar v;
        }

        @Embeddable
        static class FlattenedArray {
            Calendar[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<Calendar> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "CalendarAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            Calendar v;
        }

        @Struct(name = "CalendarAggregateArray")
        @Embeddable
        static class AggregateArray {
            Calendar[] v;
        }

        @Struct(name = "CalendarAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<Calendar> v;
        }

        @Struct(name = "CalendarNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "CalendarNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "CalendarNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "CalendarAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            Calendar id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            Calendar v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            Calendar[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<Calendar> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

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
    static class ItemWithCollectionOfAggregateEmbeddable<T> {
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

        AggregateEmbeddableNestedWithCollectionPersistentAttribute<T> v;
    }

    @Entity
    static class ItemWithNestedCollectionOfAggregateEmbeddable<T> {
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

    @Struct(name = "AggregateEmbeddableNestedWithCollectionPersistentAttribute")
    @Embeddable
    static class AggregateEmbeddableNestedWithCollectionPersistentAttribute<T> {
        AggregateEmbeddableWithCollectionPersistentAttribute<T> v;
    }

    @Struct(name = "AggregateEmbeddableWithCollectionOfAggregateEmbeddablePersistentAttribute")
    @Embeddable
    static class AggregateEmbeddableWithCollectionOfAggregateEmbeddablePersistentAttribute<T> {
        Collection<AggregateEmbeddableWithBasicPersistentAttribute<T>> v;
    }

    static final class DateItems {
        private DateItems() {}

        // Embeddable helpers — concrete java.util.Date versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            java.util.Date v;
        }

        @Embeddable
        static class FlattenedArray {
            java.util.Date[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<java.util.Date> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "DateAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            java.util.Date v;
        }

        @Struct(name = "DateAggregateArray")
        @Embeddable
        static class AggregateArray {
            java.util.Date[] v;
        }

        @Struct(name = "DateAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<java.util.Date> v;
        }

        @Struct(name = "DateNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "DateNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "DateNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "DateAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            java.util.Date id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            java.util.Date v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            java.util.Date[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<java.util.Date> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

    static final class SqlDateItems {
        private SqlDateItems() {}

        // Embeddable helpers — concrete java.sql.Date versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            java.sql.Date v;
        }

        @Embeddable
        static class FlattenedArray {
            java.sql.Date[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<java.sql.Date> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "SqlDateAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            java.sql.Date v;
        }

        @Struct(name = "SqlDateAggregateArray")
        @Embeddable
        static class AggregateArray {
            java.sql.Date[] v;
        }

        @Struct(name = "SqlDateAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<java.sql.Date> v;
        }

        @Struct(name = "SqlDateNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "SqlDateNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "SqlDateNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "SqlDateAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            java.sql.Date id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            java.sql.Date v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            java.sql.Date[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<java.sql.Date> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

    static final class SqlTimeItems {
        private SqlTimeItems() {}

        // Embeddable helpers — concrete java.sql.Time versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            java.sql.Time v;
        }

        @Embeddable
        static class FlattenedArray {
            java.sql.Time[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<java.sql.Time> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "SqlTimeAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            java.sql.Time v;
        }

        @Struct(name = "SqlTimeAggregateArray")
        @Embeddable
        static class AggregateArray {
            java.sql.Time[] v;
        }

        @Struct(name = "SqlTimeAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<java.sql.Time> v;
        }

        @Struct(name = "SqlTimeNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "SqlTimeNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "SqlTimeNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "SqlTimeAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            java.sql.Time id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            java.sql.Time v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            java.sql.Time[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<java.sql.Time> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

    static final class SqlTimestampItems {
        private SqlTimestampItems() {}

        // Embeddable helpers — concrete java.sql.Timestamp versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            java.sql.Timestamp v;
        }

        @Embeddable
        static class FlattenedArray {
            java.sql.Timestamp[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<java.sql.Timestamp> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "TimestampAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            java.sql.Timestamp v;
        }

        @Struct(name = "TimestampAggregateArray")
        @Embeddable
        static class AggregateArray {
            java.sql.Timestamp[] v;
        }

        @Struct(name = "TimestampAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<java.sql.Timestamp> v;
        }

        @Struct(name = "TimestampNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "TimestampNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "TimestampNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "TimestampAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            java.sql.Timestamp id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            java.sql.Timestamp v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            java.sql.Timestamp[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<java.sql.Timestamp> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

    static final class LocalTimeItems {
        private LocalTimeItems() {}

        // Embeddable helpers — concrete LocalTime versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            LocalTime v;
        }

        @Embeddable
        static class FlattenedArray {
            LocalTime[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<LocalTime> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "LocalTimeAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            LocalTime v;
        }

        @Struct(name = "LocalTimeAggregateArray")
        @Embeddable
        static class AggregateArray {
            LocalTime[] v;
        }

        @Struct(name = "LocalTimeAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<LocalTime> v;
        }

        @Struct(name = "LocalTimeNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "LocalTimeNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "LocalTimeNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "LocalTimeAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            LocalTime id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            LocalTime v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            LocalTime[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<LocalTime> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

    static final class LocalDateTimeItems {
        private LocalDateTimeItems() {}

        // Embeddable helpers — concrete LocalDateTime versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            LocalDateTime v;
        }

        @Embeddable
        static class FlattenedArray {
            LocalDateTime[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<LocalDateTime> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "LocalDateTimeAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            LocalDateTime v;
        }

        @Struct(name = "LocalDateTimeAggregateArray")
        @Embeddable
        static class AggregateArray {
            LocalDateTime[] v;
        }

        @Struct(name = "LocalDateTimeAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<LocalDateTime> v;
        }

        @Struct(name = "LocalDateTimeNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "LocalDateTimeNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "LocalDateTimeNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "LocalDateTimeAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            LocalDateTime id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            LocalDateTime v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            LocalDateTime[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<LocalDateTime> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

    static final class ZonedDateTimeItems {
        private ZonedDateTimeItems() {}

        // Embeddable helpers — concrete ZonedDateTime versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            ZonedDateTime v;
        }

        @Embeddable
        static class FlattenedArray {
            ZonedDateTime[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<ZonedDateTime> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "ZonedDateTimeAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            ZonedDateTime v;
        }

        @Struct(name = "ZonedDateTimeAggregateArray")
        @Embeddable
        static class AggregateArray {
            ZonedDateTime[] v;
        }

        @Struct(name = "ZonedDateTimeAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<ZonedDateTime> v;
        }

        @Struct(name = "ZonedDateTimeNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "ZonedDateTimeNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "ZonedDateTimeNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "ZonedDateTimeAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            ZonedDateTime id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            ZonedDateTime v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            ZonedDateTime[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<ZonedDateTime> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

    static final class OffsetTimeItems {
        private OffsetTimeItems() {}

        // Embeddable helpers — concrete OffsetTime versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            OffsetTime v;
        }

        @Embeddable
        static class FlattenedArray {
            OffsetTime[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<OffsetTime> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "OffsetTimeAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            OffsetTime v;
        }

        @Struct(name = "OffsetTimeAggregateArray")
        @Embeddable
        static class AggregateArray {
            OffsetTime[] v;
        }

        @Struct(name = "OffsetTimeAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<OffsetTime> v;
        }

        @Struct(name = "OffsetTimeNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "OffsetTimeNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "OffsetTimeNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "OffsetTimeAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            OffsetTime id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            OffsetTime v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            OffsetTime[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<OffsetTime> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }

    static final class OffsetDateTimeItems {
        private OffsetDateTimeItems() {}

        // Embeddable helpers — concrete OffsetDateTime versions of the generic embeddable templates
        @Embeddable
        static class FlattenedBasic {
            OffsetDateTime v;
        }

        @Embeddable
        static class FlattenedArray {
            OffsetDateTime[] v;
        }

        @Embeddable
        static class FlattenedCollection {
            Collection<OffsetDateTime> v;
        }

        @Embeddable
        static class NestedFlattenedBasic {
            FlattenedBasic v;
        }

        @Embeddable
        static class NestedFlattenedArray {
            FlattenedArray v;
        }

        @Embeddable
        static class NestedFlattenedCollection {
            FlattenedCollection v;
        }

        @Struct(name = "OffsetDateTimeAggregateBasic")
        @Embeddable
        static class AggregateBasic {
            OffsetDateTime v;
        }

        @Struct(name = "OffsetDateTimeAggregateArray")
        @Embeddable
        static class AggregateArray {
            OffsetDateTime[] v;
        }

        @Struct(name = "OffsetDateTimeAggregateCollection")
        @Embeddable
        static class AggregateCollection {
            Collection<OffsetDateTime> v;
        }

        @Struct(name = "OffsetDateTimeNestedAggregateBasic")
        @Embeddable
        static class NestedAggregateBasic {
            AggregateBasic v;
        }

        @Struct(name = "OffsetDateTimeNestedAggregateArray")
        @Embeddable
        static class NestedAggregateArray {
            AggregateArray v;
        }

        @Struct(name = "OffsetDateTimeNestedAggregateCollection")
        @Embeddable
        static class NestedAggregateCollection {
            AggregateCollection v;
        }

        @Struct(name = "OffsetDateTimeAggregateCollectionOfAggregate")
        @Embeddable
        static class AggregateCollectionOfAggregate {
            Collection<AggregateBasic> v;
        }

        // Entity variants
        @Entity
        static class WithId {
            @Id
            OffsetDateTime id;
        }

        @Entity
        static class WithFlattenedEmbeddableId {
            @Id
            FlattenedBasic id;
        }

        @Entity
        static class WithBasicPersistentAttribute {
            @Id
            int id;

            OffsetDateTime v;
        }

        @Entity
        static class WithArrayPersistentAttribute {
            @Id
            int id;

            OffsetDateTime[] v;
        }

        @Entity
        static class WithCollectionPersistentAttribute {
            @Id
            int id;

            Collection<OffsetDateTime> v;
        }

        @Entity
        static class WithEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            FlattenedBasic v;
        }

        @Entity
        static class WithEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            FlattenedArray v;
        }

        @Entity
        static class WithEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            FlattenedCollection v;
        }

        @Entity
        static class WithNestedEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedFlattenedBasic v;
        }

        @Entity
        static class WithNestedEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedFlattenedArray v;
        }

        @Entity
        static class WithNestedEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedFlattenedCollection v;
        }

        @Entity
        static class WithAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            AggregateBasic v;
        }

        @Entity
        static class WithAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            AggregateArray v;
        }

        @Entity
        static class WithAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            AggregateCollection v;
        }

        @Entity
        static class WithCollectionOfAggregateEmbeddable {
            @Id
            int id;

            Collection<AggregateBasic> v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithBasicPersistentAttribute {
            @Id
            int id;

            NestedAggregateBasic v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithArrayPersistentAttribute {
            @Id
            int id;

            NestedAggregateArray v;
        }

        @Entity
        static class WithNestedAggregateEmbeddableWithCollectionPersistentAttribute {
            @Id
            int id;

            NestedAggregateCollection v;
        }

        @Entity
        static class WithNestedCollectionOfAggregateEmbeddable {
            @Id
            int id;

            AggregateCollectionOfAggregate v;
        }
    }
}
