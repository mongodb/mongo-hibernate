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

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;

public final class MongoTestAssertions {
    private MongoTestAssertions() {}

    /**
     * This method is intended to be a drop-in replacement for
     * {@link org.junit.jupiter.api.Assertions#assertEquals(Object, Object)}. It should work even if
     * {@code expected}/{@code actual} does not override {@link Object#equals(Object)}.
     */
    public static void assertEquals(@Nullable Object expected, @Nullable Object actual) {
        assertThat(actual)
                .usingRecursiveComparison()
                .usingOverriddenEquals()
                .withStrictTypeChecking()
                .isEqualTo(expected);
    }

    /**
     * This method is intended to be a drop-in replacement for
     * {@link org.junit.jupiter.api.Assertions#assertNotEquals(Object, Object)}. It should work even if
     * {@code expected}/{@code actual} does not override {@link Object#equals(Object)}.
     */
    public static void assertNotEquals(@Nullable Object unexpected, @Nullable Object actual) {
        assertThat(actual)
                .usingRecursiveComparison()
                .usingOverriddenEquals()
                .withStrictTypeChecking()
                .isNotEqualTo(unexpected);
    }
}
