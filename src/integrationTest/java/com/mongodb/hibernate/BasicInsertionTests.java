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

package com.mongodb.hibernate;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.hibernate.cfg.Configuration;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {BasicInsertionTests.Book.class, BasicInsertionTests.BookWithEmbeddedField.class})
class BasicInsertionTests {

    @BeforeEach
    void setUp() {
        onMongoCollection(MongoCollection::drop);
    }

    @Test
    void testSimpleEntityInsertion(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var book = new Book();
            book.id = 1;
            book.title = "War and Peace";
            book.author = "Leo Tolstoy";
            book.publishYear = 1867;
            session.persist(book);
        });
        var expectedDocument = BsonDocument.parse(
                """
                        {
                            _id: 1,
                            title: "War and Peace",
                            author: "Leo Tolstoy",
                            publishYear: 1867
                        }""");
        assertCollectionContainsOnly(expectedDocument);
    }

    @Test
    void testEntityWithNullFieldValueInsertion(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var book = new Book();
            book.id = 1;
            book.title = "War and Peace";
            book.publishYear = 1867;
            session.persist(book);
        });
        var expectedDocument = BsonDocument.parse(
                """
                        {
                            _id: 1,
                            title: "War and Peace",
                            author: null,
                            publishYear: 1867
                        }""");
        assertCollectionContainsOnly(expectedDocument);
    }

    @Test
    void testEntityWithEmbeddedFieldInsertion(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var book = new BookWithEmbeddedField();
            book.id = 1;
            book.title = "War and Peace";
            book.author = new Author("Leo", "Tolstoy");
            book.publishYear = 1867;
            session.persist(book);
        });
        var expectedDocument = BsonDocument.parse(
                """
                        {
                            _id: 1,
                            title: "War and Peace",
                            authorFirstName: "Leo",
                            authorLastName: "Tolstoy",
                            publishYear: 1867
                        }""");
        assertCollectionContainsOnly(expectedDocument);
    }

    private void onMongoCollection(Consumer<MongoCollection<BsonDocument>> collectionConsumer) {
        var connectionString = new ConnectionString(new Configuration().getProperty(JAKARTA_JDBC_URL));
        try (var mongoClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build())) {
            var collection = mongoClient
                    .getDatabase(assertNotNull(connectionString.getDatabase()))
                    .getCollection("books", BsonDocument.class);
            collectionConsumer.accept(collection);
        }
    }

    private List<BsonDocument> getCollectionDocuments() {
        var documents = new ArrayList<BsonDocument>();
        onMongoCollection(collection -> collection.find().into(documents));
        return documents;
    }

    private void assertCollectionContainsOnly(BsonDocument expectedDoc) {
        assertThat(getCollectionDocuments()).asList().singleElement().isEqualTo(expectedDoc);
    }

    @Entity(name = "Book")
    @Table(name = "books")
    static class Book {
        @Id
        @Column(name = "_id")
        int id;

        @Nullable String title;

        @Nullable String author;

        int publishYear;
    }

    @Entity(name = "BookWithEmbeddedField")
    @Table(name = "books")
    static class BookWithEmbeddedField {
        @Id
        @Column(name = "_id")
        int id;

        @Nullable String title;

        @Nullable Author author;

        int publishYear;
    }

    @Embeddable
    static class Author {

        @Column(name = "authorFirstName")
        @Nullable String firstName;

        @Column(name = "authorLastName")
        @Nullable String lastName;

        Author() {}

        Author(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }
}
