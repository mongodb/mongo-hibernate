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

import static com.mongodb.hibernate.MongoTestAssertions.assertEquals;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.AvailableSettings.WRAPPER_ARRAY_HANDLING;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.embeddable.EmbeddableIntegrationTests;
import com.mongodb.hibernate.embeddable.StructAggregateEmbeddableIntegrationTests;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
import org.bson.BsonString;
import org.bson.types.Decimal128;
import org.hibernate.MappingException;
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
            CollectionIntegrationTests.ItemWithBasicArrayValues.class,
            CollectionIntegrationTests.ItemWithBasicCollectionValues.class,
            CollectionIntegrationTests.ItemWithStructAggregateEmbeddablesCollectionValue.class,
            CollectionIntegrationTests.Unsupported.ItemWithBoxedBytesArrayValue.class,
            CollectionIntegrationTests.Unsupported.ItemWithBytesCollectionValue.class
        })
@ServiceRegistry(settings = {@Setting(name = WRAPPER_ARRAY_HANDLING, value = "allow")})
@ExtendWith(MongoExtension.class)
// TODO-HIBERNATE-48 verify that we support `null`s in arrays/collections
// VAKOTODO test `ObjectId`s array/collection. It does not work, do we need to register a `UserType` for that?
class CollectionIntegrationTests implements SessionFactoryScopeAware {
    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Test
    void testBasicArrayValues() {
        var item = new ItemWithBasicArrayValues();
        {
            item.id = 1;
            item.bytes = new byte[] {2, 3};
            item.chars = new char[] {'s', 't', 'r'};
            item.ints = new int[] {5};
            item.longs = new long[] {Long.MAX_VALUE, 6};
            item.doubles = new double[] {Double.MAX_VALUE};
            item.booleans = new boolean[] {true};
            item.boxedChars = new Character[] {'s', 't', 'r'};
            item.boxedInts = new Integer[] {7};
            item.boxedLongs = new Long[] {8L};
            item.boxedDoubles = new Double[] {9.1d};
            item.boxedBooleans = new Boolean[] {true};
            item.strings = new String[] {"str"};
            item.bigDecimals = new BigDecimal[] {BigDecimal.valueOf(10.1)};
        }
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
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1)))))));
        var loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithBasicArrayValues.class, item.id));
        assertEquals(item, loadedItem);
        ItemWithBasicArrayValues updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithBasicArrayValues.class, item.id);
            result.bytes[0] = (byte) -result.bytes[0];
            result.longs[1] = -result.longs[1];
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
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1)))))));
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithBasicArrayValues.class, updatedItem.id));
        assertEquals(updatedItem, loadedItem);
    }

    @Test
    void testBasicCollectionValues() {
        var item = new ItemWithBasicCollectionValues();
        {
            item.id = 1;
            item.chars = List.of('s', 't', 'r');
            item.ints = List.of(5);
            item.longs = List.of(Long.MAX_VALUE, 6L);
            item.doubles = List.of(Double.MAX_VALUE);
            item.booleans = List.of(true);
            item.strings = List.of("str");
            item.bigDecimals = List.of(BigDecimal.valueOf(10.1));
        }
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(new BsonDocument()
                .append(ID_FIELD_NAME, new BsonInt32(1))
                .append("chars", new BsonArray(List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                .append("ints", new BsonArray(List.of(new BsonInt32(5))))
                .append("longs", new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(6))))
                .append("doubles", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                .append("booleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                .append("strings", new BsonArray(List.of(new BsonString("str"))))
                .append(
                        "bigDecimals",
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1)))))));
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithBasicCollectionValues.class, item.id));
        assertEquals(item, loadedItem);
        ItemWithBasicCollectionValues updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithBasicCollectionValues.class, item.id);
            result.longs.remove(6L);
            result.longs.add(-6L);
            return result;
        });
        assertCollectionContainsExactly(new BsonDocument()
                .append(ID_FIELD_NAME, new BsonInt32(1))
                .append("chars", new BsonArray(List.of(new BsonString("s"), new BsonString("t"), new BsonString("r"))))
                .append("ints", new BsonArray(List.of(new BsonInt32(5))))
                .append("longs", new BsonArray(List.of(new BsonInt64(Long.MAX_VALUE), new BsonInt64(-6))))
                .append("doubles", new BsonArray(List.of(new BsonDouble(Double.MAX_VALUE))))
                .append("booleans", new BsonArray(List.of(BsonBoolean.TRUE)))
                .append("strings", new BsonArray(List.of(new BsonString("str"))))
                .append(
                        "bigDecimals",
                        new BsonArray(List.of(new BsonDecimal128(new Decimal128(BigDecimal.valueOf(10.1)))))));
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithBasicCollectionValues.class, updatedItem.id));
        assertEquals(updatedItem, loadedItem);
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
    static class ItemWithBasicArrayValues {
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
    }

    @Entity
    @Table(name = "items")
    static class ItemWithBasicCollectionValues {
        @Id
        int id;

        Collection<Character> chars;
        Collection<Integer> ints;
        Collection<Long> longs;
        Collection<Double> doubles;
        Collection<Boolean> booleans;
        Collection<String> strings;
        Collection<BigDecimal> bigDecimals;
    }

    @Entity
    @Table(name = "items")
    // VAKOTODO support and add a test for it, as well as for arrays; also add tests to the embeddable test classes
    static class ItemWithStructAggregateEmbeddablesCollectionValue {
        @Id
        int id;

        Collection<StructAggregateEmbeddableIntegrationTests.SingleValue> embeddables;
    }

    @Nested
    class Unsupported {
        @Test
        void testBoxedBytesArrayValue() {
            var item = new ItemWithBoxedBytesArrayValue();
            {
                item.id = 1;
                item.bytes = new byte[] {1};
                item.boxedBytes = new Byte[] {2};
            }
            // this is Hibernate ORM bug, and it goes away if the `ItemWithBoxedBytesArrayValue.bytes` field is removed
            assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                    .isInstanceOf(ClassCastException.class);
        }

        @Test
        void testBytesCollectionValue() {
            var item = new ItemWithBytesCollectionValue();
            {
                item.id = 1;
                item.bytes = List.of((byte) 2);
            }
            assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                    .hasCauseInstanceOf(SQLFeatureNotSupportedException.class);
        }

        @Test
        void testNestedArrayValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(CollectionIntegrationTests.Unsupported.ItemWithNestedArrayValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testNestedCollectionValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(
                                    CollectionIntegrationTests.Unsupported.ItemWithNestedCollectionValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testEmbeddablesArrayValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(
                                    CollectionIntegrationTests.Unsupported.ItemWithEmbeddablesArrayValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Test
        void testEmbeddablesCollectionValue() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(
                                    CollectionIntegrationTests.Unsupported.ItemWithEmbeddablesCollectionValue.class)
                            .buildMetadata())
                    .isInstanceOf(MappingException.class);
        }

        @Entity
        @Table(name = "items")
        static class ItemWithBoxedBytesArrayValue {
            @Id
            int id;

            byte[] bytes;
            Byte[] boxedBytes;
        }

        @Entity
        @Table(name = "items")
        static class ItemWithBytesCollectionValue {
            @Id
            int id;

            Collection<Byte> bytes;
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
        static class ItemWithEmbeddablesArrayValue {
            @Id
            int id;

            EmbeddableIntegrationTests.SingleValue[] embeddables;
        }

        @Entity
        @Table(name = "items")
        static class ItemWithEmbeddablesCollectionValue {
            @Id
            int id;

            Collection<EmbeddableIntegrationTests.SingleValue> embeddables;
        }
    }
}
