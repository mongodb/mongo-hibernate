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

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.ArrayAndCollectionIntegrationTests;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.lang.Nullable;
import jakarta.persistence.AccessType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.hibernate.HibernateException;
import org.hibernate.annotations.Parent;
import org.hibernate.boot.MetadataSources;
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
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

import static com.mongodb.hibernate.MongoTestAssertions.assertUsingRecursiveComparison;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNull;

@SessionFactory(exportSchema = true)
@DomainModel(
        annotatedClasses = {
            EmbeddableIntegrationTests.ItemWithFlattenedValues.class,
            EmbeddableIntegrationTests.ItemWithFlattenedValueHavingArraysAndCollections.class,
            EmbeddableIntegrationTests.Unsupported.ItemWithFlattenedValueHavingStructAggregateEmbeddable.class
        })
@ExtendWith(MongoExtension.class)
public class EmbeddableIntegrationTests implements SessionFactoryScopeAware {

    private static final TimeZone CURRENT_TIME_ZONE = TimeZone.getDefault();

    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @BeforeAll
    public static void setUp() {
        // Set timezone to UTC to have deterministic date/time values.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterAll
    public static void tearDown() {
        TimeZone.setDefault(CURRENT_TIME_ZONE);
    }

    @Test
    void testFlattenedValues() {
        var item = new ItemWithFlattenedValues(
                new Single(1),
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
                                new ObjectId("000000000000000000000001"),
                                LocalDate.of(2025, 1, 1),
                                LocalTime.of(3, 15, 30),
                                LocalDateTime.of(2025, 10, 10, 3, 15, 30),
                                OffsetTime.parse("03:15:30Z"),
                                OffsetDateTime.parse("2025-10-10T03:15:30Z"),
                                ZonedDateTime.parse("2025-10-10T03:15:30Z"),
                                Instant.parse("2007-12-03T10:15:30Z"),
                                new Date(3_600_000))));
        item.flattened2.parent = item;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    flattened1_a: 2,
                    flattened2_a: 3,
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
                    localDate: {"$date": "2025-01-01T00:00:00Z"},
                    local_Time: {"$date": "1970-01-01T03:15:30Z"},
                    localDateTime: {"$date": "2025-10-10T03:15:30Z"},
                    offsetTime: {"$date": "1970-01-01T03:15:30Z"},
                    offsetDateTime: {"$date": "2025-10-10T03:15:30Z"},
                    zonedDateTime: {"$date": "2025-10-10T03:15:30Z"},
                    instant: {"$date": "2007-12-03T10:15:30Z"},
                    date: {"$date": "1970-01-01T01:00:00Z"}
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
                    localDate: {"$date": "2025-01-01T00:00:00Z"},
                    local_Time: {"$date": "1970-01-01T03:15:30Z"},
                    localDateTime: {"$date": "2025-10-10T03:15:30Z"},
                    offsetTime: {"$date": "1970-01-01T03:15:30Z"},
                    offsetDateTime: {"$date": "2025-10-10T03:15:30Z"},
                    zonedDateTime: {"$date": "2025-10-10T03:15:30Z"},
                    instant: {"$date": "2007-12-03T10:15:30Z"},
                    date: {"$date": "1970-01-01T01:00:00Z"}
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValues.class, updatedItem.flattenedId));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testFlattenedNullValueOrHavingNulls() {
        var item = new ItemWithFlattenedValues(
                new Single(1),
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
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)));
        item.flattened2.parent = item;
        sessionFactoryScope.inTransaction(session -> session.persist(item));
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    flattened1_a: null,
                    flattened2_a: 3,
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
                    local_Time: null,
                    localDateTime: null,
                    offsetTime: null,
                    offsetDateTime: null,
                    zonedDateTime: null,
                    instant: null,
                    date: null
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValues.class, item.flattenedId));
        assertEq(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithFlattenedValues.class, item.flattenedId);
            result.flattened2.flattened = null;
            return result;
        });
        assertCollectionContainsExactly(
                """
                {
                    _id: 1,
                    flattened1_a: null,
                    flattened2_a: 3,
                    primitiveChar: null,
                    primitiveInt: null,
                    primitiveLong: null,
                    primitiveDouble: null,
                    primitiveBoolean: null,
                    boxedChar: null,
                    boxedInt: null,
                    boxedLong: null,
                    boxedDouble: null,
                    boxedBoolean: null,
                    string: null,
                    bigDecimal: null,
                    objectId: null,
                    localDate: null,
                    local_Time: null,
                    localDateTime: null,
                    offsetTime: null,
                    offsetDateTime: null,
                    zonedDateTime: null,
                    instant: null,
                    date: null
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValues.class, updatedItem.flattenedId));
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
                        new Character[] {'s', null, 't', 'r'},
                        new Integer[] {null, 7},
                        new Long[] {8L, null},
                        new Double[] {9.1d, null},
                        new Boolean[] {true, null},
                        new String[] {null, "str"},
                        new BigDecimal[] {null, BigDecimal.valueOf(10.1)},
                        new ObjectId[] {new ObjectId("000000000000000000000001"), null},
                        new StructAggregateEmbeddableIntegrationTests.Single[] {
                            new StructAggregateEmbeddableIntegrationTests.Single(1), null
                        },
                        asList('s', 't', null, 'r'),
                        new HashSet<>(asList(null, 5)),
                        asList(Long.MAX_VALUE, null, 6L),
                        asList(null, Double.MAX_VALUE),
                        asList(null, true),
                        asList("str", null),
                        asList(BigDecimal.valueOf(10.1), null),
                        asList(null, new ObjectId("000000000000000000000001")),
                        asList(new StructAggregateEmbeddableIntegrationTests.Single(1), null),
                        new LocalDate[] {LocalDate.of(2025, 1, 1)},
                        new LocalTime[] {LocalTime.of(3, 15, 30)},
                        new LocalDateTime[] {LocalDateTime.of(2025, 10, 10, 3, 15, 30)},
                        new OffsetTime[] {OffsetTime.parse("03:15:30Z")},
                        new OffsetDateTime[] {OffsetDateTime.parse("2025-10-10T03:15:30Z")},
                        new ZonedDateTime[] {ZonedDateTime.parse("2025-10-10T03:15:30Z[UTC]")},
                        new Instant[] {Instant.parse("2007-12-03T10:15:30Z")},
                        new Date[] {new Date(0)},
                        List.of(LocalDate.of(2025, 1, 1)),
                        List.of(LocalTime.of(3, 15, 30)),
                        List.of(LocalDateTime.of(2025, 10, 10, 3, 15, 30)),
                        List.of(OffsetTime.parse("03:15:30Z")),
                        List.of(OffsetDateTime.parse("2025-10-10T03:15:30Z")),
                        List.of(ZonedDateTime.parse("2025-10-10T03:15:30Z[UTC]")),
                        List.of(Instant.parse("2007-12-03T10:15:30Z")),
                        List.of(new Date(0))));
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
                    boxedChars: ["s", null, "t", "r"],
                    boxedInts: [null, 7],
                    boxedLongs: [{$numberLong: "8"}, null],
                    boxedDoubles: [{$numberDouble: "9.1"}, null],
                    boxedBooleans: [true, null],
                    strings: [null, "str"],
                    bigDecimals: [null, {$numberDecimal: "10.1"}],
                    objectIds: [{$oid: "000000000000000000000001"}, null],
                    structAggregateEmbeddables: [{a: 1}, null],
                    charsCollection: ["s", "t", null, "r"],
                    intsCollection: [null, 5],
                    longsCollection: [{$numberLong: "9223372036854775807"}, null, {$numberLong: "6"}],
                    doublesCollection: [null, {$numberDouble: "1.7976931348623157E308"}],
                    booleansCollection: [null, true],
                    stringsCollection: ["str", null],
                    bigDecimalsCollection: [{$numberDecimal: "10.1"}, null],
                    objectIdsCollection: [null, {$oid: "000000000000000000000001"}],
                    structAggregateEmbeddablesCollection: [{a: 1}, null],
                    localDates: [{"$date": "2025-01-01T00:00:00Z"}],
                    localTimes: [{"$date": "1970-01-01T03:15:30Z"}],
                    localDateTimes: [{"$date": "2025-10-10T03:15:30Z"}],
                    offsetTimes: [{"$date": "1970-01-01T03:15:30Z"}],
                    offsetDateTimes: [{"$date": "2025-10-10T03:15:30Z"}],
                    zonedDateTimes: [{"$date": "2025-10-10T03:15:30Z"}],
                    instants: [{"$date": "2007-12-03T10:15:30Z"}],
                    dates: [{"$date": "1970-01-01T00:00:00Z"}],
                    localDatesCollection: [{"$date": "2025-01-01T00:00:00Z"}],
                    localTimesCollection: [{"$date": "1970-01-01T03:15:30Z"}],
                    localDateTimesCollection: [{"$date": "2025-10-10T03:15:30Z"}],
                    offsetTimesCollection: [{"$date": "1970-01-01T03:15:30Z"}],
                    offsetDateTimesCollection: [{"$date": "2025-10-10T03:15:30Z"}],
                    zonedDateTimesCollection: [{"$date": "2025-10-10T03:15:30Z"}],
                    instantsCollection: [{"$date": "2007-12-03T10:15:30Z"}],
                    datesCollection: [{"$date": "1970-01-01T00:00:00Z"}]
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValueHavingArraysAndCollections.class, item.id));
        assertEq(item, loadedItem);
        var updatedItem = sessionFactoryScope.fromTransaction(session -> {
            var result = session.find(ItemWithFlattenedValueHavingArraysAndCollections.class, item.id);
            result.flattened.bytes[0] = (byte) -result.flattened.bytes[0];
            result.flattened.longs[1] = -result.flattened.longs[1];
            result.flattened.objectIds[0] = new ObjectId("000000000000000000000002");
            result.flattened.longsCollection.remove(6L);
            result.flattened.longsCollection.add(-6L);
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
                    boxedChars: ["s", null, "t", "r"],
                    boxedInts: [null, 7],
                    boxedLongs: [{$numberLong: "8"}, null],
                    boxedDoubles: [{$numberDouble: "9.1"}, null],
                    boxedBooleans: [true, null],
                    strings: [null, "str"],
                    bigDecimals: [null, {$numberDecimal: "10.1"}],
                    objectIds: [{$oid: "000000000000000000000002"}, null],
                    structAggregateEmbeddables: [{a: 1}, null],
                    charsCollection: ["s", "t", null, "r"],
                    intsCollection: [null, 5],
                    longsCollection: [{$numberLong: "9223372036854775807"}, null, {$numberLong: "-6"}],
                    doublesCollection: [null, {$numberDouble: "1.7976931348623157E308"}],
                    booleansCollection: [null, true],
                    stringsCollection: ["str", null],
                    bigDecimalsCollection: [{$numberDecimal: "10.1"}, null],
                    objectIdsCollection: [null, {$oid: "000000000000000000000001"}],
                    structAggregateEmbeddablesCollection: [{a: 1}, null],
                    localDates: [{"$date": "2025-01-01T00:00:00Z"}],
                    localTimes: [{"$date": "1970-01-01T03:15:30Z"}],
                    localDateTimes: [{"$date": "2025-10-10T03:15:30Z"}],
                    offsetTimes: [{"$date": "1970-01-01T03:15:30Z"}],
                    offsetDateTimes: [{"$date": "2025-10-10T03:15:30Z"}],
                    zonedDateTimes: [{"$date": "2025-10-10T03:15:30Z"}],
                    instants: [{"$date": "2007-12-03T10:15:30Z"}],
                    dates: [{"$date": "1970-01-01T00:00:00Z"}],
                    localDatesCollection: [{"$date": "2025-01-01T00:00:00Z"}],
                    localTimesCollection: [{"$date": "1970-01-01T03:15:30Z"}],
                    localDateTimesCollection: [{"$date": "2025-10-10T03:15:30Z"}],
                    offsetTimesCollection: [{"$date": "1970-01-01T03:15:30Z"}],
                    offsetDateTimesCollection: [{"$date": "2025-10-10T03:15:30Z"}],
                    zonedDateTimesCollection: [{"$date": "2025-10-10T03:15:30Z"}],
                    instantsCollection: [{"$date": "2007-12-03T10:15:30Z"}],
                    datesCollection: [{"$date": "1970-01-01T00:00:00Z"}]
                }
                """);
        loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValueHavingArraysAndCollections.class, updatedItem.id));
        assertEq(updatedItem, loadedItem);
    }

    @Test
    void testFlattenedValueHavingEmptyArraysAndCollections() {
        var item = new ItemWithFlattenedValueHavingArraysAndCollections(
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
                        new StructAggregateEmbeddableIntegrationTests.Single[0],
                        List.of(),
                        Set.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        new LocalDate[0],
                        new LocalTime[0],
                        new LocalDateTime[0],
                        new OffsetTime[0],
                        new OffsetDateTime[0],
                        new ZonedDateTime[0],
                        new Instant[0],
                        new Date[0],
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
                    structAggregateEmbeddablesCollection: [],
                    localDates: [],
                    localTimes: [],
                    localDateTimes: [],
                    offsetTimes: [],
                    offsetDateTimes: [],
                    zonedDateTimes: [],
                    instants: [],
                    dates: [],
                    localDatesCollection: [],
                    localTimesCollection: [],
                    localDateTimesCollection: [],
                    offsetTimesCollection: [],
                    offsetDateTimesCollection: [],
                    zonedDateTimesCollection: [],
                    instantsCollection: [],
                    datesCollection: []
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValueHavingArraysAndCollections.class, item.id));
        assertEq(item, loadedItem);
    }

    /**
     * This test also covers the behavior of an empty {@linkplain Embeddable embeddable} value, that is one having
     * {@code null} as the value of each of its persistent attributes.
     *
     * @see StructAggregateEmbeddableIntegrationTests#testNestedValueHavingNullArraysAndCollections()
     * @see ArrayAndCollectionIntegrationTests#testArrayAndCollectionValuesOfEmptyStructAggregateEmbeddables()
     */
    @Test
    public void testFlattenedValueHavingNullArraysAndCollections() {
        var emptyEmbeddable = new ArraysAndCollections(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        var item = new ItemWithFlattenedValueHavingArraysAndCollections(1, emptyEmbeddable);
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
                    structAggregateEmbeddablesCollection: null,
                    localDates: null,
                    localTimes: null,
                    localDateTimes: null,
                    offsetTimes: null,
                    offsetDateTimes: null,
                    zonedDateTimes: null,
                    instants: null,
                    dates: null,
                    localDatesCollection: null,
                    localTimesCollection: null,
                    localDateTimesCollection: null,
                    offsetTimesCollection: null,
                    offsetDateTimesCollection: null,
                    zonedDateTimesCollection: null,
                    instantsCollection: null,
                    datesCollection: null
                }
                """);
        var loadedItem = sessionFactoryScope.fromTransaction(
                session -> session.find(ItemWithFlattenedValueHavingArraysAndCollections.class, item.id));
        // `loadedItem.flattened` is `null` despite `item.flattened` not being `null`.
        // There is nothing we can do here, such is the Hibernate ORM behavior.
        assertNull(loadedItem.flattened);
        assertUsingRecursiveComparison(item, loadedItem, (assertion, expected) -> assertion
                .ignoringFields("flattened")
                .isEqualTo(expected));
    }

    @Test
    void testReadNestedValuesMissingFields() {
        var insertResult = mongoCollection.insertOne(
                BsonDocument.parse(
                        """
                        {
                            _id: 1,
                            flattened2_a: 3,
                            primitiveChar: "c",
                            primitiveInt: 1,
                            primitiveLong: {$numberLong: "9223372036854775807"},
                            primitiveDouble: {$numberDouble: "1.7976931348623157E308"},
                            primitiveBoolean: true
                        }
                        """));
        var id = new Single(insertResult.getInsertedId().asInt32().getValue());
        var expectedItem = new ItemWithFlattenedValues(
                id,
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
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)));
        expectedItem.flattened2.parent = expectedItem;
        var loadedItem =
                sessionFactoryScope.fromTransaction(session -> session.find(ItemWithFlattenedValues.class, id));
        assertEq(expectedItem, loadedItem);
    }

    private static void assertCollectionContainsExactly(String documentAsJsonObject) {
        assertThat(mongoCollection.find()).containsExactly(BsonDocument.parse(documentAsJsonObject));
    }

    @Entity
    @Table(name = "items")
    static class ItemWithFlattenedValues {
        @Id
        Single flattenedId;

        @AttributeOverride(name = "a", column = @Column(name = "flattened1_a"))
        Single flattened1;

        @AttributeOverride(name = "a", column = @Column(name = "flattened2_a"))
        PairWithParent flattened2;

        public ItemWithFlattenedValues() {}

        ItemWithFlattenedValues(Single flattenedId, Single flattened1, PairWithParent flattened2) {
            this.flattenedId = flattenedId;
            this.flattened1 = flattened1;
            this.flattened2 = flattened2;
        }
    }

    @Embeddable
    public static class Single {
        int a;

        public Single() {}

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
        Plural flattened;

        @Parent ItemWithFlattenedValues parent;

        public PairWithParent() {}

        PairWithParent(int a, Plural flattened) {
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

    @Embeddable
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
            ObjectId objectId,
            LocalDate localDate,
            @Column(name = "local_Time") LocalTime localTime,
            LocalDateTime localDateTime,
            OffsetTime offsetTime,
            OffsetDateTime offsetDateTime,
            ZonedDateTime zonedDateTime,
            Instant instant,
            Date date) {}

    @Entity
    @Table(name = "items")
    static class ItemWithFlattenedValueHavingArraysAndCollections {
        @Id
        int id;

        ArraysAndCollections flattened;

        public ItemWithFlattenedValueHavingArraysAndCollections() {}

        ItemWithFlattenedValueHavingArraysAndCollections(int id, ArraysAndCollections flattened) {
            this.id = id;
            this.flattened = flattened;
        }
    }

    @Embeddable
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
        LocalDate[] localDates;
        LocalTime[] localTimes;
        LocalDateTime[] localDateTimes;
        OffsetTime[] offsetTimes;
        OffsetDateTime[] offsetDateTimes;
        ZonedDateTime[] zonedDateTimes;
        Instant[] instants;
        Date[] dates;
        StructAggregateEmbeddableIntegrationTests.Single[] structAggregateEmbeddables;
        List<Character> charsCollection;
        Set<Integer> intsCollection;
        Collection<Long> longsCollection;
        Collection<Double> doublesCollection;
        Collection<Boolean> booleansCollection;
        Collection<String> stringsCollection;
        Collection<BigDecimal> bigDecimalsCollection;
        Collection<ObjectId> objectIdsCollection;
        Collection<LocalDate> localDatesCollection;
        Collection<LocalTime> localTimesCollection;
        Collection<LocalDateTime> localDateTimesCollection;
        Collection<OffsetTime> offsetTimesCollection;
        Collection<OffsetDateTime> offsetDateTimesCollection;
        Collection<ZonedDateTime> zonedDateTimesCollection;
        Collection<Instant> instantsCollection;
        Collection<Date> datesCollection;
        Collection<StructAggregateEmbeddableIntegrationTests.Single> structAggregateEmbeddablesCollection;

        public ArraysAndCollections() {}

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
                StructAggregateEmbeddableIntegrationTests.Single[] structAggregateEmbeddables,
                List<Character> charsCollection,
                Set<Integer> intsCollection,
                Collection<Long> longsCollection,
                Collection<Double> doublesCollection,
                Collection<Boolean> booleansCollection,
                Collection<String> stringsCollection,
                Collection<BigDecimal> bigDecimalsCollection,
                Collection<ObjectId> objectIdsCollection,
                Collection<StructAggregateEmbeddableIntegrationTests.Single> structAggregateEmbeddablesCollection,
                LocalDate[] localDates,
                LocalTime[] localTimes,
                LocalDateTime[] localDateTimes,
                OffsetTime[] offsetTimes,
                OffsetDateTime[] offsetDateTimes,
                ZonedDateTime[] zonedDateTimes,
                Instant[] instants,
                Date[] dates,
                List<LocalDate> localDatesCollection,
                List<LocalTime> localTimesCollection,
                List<LocalDateTime> localDateTimesCollection,
                List<OffsetTime> offsetTimesCollection,
                List<OffsetDateTime> offsetDateTimesCollection,
                List<ZonedDateTime> zonedDateTimesCollection,
                List<Instant> instantsCollection,
                List<Date> datesCollection) {
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
            this.localDates = localDates;
            this.localTimes = localTimes;
            this.localDateTimes = localDateTimes;
            this.offsetTimes = offsetTimes;
            this.offsetDateTimes = offsetDateTimes;
            this.zonedDateTimes = zonedDateTimes;
            this.instants = instants;
            this.dates = dates;
            this.localDatesCollection = localDatesCollection;
            this.localTimesCollection = localTimesCollection;
            this.localDateTimesCollection = localDateTimesCollection;
            this.offsetTimesCollection = offsetTimesCollection;
            this.offsetDateTimesCollection = offsetDateTimesCollection;
            this.zonedDateTimesCollection = zonedDateTimesCollection;
            this.instantsCollection = instantsCollection;
            this.datesCollection = datesCollection;
        }
    }

    @Nested
    class Unsupported {
        @Test
        void testPrimaryKeySpanningMultipleFields() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithPluralAsId.class)
                            .buildMetadata())
                    .hasMessageContaining("does not support primary key spanning multiple columns");
        }

        @Test
        void testStructAggregateEmbeddable() {
            var item = new ItemWithFlattenedValueHavingStructAggregateEmbeddable(
                    1,
                    new SingleHavingStructAggregateEmbeddable(new StructAggregateEmbeddableIntegrationTests.Single(2)));
            assertThatThrownBy(() -> sessionFactoryScope.inTransaction(session -> session.persist(item)))
                    .isInstanceOf(HibernateException.class);
        }

        @Test
        void testNoPersistentAttributes() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithFlattenedValueHavingNoPersistentAttributes.class)
                            .buildMetadata()
                            .buildSessionFactory()
                            .close())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining("must have at least one persistent attribute");
        }

        @Entity
        @Table(name = "items")
        static class ItemWithPluralAsId {
            @Id
            Plural id;
        }

        @Entity
        @Table(name = "items")
        static class ItemWithFlattenedValueHavingStructAggregateEmbeddable {
            @Id
            int id;

            SingleHavingStructAggregateEmbeddable flattened;

            ItemWithFlattenedValueHavingStructAggregateEmbeddable(
                    int id, SingleHavingStructAggregateEmbeddable flattened) {
                this.id = id;
                this.flattened = flattened;
            }
        }

        @Embeddable
        record SingleHavingStructAggregateEmbeddable(StructAggregateEmbeddableIntegrationTests.Single nested) {}

        @Entity
        @Table(name = "items")
        static class ItemWithFlattenedValueHavingNoPersistentAttributes {
            @Id
            int id;

            NoPersistentAttributes flattened;
        }

        @Embeddable
        static class NoPersistentAttributes {}
    }

    private void assertEq(@Nullable Object expected, @Nullable Object actual) {
        assertUsingRecursiveComparison(expected, actual, (recursiveComparisonAssert, expectedToCompare) -> {
            recursiveComparisonAssert
                    .ignoringAllOverriddenEquals()
                    .withComparatorForType(Comparator.comparingLong(Date::getTime), Date.class)
                    .isEqualTo(expectedToCompare);
        });
    }
}
