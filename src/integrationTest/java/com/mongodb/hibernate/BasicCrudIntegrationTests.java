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

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.bson.BsonDocument;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {BasicCrudIntegrationTests.Book.class, BasicCrudIntegrationTests.BookDynamicallyUpdated.class
        })
@ExtendWith(MongoExtension.class)
class BasicCrudIntegrationTests implements SessionFactoryScopeAware {

    @InjectMongoCollection("books")
    private static MongoCollection<BsonDocument> mongoCollection;

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
            var author =
                    """
                    TODO-HIBERNATE-74 https://jira.mongodb.org/browse/HIBERNATE-74,
                    TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 Make sure `book.author`
                    is set to `null` when we implement `MongoPreparedStatement.setNull` properly.""";
            sessionFactoryScope.inTransaction(session -> {
                var book = new Book();
                book.id = 1;
                book.title = "War and Peace";
                book.author = author;
                book.publishYear = 1867;
                session.persist(book);
            });
            var expectedDocument = BsonDocument.parse(
                    """
                    {
                        _id: 1,
                        title: "War and Peace",
                        author: "%s",
                        publishYear: 1867
                    }"""
                            .formatted(author));
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
            assertThat(mongoCollection.find()).hasSize(1);

            sessionFactoryScope.inTransaction(session -> {
                var book = session.getReference(Book.class, id);
                session.remove(book);
            });

            assertThat(mongoCollection.find()).isEmpty();
        }
    }

    @Nested
    class UpdateTests {

        @Test
        void testSimpleUpdate() {
            sessionFactoryScope.inTransaction(session -> {
                var book = new Book();
                book.id = 1;
                book.title = "War and Peace";
                book.author = "Leo Tolstoy";
                book.publishYear = 1867;
                session.persist(book);
                session.flush();

                book.title = "Resurrection";
                book.publishYear = 1899;
            });

            assertCollectionContainsExactly(
                    BsonDocument.parse(
                            """
                            {"_id": 1, "author": "Leo Tolstoy", "publishYear": 1899, "title": "Resurrection"}\
                            """));
        }

        @Test
        void testDynamicUpdate() {
            sessionFactoryScope.inTransaction(session -> {
                var book = new BookDynamicallyUpdated();
                book.id = 1;
                book.title = "War and Peace";
                book.author = "Leo Tolstoy";
                book.publishYear = 1899;
                session.persist(book);
                session.flush();

                book.publishYear = 1867;
            });

            assertCollectionContainsExactly(
                    BsonDocument.parse(
                            """
                            {"_id": 1, "author": "Leo Tolstoy", "publishYear": 1867, "title": "War and Peace"}\
                            """));
        }
    }

    @Nested
    class SelectTests {

        @Test
        void testFindByPrimaryKeyWithoutNullValueField() {
            var book = new Book();
            book.id = 1;
            book.author = "Marcel Proust";
            book.title = "In Search of Lost Time";
            book.publishYear = 1913;

            sessionFactoryScope.inTransaction(session -> session.persist(book));
            var loadedBook = sessionFactoryScope.fromTransaction(session -> session.find(Book.class, 1));
            assertEq(book, loadedBook);
        }

        @Test
        void testFindByPrimaryKeyWithNullValueField() {
            var book = new Book();
            book.id = 1;
            book.title = "Brave New World";
            book.author =
                    """
                    TODO-HIBERNATE-74 https://jira.mongodb.org/browse/HIBERNATE-74,
                    TODO-HIBERNATE-48 https://jira.mongodb.org/browse/HIBERNATE-48 Make sure `book.author`
                    is set to `null` when we implement `MongoPreparedStatement.setNull` properly.""";
            book.publishYear = 1932;

            sessionFactoryScope.inTransaction(session -> session.persist(book));
            var loadedBook = sessionFactoryScope.fromTransaction(session -> session.find(Book.class, 1));
            assertEq(book, loadedBook);
        }
    }

    @Nested
    class NativeQueryTests {

        @Test
        void testNative() {
            var book = new Book();
            book.id = 1;
            book.title = "In Search of Lost Time";
            book.author = "Marcel Proust";
            book.publishYear = 1913;

            sessionFactoryScope.inTransaction(session -> session.persist(book));

            var nativeQuery =
                    """
                    {
                        aggregate: "books",
                        pipeline: [
                            { $match :  { _id: { $eq: :id } } },
                            { $project: { _id: 1, publishYear: 1, title: 1, author: 1 } }
                        ]
                    }
                    """;
            sessionFactoryScope.inTransaction(session -> {
                var query = session.createNativeQuery(nativeQuery, Book.class)
                        .setParameter("id", book.id);
                var queriedBook = query.getSingleResult();
                assertThat(queriedBook).usingRecursiveComparison().isEqualTo(book);
            });
        }
    }

    private static void assertCollectionContainsExactly(BsonDocument expectedDoc) {
        assertThat(mongoCollection.find()).containsExactly(expectedDoc);
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
    @DynamicUpdate
    static class BookDynamicallyUpdated {
        @Id
        int id;

        String title;

        String author;

        int publishYear;
    }
}
