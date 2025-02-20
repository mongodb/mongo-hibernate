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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.dialect.MongoDialectSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
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
            var author = new Author();
            author.firstName = "Leo";
            author.lastName = "Tolstoy";
            book.author = author;
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
        MongoDialectSettings config = MongoDialectSettings.builder(new StandardServiceRegistryBuilder().getSettings())
                .build();
        try (var mongoClient = MongoClients.create(config.getMongoClientSettings())) {
            var collection =
                    mongoClient.getDatabase(config.getDatabaseName()).getCollection("books", BsonDocument.class);
            collectionConsumer.accept(collection);
        }
    }

    private List<BsonDocument> getCollectionDocuments() {
        var documents = new ArrayList<BsonDocument>();
        onMongoCollection(collection -> collection.find().into(documents));
        return documents;
    }

    private void assertCollectionContainsOnly(BsonDocument expectedDoc) {
        assertThat(getCollectionDocuments()).asInstanceOf(LIST).singleElement().isEqualTo(expectedDoc);
    }

    @Entity(name = "Book")
    @Table(name = "books")
    static class Book {
        @Id
        @Column(name = "_id")
        int id;

        String title;

        String author;

        int publishYear;
    }

    @Entity(name = "BookWithEmbeddedField")
    @Table(name = "books")
    static class BookWithEmbeddedField {
        @Id
        @Column(name = "_id")
        int id;

        String title;

        Author author;

        int publishYear;
    }

    @Embeddable
    static class Author {

        @Column(name = "authorFirstName")
        String firstName;

        @Column(name = "authorLastName")
        String lastName;
    }
}
