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

package com.mongodb.hibernate.query.select.temporal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.params.provider.Arguments;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

@DomainModel(annotatedClasses = {SelectionOffsetDateTimeIntegrationTest.Item.class})
public class SelectionOffsetDateTimeIntegrationTest
        extends AbstractSelectionTemporalIntegrationTest<SelectionOffsetDateTimeIntegrationTest.Item, OffsetDateTime> {

    private static final List<Item> ITEMS = List.of(
            // We write in GMT+1, validate behavior against absolute OffsetDateTime values.
            new Item(1, OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 9, 0), ZoneOffset.UTC)),
            new Item(2, OffsetDateTime.of(LocalDateTime.of(2025, 11, 10, 11, 1), ZoneOffset.UTC)),
            new Item(3, OffsetDateTime.of(LocalDateTime.of(2025, 12, 10, 12, 2), ZoneOffset.UTC)));

    private static List<Item> getTestingItems(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> ITEMS.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .toList();
    }

    @Override
    public List<Item> getData() {
        return ITEMS;
    }

    public static Stream<Arguments> testComparisonByEq() {
        return Stream.of(
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 9, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(1),
                        """
                        {"$date": "2025-10-10T09:00:00Z"}""",
                        Item.class),
                // System TZ does not affect OffsetDateTime comparison, but the ZoneOffset.ofHours(1) parameter does, it
                // gets normalized to UTC.
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 10, 0), ZoneOffset.ofHours(1)),
                        TimeZone.getTimeZone("GMT+10"),
                        getTestingItems(1),
                        """
                        {"$date": "2025-10-10T09:00:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByNe() {
        return Stream.of(
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 9, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(2, 3),
                        """
                        {"$date": "2025-10-10T09:00:00Z"}""",
                        Item.class),
                // System TZ does not affect OffsetDateTime comparison, but the ZoneOffset.ofHours(1) parameter does, it
                // gets normalized to UTC.
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 10, 0), ZoneOffset.ofHours(1)),
                        TimeZone.getTimeZone("GMT+10"),
                        getTestingItems(2, 3),
                        """
                        {"$date": "2025-10-10T09:00:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByLt() {
        return Stream.of(
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 12, 10, 12, 2), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(1, 2),
                        """
                        {"$date": "2025-12-10T12:02:00Z"}""",
                        Item.class),
                // System TZ does not affect OffsetDateTime comparison, but the ZoneOffset.ofHours(1) parameter does, it
                // gets normalized to UTC.
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 12, 10, 13, 2), ZoneOffset.ofHours(1)),
                        TimeZone.getTimeZone("GMT+10"),
                        getTestingItems(1, 2),
                        """
                        {"$date": "2025-12-10T12:02:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByLte() {
        return Stream.of(
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 12, 10, 12, 2), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "2025-12-10T12:02:00Z"}""",
                        Item.class),
                // System TZ does not affect OffsetDateTime comparison, but the ZoneOffset.ofHours(1) parameter does, it
                // gets normalized to UTC.
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 12, 10, 13, 2), ZoneOffset.ofHours(1)),
                        TimeZone.getTimeZone("GMT+10"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "2025-12-10T12:02:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByGt() {
        return Stream.of(
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 9, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(2, 3),
                        """
                        {"$date": "2025-10-10T09:00:00Z"}""",
                        Item.class),
                // System TZ does not affect OffsetDateTime comparison, but the ZoneOffset.ofHours(1) parameter does, it
                // gets normalized to UTC.
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 10, 0), ZoneOffset.ofHours(1)),
                        TimeZone.getTimeZone("GMT+10"),
                        getTestingItems(2, 3),
                        """
                        {"$date": "2025-10-10T09:00:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByGte() {
        return Stream.of(
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 9, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "2025-10-10T09:00:00Z"}""",
                        Item.class),
                // System TZ does not affect OffsetDateTime comparison, but the ZoneOffset.ofHours(1) parameter does, it
                // gets normalized to UTC.
                Arguments.of(
                        OffsetDateTime.of(LocalDateTime.of(2025, 10, 10, 10, 0), ZoneOffset.ofHours(1)),
                        TimeZone.getTimeZone("GMT+10"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "2025-10-10T09:00:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testOrderByAsc() {
        return Stream.of(
                Arguments.of(TimeZone.getTimeZone("UTC"), getTestingItems(1, 2, 3), Item.class),
                Arguments.of(TimeZone.getTimeZone("GMT+10"), getTestingItems(1, 2, 3), Item.class));
    }

    public static Stream<Arguments> testOrderByDesc() {
        return Stream.of(
                Arguments.of(TimeZone.getTimeZone("UTC"), getTestingItems(3, 2, 1), Item.class),
                Arguments.of(TimeZone.getTimeZone("GMT+10"), getTestingItems(3, 2, 1), Item.class));
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        public int id;

        public OffsetDateTime temporal;

        public Item() {}

        public Item(int id, OffsetDateTime temporal) {
            this.id = id;
            this.temporal = temporal;
        }

        @Override
        public String toString() {
            return "Item{" + "id=" + id + ", temporal=" + temporal + '}';
        }
    }
}
