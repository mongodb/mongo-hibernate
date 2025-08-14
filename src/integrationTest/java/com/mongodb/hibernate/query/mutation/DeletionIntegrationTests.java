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

package com.mongodb.hibernate.query.mutation;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import com.mongodb.hibernate.query.Book;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = Book.class)
class DeletionIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection(Book.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollection;

    private static final List<Book> testingBooks = List.of(
            new Book(1, "War and Peace", 1869, true),
            new Book(2, "Crime and Punishment", 1866, false),
            new Book(3, "Anna Karenina", 1877, false),
            new Book(4, "The Brothers Karamazov", 1880, false),
            new Book(5, "War and Peace", 2025, false));

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> testingBooks.forEach(session::persist));
        getTestCommandListener().clear();
    }

    @Test
    void testDeletionWithNonZeroMutationCount() {
        assertMutationQuery(
                "delete from Book where title = :title",
                q -> q.setParameter("title", "War and Peace"),
                2,
                """
                {
                  "delete": "books",
                  "deletes": [
                    {
                      "limit": 0,
                      "q": {
                        "title": {
                          "$eq": "War and Peace"
                        }
                      }
                    }
                  ]
                }
                """,
                mongoCollection,
                List.of(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 2,
                                  "title": "Crime and Punishment",
                                  "outOfStock": false,
                                  "publishYear": 1866,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 3,
                                  "title": "Anna Karenina",
                                  "outOfStock": false,
                                  "publishYear": 1877,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 4,
                                  "title": "The Brothers Karamazov",
                                  "outOfStock": false,
                                  "publishYear": 1880,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """)),
                Set.of(Book.COLLECTION_NAME));
    }

    @Test
    void testDeletionWithZeroMutationCount() {
        assertMutationQuery(
                "delete from Book where publishYear < :year",
                q -> q.setParameter("year", 1850),
                0,
                """
                {
                  "delete": "books",
                  "deletes": [
                    {
                      "limit": 0,
                      "q": {
                        "publishYear": {
                          "$lt": 1850
                        }
                      }
                    }
                  ]
                }
                """,
                mongoCollection,
                List.of(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 1,
                                  "title": "War and Peace",
                                  "outOfStock": true,
                                  "publishYear": 1869,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 2,
                                  "title": "Crime and Punishment",
                                  "outOfStock": false,
                                  "publishYear": 1866,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 3,
                                  "title": "Anna Karenina",
                                  "outOfStock": false,
                                  "publishYear": 1877,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 4,
                                  "title": "The Brothers Karamazov",
                                  "outOfStock": false,
                                  "publishYear": 1880,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 5,
                                  "title": "War and Peace",
                                  "outOfStock": false,
                                  "publishYear": 2025,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """)),
                Set.of(Book.COLLECTION_NAME));
    }
}
