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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.ArrayAndCollectionIntegrationTests;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.hibernate.annotations.Parent;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.MetadataSources;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            StructAggregateEmbeddableIntegrationTests.ItemWithNestedValues.class,
            StructAggregateEmbeddableIntegrationTests.ItemWithNestedValueHavingArraysAndCollections.class,
            StructAggregateEmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingNonInsertable.class,
            StructAggregateEmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingAllNonInsertable.class,
            StructAggregateEmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingNonUpdatable.class,
            StructAggregateEmbeddableIntegrationTests.Unsupported.ItemWithNestedValueHavingEmbeddable.class
        })
@ExtendWith(MongoExtension.class)
public class StructAggregateEmbeddableIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void testNestedValues() {
        var item = new ItemWithNestedValues(
                new EmbeddableIntegrationTests.Single(1),
                new Single(2),
                new PairWithParent(
                        3,
                        new Plural(
                                'c',
                                1,
                                Long.MAX_VALUE,
                                Double.MAX_VALUE,
                                true,
                                'c',
                                1,
                                Long.MAX_VALUE,
                                Double.MAX_VALUE,
                                true,
                                "str",
                                BigDecimal.valueOf(10.1),
                                new ObjectId("000000000000000000000001"))));
        item.nested2.parent = item;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    nested1: {
                        a: 2
                    },
                    nested2: {
                        a: 3,
                        nested: {
                            primitiveChar: "c",
                            primitiveInt: 1,
                            primitiveLong: {$numberLong: "9223372036854775807"},
                            primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                            primitiveBoolean: true,
                            boxedChar: "c",
                            boxedInt: 1,
                            boxedLong: {$numberLong: "9223372036854775807"},
                            boxedDouble: {$numberDouble: "1.7976931348623157E308"},
                            boxedBoolean: true,
                            string: "str",
                            bigDecimal: {$numberDecimal: "10.1"},
                            objectId: {$oid: "000000000000000000000001"}
                        }
                    }
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValues.class, item.flattenedId));
        assertEq(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithNestedValues.class, item.flattenedId);
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
                            primitiveChar: "c",
                            primitiveInt: 1,
                            primitiveLong: {$numberLong: "9223372036854775807"},
                            primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                            primitiveBoolean: true,
                            boxedChar: "c",
                            boxedInt: 1,
                            boxedLong: {$numberLong: "9223372036854775807"},
                            boxedDouble: {$numberDouble: "1.7976931348623157E308"},
                            boxedBoolean: true,
                            string: "str",
                            bigDecimal: {$numberDecimal: "10.1"},
                            objectId: {$oid: "000000000000000000000001"}
                        }
                    }
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValues.class, updatedItem.flattenedId));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    @Disabled("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 enable this test")
    void testNestedNullValueOrHavingNulls() {
        var item = new ItemWithNestedValues(
                new EmbeddableIntegrationTests.Single(1),
                null,
                new PairWithParent(
                        3,
                        new Plural(
                                'c',
                                1,
                                Long.MAX_VALUE,
                                Double.MAX_VALUE,
                                true,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)));
        item.nested2.parent = item;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    nested1: null,
                    nested2: {
                        a: 3,
                        nested: {
                            primitiveChar: "c",
                            primitiveInt: 1,
                            primitiveLong: {$numberLong: "9223372036854775807"},
                            primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                            primitiveBoolean: true,
                            boxedChar: null,
                            boxedInt: null,
                            boxedLong: null,
                            boxedDouble: null,
                            boxedBoolean: null,
                            string: null,
                            bigDecimal: null,
                            objectId: null
                        }
                    }
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValues.class, item.flattenedId));
        assertEq(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithNestedValues.class, item.flattenedId);
            result.nested2.nested = null;
            return result;
        });
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    nested1: null,
                    nested2: {
                        a: 3,
                        nested: null
                    }
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValues.class, updatedItem.flattenedId));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testNestedValueHavingArraysAndCollections() {
        var item = new ItemWithNestedValueHavingArraysAndCollections(
                1,
                // TODO-HIBERNATE-48 sprinkle on `null` array/collection elements
                new ArraysAndCollections(
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
                        new Single[] {new Single(1)},
                        List.of('s', 't', 'r'),
                        Set.of(5),
                        List.of(Long.MAX_VALUE, 6L),
                        List.of(Double.MAX_VALUE),
                        List.of(true),
                        List.of("str"),
                        List.of(BigDecimal.valueOf(10.1)),
                        List.of(new ObjectId("000000000000000000000001")),
                        List.of(new Single(1))));
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    nested: {
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
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValueHavingArraysAndCollections.class, item.id));
        assertEq(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithNestedValueHavingArraysAndCollections.class, item.id);
            result.nested.bytes[0] = (byte) -result.nested.bytes[0];
            result.nested.longs[1] = -result.nested.longs[1];
            result.nested.objectIds[0] = new ObjectId("000000000000000000000002");
            result.nested.longsCollection.remove(6L);
            result.nested.longsCollection.add(-6L);
            return result;
        });
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    nested: {
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
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValueHavingArraysAndCollections.class, updatedItem.id));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testNestedValueHavingEmptyArraysAndCollections() {
        var item = new ItemWithNestedValueHavingArraysAndCollections(
                1,
                new ArraysAndCollections(
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
                        new Single[0],
                        List.of(),
                        Set.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    nested: {
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
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValueHavingArraysAndCollections.class, item.id));
        assertEq(item, loadedItem);
    }

    /**
     * This test also covers the behavior of an empty {@linkplain Struct struct} aggregate {@linkplain Embeddable
     * embeddable} value, that is one having {@code null} as the value of each of its persistent attributes.
     *
     * @see EmbeddableIntegrationTests#testFlattenedValueHavingNullArraysAndCollections()
     * @see ArrayAndCollectionIntegrationTests#testArrayAndCollectionValuesOfEmptyStructAggregateEmbeddables()
     */
    @Test
    @Disabled("TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 enable this test")
    public void testNestedValueHavingNullArraysAndCollections() {
        var emptyStructAggregateEmbeddable = new ArraysAndCollections(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        var item = new ItemWithNestedValueHavingArraysAndCollections(1, emptyStructAggregateEmbeddable);
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    nested: {
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
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValueHavingArraysAndCollections.class, item.id));
        // `loadedItem.nested` is `null` despite `item.nested` not being `null`.
        // TODO-HIBERNATE-48 There is nothing we can do here, such is the Hibernate ORM behavior. - or can we?
        assertNull(loadedItem.nested);
        assertUsingRecursiveComparison(item, loadedItem, (assertion, expected) -> assertion
                .ignoringFields("nested")
                .isEqualTo(expected));
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
        EmbeddableIntegrationTests.Single flattenedId;

        Single nested1;

        PairWithParent nested2;

        ItemWithNestedValues() {}

        ItemWithNestedValues(EmbeddableIntegrationTests.Single flattenedId, Single nested1, PairWithParent nested2) {
            this.flattenedId = flattenedId;
            this.nested1 = nested1;
            this.nested2 = nested2;
        }
    }

    @Embeddable
    @Struct(name = "Single")
    public static class Single {
        public int a;

        Single() {}

        public Single(int a) {
            this.a = a;
        }
    }

    @Embeddable
    @Struct(name = "PairWithParent")
    static class PairWithParent {
        int a;
        Plural nested;

        @Parent ItemWithNestedValues parent;

        PairWithParent() {}

        PairWithParent(int a, Plural nested) {
            this.a = a;
            this.nested = nested;
        }

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
    @Struct(name = "Plural")
    record Plural(
            char primitiveChar,
            int primitiveInt,
            long primitiveLong,
            double primitiveDouble,
            boolean primitiveBoolean,
            Character boxedChar,
            Integer boxedInt,
            Long boxedLong,
            Double boxedDouble,
            Boolean boxedBoolean,
            String string,
            BigDecimal bigDecimal,
            ObjectId objectId) {}

    @Entity
    @Table(name = "items")
    static class ItemWithNestedValueHavingArraysAndCollections {
        @Id
        int id;

        ArraysAndCollections nested;

        ItemWithNestedValueHavingArraysAndCollections() {}

        ItemWithNestedValueHavingArraysAndCollections(int id, ArraysAndCollections nested) {
            this.id = id;
            this.nested = nested;
        }
    }

    @Embeddable
    @Struct(name = "ArraysAndCollections")
    public static class ArraysAndCollections {
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
        Single[] structAggregateEmbeddables;
        List<Character> charsCollection;
        Set<Integer> intsCollection;
        Collection<Long> longsCollection;
        Collection<Double> doublesCollection;
        Collection<Boolean> booleansCollection;
        Collection<String> stringsCollection;
        Collection<BigDecimal> bigDecimalsCollection;
        Collection<ObjectId> objectIdsCollection;
        Collection<Single> structAggregateEmbeddablesCollection;

        ArraysAndCollections() {}

        public ArraysAndCollections(
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
                Single[] structAggregateEmbeddables,
                List<Character> charsCollection,
                Set<Integer> intsCollection,
                Collection<Long> longsCollection,
                Collection<Double> doublesCollection,
                Collection<Boolean> booleansCollection,
                Collection<String> stringsCollection,
                Collection<BigDecimal> bigDecimalsCollection,
                Collection<ObjectId> objectIdsCollection,
                Collection<Single> structAggregateEmbeddablesCollection) {
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

    @Nested
    class Unsupported {
        @Test
        void testStructPrimaryKey() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithSingleAsId.class)
                            .buildMetadata())
                    .hasMessageContaining("aggregate embeddable primary keys are not supported");
        }

        @Test
        void testNonInsertable() {
            var item = new ItemWithNestedValueHavingNonInsertable(1, new PairHavingNonInsertable(2, 3));
            assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                    .hasMessageContaining("must be insertable");
        }

        @Test
        void testAllNonInsertable() {
            var item = new ItemWithNestedValueHavingAllNonInsertable(1, new PairAllNonInsertable(2, 3));
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
                var item = new ItemWithNestedValueHavingNonUpdatable(1, new PairHavingNonUpdatable(2, 3));
                session.persist(item);
                assertThatThrownBy(session::flush).hasMessageContaining("must be updatable");
            });
        }

        @Test
        void testPolymorphic() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithPolymorphicPersistentAttribute.class)
                            .addAnnotatedClass(Polymorphic.class)
                            .addAnnotatedClass(Concrete.class)
                            .buildMetadata()
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessage("Polymorphic mapping is not supported");
        }

        @Test
        void testEmbeddable() {
            var item = new ItemWithNestedValueHavingEmbeddable(
                    1, new SingleHavingEmbeddable(new EmbeddableIntegrationTests.Single(2)));
            assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                    .hasRootCauseInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        void testNoPersistentAttributes() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithNestedValueHavingNoPersistentAttributes.class)
                            .buildMetadata()
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("must have at least one persistent attribute");
        }

        @Entity
        @Table(name = "items")
        static class ItemWithSingleAsId {
            @Id
            Single id;
        }

        @Entity
        @Table(name = "items")
        record ItemWithNestedValueHavingNonInsertable(@Id int id, PairHavingNonInsertable nested) {}

        @Embeddable
        @Struct(name = "PairHavingNonInsertable")
        record PairHavingNonInsertable(@Column(insertable = false) int a, int b) {}

        @Entity
        @Table(name = "items")
        record ItemWithNestedValueHavingNonUpdatable(@Id int id, PairHavingNonUpdatable nested) {}

        @Embeddable
        @Struct(name = "PairHavingNonUpdatable")
        static class PairHavingNonUpdatable {
            @Column(updatable = false)
            int a;

            int b;

            PairHavingNonUpdatable() {}

            PairHavingNonUpdatable(int a, int b) {
                this.a = a;
                this.b = b;
            }
        }

        @Entity
        @Table(name = "items")
        static class ItemWithNestedValueHavingAllNonInsertable {
            @Id
            int id;

            PairAllNonInsertable omitted;

            ItemWithNestedValueHavingAllNonInsertable() {}

            ItemWithNestedValueHavingAllNonInsertable(int id, PairAllNonInsertable omitted) {
                this.id = id;
                this.omitted = omitted;
            }
        }

        @Embeddable
        @Struct(name = "PairAllNonInsertable")
        record PairAllNonInsertable(@Column(insertable = false) int a, @Column(insertable = false) int b) {}

        @Entity
        @Table(name = "items")
        static class ItemWithPolymorphicPersistentAttribute {
            @Id
            int id;

            Polymorphic polymorphic;

            ItemWithPolymorphicPersistentAttribute() {}

            ItemWithPolymorphicPersistentAttribute(int id, Polymorphic polymorphic) {
                this.id = id;
                this.polymorphic = polymorphic;
            }
        }

        @Embeddable
        @Struct(name = "Polymorphic")
        abstract static class Polymorphic {
            Polymorphic() {}
        }

        @Embeddable
        @Struct(name = "Concrete")
        static class Concrete extends Polymorphic {
            int a;

            Concrete() {}

            Concrete(int a) {
                this.a = a;
            }
        }

        @Entity
        @Table(name = "items")
        static class ItemWithNestedValueHavingEmbeddable {
            @Id
            int id;

            SingleHavingEmbeddable nested;

            ItemWithNestedValueHavingEmbeddable(int id, SingleHavingEmbeddable nested) {
                this.id = id;
                this.nested = nested;
            }
        }

        @Embeddable
        @Struct(name = "SingleHavingEmbeddable")
        record SingleHavingEmbeddable(EmbeddableIntegrationTests.Single flattened) {}

        @Entity
        @Table(name = "items")
        static class ItemWithNestedValueHavingNoPersistentAttributes {
            @Id
            int id;

            NoPersistentAttributes nested;
        }

        @Embeddable
        @Struct(name = "NoPersistentAttributes")
        static class NoPersistentAttributes {}
    }
}
