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

package com.mongodb.hibernate.example.model;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.hibernate.annotations.ObjectIdGenerator;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.hibernate.annotations.Struct;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.mongodb.hibernate.example.model.Item.COLLECTION_NAME;
import static java.util.Collections.unmodifiableList;

@Entity
@Table(name = COLLECTION_NAME)
public class Item {
    public static final String COLLECTION_NAME = "items";

    @Id
    // specifying `@Column(name = "_id")` is not necessary, as it is implied
    @ObjectIdGenerator
    private final ObjectId id;
    private String string;
    private final List<MyStruct> structsList;

    public Item() {
        this(null, null, null);
    }

    public Item(ObjectId id) {
        this(id, null, null);
    }

    private Item(ObjectId id, String string, List<MyStruct> structsList) {
        this.id = id;
        this.string = string;
        this.structsList = structsList == null ? new ArrayList<>() : structsList;
    }

    public ObjectId getId() {
        return id;
    }

    public Item setString(String string) {
        this.string = string;
        return this;
    }

    public String getString() {
        return string;
    }

    public Item addToStructsList(MyStruct struct) {
        structsList.add(struct);
        return this;
    }

    public List<MyStruct> getStructsList() {
        return unmodifiableList(structsList);
    }

    @Override
    public String toString() {
        return "Item{"
                + "id=" + id
                + ", string=" + string
                + ", structsList=" + structsList
                + '}';
    }

    public static BsonDocument projectAll() {
        return Aggregates.project(Projections.include(
                "_id",
                "string",
                "structsList"))
                .toBsonDocument();
    }

    @Embeddable
    @Struct(name = "MyStruct")
    public record MyStruct(Instant instant, Set<Integer> intsSet) {}
}
