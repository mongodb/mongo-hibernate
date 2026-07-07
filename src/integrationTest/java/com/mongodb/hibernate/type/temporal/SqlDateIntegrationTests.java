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

package com.mongodb.hibernate.type.temporal;

import static com.mongodb.hibernate.type.UnsupportedTypeAssertions.assertNotSupported;
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.SqlDateItems;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class SqlDateIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(SqlDateItems.WithId.class),
                () -> assertNotSupported(SqlDateItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(SqlDateItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(SqlDateItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(SqlDateItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(SqlDateItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(SqlDateItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        SqlDateItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(SqlDateItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }
}
