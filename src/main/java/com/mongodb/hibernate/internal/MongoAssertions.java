/*
 * Copyright 2024-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal;

import org.jspecify.annotations.Nullable;

/**
 * Util class for various assertion purposes.
 *
 * @hidden
 */
public final class MongoAssertions {

    private MongoAssertions() {}

    /**
     * Asserts that some value is not {@code null}.
     *
     * @param value A value to check.
     * @param <T> The type of {@code value}.
     * @return {@code value}
     * @throws AssertionError If {@code value} is {@code null}.
     */
    public static <T> T assertNotNull(@Nullable T value) throws AssertionError {
        if (value == null) {
            throw fail();
        }
        return value;
    }

    /**
     * Asserts that failure happens invariably with message.
     *
     * @param msg The failure message.
     * @return Never completes normally. The return type is {@link AssertionError} to allow writing {@code throw
     *     fail("failure message")}. This may be helpful in non-{@code void} methods.
     * @throws AssertionError Always
     */
    public static AssertionError fail(String msg) throws AssertionError {
        throw new AssertionError(assertNotNull(msg));
    }

    /**
     * Asserts that failure happens invariably.
     *
     * @return Never completes normally. The return type is {@link AssertionError} to allow writing {@code throw
     *     fail()}. This may be helpful in non-{@code void} methods.
     * @throws AssertionError Always
     */
    public static AssertionError fail() throws AssertionError {
        throw new AssertionError();
    }

    /**
     * Asserts that some value is {@code null}.
     *
     * @param value A value to check.
     * @throws AssertionError If {@code value} is not {@code null}.
     */
    public static void assertNull(@Nullable Object value) throws AssertionError {
        if (value != null) {
            throw fail();
        }
    }

    /**
     * Asserts that {@code value} is {@code true}.
     *
     * @param value A value to check.
     * @return {@code true}.
     * @throws AssertionError If {@code value} is {@code false}.
     */
    public static boolean assertTrue(boolean value) throws AssertionError {
        if (!value) {
            throw fail();
        }
        return true;
    }

    /**
     * Asserts that {@code value} is {@code false}.
     *
     * @param value A value to check.
     * @return {@code false}.
     * @throws AssertionError If {@code value} is {@code true}.
     */
    public static boolean assertFalse(boolean value) throws AssertionError {
        if (value) {
            throw fail();
        }
        return false;
    }

    public static <T> T assertInstanceOf(Object value, Class<? extends T> type) {
        try {
            return type.cast(value);
        } catch (ClassCastException e) {
            throw fail();
        }
    }
}
