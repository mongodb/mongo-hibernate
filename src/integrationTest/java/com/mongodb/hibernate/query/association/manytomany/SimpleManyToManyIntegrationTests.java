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

package com.mongodb.hibernate.query.association.manytomany;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Set;
import org.hibernate.Hibernate;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {SimpleManyToManyIntegrationTests.Book.class, SimpleManyToManyIntegrationTests.Author.class})
class SimpleManyToManyIntegrationTests extends AbstractQueryIntegrationTests {

    @Test
    void test() {
        var author1 = new Author(1, "Gavin King");
        var author2 = new Author(2, "Christian Bauer");
        var book1 = new Book(1, "Java Persistence with Hibernate");
        var book2 = new Book(2, "Hibernate Tips");
        author1.writtenBooks = Set.of(book1, book2);
        author2.writtenBooks = Set.of(book1);
        book1.authors = Set.of(author1, author2);
        book2.authors = Set.of(author1);

        getSessionFactoryScope().inTransaction(session -> {
            session.persist(author1);
            session.persist(author2);
            session.persist(book1);
            session.persist(book2);
        });

        var loadedBook1OutsideSession =
                getSessionFactoryScope().fromTransaction(session -> session.find(Book.class, book1.id));
        assertThat(Hibernate.isInitialized(loadedBook1OutsideSession.authors)).isFalse();

        var loadedBook2OutsideSession =
                getSessionFactoryScope().fromTransaction(session -> session.find(Book.class, book2.id));
        assertThat(Hibernate.isInitialized(loadedBook2OutsideSession.authors)).isFalse();

        var loadedAuthor1OutsideSession =
                getSessionFactoryScope().fromTransaction(session -> session.find(Author.class, author1.id));
        assertThat(Hibernate.isInitialized(loadedAuthor1OutsideSession.writtenBooks))
                .isFalse();

        var loadedAuthor2OutsideSession =
                getSessionFactoryScope().fromTransaction(session -> session.find(Author.class, author2.id));
        assertThat(Hibernate.isInitialized(loadedAuthor2OutsideSession.writtenBooks))
                .isFalse();

        getSessionFactoryScope().inTransaction(session -> {
            var loadedBook1 = session.find(Book.class, book1.id);
            var loadedBook2 = session.find(Book.class, book2.id);
            var loadedAuthor1 = session.find(Author.class, author1.id);
            var loadedAuthor2 = session.find(Author.class, author2.id);
            assertEq(book1, loadedBook1);
            assertEq(book2, loadedBook2);
            assertEq(author1, loadedAuthor1);
            assertEq(author2, loadedAuthor2);
        });
    }

    @Entity
    @Table(name = "books")
    static class Book {
        @Id
        int id;

        String title;

        @ManyToMany(mappedBy = "writtenBooks")
        Set<Author> authors;

        Book() {}

        Book(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    @Entity
    @Table(name = "authors")
    static class Author {
        @Id
        int id;

        String name;

        @ManyToMany
        Set<Book> writtenBooks;

        Author() {}

        Author(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
