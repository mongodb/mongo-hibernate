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
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import com.mongodb.hibernate.query.Book;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = Book.class)
class UpdatingIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection(Book.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollection;

    private static final List<Book> testingBooks = List.of(
            new Book(1, "War & Peace", 1869, true),
            new Book(2, "Crime and Punishment", 1866, false),
            new Book(3, "Anna Karenina", 1877, false),
            new Book(4, "The Brothers Karamazov", 1880, false),
            new Book(5, "War & Peace", 2025, false));

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> testingBooks.forEach(session::persist));
        getTestCommandListener().clear();
    }

    @Test
    void testUpdateWithNonZeroMutationCount() {
        assertMutationQuery(
                "update Book set title = :newTitle, outOfStock = false where title = :oldTitle",
                q -> q.setParameter("oldTitle", "War & Peace").setParameter("newTitle", "War and Peace"),
                2,
                """
                {
                   "update": "books",
                   "updates": [
                     {
                       "multi": true,
                       "q": {
                         "title": {
                           "$eq": "War & Peace"
                         }
                       },
                       "u": {
                         "$set": {
                           "title": "War and Peace",
                           "outOfStock": false
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
                                  "outOfStock": false,
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

    @Test
    void testUpdateWithZeroMutationCount() {
        assertMutationQuery(
                "update Book set outOfStock = false where publishYear < :year",
                q -> q.setParameter("year", 1850),
                0,
                """
                {
                   "update": "books",
                   "updates": [
                     {
                       "multi": true,
                       "q": {
                         "publishYear": {
                           "$lt": 1850
                         }
                       },
                       "u": {
                         "$set": {
                           "outOfStock": false
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
                                  "title": "War & Peace",
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
                                  "title": "War & Peace",
                                  "outOfStock": false,
                                  "publishYear": 2025,
                                  "isbn13": null,
                                  "discount": null,
                                  "price": null
                                }
                                """)),
                Set.of(Book.COLLECTION_NAME));
    }

    @Nested
    class Unsupported {
        @Test
        void testFunctionExpressionAssignment() {
            var hql = "update Book b set b.title = upper(b.title) where b.id = 1";
            assertMutationQueryFailure(
                    hql,
                    query -> {},
                    FeatureNotSupportedException.class,
                    "Function expression [upper] as update assignment value for field path [title] are not supported");
        }

        @Test
        void testPredicateExpressionAssignment() {
            var hql = "update Book b set b.outOfStock = (b.publishYear > 2000) where b.id = 2";
            assertMutationQueryFailure(
                    hql,
                    query -> {},
                    FeatureNotSupportedException.class,
                    "Predicate expressions as update assignment value for field path [outOfStock] are not supported");
        }

        @Test
        void testPathExpressionAssignment() {
            var hql = "update Book b set b.publishYear = b.isbn13 where b.id = 3";
            assertMutationQueryFailure(
                    hql,
                    query -> {},
                    FeatureNotSupportedException.class,
                    "Path expressions as update assignment value for field path [publishYear] are not supported");
        }
    }
}
