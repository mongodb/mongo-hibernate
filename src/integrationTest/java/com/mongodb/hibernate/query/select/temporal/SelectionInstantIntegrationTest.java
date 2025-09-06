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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.params.provider.Arguments;

@DomainModel(annotatedClasses = {SelectionInstantIntegrationTest.Item.class})
class SelectionInstantIntegrationTest
        extends AbstractSelectionTemporalIntegrationTest<SelectionInstantIntegrationTest.Item, Instant> {

    private static final List<Item> ITEMS = List.of(
            new Item(1, Instant.parse("2025-01-04T10:05:01Z")),
            new Item(2, Instant.parse("2025-05-04T14:30:15Z")),
            new Item(3, Instant.parse("2025-12-04T23:59:59Z")));

    private static List<Item> getTestingItems(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> ITEMS.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .toList();
    }

    @Override
    List<Item> getData() {
        return ITEMS;
    }

    public static Stream<Arguments> testComparisonByEq() {
        return Stream.of(
                Arguments.of(
                        Instant.parse("2025-01-04T10:05:01Z"),
                        getTestingItems(1),
                        """
                        {"$date": "2025-01-04T10:05:01Z"}""",
                        Item.class),
                Arguments.of(
                        Instant.parse("2025-01-04T10:05:01Z"),
                        getTestingItems(1),
                        """
                        {"$date": "2025-01-04T10:05:01Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByNe() {
        return Stream.of(
                Arguments.of(
                        Instant.parse("2025-01-04T10:05:01Z"),
                        getTestingItems(2, 3),
                        """
                        {"$date": "2025-01-04T10:05:01Z"}""",
                        Item.class),
                Arguments.of(
                        Instant.parse("2025-01-04T10:05:01Z"),
                        getTestingItems(2, 3),
                        """
                        {"$date": "2025-01-04T10:05:01Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByLt() {
        return Stream.of(
                Arguments.of(
                        Instant.parse("2025-12-04T23:59:59Z"),
                        getTestingItems(1, 2),
                        """
                        {"$date": "2025-12-04T23:59:59Z"}""",
                        Item.class),
                Arguments.of(
                        Instant.parse("2025-12-04T23:59:59Z"),
                        getTestingItems(1, 2),
                        """
                        {"$date": "2025-12-04T23:59:59Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByLte() {
        return Stream.of(
                Arguments.of(
                        Instant.parse("2025-12-04T23:59:59Z"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "2025-12-04T23:59:59Z"}""",
                        Item.class),
                Arguments.of(
                        Instant.parse("2025-12-04T23:59:59Z"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "2025-12-04T23:59:59Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByGt() {
        return Stream.of(
                Arguments.of(
                        Instant.parse("2025-01-04T10:05:01Z"),
                        getTestingItems(2, 3),
                        """
                        {"$date": "2025-01-04T10:05:01Z"}""",
                        Item.class),
                Arguments.of(
                        Instant.parse("2025-01-04T10:05:01Z"),
                        getTestingItems(2, 3),
                        """
                        {"$date": "2025-01-04T10:05:01Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testComparisonByGte() {
        return Stream.of(
                Arguments.of(
                        Instant.parse("2025-01-04T10:05:01Z"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "2025-01-04T10:05:01Z"}""",
                        Item.class),
                Arguments.of(
                        Instant.parse("2025-01-04T10:05:01Z"),
                        getTestingItems(1, 2, 3),
                        """
                        {"$date": "2025-01-04T10:05:01Z"}""",
                        Item.class));
    }

    public static Stream<Arguments> testOrderByAsc() {
        return Stream.of(
                Arguments.of(getTestingItems(1, 2, 3), Item.class), Arguments.of(getTestingItems(1, 2, 3), Item.class));
    }

    public static Stream<Arguments> testOrderByDesc() {
        return Stream.of(
                Arguments.of(getTestingItems(3, 2, 1), Item.class), Arguments.of(getTestingItems(3, 2, 1), Item.class));
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        public int id;

        public Instant temporal;

        public Item() {}

        public Item(int id, Instant temporal) {
            this.id = id;
            this.temporal = temporal;
        }

        @Override
        public String toString() {
            return "Item{" + "id=" + id + ", temporal=" + temporal + '}';
        }
    }
}
