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
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.WRAPPER_ARRAY_HANDLING;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.embeddable.EmbeddableIntegrationTests;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.SQLFeatureNotSupportedException;
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
import org.bson.BsonString;
import org.bson.types.Decimal128;
import org.hibernate.MappingException;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.MetadataSources;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            ArrayAndCollectionIntegrationTests.ItemWithBasicArrayAndCollectionValues.class,
            ArrayAndCollectionIntegrationTests.ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue.class,
            ArrayAndCollectionIntegrationTests.Unsupported.ItemWithBoxedBytesArrayValue.class,
            ArrayAndCollectionIntegrationTests.Unsupported.ItemWithBytesCollectionValue.class
        })
@ServiceRegistry(settings = {@Setting(name = WRAPPER_ARRAY_HANDLING, value = "allow")})
@ExtendWith(MongoExtension.class)
// TODO-HIBERNATE-48 verify that we support `null`s in arrays/collections
// VAKOTODO test `ObjectId`s array/collection. It does not work, do we need to register a `UserType` for that?
class ArrayAndCollectionIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void testBasicArrayAndCollectionValues() {
        var item = new ItemWithBasicArrayAndCollectionValues(
                1,
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
                List.of('s', 't', 'r'),
                List.of(5),
                List.of(Long.MAX_VALUE, 6L),
                List.of(Double.MAX_VALUE),
                List.of(true),
                List.of("str"),
                List.of(BigDecimal.valueOf(10.1)));
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
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1)))))));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithBasicArrayAndCollectionValues.class, item.id));
        assertEq(item, loadedItem);
        ItemWithBasicArrayAndCollectionValues updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithBasicArrayAndCollectionValues.class, item.id);
            result.bytes[0] = (byte) -result.bytes[0];
            result.longs[1] = -result.longs[1];
            result.longsCollection.remove(6L);
            result.longsCollection.add(-6L);
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
                .append(ID_FIELD_NAME, new BsonInt32(1))
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
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1)))))));
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithBasicArrayAndCollectionValues.class, updatedItem.id));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testArrayAndCollectionOfStructAggregateEmbeddablesValue() {
        var item = new ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue(
                1,
                new SingleCollection[] {new SingleCollection(List.of(2, 3)), new SingleCollection(List.of(4))},
                List.of(new SingleCollection(List.of(2, 3)), new SingleCollection(List.of(4))));
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(new BsonDocument()
                .append(ID_FIELD_NAME, new BsonInt32(1))
                .append(
                        "embeddables",
                        new BsonArray(List.of(
                                new BsonDocument("ints", new BsonArray(List.of(new BsonInt32(2), new BsonInt32(3)))),
                                new BsonDocument("ints", new BsonArray(List.of(new BsonInt32(4)))))))
                .append(
                        "embeddablesCollection",
                        new BsonArray(List.of(
                                new BsonDocument("ints", new BsonArray(List.of(new BsonInt32(2), new BsonInt32(3)))),
                                new BsonDocument("ints", new BsonArray(List.of(new BsonInt32(4))))))));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue.class, item.id));
        assertEq(item, loadedItem);
        ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue.class, item.id);
            result.embeddables[1] = new SingleCollection(List.of(-4));
            result.embeddablesCollection.remove(new SingleCollection(List.of(4)));
            result.embeddablesCollection.add(new SingleCollection(List.of(-4)));
            return result;
        });
        assertCollectionContainsExactly(new BsonDocument()
                .append(ID_FIELD_NAME, new BsonInt32(1))
                .append(
                        "embeddables",
                        new BsonArray(List.of(
                                new BsonDocument("ints", new BsonArray(List.of(new BsonInt32(2), new BsonInt32(3)))),
                                new BsonDocument("ints", new BsonArray(List.of(new BsonInt32(-4)))))))
                .append(
                        "embeddablesCollection",
                        new BsonArray(List.of(
                                new BsonDocument("ints", new BsonArray(List.of(new BsonInt32(2), new BsonInt32(3)))),
                                new BsonDocument("ints", new BsonArray(List.of(new BsonInt32(-4))))))));
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue.class, updatedItem.id));
        assertEq(updatedItem, loadedItem);
    }

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    private static void assertCollectionContainsExactly(String json) { // VAKOTODO remove?
        assertThat(mongoCollection.find()).containsExactly(BsonDocument.parse(json));
    }

    private static void assertCollectionContainsExactly(BsonDocument document) {
        assertThat(mongoCollection.find()).containsExactly(document);
    }

    @Entity
    @Table(name = "items")
    static class ItemWithBasicArrayAndCollectionValues {
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
        Collection<Character> charsCollection;
        Collection<Integer> intsCollection;
        Collection<Long> longsCollection;
        Collection<Double> doublesCollection;
        Collection<Boolean> booleansCollection;
        Collection<String> stringsCollection;
        Collection<BigDecimal> bigDecimalsCollection;

        ItemWithBasicArrayAndCollectionValues() {}

        ItemWithBasicArrayAndCollectionValues(
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
                Collection<Character> charsCollection,
                Collection<Integer> intsCollection,
                Collection<Long> longsCollection,
                Collection<Double> doublesCollection,
                Collection<Boolean> booleansCollection,
                Collection<String> stringsCollection,
                Collection<BigDecimal> bigDecimalsCollection) {
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
            this.charsCollection = charsCollection;
            this.intsCollection = intsCollection;
            this.longsCollection = longsCollection;
            this.doublesCollection = doublesCollection;
            this.booleansCollection = booleansCollection;
            this.stringsCollection = stringsCollection;
            this.bigDecimalsCollection = bigDecimalsCollection;
        }
    }

    @Entity
    @Table(name = "items")
    static class ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue {
        @Id
        int id;

        SingleCollection[] embeddables;
        Collection<SingleCollection> embeddablesCollection;

        ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue() {}

        ItemWithArrayAndCollectionOfStructAggregateEmbeddablesValue(int id, SingleCollection[] embeddables, Collection<SingleCollection> embeddablesCollection) {
            this.id = id;
            this.embeddables = embeddables;
            this.embeddablesCollection = embeddablesCollection;
        }
    }

    @Embeddable
    @Struct(name = "SingleCollection")
    static class SingleCollection { // VAKOTODO use StructAggregateEmbeddableIntegrationTests.PairOfArrayAndCollection
        Collection<Integer> ints;

        SingleCollection() {}

        SingleCollection(Collection<Integer> ints) {
            this.ints = ints;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SingleCollection that = (SingleCollection) o;
            return Objects.equals(ints, that.ints);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(ints);
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
        void testArrayOfEmbeddablesBasicValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithArrayOfEmbeddablesBasicValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testCollectionOfEmbeddablesBasicValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithCollectionOfEmbeddablesBasicValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testArrayOfEmbeddablesValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithArrayOfEmbeddablesValue.class)
                            .buildMetadata()
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class);
        }

        @Test
        void testCollectionOfEmbeddablesValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithCollectionOfEmbeddablesValue.class)
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

        /**
         * <a
         * hraf="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#collections-as-basic">
         * Notice how all the previous examples explicitly mark the collection attribute as either `@ElementCollection`,
         * `@OneToMany` or `@ManyToMany`. Attributes of collection or array type without any of those annotations are
         * considered basic types and by default mapped like basic arrays...</a>
         */
        @Entity
        @Table(name = "items")
        static class ItemWithArrayOfEmbeddablesBasicValue {
            @Id
            int id;

            EmbeddableIntegrationTests.Single[] embeddables;
        }

        /**
         * <a
         * hraf="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#collections-as-basic">
         * Notice how all the previous examples explicitly mark the collection attribute as either `@ElementCollection`,
         * `@OneToMany` or `@ManyToMany`. Attributes of collection or array type without any of those annotations are
         * considered basic types and by default mapped like basic arrays...</a>
         */
        @Entity
        @Table(name = "items")
        static class ItemWithCollectionOfEmbeddablesBasicValue {
            @Id
            int id;

            Collection<EmbeddableIntegrationTests.Single> embeddables;
        }

        /**
         * <a
         * hraf="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#collections-as-basic">
         * Notice how all the previous examples explicitly mark the collection attribute as either `@ElementCollection`,
         * `@OneToMany` or `@ManyToMany`. Attributes of collection or array type without any of those annotations are
         * considered basic types and by default mapped like basic arrays...</a>
         */
        @Entity
        @Table(name = "items")
        static class ItemWithArrayOfEmbeddablesValue {
            @Id
            int id;

            @ElementCollection
            EmbeddableIntegrationTests.Single[] embeddables;
        }

        /**
         * <a
         * hraf="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#collections-as-basic">
         * Notice how all the previous examples explicitly mark the collection attribute as either `@ElementCollection`,
         * `@OneToMany` or `@ManyToMany`. Attributes of collection or array type without any of those annotations are
         * considered basic types and by default mapped like basic arrays...</a>
         */
        @Entity
        @Table(name = "items")
        static class ItemWithCollectionOfEmbeddablesValue {
            @Id
            int id;

            @ElementCollection
            Collection<EmbeddableIntegrationTests.Single> embeddables;
        }
    }
}
