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

package com.mongodb.hibernate.type;

import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.FIRST_DURATION;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.FIRST_TIME_ZONE;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.FIRST_YEAR;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.FIRST_ZONE_ID;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.FIRST_ZONE_OFFSET;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.THIRD_DURATION;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.THIRD_TIME_ZONE;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.THIRD_YEAR;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.THIRD_ZONE_ID;
import static com.mongodb.hibernate.type.UnwrappedDomainTypeConstants.THIRD_ZONE_OFFSET;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.hibernate.annotations.Struct;
import org.hibernate.query.MutationQuery;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Covers the types {@code ValueConversions} does not recognize by domain class, and which therefore reach BSON only by
 * being unwrapped through their {@code JavaType}: {@link Duration}, {@link Year}, {@link ZoneId}, {@link ZoneOffset}
 * and {@link TimeZone}.
 */
@DomainModel(annotatedClasses = {UnwrappedDomainTypeIntegrationTests.Item.class})
class UnwrappedDomainTypeIntegrationTests extends AbstractQueryIntegrationTests {

    private static final String COLLECTION_NAME = "items";
    private static final String CONSTANTS = UnwrappedDomainTypeConstants.class.getName();

    private static final String PROJECT_ALL_FIELDS =
            """
            {
              "$project": {
                "_id": true,
                "aggregateEmbeddable": true,
                "duration": true,
                "durations": true,
                "flattenedDuration": true,
                "flattenedTimeZone": true,
                "flattenedYear": true,
                "flattenedZoneId": true,
                "flattenedZoneOffset": true,
                "timeZone": true,
                "timeZones": true,
                "year": true,
                "years": true,
                "zoneId": true,
                "zoneIds": true,
                "zoneOffset": true,
                "zoneOffsets": true
              }
            }""";

    private static final List<Item> ITEMS = List.of(
            new Item(1, FIRST_DURATION, FIRST_YEAR, FIRST_ZONE_ID, FIRST_ZONE_OFFSET, FIRST_TIME_ZONE),
            new Item(
                    2,
                    Duration.ofSeconds(120),
                    Year.of(2025),
                    ZoneId.of("America/New_York"),
                    ZoneOffset.of("-05:00"),
                    TimeZone.getTimeZone("Europe/Paris")),
            new Item(3, THIRD_DURATION, THIRD_YEAR, THIRD_ZONE_ID, THIRD_ZONE_OFFSET, THIRD_TIME_ZONE));

