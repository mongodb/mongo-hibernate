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
 * <p>This class is not part of the public API and may be removed or changed at any time
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
            throw new AssertionError();
        }
        return value;
    }

    /**
     * Asserts that failure happens invariably.
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
     * Asserts that some value is {@code null}.
     *
     * @param value A value to check.
     * @param <T> The type of {@code value}.
     * @throws AssertionError If {@code value} is not {@code null}.
     */
    public static <T> void assertNull(@Nullable T value) throws AssertionError {
        if (value != null) {
            throw new AssertionError();
        }
    }

    /**
     * Throw AssertionError if the condition if false.
     *
     * @param name the name of the state that is being checked
     * @param condition the condition about the parameter to check
     * @throws AssertionError if the condition is false
     */
    public static void assertTrue(String name, boolean condition) {
        if (!condition) {
            throw new AssertionError("state should be: " + assertNotNull(name));
        }
    }
}
