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

package com.mongodb.hibernate.internal.jdbc;

/** @hidden */
@SuppressWarnings("MissingSummary")
public final class MongoArray implements ArrayAdapter {
    private final Object contents;

    public MongoArray(Object contents) {
        this.contents = contents;
    }

    @Override
    public Object getArray() {
        // Hibernate ORM does not call `Connection.getTypeMap`/`setTypeMap`, therefore we are free to ignore it
        return contents;
    }
}
