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

package com.mongodb.hibernate.internal.translate;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;

import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import org.jspecify.annotations.Nullable;

/**
 * A data exchange mechanism to overcome the limitation of various {@code void} methods of
 * {@link org.hibernate.sql.ast.SqlAstWalker} or {@link org.hibernate.sql.ast.tree.SqlAstNode} not returning values,
 * which are required during MQL translation (e.g. returning intermediate MQL {@link AstNode}s).
 *
 * <p>This class is not part of the public API and may be removed or changed at any time
 *
 * @see org.hibernate.sql.ast.SqlAstWalker
 * @see org.hibernate.sql.ast.tree.SqlAstNode
 * @see AstVisitorValueDescriptor
 */
final class AstVisitorValueHolder {

    private @Nullable AstVisitorValueDescriptor<?> valueDescriptor;
    private @Nullable Object value;

    AstVisitorValueHolder() {}

    /**
     * Executes the {@link Runnable} which internally invokes the {@link #yield(AstVisitorValueDescriptor, Object)}
     * method with the identical {@link AstVisitorValueDescriptor}.
     *
     * @param valueDescriptor expected semantics of the data to yield
     * @param yieldingRunnable the {@code Runnable} wrapper
     * @return the value to yield by {@code yieldingRunnable}
     * @param <T> generics type of the value
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(AstVisitorValueDescriptor<T> valueDescriptor, Runnable yieldingRunnable) {
        assertNull(value);
        var previousValueDescriptor = this.valueDescriptor;
        this.valueDescriptor = valueDescriptor;
        try {
            yieldingRunnable.run();
            return (T) assertNotNull(value);
        } finally {
            this.valueDescriptor = previousValueDescriptor;
            value = null;
        }
    }

    /**
     * Yields the value matching the provided {@link AstVisitorValueDescriptor}.
     *
     * @param valueDescriptor semantics of the {@code value}
     * @param value data returned inside some {@code void} method
     * @param <T> generics type of the {@code value}
     */
    @SuppressWarnings("NamedLikeContextualKeyword")
    public <T> void yield(AstVisitorValueDescriptor<T> valueDescriptor, T value) {
        assertTrue(valueDescriptor.equals(this.valueDescriptor));
        assertNull(this.value);
        this.value = value;
    }
}
