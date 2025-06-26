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

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            DynamicInsertWithStructWithNullValuesIntegrationTests.Book.class,
            DynamicInsertWithStructWithNullValuesIntegrationTests.Author.class
        })
@ExtendWith(MongoExtension.class)
class DynamicInsertWithStructWithNullValuesIntegrationTests {

    @InjectMongoCollection("books")
    private static MongoCollection<BsonDocument> mongoCollection;

    @Test
    void test(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var book = new Book();
            book.id = 1;
            book.author = new Author();
            session.persist(book);
        });
        assertThat(mongoCollection.find())
                .containsExactly(
                        BsonDocument.parse(
                                """
                                {
                                    _id: 1,
                                    author: {
                                        firstName: null,
                                        lastName: null
                                    }
                                }
                                """));
    }

    @Entity
    @DynamicInsert
    @Table(name = "books")
    static class Book {
        @Id
        int id;

        Author author;
    }

    @Embeddable
    @Struct(name = "Author")
    static class Author {
        String firstName;
        String lastName;
    }
}
