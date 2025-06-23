package com.mongodb.hibernate.embeddable;


import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.mongodb.hibernate.MongoTestAssertions.assertEq;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {
        EmptyStructAggregateRetrievalIntegrationTests.Book.class,
        EmptyStructAggregateRetrievalIntegrationTests.Author.class
})
@ExtendWith(MongoExtension.class)
class EmptyStructAggregateRetrievalIntegrationTests {

    @Test
    void testEmptyStructAggregateRetriedAsNonNull(SessionFactoryScope scope) {
        var book = new Book();
        book.id = 1;
        book.author = new Author();

        scope.inTransaction(session -> session.persist(book));

        var retrievedBook = scope.fromTransaction(session -> session.get(Book.class, 1));
        assertEq(book, retrievedBook);
    }

    @Entity
    @Table(name = "books")
    static class Book {
        @Id int id;
        Author author;
    }

    @Embeddable
    @Struct(name = "Author")
    static class Author {
        String firstName;
        String lastName;
    }
}
