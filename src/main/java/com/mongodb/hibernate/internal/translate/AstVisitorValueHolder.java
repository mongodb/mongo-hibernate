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

import org.jspecify.annotations.Nullable;

final class AstVisitorValueHolder {

    private @Nullable AstVisitorValueDescriptor<?> valueDescriptor;
    private @Nullable Object value;

    AstVisitorValueHolder() {}

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

    @SuppressWarnings("NamedLikeContextualKeyword")
    public <T> void yield(AstVisitorValueDescriptor<T> valueDescriptor, T value) {
        assertTrue(valueDescriptor.equals(this.valueDescriptor));
        assertNull(this.value);
        this.value = value;
    }
}
