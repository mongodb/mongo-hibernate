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
import static java.lang.String.format;

import com.google.common.reflect.TypeToken;
import com.mongodb.hibernate.translate.mongoast.AstNode;
import org.jspecify.annotations.Nullable;

/**
 * A data exchange mechanism to overcome the limitation of various visitor methods in
 * {@link org.hibernate.sql.ast.SqlAstWalker} don't return value; Returning values is required during MQL translation
 * (e.g. returning intermediate MQL {@link AstNode}).
 *
 * <p>Contains both value and its type info (using Guava's {@link TypeToken} so Java generics info is retained at
 * runtime), which will be used to ensure value provided has identical type with value consumer's expectation.
 *
 * <p>During one MQL translation process, one single object of this class should be created globally (or not within
 * methods as temporary variable) so various {@code void} visitor methods of {@code SqlAstWalker} could access it as
 * either producer calling {@link #setValue(TypeToken, Object)} method) or consumer calling {@link #getValue(TypeToken,
 * Runnable)} method). Once the consumer grabs its expected value, it becomes the sole owner of the value with the
 * holder being blank.
 *
 * @see org.hibernate.sql.ast.SqlAstWalker
 * @see TypeToken
 */
final class AstVisitorValueHolder {

    private TypeToken<?> valueType;
    private @Nullable Object value;

    private AstVisitorValueHolder(TypeToken<?> valueType) {
        this.valueType = valueType;
    }

    public static AstVisitorValueHolder emptyHolder() {
        return new AstVisitorValueHolder(new TypeToken<Void>() {});
    }

    /**
     * Grabs the value (matching the expected type) then empties the holder and restored its previous state.
     *
     * <p>Note that during SQL AST tree traversal, it is common to call this method recursively, so one salient feature
     * of this class is the capability to restore previous state.
     *
     * @param valueType expected type of the data to be grabbed.
     * @param valueSetter the {@code Runnable} wrapper of a void AST visitor method which is supposed to invoke
     *     {@link #setValue(TypeToken, Object)} internally with type identical to the {@code valueType}.
     * @return the grabbed value set by {@code valueSetter}
     * @param <T> generics type of the value returned
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue(TypeToken<T> valueType, Runnable valueSetter) {
        var previousType = this.valueType;
        assertNull(value);
        this.valueType = valueType;
        try {
            valueSetter.run();
            return (T) assertNotNull(value);
        } finally {
            this.valueType = previousType;
            value = null;
        }
    }

    /**
     * Overloaded method to simplify usage by providing {@code Class} instead of {@link TypeToken}.
     *
     * @see #getValue(TypeToken, Runnable)
     */
    public <T> T getValue(Class<T> clazz, Runnable valueSetter) {
        return getValue(TypeToken.of(clazz), valueSetter);
    }

    /**
     * Sets the value matching type expectation.
     *
     * <p>To ensure smooth data exchange, the following preconditions need to be satisfied:
     *
     * <ul>
     *   <li>the holder contains empty value
     *   <li>the holder's data type matches the real data type
     * </ul>
     *
     * @param valueType data type of the {@code value}; should match the expected type in holder.
     * @param value data returned inside some {@code void} method.
     * @param <T> generics type of the {@code value}
     */
    public <T> void setValue(TypeToken<T> valueType, T value) {
        assertTrue(
                format("provided type [%s] should match expected [%s]", valueType, this.valueType),
                valueType.equals(this.valueType));
        assertNull(this.value);
        this.value = value;
    }

    /**
     * Overloaded method to simplify usage by providing {@code Class} instead of {@link TypeToken}.
     *
     * @see #setValue(TypeToken, Object)
     */
    public <T> void setValue(Class<T> clazz, T value) {
        setValue(TypeToken.of(clazz), value);
    }
}
