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

import static com.mongodb.hibernate.type.temporal.CalendarIntegrationTests.assertNotSupported;
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.SqlTimestampItems;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class SqlTimestampIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(SqlTimestampItems.WithId.class),
                () -> assertNotSupported(SqlTimestampItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(SqlTimestampItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(SqlTimestampItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(SqlTimestampItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(SqlTimestampItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        SqlTimestampItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        SqlTimestampItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(
                        SqlTimestampItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        SqlTimestampItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(SqlTimestampItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }
}
