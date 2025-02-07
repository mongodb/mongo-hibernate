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

package com.mongodb.hibernate.translate;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;

import com.mongodb.hibernate.internal.mongoast.AstNode;
import org.hibernate.sql.ast.SqlAstWalker;
import org.jspecify.annotations.Nullable;

/**
 * A data exchange mechanism to overcome the limitation of various visitor methods in
 * {@link org.hibernate.sql.ast.SqlAstWalker} not returning a value; Returning values is required during MQL translation
 * (e.g. returning intermediate MQL {@link AstNode}).
 *
 * <p>During one MQL translation process, one single object of this class should be created globally (or not within
 * methods as temporary variable) so various {@code void} visitor methods of {@code SqlAstWalker} or the
 * {@link org.hibernate.sql.ast.tree.SqlAstNode#accept(SqlAstWalker)} could access it as either producer calling
 * {@link #yield(AstVisitorValueDescriptor, Object)} method) or consumer calling
 * {@link #execute(AstVisitorValueDescriptor, Runnable)} method). Once the consumer grabs its expected value, it becomes
 * the sole owner of the value with the holder being blank.
 *
 * @see org.hibernate.sql.ast.SqlAstWalker
 * @see AstVisitorValueDescriptor
 */
final class AstVisitorValueHolder {

    private @Nullable AstVisitorValueDescriptor<?> valueDescriptor;
    private @Nullable Object value;

    AstVisitorValueHolder() {}

    /**
     * Executes the {@link Runnable} which will internally invoke the {@link #yield(AstVisitorValueDescriptor, Object)}
     * method with identical {@link AstVisitorValueDescriptor}.
     *
     * @param valueDescriptor expected semantics of the data to yield.
     * @param yieldingRunnable the {@code Runnable} wrapper which is supposed to invoke
     *     {@link #yield(AstVisitorValueDescriptor, Object)} internally sharing the identical {@code valueDescriptor}.
     * @return the value to yield by {@code yieldingRunnable}
     * @param <T> generics type of the value
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(AstVisitorValueDescriptor<T> valueDescriptor, Runnable yieldingRunnable) {
        assertNull(value);
        var previousType = this.valueDescriptor;
        this.valueDescriptor = valueDescriptor;
        try {
            yieldingRunnable.run();
            return (T) assertNotNull(value);
        } finally {
            this.valueDescriptor = previousType;
            value = null;
        }
    }

    /**
     * Yields the value matching the provided {@link AstVisitorValueDescriptor}.
     *
     * @param valueDescriptor semantics of the {@code value}; should match the expected one in holder.
     * @param value data returned inside some {@code void} method.
     * @param <T> generics type of the {@code value}
     */
    @SuppressWarnings("NamedLikeContextualKeyword")
    public <T> void yield(AstVisitorValueDescriptor<T> valueDescriptor, T value) {
        assertTrue(valueDescriptor.equals(this.valueDescriptor));
        assertNull(this.value);
        this.value = value;
    }
}
