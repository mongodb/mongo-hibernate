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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;

import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static java.util.TimeZone.getTimeZone;

@DomainModel(annotatedClasses = {SelectionDateIntegrationTest.Item.class})
public class SelectionDateIntegrationTest
        extends AbstractSelectionTemporalIntegrationTest<SelectionDateIntegrationTest.Item, Date> {

    public static final List<Item> ITEMS;

    static {
        ITEMS = List.of(
                new Item(1, new Date(toEpochMilli(LocalDateTime.of(1400, 1, 1, 1, 1, 1)))),
                new Item(2, new Date(toEpochMilli(LocalDateTime.of(1970, 2, 2, 2, 2, 2)))),
                new Item(3, new Date(toEpochMilli(LocalDateTime.of(2025, 3, 3, 3, 3, 3)))));
    }

    private static long toEpochMilli(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static List<Item> getTestingItems(TimeZone timeZOne, int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> ITEMS.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .map(item -> {
                    long expectedEpochTime = Instant.ofEpochMilli(item.temporal.getTime())
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDateTime()
                            .atZone(timeZOne.toZoneId())
                            .toInstant()
                            .toEpochMilli();
                    return new Item(item.id, new Date(expectedEpochTime));
                })
                .toList();
    }

    private static List<Item> getTestingItems(int... ids) {
        throw fail();
    }

    @Override
    public List<Item> getData() {
        return ITEMS;
    }

    public static Stream<Arguments> testComparisonByEq() {
        return Stream.of(
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(1400, 1, 1, 1, 1, 1))),
                        getTimeZone("UTC"),
                        getTestingItems(getTimeZone("UTC"), 1),
                        """
                        {"$date": {"$numberLong": "-17987439539000"}}""",
                        Item.class),
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(1399, 12, 31, 20, 1, 1))),
                        getTimeZone("GMT+5"),
                        getTestingItems(getTimeZone("GMT+5"), 1),
                        """
                        {"$date": {"$numberLong": "-17987439539000"}}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByNe() {
        return Stream.of(
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(1400, 1, 1, 1, 1, 1))),
                        getTimeZone("UTC"),
                        getTestingItems(getTimeZone("UTC"), 2, 3),
                        """
                        {"$date": {"$numberLong": "-17987439539000"}}""",
                        Item.class),
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(1399, 12, 31, 20, 1, 1))),
                        getTimeZone("GMT+5"),
                        getTestingItems(getTimeZone("GMT+5"), 2, 3),
                        """
                        {"$date": {"$numberLong": "-17987439539000"}}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByLt() {
        return Stream.of(
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(2025, 3, 3, 3, 3, 3))),
                        getTimeZone("UTC"),
                        getTestingItems(getTimeZone("UTC"), 1, 2),
                        """
                        {"$date": "2025-03-03T03:03:03Z"}""",
                        Item.class),
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(2025, 3, 2, 22, 3, 3))),
                        getTimeZone("GMT+5"),
                        getTestingItems(getTimeZone("GMT+5"), 1, 2),
                        """
                        {"$date": "2025-03-03T03:03:03Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByLte() {
        return Stream.of(
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(2025, 3, 3, 3, 3, 3))),
                        getTimeZone("UTC"),
                        getTestingItems(getTimeZone("UTC"), 1, 2, 3),
                        """
                        {"$date": "2025-03-03T03:03:03Z"}""",
                        Item.class),
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(2025, 3, 2, 22, 3, 3))),
                        getTimeZone("GMT+5"),
                        getTestingItems(getTimeZone("GMT+5"), 1, 2, 3),
                        """
                        {"$date": "2025-03-03T03:03:03Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByGt() {
        return Stream.of(
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(1400, 1, 1, 1, 1, 1))),
                        getTimeZone("UTC"),
                        getTestingItems(getTimeZone("UTC"), 2, 3),
                        """
                        {"$date": {"$numberLong": "-17987439539000"}}""",
                        Item.class),
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(1399, 12, 31, 20, 1, 1))),
                        getTimeZone("GMT+5"),
                        getTestingItems(getTimeZone("GMT+5"), 2, 3),
                        """
                        {"$date": {"$numberLong": "-17987439539000"}}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByGte() {
        return Stream.of(
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(1400, 1, 1, 1, 1, 1))),
                        getTimeZone("UTC"),
                        getTestingItems(getTimeZone("UTC"), 1, 2, 3),
                        """
                        {"$date": {"$numberLong": "-17987439539000"}}""",
                        Item.class),
                Arguments.of(
                        new Date(toEpochMilli(LocalDateTime.of(1399, 12, 31, 20, 1, 1))),
                        getTimeZone("GMT+5"),
                        getTestingItems(getTimeZone("GMT+5"), 1, 2, 3),
                        """
                        {"$date": {"$numberLong": "-17987439539000"}}""",
                        Item.class));
    }

    public static Stream<Arguments> testOrderByAsc() {
        return Stream.of(
                Arguments.of(getTimeZone("UTC"), getTestingItems(getTimeZone("UTC"), 1, 2, 3), Item.class),
                Arguments.of(getTimeZone("GMT+5"), getTestingItems(getTimeZone("GMT+5"), 1, 2, 3), Item.class));
    }

    public static Stream<Arguments> testOrderByDesc() {
        return Stream.of(
                Arguments.of(getTimeZone("UTC"), getTestingItems(getTimeZone("UTC"), 3, 2, 1), Item.class),
                Arguments.of(getTimeZone("GMT+5"), getTestingItems(getTimeZone("GMT+5"), 3, 2, 1), Item.class));
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        public int id;

        public Date temporal;

        public Item() {}

        public Item(int id, Date temporal) {
            this.id = id;
            this.temporal = temporal;
        }

        @Override
        public String toString() {
            return "Item{" + "id=" + id + ", temporal=" + temporal + '}';
        }
    }
}
