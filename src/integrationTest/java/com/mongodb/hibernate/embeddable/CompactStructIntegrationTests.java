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

package com.mongodb.hibernate.embeddable;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            CompactStructIntegrationTests.StructHolder.class,
            CompactStructIntegrationTests.CompactStruct.class
        })
@ExtendWith(MongoExtension.class)
class CompactStructIntegrationTests {

    @InjectMongoCollection("items")
    private static MongoCollection<BsonDocument> mongoCollection;

    @Test
    void test(SessionFactoryScope scope) {
        var holder = new StructHolder();
        holder.id = 1;
        holder.compactStruct = new CompactStruct();
        holder.compactStruct.field1 = null;
        holder.compactStruct.field2 = "value2";
        scope.inTransaction(session -> session.persist(holder));

        assertThat(mongoCollection.find())
                .containsExactly(new BsonDocument("_id", new BsonInt32(1))
                        .append("compactStruct", new BsonDocument("field2", new BsonString("value2"))));

        var loadedHolder = scope.fromTransaction(session -> session.find(StructHolder.class, 1));
        assertEq(holder, loadedHolder);
    }

    @Entity(name = "StructHolder")
    @Table(name = "items")
    static class StructHolder {
        @Id
        int id;

        CompactStruct compactStruct;
    }

    @Embeddable
    @Struct(name = "CompactStruct")
    static class CompactStruct {
        String field1;
        String field2;
    }
}
