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

package com.mongodb.hibernate.internal;

import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;

/**
 * Owns the naming convention that flattens the components of an {@code @EmbeddedId} composite primary key into sibling
 * columns and reconstitutes them into the {@code _id} sub-document.
 *
 * <p>A component named {@code publisherId} is carried through Hibernate's column metadata as {@code _id.publisherId} —
 * a MongoDB dot-path into the {@code _id} sub-document. Boot encodes the name; the translator recognises and decodes
 * it. This class is the single owner of that encoding so the two layers cannot drift apart.
 *
 * @hidden
 */
public final class EmbeddedIdColumnName {

    private static final String PREFIX = ID_FIELD_NAME + ".";

    private EmbeddedIdColumnName() {}

    /**
     * Returns the column name for a composite-id component, e.g. {@code "publisherId"} -> {@code "_id.publisherId"}.
     *
     * @param componentName The bare component name.
     * @return The {@code _id}-qualified column name.
     */
    public static String forComponent(String componentName) {
        return PREFIX + componentName;
    }

    /**
     * Returns whether a column/element name is a composite-id component, i.e. starts with {@code "_id."}.
     *
     * @param columnOrElementName The column or element name to test.
     * @return {@code true} if the name denotes a composite-id component.
     */
    public static boolean isComponent(String columnOrElementName) {
        return columnOrElementName.startsWith(PREFIX);
    }

    /**
     * Returns the bare component name carried by a composite-id column name, e.g. {@code "_id.bookNo"} ->
     * {@code "bookNo"}.
     *
     * @param columnName The {@code _id}-qualified column name.
     * @return The bare component name.
     */
    public static String componentName(String columnName) {
        return columnName.substring(PREFIX.length());
    }
}
