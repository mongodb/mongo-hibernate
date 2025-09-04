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

import org.assertj.core.api.RecursiveComparisonAssert;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public final class MongoTestAssertions {
    private MongoTestAssertions() {}

    /**
     * This method is intended to be a drop-in replacement for
     * {@link org.junit.jupiter.api.Assertions#assertEquals(Object, Object)}. It should work even if
     * {@code expected}/{@code actual} does not override {@link Object#equals(Object)}.
     */
    public static void assertEq(@Nullable Object expected, @Nullable Object actual) {
        assertUsingRecursiveComparison(expected, actual, RecursiveComparisonAssert::isEqualTo);
    }

    public static void assertUsingRecursiveComparison(
            @Nullable Object expected,
            @Nullable Object actual,
            BiConsumer<RecursiveComparisonAssert<?>, Object> assertion) {
        assertion.accept(
                assertThat(actual)
                        .usingRecursiveComparison()
                        .usingOverriddenEquals()
                        .withStrictTypeChecking(),
                expected);
    }

    /**
     * This method is intended to be a drop-in replacement for
     * {@link org.junit.jupiter.api.Assertions#assertIterableEquals(Iterable, Iterable)}. It should work even if
     * elements in {@code expected}/{@code actual} do not override {@link Object#equals(Object)}.
     */
    @SuppressWarnings("unchecked")
    public static <T> void assertIterableEq(Iterable<T> expectedResultList, Iterable<? extends T> actualResultList) {
        assertIterableEq(expectedResultList, actualResultList, b -> {});
    }

    /**
     * This method is intended to be a drop-in replacement for
     * {@link org.junit.jupiter.api.Assertions#assertIterableEquals(Iterable, Iterable)}. It should work even if
     * elements in {@code expected}/{@code actual} do not override {@link Object#equals(Object)}.
     */
    @SuppressWarnings("unchecked")
    public static <T> void assertIterableEq(
            Iterable<T> expectedResultList,
            Iterable<? extends T> actualResultList,
            Consumer<RecursiveComparisonConfiguration.Builder> additionalComparisonConfiguration) {

        RecursiveComparisonConfiguration.Builder comparisonConfigurationBuilder =
                RecursiveComparisonConfiguration.builder()
                        .withIgnoreAllOverriddenEquals(false)
                        .withStrictTypeChecking(true);

        additionalComparisonConfiguration.accept(comparisonConfigurationBuilder);

        assertThat((Iterable<T>) actualResultList)
                .usingRecursiveFieldByFieldElementComparator(comparisonConfigurationBuilder.build())
                .containsExactlyElementsOf(expectedResultList);
    }
}
