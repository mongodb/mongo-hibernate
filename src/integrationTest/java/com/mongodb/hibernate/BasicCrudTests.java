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
import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.OptimisticLocking;
import org.hibernate.cfg.Configuration;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            BasicCrudTests.Book.class,
            BasicCrudTests.BookWithEmbeddedField.class,
            BasicCrudTests.BookWithVersionOptimisticLock.class,
            BasicCrudTests.BookWithVersionlessOptimisticLock.class
        })
class BasicCrudTests implements SessionFactoryScopeAware {

    private SessionFactoryScope sessionFactoryScope;

    @BeforeEach
    void setUp() {
        onMongoCollection(MongoCollection::drop);
    }

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
            assertCollectionContainsOnly(expectedDocument);
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
            assertCollectionContainsOnly(expectedDocument);
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
            assertCollectionContainsOnly(expectedDocument);
        }
    }

    @Nested
    class DeleteTests {

        @Test
        void testSimpleDeletion() {

            // given
            var id = 1;
            sessionFactoryScope.inTransaction(session -> {
                var book = new Book();
                book.id = id;
                book.title = "War and Peace";
                book.author = "Leo Tolstoy";
                book.publishYear = 1867;
                session.persist(book);
            });
            assertThat(getCollectionDocuments())
                    .singleElement()
                    .satisfies(doc -> assertThat(doc.getInt32("_id").getValue()).isEqualTo(id));

            // when
            sessionFactoryScope.inTransaction(session -> {
                var book = session.getReference(Book.class, id);
                session.remove(book);
            });

            // then
            assertThat(getCollectionDocuments()).isEmpty();
        }

        @Test
        void testVersionOptimisticLockEntityDeletion() {

            // given
            sessionFactoryScope.inTransaction(session -> {
                var book = new BookWithVersionOptimisticLock();
                book.id = 1;
                book.title = "War and Peace";
                session.persist(book);

                // when
                session.remove(book);
            });

            // then
            assertThat(getCollectionDocuments()).isEmpty();
        }

        @Test
        void testVersionlessOptimisticLockEntityDeletion() {

            // given
            sessionFactoryScope.inTransaction(session -> {
                var book = new BookWithVersionlessOptimisticLock();
                book.id = 1;
                book.publishYear = 1867;
                session.persist(book);

                // when
                session.remove(book);
            });

            // then
            assertThat(getCollectionDocuments()).isEmpty();
        }
    }

    private void onMongoCollection(Consumer<MongoCollection<BsonDocument>> collectionConsumer) {
        var connectionString = new ConnectionString(new Configuration().getProperty(JAKARTA_JDBC_URL));
        try (var mongoClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build())) {
            var collection =
                    mongoClient.getDatabase(connectionString.getDatabase()).getCollection("books", BsonDocument.class);
            collectionConsumer.accept(collection);
        }
    }

    private List<BsonDocument> getCollectionDocuments() {
        var documents = new ArrayList<BsonDocument>();
        onMongoCollection(
                collection -> collection.find().sort(Sorts.ascending("_id")).into(documents));
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

    @Entity(name = "BookWithVersionOptimisticLock")
    @Table(name = "books")
    static class BookWithVersionOptimisticLock {
        @Id
        @Column(name = "_id")
        int id;

        String title;

        @Version
        int version;
    }

    @Entity(name = "BookWithVersionlessOptimisticLock")
    @Table(name = "books")
    @OptimisticLocking(type = OptimisticLockType.ALL)
    @DynamicUpdate
    static class BookWithVersionlessOptimisticLock {
        @Id
        @Column(name = "_id")
        int id;

        String title;
        int publishYear;
    }
}
