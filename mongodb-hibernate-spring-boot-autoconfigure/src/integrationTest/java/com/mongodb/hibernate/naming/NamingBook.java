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

package com.mongodb.hibernate.naming;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.types.ObjectId;

// The collection name is pinned to a lowercase single word so it is stable under both the identity and
// snake_case physical naming strategies; only the unannotated multi-word field `publishYear` varies
// (publishYear vs publish_year), which is what the naming tests assert.
@Entity
@Table(name = "namingbook")
public class NamingBook {

    @Id
    public ObjectId id;

    public String title;

    public int publishYear;

    public NamingBook() {}

    public NamingBook(ObjectId id, String title, int publishYear) {
        this.id = id;
        this.title = title;
        this.publishYear = publishYear;
    }
}
