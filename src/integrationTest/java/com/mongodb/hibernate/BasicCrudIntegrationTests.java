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

import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {BasicCrudIntegrationTests.Book.class, BasicCrudIntegrationTests.BookWithEmbeddedField.class
        })
@ExtendWith(MongoExtension.class)
class BasicCrudIntegrationTests implements SessionFactoryScopeAware {

    @InjectMongoCollection("books")
    private static MongoCollection<BsonDocument> collection;

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Nested
    class InsertTests {
        @Test
        void testSimpleEntityInsertion() {
            sessionFactoryScope.inTransaction(session -> {
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
            assertCollectionContainsExactly(expectedDocument);
        }

        @Test
        void testEntityWithNullFieldValueInsertion() {
            sessionFactoryScope.inTransaction(session -> {
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
            assertCollectionContainsExactly(expectedDocument);
        }

        @Test
        void testEntityWithEmbeddedFieldInsertion() {
            sessionFactoryScope.inTransaction(session -> {
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
            assertCollectionContainsExactly(expectedDocument);
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void testSimpleDeletion() {

            var id = 1;
            sessionFactoryScope.inTransaction(session -> {
                var book = new Book();
                book.id = id;
                book.title = "War and Peace";
                book.author = "Leo Tolstoy";
                book.publishYear = 1867;
                session.persist(book);
            });
            assertThat(getCollectionDocuments()).hasSize(1);

            sessionFactoryScope.inTransaction(session -> {
                var book = session.getReference(Book.class, id);
                session.remove(book);
            });

            assertThat(getCollectionDocuments()).isEmpty();
        }
    }

    private static List<BsonDocument> getCollectionDocuments() {
        var documents = new ArrayList<BsonDocument>();
        collection.find().sort(Sorts.ascending(ID_FIELD_NAME)).into(documents);
        return documents;
    }

    private static void assertCollectionContainsExactly(BsonDocument expectedDoc) {
        assertThat(getCollectionDocuments()).containsExactly(expectedDoc);
    }

    @Entity
    @Table(name = "books")
    static class Book {
        @Id
        int id;

        String title;

        String author;

        int publishYear;
    }

    @Entity
    @Table(name = "books")
    static class BookWithEmbeddedField {
        @Id
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
