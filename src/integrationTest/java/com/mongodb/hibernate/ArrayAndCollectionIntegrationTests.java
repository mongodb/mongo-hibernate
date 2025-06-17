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

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.WRAPPER_ARRAY_HANDLING;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.embeddable.EmbeddableIntegrationTests;
import com.mongodb.hibernate.embeddable.StructAggregateEmbeddableIntegrationTests;
import com.mongodb.hibernate.embeddable.StructAggregateEmbeddableIntegrationTests.ArraysAndCollections;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.hibernate.MappingException;
import org.hibernate.boot.MetadataSources;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            ArrayAndCollectionIntegrationTests.ItemWithArrayAndCollectionValues.class,
            ArrayAndCollectionIntegrationTests
                    .ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections.class,
            ArrayAndCollectionIntegrationTests.Unsupported.ItemWithBoxedBytesArrayValue.class,
            ArrayAndCollectionIntegrationTests.Unsupported.ItemWithBytesCollectionValue.class
        })
@ServiceRegistry(settings = {@Setting(name = WRAPPER_ARRAY_HANDLING, value = "allow")})
@ExtendWith(MongoExtension.class)
class ArrayAndCollectionIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void testArrayAndCollectionValues() {
        var item = new ItemWithArrayAndCollectionValues(
                1,
                // TODO-HIBERNATE-48 sprinkle on `null` array/collection elements
                new byte[] {2, 3},
                new char[] {'s', 't', 'r'},
                new int[] {5},
                new long[] {Long.MAX_VALUE, 6},
                new double[] {Double.MAX_VALUE},
                new boolean[] {true},
                new Character[] {'s', 't', 'r'},
                new Integer[] {7},
                new Long[] {8L},
                new Double[] {9.1d},
                new Boolean[] {true},
                new String[] {"str"},
                new BigDecimal[] {BigDecimal.valueOf(10.1)},
                new ObjectId[] {new ObjectId("000000000000000000000001")},
                new StructAggregateEmbeddableIntegrationTests.Single[] {
                    new StructAggregateEmbeddableIntegrationTests.Single(1)
                },
                List.of('s', 't', 'r'),
                List.of(5),
                List.of(Long.MAX_VALUE, 6L),
                List.of(Double.MAX_VALUE),
                List.of(true),
                List.of("str"),
                List.of(BigDecimal.valueOf(10.1)),
                List.of(new ObjectId("000000000000000000000001")),
                List.of(new StructAggregateEmbeddableIntegrationTests.Single(1)));
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    bytes: {$binary: {base64: "AgM=", subType: "0"}},
                    chars: "str",
                    ints: [5],
                    longs: [{$numberLong: "9223372036854775807"}, {$numberLong: "6"}],
                    doubles: [{$numberDouble: "1.7976931348623157E308"}],
                    booleans: [true],
                    boxedChars: ["s", "t", "r"],
                    boxedInts: [7],
                    boxedLongs: [{$numberLong: "8"}],
                    boxedDoubles: [{$numberDouble: "9.1"}],
                    boxedBooleans: [true],
                    strings: ["str"],
                    bigDecimals: [{$numberDecimal: "10.1"}],
                    objectIds: [{$oid: "000000000000000000000001"}],
                    structAggregateEmbeddables: [{a: 1}],
                    charsCollection: ["s", "t", "r"],
                    intsCollection: [5],
                    longsCollection: [{$numberLong: "9223372036854775807"}, {$numberLong: "6"}],
                    doublesCollection: [{$numberDouble: "1.7976931348623157E308"}],
                    booleansCollection: [true],
                    stringsCollection: ["str"],
                    bigDecimalsCollection: [{$numberDecimal: "10.1"}],
                    objectIdsCollection: [{$oid: "000000000000000000000001"}],
                    structAggregateEmbeddablesCollection: [{a: 1}]
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithArrayAndCollectionValues.class, item.id));
        assertEq(item, loadedItem);
        ItemWithArrayAndCollectionValues updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithArrayAndCollectionValues.class, item.id);
            result.bytes[0] = (byte) -result.bytes[0];
            result.longs[1] = -result.longs[1];
            result.objectIds[0] = new ObjectId("000000000000000000000002");
            result.longsCollection.remove(6L);
            result.longsCollection.add(-6L);
            return result;
        });
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    bytes: {$binary: {base64: "/gM=", subType: "0"}},
                    chars: "str",
                    ints: [5],
                    longs: [{$numberLong: "9223372036854775807"}, {$numberLong: "-6"}],
                    doubles: [{$numberDouble: "1.7976931348623157E308"}],
                    booleans: [true],
                    boxedChars: ["s", "t", "r"],
                    boxedInts: [7],
                    boxedLongs: [{$numberLong: "8"}],
                    boxedDoubles: [{$numberDouble: "9.1"}],
                    boxedBooleans: [true],
                    strings: ["str"],
                    bigDecimals: [{$numberDecimal: "10.1"}],
                    objectIds: [{$oid: "000000000000000000000002"}],
                    structAggregateEmbeddables: [{a: 1}],
                    charsCollection: ["s", "t", "r"],
                    intsCollection: [5],
                    longsCollection: [{$numberLong: "9223372036854775807"}, {$numberLong: "-6"}],
                    doublesCollection: [{$numberDouble: "1.7976931348623157E308"}],
                    booleansCollection: [true],
                    stringsCollection: ["str"],
                    bigDecimalsCollection: [{$numberDecimal: "10.1"}],
                    objectIdsCollection: [{$oid: "000000000000000000000001"}],
                    structAggregateEmbeddablesCollection: [{a: 1}]
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithArrayAndCollectionValues.class, updatedItem.id));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testArrayAndCollectionEmptyValues() {
        var item = new ItemWithArrayAndCollectionValues(
                1,
                new byte[0],
                new char[0],
                new int[0],
                new long[0],
                new double[0],
                new boolean[0],
                new Character[0],
                new Integer[0],
                new Long[0],
                new Double[0],
                new Boolean[0],
                new String[0],
                new BigDecimal[0],
                new ObjectId[0],
                new StructAggregateEmbeddableIntegrationTests.Single[0],
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    bytes: {$binary: {base64: "", subType: "0"}},
                    chars: "",
                    ints: [],
                    longs: [],
                    doubles: [],
                    booleans: [],
                    boxedChars: [],
                    boxedInts: [],
                    boxedLongs: [],
                    boxedDoubles: [],
                    boxedBooleans: [],
                    strings: [],
                    bigDecimals: [],
                    objectIds: [],
                    structAggregateEmbeddables: [],
                    charsCollection: [],
                    intsCollection: [],
                    longsCollection: [],
                    doublesCollection: [],
                    booleansCollection: [],
                    stringsCollection: [],
                    bigDecimalsCollection: [],
                    objectIdsCollection: [],
                    structAggregateEmbeddablesCollection: []
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithArrayAndCollectionValues.class, item.id));
        assertEq(item, loadedItem);
    }

    @Test
    @Disabled("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 enable this test")
    void testArrayAndCollectionNullValues() {
        var item = new ItemWithArrayAndCollectionValues(
                1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    bytes: null,
                    chars: null,
                    ints: null,
                    longs: null,
                    doubles: null,
                    booleans: null,
                    boxedChars: null,
                    boxedInts: null,
                    boxedLongs: null,
                    boxedDoubles: null,
                    boxedBooleans: null,
                    strings: null,
                    bigDecimals: null,
                    objectIds: null,
                    structAggregateEmbeddables: null,
                    charsCollection: null,
                    intsCollection: null,
                    longsCollection: null,
                    doublesCollection: null,
                    booleansCollection: null,
                    stringsCollection: null,
                    bigDecimalsCollection: null,
                    objectIdsCollection: null,
                    structAggregateEmbeddablesCollection: null
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithArrayAndCollectionValues.class, item.id));
        assertEq(item, loadedItem);
    }

    @Test
    void testArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections() {
        var arraysAndCollections = new ArraysAndCollections(
                new byte[] {2, 3},
                new char[] {'s', 't', 'r'},
                new int[] {5},
                new long[] {Long.MAX_VALUE, 6},
                new double[] {Double.MAX_VALUE},
                new boolean[] {true},
                new Character[] {'s', 't', 'r'},
                new Integer[] {7},
                new Long[] {8L},
                new Double[] {9.1d},
                new Boolean[] {true},
                new String[] {"str"},
                new BigDecimal[] {BigDecimal.valueOf(10.1)},
                new ObjectId[] {new ObjectId("000000000000000000000001")},
                new StructAggregateEmbeddableIntegrationTests.Single[] {
                    new StructAggregateEmbeddableIntegrationTests.Single(1)
                },
                List.of('s', 't', 'r'),
                // Hibernate ORM uses `LinkedHashSet`, forcing us to also use it, but messing up the order anyway
                new LinkedHashSet<>(List.of(5)),
                List.of(Long.MAX_VALUE, 6L),
                List.of(Double.MAX_VALUE),
                List.of(true),
                List.of("str"),
                List.of(BigDecimal.valueOf(10.1)),
                List.of(new ObjectId("000000000000000000000001")),
                List.of(new StructAggregateEmbeddableIntegrationTests.Single(1)));
        var item = new ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections(
                1, new ArraysAndCollections[] {arraysAndCollections}, List.of());
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    structAggregateEmbeddables: [{
                        bytes: {$binary: {base64: "AgM=", subType: "0"}},
                        chars: "str",
                        ints: [5],
                        longs: [{$numberLong: "9223372036854775807"}, {$numberLong: "6"}],
                        doubles: [{$numberDouble: "1.7976931348623157E308"}],
                        booleans: [true],
                        boxedChars: ["s", "t", "r"],
                        boxedInts: [7],
                        boxedLongs: [{$numberLong: "8"}],
                        boxedDoubles: [{$numberDouble: "9.1"}],
                        boxedBooleans: [true],
                        strings: ["str"],
                        bigDecimals: [{$numberDecimal: "10.1"}],
                        objectIds: [{$oid: "000000000000000000000001"}],
                        structAggregateEmbeddables: [{a: 1}],
                        charsCollection: ["s", "t", "r"],
                        intsCollection: [5],
                        longsCollection: [{$numberLong: "9223372036854775807"}, {$numberLong: "6"}],
                        doublesCollection: [{$numberDouble: "1.7976931348623157E308"}],
                        booleansCollection: [true],
                        stringsCollection: ["str"],
                        bigDecimalsCollection: [{$numberDecimal: "10.1"}],
                        objectIdsCollection: [{$oid: "000000000000000000000001"}],
                        structAggregateEmbeddablesCollection: [{a: 1}]
                    }],
                    structAggregateEmbeddablesCollection: []
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(session -> session.find(
                ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections.class, item.id));
        assertEq(item, loadedItem);
        ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections updatedItem =
                sessionFactoryScope.fromTransaction(session -> {
                    var result = session.find(
                            ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections
                                    .class,
                            item.id);
                    result.structAggregateEmbeddablesCollection.add(result.structAggregateEmbeddables[0]);
                    result.structAggregateEmbeddables = new ArraysAndCollections[0];
                    return result;
                });
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    structAggregateEmbeddables: [],
                    structAggregateEmbeddablesCollection: [{
                        bytes: {$binary: {base64: "AgM=", subType: "0"}},
                        chars: "str",
                        ints: [5],
                        longs: [{$numberLong: "9223372036854775807"}, {$numberLong: "6"}],
                        doubles: [{$numberDouble: "1.7976931348623157E308"}],
                        booleans: [true],
                        boxedChars: ["s", "t", "r"],
                        boxedInts: [7],
                        boxedLongs: [{$numberLong: "8"}],
                        boxedDoubles: [{$numberDouble: "9.1"}],
                        boxedBooleans: [true],
                        strings: ["str"],
                        bigDecimals: [{$numberDecimal: "10.1"}],
                        objectIds: [{$oid: "000000000000000000000001"}],
                        structAggregateEmbeddables: [{a: 1}],
                        charsCollection: ["s", "t", "r"],
                        intsCollection: [5],
                        longsCollection: [{$numberLong: "9223372036854775807"}, {$numberLong: "6"}],
                        doublesCollection: [{$numberDouble: "1.7976931348623157E308"}],
                        booleansCollection: [true],
                        stringsCollection: ["str"],
                        bigDecimalsCollection: [{$numberDecimal: "10.1"}],
                        objectIdsCollection: [{$oid: "000000000000000000000001"}],
                        structAggregateEmbeddablesCollection: [{a: 1}]
                    }]
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(session -> session.find(
                ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections.class,
                updatedItem.id));
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
    static class ItemWithArrayAndCollectionValues {
        @Id
        int id;

        byte[] bytes;
        char[] chars;
        int[] ints;
        long[] longs;
        double[] doubles;
        boolean[] booleans;
        Character[] boxedChars;
        Integer[] boxedInts;
        Long[] boxedLongs;
        Double[] boxedDoubles;
        Boolean[] boxedBooleans;
        String[] strings;
        BigDecimal[] bigDecimals;
        ObjectId[] objectIds;
        StructAggregateEmbeddableIntegrationTests.Single[] structAggregateEmbeddables;
        Collection<Character> charsCollection;
        Collection<Integer> intsCollection;
        Collection<Long> longsCollection;
        Collection<Double> doublesCollection;
        Collection<Boolean> booleansCollection;
        Collection<String> stringsCollection;
        Collection<BigDecimal> bigDecimalsCollection;
        Collection<ObjectId> objectIdsCollection;
        Collection<StructAggregateEmbeddableIntegrationTests.Single> structAggregateEmbeddablesCollection;

        ItemWithArrayAndCollectionValues() {}

        ItemWithArrayAndCollectionValues(
                int id,
                byte[] bytes,
                char[] chars,
                int[] ints,
                long[] longs,
                double[] doubles,
                boolean[] booleans,
                Character[] boxedChars,
                Integer[] boxedInts,
                Long[] boxedLongs,
                Double[] boxedDoubles,
                Boolean[] boxedBooleans,
                String[] strings,
                BigDecimal[] bigDecimals,
                ObjectId[] objectIds,
                StructAggregateEmbeddableIntegrationTests.Single[] structAggregateEmbeddables,
                Collection<Character> charsCollection,
                Collection<Integer> intsCollection,
                Collection<Long> longsCollection,
                Collection<Double> doublesCollection,
                Collection<Boolean> booleansCollection,
                Collection<String> stringsCollection,
                Collection<BigDecimal> bigDecimalsCollection,
                Collection<ObjectId> objectIdsCollection,
                Collection<StructAggregateEmbeddableIntegrationTests.Single> structAggregateEmbeddablesCollection) {
            this.id = id;
            this.bytes = bytes;
            this.chars = chars;
            this.ints = ints;
            this.longs = longs;
            this.doubles = doubles;
            this.booleans = booleans;
            this.boxedChars = boxedChars;
            this.boxedInts = boxedInts;
            this.boxedLongs = boxedLongs;
            this.boxedDoubles = boxedDoubles;
            this.boxedBooleans = boxedBooleans;
            this.strings = strings;
            this.bigDecimals = bigDecimals;
            this.objectIds = objectIds;
            this.structAggregateEmbeddables = structAggregateEmbeddables;
            this.charsCollection = charsCollection;
            this.intsCollection = intsCollection;
            this.longsCollection = longsCollection;
            this.doublesCollection = doublesCollection;
            this.booleansCollection = booleansCollection;
            this.stringsCollection = stringsCollection;
            this.bigDecimalsCollection = bigDecimalsCollection;
            this.objectIdsCollection = objectIdsCollection;
            this.structAggregateEmbeddablesCollection = structAggregateEmbeddablesCollection;
        }
    }

    @Entity
    @Table(name = "items")
    static class ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections {
        @Id
        int id;

        ArraysAndCollections[] structAggregateEmbeddables;
        Collection<ArraysAndCollections> structAggregateEmbeddablesCollection;

        ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections() {}

        ItemWithArrayAndCollectionValuesOfStructAggregateEmbeddablesHavingArraysAndCollections(
                int id,
                ArraysAndCollections[] structAggregateEmbeddables,
                Collection<ArraysAndCollections> structAggregateEmbeddablesCollection) {
            this.id = id;
            this.structAggregateEmbeddables = structAggregateEmbeddables;
            this.structAggregateEmbeddablesCollection = structAggregateEmbeddablesCollection;
        }
    }

    @Nested
    class Unsupported {
        /**
         * The {@link ClassCastException} caught here manifests a Hibernate ORM bug. The issue goes away if the
         * {@link ItemWithBoxedBytesArrayValue#bytes} field is removed. Otherwise, the behavior of this test should have
         * been equivalent to {@link #testBytesCollectionValue()}.
         */
        @Test
        void testBoxedBytesArrayValue() {
            var item = new ItemWithBoxedBytesArrayValue(1, new byte[] {1}, new Byte[] {2});
            assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                    .isInstanceOf(ClassCastException.class);
        }

        @Test
        void testBytesCollectionValue() {
            var item = new ItemWithBytesCollectionValue(1, List.of((byte) 2));
            assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                    .hasCauseInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        void testNestedArrayValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithNestedArrayValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testNestedCollectionValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithNestedCollectionValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testArrayOfEmbeddablesValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithArrayOfEmbeddablesValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testCollectionOfEmbeddablesValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithCollectionOfEmbeddablesValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testArrayOfEmbeddablesElementCollectionValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithArrayOfEmbeddablesElementCollectionValue.class)
                            .buildMetadata()
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class);
        }

        @Test
        void testCollectionOfEmbeddablesElementCollectionValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithCollectionOfEmbeddablesElementCollectionValue.class)
                            .buildMetadata()
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class);
        }

        @Entity
        @Table(name = "items")
        static class ItemWithBoxedBytesArrayValue {
            @Id
            int id;

            byte[] bytes;
            Byte[] boxedBytes;

            ItemWithBoxedBytesArrayValue() {}

            ItemWithBoxedBytesArrayValue(int id, byte[] bytes, Byte[] boxedBytes) {
                this.id = id;
                this.bytes = bytes;
                this.boxedBytes = boxedBytes;
            }
        }

        @Entity
        @Table(name = "items")
        static class ItemWithBytesCollectionValue {
            @Id
            int id;

            Collection<Byte> bytes;

            ItemWithBytesCollectionValue() {}

            ItemWithBytesCollectionValue(int id, Collection<Byte> bytes) {
                this.id = id;
                this.bytes = bytes;
            }
        }

        /**
         * <a
         * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#collections">
         * Collections cannot be nested, meaning Hibernate does not support mapping {@code List<List<?>>}, for
         * example.</a>
         */
        @Entity
        @Table(name = "items")
        static class ItemWithNestedArrayValue {
            @Id
            int id;

            int[][] nestedInts;
        }

        /**
         * <a
         * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#collections">
         * Collections cannot be nested, meaning Hibernate does not support mapping {@code List<List<?>>}, for
         * example.</a>
         */
        @Entity
        @Table(name = "items")
        static class ItemWithNestedCollectionValue {
            @Id
            int id;

            Collection<Collection<Integer>> nestedInts;
        }

        @Entity
        @Table(name = "items")
        static class ItemWithArrayOfEmbeddablesValue {
            @Id
            int id;

            EmbeddableIntegrationTests.Single[] embeddables;
        }

        @Entity
        @Table(name = "items")
        static class ItemWithCollectionOfEmbeddablesValue {
            @Id
            int id;

            Collection<EmbeddableIntegrationTests.Single> embeddables;
        }

        @Entity
        @Table(name = "items")
        static class ItemWithArrayOfEmbeddablesElementCollectionValue {
            @Id
            int id;

            @ElementCollection
            EmbeddableIntegrationTests.Single[] embeddables;
        }

        @Entity
        @Table(name = "items")
        static class ItemWithCollectionOfEmbeddablesElementCollectionValue {
            @Id
            int id;

            @ElementCollection
            Collection<EmbeddableIntegrationTests.Single> embeddables;
        }
    }
}
