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

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;
import static com.mongodb.hibernate.MongoTestAssertions.assertUsingRecursiveComparison;
import static org.assertj.core.api.Assertions.assertThat;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            BasicCrudIntegrationTests.Item.class,
            BasicCrudIntegrationTests.ItemDynamicallyUpdated.class,
        })
@ExtendWith(MongoExtension.class)
class BasicCrudIntegrationTests implements SessionFactoryScopeAware {

    private static final TimeZone CURRENT_TIME_ZONE = TimeZone.getDefault();

    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    protected SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @BeforeAll
    private static void setUp() {
        // Set timezone to UTC to have deterministic date/time values.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    private static void tearDown() {
        TimeZone.setDefault(CURRENT_TIME_ZONE);
    }

    @Nested
    class InsertTests {
        @Test
        void testSimpleEntityInsertion() {
            sessionFactoryScope.inTransaction(session -> session.persist(new Item(
                    1,
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
                    new ObjectId("000000000000000000000001"),
                    LocalDate.of(2024, 1, 1),
                    LocalTime.of(12, 0),
                    Instant.ofEpochMilli(100),
                    OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC),
                    OffsetTime.of(1, 1, 1, 1, ZoneOffset.UTC),
                    LocalDateTime.of(2024, 1, 1, 12, 0),
                    new Date(123456789L))));
            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
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
                        objectId: {$oid: "000000000000000000000001"},
                        localDate: {"$date": "2024-01-01T00:00:00Z"},
                        localTime1: {"$date": "1970-01-01T12:00:00Z"},
                        instant: {"$date": "1970-01-01T00:00:00.1Z"},
                        "offsetDateTime": {"$date": "2024-01-01T12:00:00Z"},
                        "offsetTime": {"$date": "1970-01-01T01:01:01Z"},
                        "localDateTime": {"$date": "2024-01-01T12:00:00Z"},
                        "date": {"$date": "1970-01-02T10:17:36.789Z"}
                    }
                    """);
        }

        @Test
        void testEntityWithNullFieldValuesInsertion() {
            sessionFactoryScope.inTransaction(session -> session.persist(new Item(
                    1,
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
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null)));
            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
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
                        objectId: null,
                        localDate: null,
                        localTime1: null,
                        instant: null,
                        offsetDateTime: null,
                        "offsetTime": null,
                        "localDateTime": null,
                        "date": null
                    }
                    """);
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void testSimpleDeletion() {

            var id = 1;
            sessionFactoryScope.inTransaction(session -> session.persist(new Item(
                    id,
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
                    new ObjectId("000000000000000000000001"),
                    LocalDate.of(2024, 1, 1),
                    LocalTime.of(12, 0),
                    Instant.ofEpochMilli(100),
                    OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC),
                    OffsetTime.of(1, 1, 1, 1, ZoneOffset.UTC),
                    LocalDateTime.of(2024, 1, 1, 12, 0),
                    new Date(123456789L))));

            assertCollectionSize(1);

            sessionFactoryScope.inTransaction(session -> {
                var item = session.getReference(Item.class, id);
                session.remove(item);
            });

            assertThat(mongoCollection.find()).isEmpty();
        }
    }

    protected void assertCollectionSize(final int expectedSize) {
        assertThat(mongoCollection.find()).hasSize(expectedSize);
    }

    @Nested
    class UpdateTests {

        @Test
        void testSimpleUpdate() {
            sessionFactoryScope.inTransaction(session -> {
                var item = new Item(
                        1,
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
                        new ObjectId("000000000000000000000001"),
                        LocalDate.of(2024, 1, 1),
                        LocalTime.of(12, 0),
                        Instant.ofEpochMilli(100),
                        OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC),
                        OffsetTime.of(1, 1, 1, 1, ZoneOffset.UTC),
                        LocalDateTime.of(2024, 1, 1, 12, 0),
                        new Date(123456789L));
                session.persist(item);
                session.flush();
                item.primitiveBoolean = false;
                item.boxedBoolean = false;
            });

            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveChar: "c",
                        primitiveInt: 1,
                        primitiveLong: {$numberLong: "9223372036854775807"},
                        primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                        primitiveBoolean: false,
                        boxedChar: "c",
                        boxedInt: 1,
                        boxedLong: {$numberLong: "9223372036854775807"},
                        boxedDouble: {$numberDouble: "1.7976931348623157E308"},
                        boxedBoolean: false,
                        string: "str",
                        bigDecimal: {$numberDecimal: "10.1"},
                        objectId: {$oid: "000000000000000000000001"},
                        localDate: {"$date": "2024-01-01T00:00:00Z"},
                        localTime1: {"$date": "1970-01-01T12:00:00Z"},
                        instant: {"$date": "1970-01-01T00:00:00.1Z"},
                        "offsetDateTime": {"$date": "2024-01-01T12:00:00Z"},
                        "offsetTime": {"$date": "1970-01-01T01:01:01Z"},
                        "localDateTime": {"$date": "2024-01-01T12:00:00Z"},
                        "date": {"$date": "1970-01-02T10:17:36.789Z"}
                    }
                    """);
        }

        @Test
        void testSimpleUpdateWithNullFieldValues() {
            sessionFactoryScope.inTransaction(session -> {
                var item = new Item(
                        1,
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
                        new ObjectId("000000000000000000000001"),
                        LocalDate.of(2024, 1, 1),
                        LocalTime.of(12, 0),
                        Instant.ofEpochMilli(100),
                        OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC),
                        OffsetTime.of(1, 1, 1, 1, ZoneOffset.UTC),
                        LocalDateTime.of(2024, 1, 1, 12, 0),
                        new Date(123456789L));
                session.persist(item);
                session.flush();
                item.boxedChar = null;
                item.boxedInt = null;
                item.boxedLong = null;
                item.boxedDouble = null;
                item.boxedBoolean = null;
                item.string = null;
                item.bigDecimal = null;
                item.objectId = null;
                item.localDate = null;
                item.localTime1 = null;
                item.instant = null;
                item.offsetDateTime = null;
                item.offsetTime = null;
                item.localDateTime = null;
                item.date = null;
            });

            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
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
                        objectId: null,
                        localDate: null,
                        localTime1: null,
                        instant: null,
                        offsetDateTime: null,
                        offsetTime: null,
                        localDateTime: null,
                        date: null
                    }
                    """);
        }

        @Test
        void testDynamicUpdate() {
            sessionFactoryScope.inTransaction(session -> {
                var item = new ItemDynamicallyUpdated(1, true, true);
                session.persist(item);
                session.flush();
                item.primitiveBoolean = false;
                item.boxedBoolean = false;
            });

            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveBoolean: false,
                        boxedBoolean: false
                    }
                    """);
        }

        @Test
        void testDynamicUpdateWithNullFieldValues() {
            sessionFactoryScope.inTransaction(session -> {
                var item = new ItemDynamicallyUpdated(1, false, true);
                session.persist(item);
                session.flush();
                item.boxedBoolean = null;
            });

            assertCollectionContainsExactly(
                    """
                    {
                        _id: 1,
                        primitiveBoolean: false,
                        boxedBoolean: null
                    }
                    """);
        }
    }

    @Nested
    class SelectTests {

        @Test
        void testFindByPrimaryKey() {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            var item = new Item(
                    1,
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
                    new ObjectId("000000000000000000000001"),
                    LocalDate.of(2024, 1, 1),
                    LocalTime.of(12, 0),
                    Instant.ofEpochMilli(100),
                    OffsetDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC),
                    OffsetTime.of(1, 1, 1, 0, ZoneOffset.UTC),
                    LocalDateTime.of(2024, 1, 1, 12, 0),
                    new Date(Instant.now().toEpochMilli()));
            sessionFactoryScope.inTransaction(session -> session.persist(item));

            var loadedItem = sessionFactoryScope.fromTransaction(session -> session.find(Item.class, item.id));

            assertUsingRecursiveComparison(item, loadedItem, (recursiveComparisonAssert, expected) -> {
                recursiveComparisonAssert
                        .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                        .isEqualTo(expected);
            });
        }

        @Test
        void testFindByPrimaryKeyWithNullFieldValues() {

            var item = new Item(
                    1,
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
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            sessionFactoryScope.inTransaction(session -> session.persist(item));

            var loadedItem = sessionFactoryScope.fromTransaction(session -> session.find(Item.class, item.id));
            assertEq(item, loadedItem);
        }
    }

    protected void assertCollectionContainsExactly(String documentAsJsonObject) {
        List<BsonDocument> actualItems = new ArrayList<>();
        mongoCollection.find().into(actualItems);
        for (BsonDocument actualItem : actualItems) {
            System.err.println("JSON " + actualItem.toJson());
        }
        assertThat(actualItems).containsExactly(BsonDocument.parse(documentAsJsonObject));
    }

    @Entity
    @Table(name = "items")
    static class Item {
        @Id
        int id;

        char primitiveChar;
        int primitiveInt;
        long primitiveLong;
        double primitiveDouble;
        boolean primitiveBoolean;
        Character boxedChar;
        Integer boxedInt;
        Long boxedLong;
        Double boxedDouble;
        Boolean boxedBoolean;
        String string;

        @Column(precision = 5, scale = 1)
        BigDecimal bigDecimal;

        ObjectId objectId;
        LocalDate localDate;
        LocalTime localTime1;
        Instant instant;
        OffsetDateTime offsetDateTime;
        OffsetTime offsetTime;
        LocalDateTime localDateTime;
        Date date;

        public Item() {}

        public Item(
                int id,
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
                ObjectId objectId,
                LocalDate localDate,
                LocalTime localTime,
                Instant instant,
                OffsetDateTime offsetDateTime,
                OffsetTime offsetTime,
                LocalDateTime localDateTime,
                Date date) {
            this.id = id;
            this.primitiveChar = primitiveChar;
            this.primitiveInt = primitiveInt;
            this.primitiveLong = primitiveLong;
            this.primitiveDouble = primitiveDouble;
            this.primitiveBoolean = primitiveBoolean;
            this.boxedChar = boxedChar;
            this.boxedInt = boxedInt;
            this.boxedLong = boxedLong;
            this.boxedDouble = boxedDouble;
            this.boxedBoolean = boxedBoolean;
            this.string = string;
            this.bigDecimal = bigDecimal;
            this.objectId = objectId;
            this.localDate = localDate;
            this.localTime1 = localTime;
            this.instant = instant;
            this.offsetDateTime = offsetDateTime;
            this.offsetTime = offsetTime;
            this.localDateTime = localDateTime;
            this.date = date;
        }
    }

    @Entity
    @Table(name = "items")
    static class ItemDynamicallyUpdated {
        @Id
        int id;

        boolean primitiveBoolean;
        Boolean boxedBoolean;

        public ItemDynamicallyUpdated() {}

        public ItemDynamicallyUpdated(int id, boolean primitiveBoolean, Boolean boxedBoolean) {
            this.id = id;
            this.primitiveBoolean = primitiveBoolean;
            this.boxedBoolean = boxedBoolean;
        }
    }
}
