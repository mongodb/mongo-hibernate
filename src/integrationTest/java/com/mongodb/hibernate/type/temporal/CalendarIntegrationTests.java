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

import static com.mongodb.hibernate.type.temporal.UnsupportedItems.CalendarItems;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import org.hibernate.boot.MetadataSources;
import org.junit.jupiter.api.Test;

class CalendarIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(CalendarItems.WithId.class),
                () -> assertNotSupported(CalendarItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(CalendarItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(CalendarItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(CalendarItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(CalendarItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(CalendarItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        CalendarItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(CalendarItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }

    // package-private because it's used by all the tests in this package
    static void assertNotSupported(Class<?> entityClass) {
        assertThatThrownBy(() ->
                        new MetadataSources().addAnnotatedClass(entityClass).buildMetadata())
                .isInstanceOf(FeatureNotSupportedException.class)
                .hasMessageMatching(".*persistent attribute .* has .*type .* that is not supported");
    }
}