    @InjectMongoCollection(COLLECTION_NAME)
    private MongoCollection<BsonDocument> mongoCollection;

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> ITEMS.forEach(session::persist));
    }

    private static List<Item> items(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> ITEMS.stream()
                        .filter(item -> item.id == id)
                        .findAny()
                        .orElseThrow(() -> fail("id does not exist: " + id)))
                .toList();
    }

    /**
     * @param first The value of {@linkplain #ITEMS seed item} 1, {@code third} that of seed item 3.
     * @param ascendingIds The seed item ids ordered by ascending attribute value.
     */
    private record TypeCase<T>(
            String attribute,
            Class<T> javaType,
            T first,
            String firstBson,
            String firstLiteral,
            T third,
            String thirdBson,
            String thirdLiteral,
            int[] ascendingIds) {
        @Override
        public String toString() {
            return attribute;
        }
    }

    private static Stream<Arguments> types() {
        return Stream.of(
                Arguments.of(new TypeCase<>(
                        "duration",
                        Duration.class,
                        FIRST_DURATION,
                        """
                        {"$numberDecimal": "90000000500"}""",
                        CONSTANTS + ".FIRST_DURATION",
                        THIRD_DURATION,
                        """
                        {"$numberDecimal": "180000000001"}""",
                        CONSTANTS + ".THIRD_DURATION",
                        new int[] {1, 2, 3})),
                Arguments.of(new TypeCase<>(
                        "year",
                        Year.class,
                        FIRST_YEAR,
                        "2024",
                        CONSTANTS + ".FIRST_YEAR",
                        THIRD_YEAR,
                        "2026",
                        CONSTANTS + ".THIRD_YEAR",
                        new int[] {1, 2, 3})),
                Arguments.of(new TypeCase<>(
                        "zoneId",
                        ZoneId.class,
                        FIRST_ZONE_ID,
                        """
                        "Europe/Paris\"""",
                        CONSTANTS + ".FIRST_ZONE_ID",
                        THIRD_ZONE_ID,
                        """
                        "Asia/Tokyo\"""",
                        CONSTANTS + ".THIRD_ZONE_ID",
                        new int[] {2, 3, 1})),
                Arguments.of(new TypeCase<>(
                        "zoneOffset",
                        ZoneOffset.class,
                        FIRST_ZONE_OFFSET,
                        """
                        "+02:00\"""",
                        CONSTANTS + ".FIRST_ZONE_OFFSET",
                        THIRD_ZONE_OFFSET,
                        """
                        "+09:00\"""",
                        CONSTANTS + ".THIRD_ZONE_OFFSET",
                        new int[] {1, 3, 2})),
                Arguments.of(new TypeCase<>(
                        "timeZone",
                        TimeZone.class,
                        FIRST_TIME_ZONE,
                        """
                        "America/New_York\"""",
                        CONSTANTS + ".FIRST_TIME_ZONE",
                        THIRD_TIME_ZONE,
                        """
                        "Asia/Tokyo\"""",
                        CONSTANTS + ".THIRD_TIME_ZONE",
                        new int[] {1, 3, 2})));
    }

    @Test
    void testRoundTrip() {
        assertThat(mongoCollection.find(new BsonDocument("_id", new BsonInt32(1))))
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 1,
                                  "duration": {"$numberDecimal": "90000000500"},
                                  "year": 2024,
                                  "zoneId": "Europe/Paris",
                                  "zoneOffset": "+02:00",
                                  "timeZone": "America/New_York",
                                  "durations": [{"$numberDecimal": "90000000500"}],
                                  "years": [2024],
                                  "zoneIds": ["Europe/Paris"],
                                  "zoneOffsets": ["+02:00"],
                                  "timeZones": ["America/New_York"],
                                  "aggregateEmbeddable": {
                                    "duration": {"$numberDecimal": "90000000500"},
                                    "year": 2024,
                                    "zoneId": "Europe/Paris",
                                    "zoneOffset": "+02:00",
                                    "timeZone": "America/New_York"
                                  },
                                  "flattenedDuration": {"$numberDecimal": "90000000500"},
                                  "flattenedYear": 2024,
                                  "flattenedZoneId": "Europe/Paris",
                                  "flattenedZoneOffset": "+02:00",
                                  "flattenedTimeZone": "America/New_York"
                                }"""));
    }

    @ParameterizedTest(name = "testProjection: {0}")
    @MethodSource("types")
    <T> void testProjection(TypeCase<T> c) {
        assertSelectionQuery(
                format("select %s from Item where id = 1", c.attribute()),
                c.javaType(),
                format(
                        """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {"$match": {"_id": {"$eq": 1}}},
                            {"$project": {"%s": true}}
                          ]
                        }""",
                        c.attribute()),
                List.of(c.first()),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testParameterPredicate: {0}")
    @MethodSource("types")
    <T> void testParameterPredicate(TypeCase<T> c) {
        assertSelectionQuery(
                format("from Item where %s = :v", c.attribute()),
                Item.class,
                q -> q.setParameter("v", c.first()),
                matchAndProjectAll(
                        format("""
                        {"%s": {"$eq": %s}}""", c.attribute(), c.firstBson())),
                items(1),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testLiteralPredicate: {0}")
    @MethodSource("types")
    <T> void testLiteralPredicate(TypeCase<T> c) {
        assertSelectionQuery(
                format("from Item where %s = %s", c.attribute(), c.firstLiteral()),
                Item.class,
                matchAndProjectAll(
                        format("""
                        {"%s": {"$eq": %s}}""", c.attribute(), c.firstBson())),
                items(1),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testInWithParameters: {0}")
    @MethodSource("types")
    <T> void testInWithParameters(TypeCase<T> c) {
        assertSelectionQuery(
                format("from Item where %s in (:a, :b)", c.attribute()),
                Item.class,
                q -> q.setParameter("a", c.first()).setParameter("b", c.third()),
                matchAndProjectAll(format(
                        """
                        {"%s": {"$in": [%s, %s]}}""",
                        c.attribute(), c.firstBson(), c.thirdBson())),
                items(1, 3),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testInWithLiterals: {0}")
    @MethodSource("types")
    <T> void testInWithLiterals(TypeCase<T> c) {
        assertSelectionQuery(
                format("from Item where %s in (%s, %s)", c.attribute(), c.firstLiteral(), c.thirdLiteral()),
                Item.class,
                matchAndProjectAll(format(
                        """
                        {"%s": {"$in": [%s, %s]}}""",
                        c.attribute(), c.firstBson(), c.thirdBson())),
                items(1, 3),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testIsNull: {0}")
    @MethodSource("types")
    <T> void testIsNull(TypeCase<T> c) {
        assertSelectionQuery(
                format("from Item where %s is null", c.attribute()),
                Item.class,
                matchAndProjectAll(format("""
                               {"%s": {"$eq": null}}""", c.attribute())),
                List.of(),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testIsNotNull: {0}")
    @MethodSource("types")
    <T> void testIsNotNull(TypeCase<T> c) {
        assertSelectionQuery(
                format("from Item where %s is not null", c.attribute()),
                Item.class,
                matchAndProjectAll(format("""
                               {"%s": {"$ne": null}}""", c.attribute())),
                items(1, 2, 3),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testOrderByAscending: {0}")
    @MethodSource("types")
    <T> void testOrderByAscending(TypeCase<T> c) {
        assertSelectionQuery(
                format("from Item order by %s asc", c.attribute()),
                Item.class,
                sortAndProjectAll(c.attribute(), 1),
                items(c.ascendingIds()),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testOrderByDescending: {0}")
    @MethodSource("types")
    <T> void testOrderByDescending(TypeCase<T> c) {
        var descendingIds = c.ascendingIds().clone();
        for (var i = 0; i < descendingIds.length / 2; i++) {
            var swap = descendingIds[i];
            descendingIds[i] = descendingIds[descendingIds.length - 1 - i];
            descendingIds[descendingIds.length - 1 - i] = swap;
        }
        assertSelectionQuery(
                format("from Item order by %s desc", c.attribute()),
                Item.class,
                sortAndProjectAll(c.attribute(), -1),
                items(descendingIds),
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testMutationSetWithParameter: {0}")
    @MethodSource("types")
    <T> void testMutationSetWithParameter(TypeCase<T> c) {
        assertMutationSetsThirdItemToFirstValue(
                format("update Item set %s = :v where id = 3", c.attribute()), q -> q.setParameter("v", c.first()), c);
    }

    @ParameterizedTest(name = "testMutationSetWithLiteral: {0}")
    @MethodSource("types")
    <T> void testMutationSetWithLiteral(TypeCase<T> c) {
        assertMutationSetsThirdItemToFirstValue(
                format("update Item set %s = %s where id = 3", c.attribute(), c.firstLiteral()), query -> {}, c);
    }

    private <T> void assertMutationSetsThirdItemToFirstValue(
            String hql, Consumer<MutationQuery> queryPostProcessor, TypeCase<T> c) {
        getSessionFactoryScope().inTransaction(session -> {
            var query = session.createMutationQuery(hql);
            queryPostProcessor.accept(query);
            assertThat(query.executeUpdate()).isEqualTo(1);
            assertActualCommandsInOrder(BsonDocument.parse(format(
                    """
                    {
                      "update": "items",
                      "updates": [
                        {
                          "multi": true,
                          "q": {"_id": {"$eq": 3}},
                          "u": {"$set": {"%s": %s}}
                        }
                      ]
                    }""",
                    c.attribute(), c.firstBson())));
        });
        assertThat(mongoCollection
                        .find(new BsonDocument("_id", new BsonInt32(3)))
                        .first())
                .isNotNull()
                .extracting(document -> document.get(c.attribute()))
                .isEqualTo(
                        BsonDocument.parse(format("{\"v\": %s}", c.firstBson())).get("v"));
    }

    private static String matchAndProjectAll(String matchFilter) {
        return format(
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {"$match": %s},
                    %s
                  ]
                }""",
                matchFilter, PROJECT_ALL_FIELDS);
    }

    private static String sortAndProjectAll(String attribute, int direction) {
        return format(
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {"$sort": {"%s": %d}},
                    %s
                  ]
                }""",
                attribute, direction, PROJECT_ALL_FIELDS);
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        Duration duration;
        Year year;
        ZoneId zoneId;
        ZoneOffset zoneOffset;
        TimeZone timeZone;

        Collection<Duration> durations;
        Collection<Year> years;
        Collection<ZoneId> zoneIds;
        Collection<ZoneOffset> zoneOffsets;
        Collection<TimeZone> timeZones;

        AggregateEmbeddable aggregateEmbeddable;
        FlattenedEmbeddable flattenedEmbeddable;

        Item() {}

        Item(int id, Duration duration, Year year, ZoneId zoneId, ZoneOffset zoneOffset, TimeZone timeZone) {
            this.id = id;
            this.duration = duration;
            this.year = year;
            this.zoneId = zoneId;
            this.zoneOffset = zoneOffset;
            this.timeZone = timeZone;
            this.durations = List.of(duration);
            this.years = List.of(year);
            this.zoneIds = List.of(zoneId);
            this.zoneOffsets = List.of(zoneOffset);
            this.timeZones = List.of(timeZone);
            this.aggregateEmbeddable = new AggregateEmbeddable(duration, year, zoneId, zoneOffset, timeZone);
            this.flattenedEmbeddable = new FlattenedEmbeddable(duration, year, zoneId, zoneOffset, timeZone);
        }

        @Override
        public String toString() {
            return "Item{id=" + id + ", duration=" + duration + ", year=" + year + ", zoneId=" + zoneId
                    + ", zoneOffset=" + zoneOffset + ", timeZone=" + timeZone.getID() + '}';
        }
    }

    @Embeddable
    @Struct(name = "AggregateEmbeddable")
    static class AggregateEmbeddable {
        Duration duration;
        Year year;
        ZoneId zoneId;
        ZoneOffset zoneOffset;
        TimeZone timeZone;

        AggregateEmbeddable() {}

        AggregateEmbeddable(Duration duration, Year year, ZoneId zoneId, ZoneOffset zoneOffset, TimeZone timeZone) {
            this.duration = duration;
            this.year = year;
            this.zoneId = zoneId;
            this.zoneOffset = zoneOffset;
            this.timeZone = timeZone;
        }
    }

    @Embeddable
    static class FlattenedEmbeddable {
        Duration flattenedDuration;
        Year flattenedYear;
        ZoneId flattenedZoneId;
        ZoneOffset flattenedZoneOffset;
        TimeZone flattenedTimeZone;

        FlattenedEmbeddable() {}

        FlattenedEmbeddable(Duration duration, Year year, ZoneId zoneId, ZoneOffset zoneOffset, TimeZone timeZone) {
            this.flattenedDuration = duration;
            this.flattenedYear = year;
            this.flattenedZoneId = zoneId;
            this.flattenedZoneOffset = zoneOffset;
            this.flattenedTimeZone = timeZone;
        }
    }
}
