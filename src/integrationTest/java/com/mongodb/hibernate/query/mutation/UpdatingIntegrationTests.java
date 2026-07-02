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

import static com.mongodb.hibernate.BasicCrudIntegrationTests.Item.COLLECTION_NAME;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.embeddable.StructAggregateEmbeddableIntegrationTests;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import com.mongodb.hibernate.query.Book;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            Book.class,
            UpdatingIntegrationTests.ItemWithNestedValue.class,
            UpdatingIntegrationTests.ItemWithPair.class
        })
class UpdatingIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection(Book.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> booksCollection;

    @InjectMongoCollection(COLLECTION_NAME)
    private static MongoCollection<BsonDocument> itemsCollection;

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
                booksCollection,
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
                booksCollection,
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

    @Test
    void testUpdateNoFilter() {
        assertMutationQuery(
                "update Book set title = :newTitle",
                q -> q.setParameter("newTitle", "Unknown"),
                5,
                """
                {
                   "update": "books",
                   "updates": [
                     {
                       "multi": true,
                       "q": {},
                       "u": {
                         "$set": {
                           "title": "Unknown"
                         }
                       }
                     }
                   ]
                }
                """,
                booksCollection,
                List.of(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 1,
                                  "title": "Unknown",
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
                                  "title": "Unknown",
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
                                  "title": "Unknown",
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
                                  "title": "Unknown",
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
                                  "title": "Unknown",
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
    class Unsupported implements MongoServiceRegistryProducer {
        @Test
        void testFunctionExpressionAssignment() {
            var hql = "update Book b set b.title = upper(b.title) where b.id = 1";
            assertMutationQueryFailure(
                    hql,
                    query -> {},
                    FeatureNotSupportedException.class,
                    "Function expression [upper] as update assignment value for field path [title] is not supported");
        }

        @Test
        void testPredicateExpressionAssignment() {
            var hql = "update Book b set b.outOfStock = (b.publishYear > 2000) where b.id = 2";
            assertMutationQueryFailure(
                    hql,
                    query -> {},
                    FeatureNotSupportedException.class,
                    "Predicate expression as update assignment value for field path [outOfStock] is not supported");
        }

        @Test
        void testPathExpressionAssignment() {
            var hql = "update Book b set b.publishYear = b.isbn13 where b.id = 3";
            assertMutationQueryFailure(
                    hql,
                    query -> {},
                    FeatureNotSupportedException.class,
                    "Path expression as update assignment value for field path [publishYear] is not supported");
        }
    }

    @Nested
    class StructAggregateEmbeddablePathExpressionTests implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope()
                    .inTransaction(session -> session.persist(
                            new ItemWithNestedValue(1, new StructAggregateEmbeddableIntegrationTests.Single(7))));
            getTestCommandListener().clear();
        }

        @Test
        void testStructAggregateEmbeddablePathExpressionAssignment() {
            assertMutationQuery(
                    "update ItemWithNestedValue set nested.a = 0",
                    1,
                    """
                    {
                      "update": "items",
                      "updates": [
                        {
                          "q": {},
                          "u": {
                            "$set": {
                              "nested.a": 0
                            }
                          },
                          "multi": true
                        }
                      ]
                    }""",
                    itemsCollection,
                    List.of(
                            BsonDocument.parse(
                                    """
                                    {
                                      "_id": 1,
                                      "nested": {
                                        "a": 0
                                      }
                                    }""")),
                    Set.of(COLLECTION_NAME));
        }
    }

    @Nested
    class StructAggregateEmbeddableMultiFieldPathExpressionTests implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope().inTransaction(session -> session.persist(new ItemWithPair(1, new Pair(10, 20))));
            getTestCommandListener().clear();
        }

        @Test
        void testStructAggregateEmbeddableMultiFieldAssignment() {
            assertMutationQuery(
                    "update ItemWithPair set pair.a = 1, pair.b = 2",
                    1,
                    """
                    {
                      "update": "items",
                      "updates": [
                        {
                          "q": {},
                          "u": {
                            "$set": {
                              "pair.a": 1,
                              "pair.b": 2
                            }
                          },
                          "multi": true
                        }
                      ]
                    }""",
                    itemsCollection,
                    List.of(
                            BsonDocument.parse(
                                    """
                                    {
                                      "_id": 1,
                                      "pair": {
                                        "a": 1,
                                        "b": 2
                                      }
                                    }""")),
                    Set.of(COLLECTION_NAME));
        }
    }

    @Test
    void testColumnTransformerWriteExpressionThrows() {
        assertBootstrapThrows(() -> new MetadataSources()
                        .addAnnotatedClass(ItemWithColumnTransformer.class)
                        .buildMetadata(new StandardServiceRegistryBuilder().build())
                        .buildSessionFactory())
                .isInstanceOf(FeatureNotSupportedException.class)
                .hasMessage("@ColumnTransformer expressions are not supported");
    }

    @Entity(name = "ItemWithColumnTransformer")
    @Table(name = COLLECTION_NAME)
    static class ItemWithColumnTransformer {
        @Id
        int id;

        @ColumnTransformer(write = "test(?)")
        String value;

        ItemWithColumnTransformer() {}
    }

    @Entity(name = "ItemWithNestedValue")
    @Table(name = COLLECTION_NAME)
    static class ItemWithNestedValue {
        @Id
        int id;

        StructAggregateEmbeddableIntegrationTests.Single nested;

        ItemWithNestedValue() {}

        ItemWithNestedValue(int id, StructAggregateEmbeddableIntegrationTests.Single nested) {
            this.id = id;
            this.nested = nested;
        }
    }

    @Embeddable
    @Struct(name = "Pair")
    static class Pair {
        int a;
        int b;

        Pair() {}

        Pair(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    @Entity(name = "ItemWithPair")
    @Table(name = COLLECTION_NAME)
    static class ItemWithPair {
        @Id
        int id;

        Pair pair;

        ItemWithPair() {}

        ItemWithPair(int id, Pair pair) {
            this.id = id;
            this.pair = pair;
        }
    }
}
