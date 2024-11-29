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

import static org.hibernate.cfg.JdbcSettings.JAKARTA_JDBC_URL;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@NullUnmarked
class BasicInsertionTests {

    private static SessionFactory sessionFactory;

    @BeforeAll
    static void beforeAll() {
        sessionFactory = new Configuration().addAnnotatedClass(Book.class).buildSessionFactory();
    }

    @AfterAll
    static void afterAll() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @BeforeEach
    void setUp() {
        onMongoCollection(MongoCollection::drop);
    }

    @Test
    void testInsertion() {
        sessionFactory.inTransaction(session -> {
            var book = new Book();
            book.id = 1;
            book.title = "War and Peace";
            book.author = "Leo Tolstoy";
            book.publishYear = 1867;
            session.persist(book);
        });
        var expectedDocuments = Set.of(
                BsonDocument.parse(
                        """
                        {
                            _id: 1,
                            title: "War and Peace",
                            author: "Leo Tolstoy",
                            publishYear: 1867
                        }"""));
        Assertions.assertEquals(expectedDocuments, getCollectionDocuments());
    }

    private void onMongoCollection(Consumer<MongoCollection<BsonDocument>> collectionConsumer) {
        var connectionString = new ConnectionString(new Configuration().getProperty(JAKARTA_JDBC_URL));
        try (var mongoClient = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build())) {
            var collection = mongoClient.getDatabase("mongo-hibernate-test").getCollection("books", BsonDocument.class);
            ;
            collectionConsumer.accept(collection);
        }
        ;
    }

    private Set<BsonDocument> getCollectionDocuments() {
        var documents = new HashSet<BsonDocument>();
        onMongoCollection(collection -> collection.find().into(documents));
        return documents;
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
}
