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

import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

@DomainModel(annotatedClasses = {SelectionOffsetTimeIntegrationTest.Item.class})
public class SelectionOffsetTimeIntegrationTest
        extends AbstractSelectionTemporalIntegrationTest<SelectionOffsetTimeIntegrationTest.Item, OffsetTime> {

    private static final List<Item> ITEMS = List.of(
            new Item(1, OffsetTime.of(LocalTime.of(9, 0), ZoneOffset.UTC)),
            new Item(2, OffsetTime.of(LocalTime.of(11, 1), ZoneOffset.UTC)),
            new Item(3, OffsetTime.of(LocalTime.of(12, 2), ZoneOffset.UTC)));

    private static List<Item> getTestingItems(int hoursToShift, int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> ITEMS.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .map(item -> new Item(item.id, item.temporal.plusHours(hoursToShift)))
                .toList();
    }

    @Override
    public List<Item> getData() {
        return ITEMS;
    }

    public static Stream<Arguments> testComparisonByEq() {
        return Stream.of(
                Arguments.of(
                        // No timezone offset, as System TZ is UTC.
                        OffsetTime.of(LocalTime.of(9, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(0, 1),
                        """
                        {"$date": "1970-01-01T09:00:00Z"}""",
                        Item.class),
                // timezone affects OffSetTime comparison
                Arguments.of(
                        OffsetTime.of(LocalTime.of(10, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("GMT+1"),
                        getTestingItems(1, 1),
                        """
                        {"$date": "1970-01-01T09:00:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByNe() {
        return Stream.of(
                Arguments.of(
                        // No timezone offset, as System TZ is UTC.
                        OffsetTime.of(LocalTime.of(9, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(0, 2, 3),
                        """
                        {"$date": "1970-01-01T09:00:00Z"}""",
                        Item.class),
                // timezone affects OffSetTime comparison
                Arguments.of(
                        OffsetTime.of(LocalTime.of(10, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("GMT+1"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "1970-01-01T09:00:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByLt() {
        return Stream.of(
                Arguments.of(
                        // No timezone offset, as System TZ is UTC.
                        OffsetTime.of(LocalTime.of(12, 2), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(0, 1, 2),
                        """
                        {"$date": "1970-01-01T12:02:00Z"}""",
                        Item.class),
                // timezone affects OffSetTime comparison
                Arguments.of(
                        OffsetTime.of(LocalTime.of(13, 2), ZoneOffset.UTC),
                        TimeZone.getTimeZone("GMT+1"),
                        getTestingItems(1, 1, 2),
                        """
                        {"$date": "1970-01-01T12:02:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByLte() {
        return Stream.of(
                Arguments.of(
                        // No timezone offset, as System TZ is UTC.
                        OffsetTime.of(LocalTime.of(12, 2), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(0, 1, 2, 3),
                        """
                        {"$date": "1970-01-01T12:02:00Z"}""",
                        Item.class),
                // timezone affects OffSetTime comparison
                Arguments.of(
                        OffsetTime.of(LocalTime.of(13, 2), ZoneOffset.UTC),
                        TimeZone.getTimeZone("GMT+1"),
                        getTestingItems(1, 1, 2, 3),
                        """
                        {"$date": "1970-01-01T12:02:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByGt() {
        return Stream.of(
                Arguments.of(
                        // No timezone offset, as System TZ is UTC.
                        OffsetTime.of(LocalTime.of(9, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(-0, 2, 3),
                        """
                        {"$date": "1970-01-01T09:00:00Z"}""",
                        Item.class),
                // timezone affects OffSetTime comparison
                Arguments.of(
                        OffsetTime.of(LocalTime.of(10, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("GMT+1"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "1970-01-01T09:00:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByGte() {
        return Stream.of(
                Arguments.of(
                        // No timezone offset, as System TZ is UTC.
                        OffsetTime.of(LocalTime.of(9, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("UTC"),
                        getTestingItems(0, 1, 2, 3),
                        """
                        {"$date": "1970-01-01T09:00:00Z"}""",
                        Item.class),
                // timezone affects OffSetTime comparison
                Arguments.of(
                        OffsetTime.of(LocalTime.of(10, 0), ZoneOffset.UTC),
                        TimeZone.getTimeZone("GMT+1"),
                        getTestingItems(1, 1, 2, 3),
                        """
                        {"$date": "1970-01-01T09:00:00Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testOrderByAsc() {
        return Stream.of(
                Arguments.of(TimeZone.getTimeZone("UTC"), getTestingItems(0, 1, 2, 3), Item.class),
                // timezone affects OffSetTime comparison
                Arguments.of(TimeZone.getTimeZone("GMT+1"), getTestingItems(1, 1, 2, 3), Item.class));
    }

    public static Stream<Arguments> testOrderByDesc() {
        return Stream.of(
                Arguments.of(TimeZone.getTimeZone("UTC"), getTestingItems(0, 3, 2, 1), Item.class),
                Arguments.of(TimeZone.getTimeZone("GMT+1"), getTestingItems(1, 3, 2, 1), Item.class));
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        public int id;

        public OffsetTime temporal;

        public Item() {}

        public Item(int id, OffsetTime temporal) {
            this.id = id;
            this.temporal = temporal;
        }

        @Override
        public String toString() {
            return "Item{" + "id=" + id + ", temporal=" + temporal + '}';
        }
    }
}
