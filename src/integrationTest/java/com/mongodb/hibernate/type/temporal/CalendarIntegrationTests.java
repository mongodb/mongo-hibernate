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

import static com.mongodb.hibernate.type.temporal.UnsupportedItems.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.util.Calendar;
import org.hibernate.boot.MetadataSources;
import org.junit.jupiter.api.Test;

class CalendarIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<Calendar>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<Calendar>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<Calendar>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(new ItemWithEmbeddableWithBasicPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(new ItemWithEmbeddableWithArrayPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithCollectionPersistentAttribute<Calendar>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<Calendar>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionOfAggregateEmbeddable<Calendar>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<Calendar>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                Calendar>() {}.getClass()),
                () -> assertNotSupported(new ItemWithNestedCollectionOfAggregateEmbeddable<Calendar>() {}.getClass()));
    }

    static void assertNotSupported(Class<?> entityClass) {
        assertThatThrownBy(() ->
                        new MetadataSources().addAnnotatedClass(entityClass).buildMetadata())
                .isInstanceOf(FeatureNotSupportedException.class)
                .hasMessageMatching(".*persistent attribute .* has .*type .* that is not supported");
    }
}
