package com.mongodb.hibernate.embeddable;

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

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(mongoCollection.find()).containsExactly(
                BsonDocument.parse(
                        """
                        {
                            _id: 1,
                            author: {
                                firstName: null,
                                lastName: null
                            }
                        }
                        """)
        );
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
