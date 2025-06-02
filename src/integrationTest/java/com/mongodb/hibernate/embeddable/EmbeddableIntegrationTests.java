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
import com.mongodb.hibernate.internal.type.MongoStructJdbcType;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.AccessType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
            EmbeddableIntegrationTests.ItemWithOmittedEmptyValue.class,
            EmbeddableIntegrationTests.ItemWithFlattenedValueHavingArraysAndCollections.class
        })
@ExtendWith(MongoExtension.class)
// VAKOTODO embeddable having struct?
public class EmbeddableIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void testFlattenedValues() {
        var item = new ItemWithFlattenedValues(
                new Single(1), new Single(2), new PairWithParent(3, new PairOfChars('a', 'b')));
        item.flattened2.parent = item;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    flattened1_a: 2,
                    flattened2_a: 3,
                    flattened2_flattened_a: "a",
                    flattened2_flattened_b: "b"
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
                    flattened2_flattened_a: "a",
                    flattened2_flattened_b: "b"
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValues.class, updatedItem.flattenedId));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testFlattenedEmptyValue() {
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
    void testFlattenedValueHavingArraysAndCollections() {
        var item = new ItemWithFlattenedValueHavingArraysAndCollections(
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
                        List.of('s', 't', 'r'),
                        List.of(5),
                        List.of(Long.MAX_VALUE, 6L),
                        List.of(Double.MAX_VALUE),
                        List.of(true),
                        List.of("str"),
                        List.of(BigDecimal.valueOf(10.1)),
                        List.of(new ObjectId(0, 1))));
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(new BsonDocument()
                .append(ID_FIELD_NAME, new BsonInt32(1))
                .append("bytes", new BsonBinary(new byte[] {2, 3}))
                .append("chars", new BsonString("str"))
                .append("ints", new BsonArray(List.of(new BsonInt32(5))))
                .append("longs", new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(6))))
                .append("doubles", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                .append("booleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                .append(
                        "boxedChars",
                        new BsonArray(List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                .append("boxedInts", new BsonArray(List.of(new BsonInt32(7))))
                .append("boxedLongs", new BsonArray(List.of(new BsonInt64(8))))
                .append("boxedDoubles", new BsonArray(List.of(new BsonDouble(9.1))))
                .append("boxedBooleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                .append("strings", new BsonArray(List.of(new BsonString("str"))))
                .append(
                        "bigDecimals",
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1))))))
                .append("objectIds", new BsonArray(List.of(new BsonObjectId(new ObjectId(0, 1)))))
                .append(
                        "charsCollection",
                        new BsonArray(List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                .append("intsCollection", new BsonArray(List.of(new BsonInt32(5))))
                .append("longsCollection", new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(6))))
                .append("doublesCollection", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                .append("booleansCollection", new BsonArray(List.of(BsonBoolean.TRUE)))
                .append("stringsCollection", new BsonArray(List.of(new BsonString("str"))))
                .append(
                        "bigDecimalsCollection",
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1))))))
                .append("objectIdsCollection", new BsonArray(List.of(new BsonObjectId(new ObjectId(0, 1))))));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValueHavingArraysAndCollections.class, item.id));
        assertEq(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithFlattenedValueHavingArraysAndCollections.class, item.id);
            result.flattened.bytes[0] = (byte) -result.flattened.bytes[0];
            result.flattened.longs[1] = -result.flattened.longs[1];
            result.flattened.objectIds[0] = new ObjectId(0, 2);
            result.flattened.longsCollection.remove(6L);
            result.flattened.longsCollection.add(-6L);
            return result;
        });
        assertCollectionContainsExactly(new BsonDocument()
                .append(ID_FIELD_NAME, new BsonInt32(1))
                .append("bytes", new BsonBinary(new byte[] {-2, 3}))
                .append("chars", new BsonString("str"))
                .append("ints", new BsonArray(List.of(new BsonInt32(5))))
                .append("longs", new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(-6))))
                .append("doubles", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                .append("booleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                .append(
                        "boxedChars",
                        new BsonArray(List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                .append("boxedInts", new BsonArray(List.of(new BsonInt32(7))))
                .append("boxedLongs", new BsonArray(List.of(new BsonInt64(8))))
                .append("boxedDoubles", new BsonArray(List.of(new BsonDouble(9.1))))
                .append("boxedBooleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                .append("strings", new BsonArray(List.of(new BsonString("str"))))
                .append(
                        "bigDecimals",
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1))))))
                .append("objectIds", new BsonArray(List.of(new BsonObjectId(new ObjectId(0, 2)))))
                .append(
                        "charsCollection",
                        new BsonArray(List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                .append("intsCollection", new BsonArray(List.of(new BsonInt32(5))))
                .append("longsCollection", new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(-6))))
                .append("doublesCollection", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                .append("booleansCollection", new BsonArray(List.of(BsonBoolean.TRUE)))
                .append("stringsCollection", new BsonArray(List.of(new BsonString("str"))))
                .append(
                        "bigDecimalsCollection",
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1))))))
                .append("objectIdsCollection", new BsonArray(List.of(new BsonObjectId(new ObjectId(0, 1))))));
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValueHavingArraysAndCollections.class, updatedItem.id));
        assertEq(updatedItem, loadedItem);
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
    static class ItemWithFlattenedValues {
        @Id
        Single flattenedId;

        @AttributeOverride(name = "a", column = @Column(name = "flattened1_a"))
        Single flattened1;

        @AttributeOverride(name = "a", column = @Column(name = "flattened2_a"))
        @AttributeOverride(name = "flattened.a", column = @Column(name = "flattened2_flattened_a"))
        @AttributeOverride(name = "flattened.b", column = @Column(name = "flattened2_flattened_b"))
        PairWithParent flattened2;

        ItemWithFlattenedValues() {}

        ItemWithFlattenedValues(Single flattenedId, Single flattened1, PairWithParent flattened2) {
            this.flattenedId = flattenedId;
            this.flattened1 = flattened1;
            this.flattened2 = flattened2;
        }
    }

    @Embeddable
    public static class Single {
        int a;

        Single() {}

        Single(int a) {
            this.a = a;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Single single = (Single) o;
            return a == single.a;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(a);
        }
    }

    @Embeddable
    static class PairWithParent {
        int a;
        PairOfChars flattened;

        @Parent ItemWithFlattenedValues parent;

        PairWithParent() {}

        PairWithParent(int a, PairOfChars flattened) {
            this.a = a;
            this.flattened = flattened;
        }

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

    /**
     * Hibernate ORM <a
     * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#basic-character">maps
     * {@code char}/{@link Character} to {@link JDBCType#CHAR} by default</a>, which for us means {@link BsonString}. We
     * test that {@link MongoStructJdbcType} handles that correctly.
     */
    // VAKOTODO use all supported types
    @Embeddable
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
    static class Empty {}

    @Entity
    @Table(name = "items")
    static class ItemWithFlattenedValueHavingArraysAndCollections {
        @Id
        int id;

        ArraysAndCollections flattened;

        ItemWithFlattenedValueHavingArraysAndCollections() {}

        ItemWithFlattenedValueHavingArraysAndCollections(int id, ArraysAndCollections flattened) {
            this.id = id;
            this.flattened = flattened;
        }
    }

    @Embeddable
    static class ArraysAndCollections { // VAKOTODO use all types
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
        Collection<Character> charsCollection;
        Collection<Integer> intsCollection;
        Collection<Long> longsCollection;
        Collection<Double> doublesCollection;
        Collection<Boolean> booleansCollection;
        Collection<String> stringsCollection;
        Collection<BigDecimal> bigDecimalsCollection;
        Collection<ObjectId> objectIdsCollection;

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
                Collection<Character> charsCollection,
                Collection<Integer> intsCollection,
                Collection<Long> longsCollection,
                Collection<Double> doublesCollection,
                Collection<Boolean> booleansCollection,
                Collection<String> stringsCollection,
                Collection<BigDecimal> bigDecimalsCollection,
                Collection<ObjectId> objectIdsCollection) {
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
            this.charsCollection = charsCollection;
            this.intsCollection = intsCollection;
            this.longsCollection = longsCollection;
            this.doublesCollection = doublesCollection;
            this.booleansCollection = booleansCollection;
            this.stringsCollection = stringsCollection;
            this.bigDecimalsCollection = bigDecimalsCollection;
            this.objectIdsCollection = objectIdsCollection;
        }
    }

    @Nested
    class Unsupported {
        @Test
        void testPrimaryKeySpanningMultipleFields() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithPairAsId.class)
                            .buildMetadata())
                    .hasMessageContaining("does not support primary key spanning multiple columns");
        }

        @Entity
        @Table(name = "items")
        static class ItemWithPairAsId {
            @Id
            PairOfChars id;
        }
    }
}
