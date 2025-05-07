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
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.type.MongoStructJdbcType;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collection;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
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
                new PairWithParent(3, new PairOfChars('a', 'b')));
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
                            a: "a",
                            b: "b"
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
                            a: "a",
                            b: "b"
                        }
                    }
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValues.class, updatedItem.flattenedId));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testNestedEmptyValue() {
        var item = new ItemWithOmittedEmptyValue(1, new Empty());
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

    @Test
    void testNestedValueHavingArraysAndCollections() {
        var item = new ItemWithNestedValueHavingArraysAndCollections(
                1,
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
                        new ObjectId[] {new ObjectId(0, 1)},
                        new Single[] {new Single(1)},
                        List.of('s', 't', 'r'),
                        List.of(5),
                        List.of(Long.MAX_VALUE, 6L),
                        List.of(Double.MAX_VALUE),
                        List.of(true),
                        List.of("str"),
                        List.of(BigDecimal.valueOf(10.1)),
                        List.of(new ObjectId(0, 1)),
                        List.of(new Single(1))));
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(new BsonDocument()
                .append(ID_FIELD_NAME, new BsonInt32(1))
                .append(
                        "nested",
                        new BsonDocument()
                                .append("bytes", new BsonBinary(new byte[] {2, 3}))
                                .append("chars", new BsonString("str"))
                                .append("ints", new BsonArray(List.of(new BsonInt32(5))))
                                .append(
                                        "longs",
                                        new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(6))))
                                .append("doubles", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                                .append("booleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                                .append(
                                        "boxedChars",
                                        new BsonArray(
                                                List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                                .append("boxedInts", new BsonArray(List.of(new BsonInt32(7))))
                                .append("boxedLongs", new BsonArray(List.of(new BsonInt64(8))))
                                .append("boxedDoubles", new BsonArray(List.of(new BsonDouble(9.1))))
                                .append("boxedBooleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                                .append("strings", new BsonArray(List.of(new BsonString("str"))))
                                .append(
                                        "bigDecimals",
                                        new BsonArray(
                                                List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1))))))
                                .append("objectIds", new BsonArray(List.of(new BsonObjectId(new ObjectId(0, 1)))))
                                .append(
                                        "structAggregateEmbeddables",
                                        new BsonArray(List.of(new BsonDocument("a", new BsonInt32(1)))))
                                .append(
                                        "charsCollection",
                                        new BsonArray(
                                                List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                                .append("intsCollection", new BsonArray(List.of(new BsonInt32(5))))
                                .append(
                                        "longsCollection",
                                        new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(6))))
                                .append("doublesCollection", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                                .append("booleansCollection", new BsonArray(List.of(BsonBoolean.TRUE)))
                                .append("stringsCollection", new BsonArray(List.of(new BsonString("str"))))
                                .append(
                                        "bigDecimalsCollection",
                                        new BsonArray(
                                                List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1))))))
                                .append(
                                        "objectIdsCollection",
                                        new BsonArray(List.of(new BsonObjectId(new ObjectId(0, 1)))))
                                .append(
                                        "structAggregateEmbeddablesCollection",
                                        new BsonArray(List.of(new BsonDocument("a", new BsonInt32(1)))))));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithNestedValueHavingArraysAndCollections.class, item.id));
        assertEq(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithNestedValueHavingArraysAndCollections.class, item.id);
            result.nested.bytes[0] = (byte) -result.nested.bytes[0];
            result.nested.longs[1] = -result.nested.longs[1];
            result.nested.objectIds[0] = new ObjectId(0, 2);
            result.nested.longsCollection.remove(6L);
            result.nested.longsCollection.add(-6L);
            return result;
        });
        assertCollectionContainsExactly(new BsonDocument()
                .append(ID_FIELD_NAME, new BsonInt32(1))
                .append(
                        "nested",
                        new BsonDocument()
                                .append("bytes", new BsonBinary(new byte[] {-2, 3}))
                                .append("chars", new BsonString("str"))
                                .append("ints", new BsonArray(List.of(new BsonInt32(5))))
                                .append(
                                        "longs",
                                        new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(-6))))
                                .append("doubles", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                                .append("booleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                                .append(
                                        "boxedChars",
                                        new BsonArray(
                                                List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                                .append("boxedInts", new BsonArray(List.of(new BsonInt32(7))))
                                .append("boxedLongs", new BsonArray(List.of(new BsonInt64(8))))
                                .append("boxedDoubles", new BsonArray(List.of(new BsonDouble(9.1))))
                                .append("boxedBooleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                                .append("strings", new BsonArray(List.of(new BsonString("str"))))
                                .append(
                                        "bigDecimals",
                                        new BsonArray(
                                                List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1))))))
                                .append("objectIds", new BsonArray(List.of(new BsonObjectId(new ObjectId(0, 2)))))
                                .append(
                                        "structAggregateEmbeddables",
                                        new BsonArray(List.of(new BsonDocument("a", new BsonInt32(1)))))
                                .append(
                                        "charsCollection",
                                        new BsonArray(
                                                List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                                .append("intsCollection", new BsonArray(List.of(new BsonInt32(5))))
                                .append(
                                        "longsCollection",
                                        new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(-6))))
                                .append("doublesCollection", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                                .append("booleansCollection", new BsonArray(List.of(BsonBoolean.TRUE)))
                                .append("stringsCollection", new BsonArray(List.of(new BsonString("str"))))
                                .append(
                                        "bigDecimalsCollection",
                                        new BsonArray(
                                                List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1))))))
                                .append(
                                        "objectIdsCollection",
                                        new BsonArray(List.of(new BsonObjectId(new ObjectId(0, 1)))))
                                .append(
                                        "structAggregateEmbeddablesCollection",
                                        new BsonArray(List.of(new BsonDocument("a", new BsonInt32(1)))))));
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
                        List.of(),
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

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    private static void assertCollectionContainsExactly(String json) {
        assertThat(mongoCollection.find()).containsExactly(BsonDocument.parse(json));
    }

    private static void assertCollectionContainsExactly(BsonDocument document) {
        assertThat(mongoCollection.find()).containsExactly(document);
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
        PairOfChars nested;

        @Parent ItemWithNestedValues parent;

        PairWithParent() {}

        PairWithParent(int a, PairOfChars nested) {
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

    /**
     * <a
     * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#basic-character">
     * Hibernate ORM maps {@code char}/{@link Character} to {@link JDBCType#CHAR} by default</a>, which for us means
     * {@link BsonString}. We need to test that {@link MongoStructJdbcType} handles that correctly.
     */
    // VAKOTODO use all supported types
    @Embeddable
    @Struct(name = "PairOfChars")
    record PairOfChars(char a, Character b) {}

    @Entity
    @Table(name = "items")
    static class ItemWithOmittedEmptyValue {
        @Id
        int id;

        Empty omitted;

        ItemWithOmittedEmptyValue() {}

        ItemWithOmittedEmptyValue(int id, Empty omitted) {
            this.id = id;
            this.omitted = omitted;
        }
    }

    @Embeddable
    @Struct(name = "Empty")
    static class Empty {}

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
    static class ArraysAndCollections {
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
        Collection<Character> charsCollection;
        Collection<Integer> intsCollection;
        Collection<Long> longsCollection;
        Collection<Double> doublesCollection;
        Collection<Boolean> booleansCollection;
        Collection<String> stringsCollection;
        Collection<BigDecimal> bigDecimalsCollection;
        Collection<ObjectId> objectIdsCollection;
        Collection<Single> structAggregateEmbeddablesCollection;

        ArraysAndCollections() {}

        ArraysAndCollections(
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
                Collection<Character> charsCollection,
                Collection<Integer> intsCollection,
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
    }
}
