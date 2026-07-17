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

package com.mongodb.hibernate.query.select;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.Hibernate;
import org.hibernate.JDBCException;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            CompositePrimaryKeyIntegrationTests.Book.class,
            CompositePrimaryKeyIntegrationTests.RecordBook.class,
            CompositePrimaryKeyIntegrationTests.VersionedBook.class
        })
class CompositePrimaryKeyIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection("books")
    private MongoCollection<BsonDocument> booksCollection;

    @InjectMongoCollection("record_books")
    private MongoCollection<BsonDocument> recordBooksCollection;

    @InjectMongoCollection("versioned_books")
    private MongoCollection<BsonDocument> versionedBooksCollection;

    private void seedBooks(Session session) {
        session.persist(new Book(new BookId(10, 2), "a"));
        session.persist(new Book(new BookId(10, 3), "b"));
        session.persist(new Book(new BookId(20, 1), "c"));
    }

    @Nested
    class Insert implements MongoServiceRegistryProducer {

        @Test
        void testManagedInsert() {
            commandHistory.clear();
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new Book(new BookId(7, 8), "g"));
                session.flush();
                assertActualCommandsInOrder(
                        BsonDocument.parse(
                                """
                                {
                                  "insert": "books",
                                  "documents": [
                                    {
                                      "_id": {"bookNo": {"$numberLong": "8"}, "publisherId": {"$numberLong": "7"}},
                                      "title": "g"
                                    }
                                  ]
                                }
                                """));
            });

            var stored = booksCollection
                    .find(BsonDocument.parse("{ \"title\": \"g\" }"))
                    .first();
            assertThat(stored).isNotNull();
            assertThat(stored.getDocument("_id").getInt64("bookNo").getValue()).isEqualTo(8L);
            assertThat(stored.getDocument("_id").getInt64("publisherId").getValue())
                    .isEqualTo(7L);
            assertThat(stored.getString("title").getValue()).isEqualTo("g");
        }

        @Test
        void testBulkInsertSingleRow() {
            assertMutationQuery(
                    "insert into Book (id, title) values (:id, 'z')",
                    q -> q.setParameter("id", new BookId(5, 7)),
                    1,
                    """
                    {
                      "insert": "books",
                      "documents": [
                        {
                          "_id": {"bookNo": {"$numberLong": "7"}, "publisherId": {"$numberLong": "5"}},
                          "title": "z"
                        }
                      ]
                    }
                    """,
                    booksCollection,
                    List.of(
                            BsonDocument.parse(
                                    """
                                    { "_id": { "bookNo": { "$numberLong": "7" }, "publisherId": { "$numberLong": "5" } }, "title": "z" }
                                    """)),
                    Set.of("books"));

            var stored = booksCollection
                    .find(BsonDocument.parse("{ \"title\": \"z\" }"))
                    .first();
            assertThat(stored).isNotNull();
            assertThat(stored.getDocument("_id").keySet()).containsExactly("bookNo", "publisherId");
            assertThat(stored.getDocument("_id").getInt64("bookNo").getValue()).isEqualTo(7L);
            assertThat(stored.getDocument("_id").getInt64("publisherId").getValue())
                    .isEqualTo(5L);
        }

        @Test
        void testBulkInsertMultiRow() {
            assertMutationQuery(
                    "insert into Book (id, title) values (:id1, 'x'), (:id2, 'y')",
                    q -> q.setParameter("id1", new BookId(30, 4)).setParameter("id2", new BookId(40, 5)),
                    2,
                    """
                    {
                      "insert": "books",
                      "documents": [
                        {
                          "_id": {"bookNo": {"$numberLong": "4"}, "publisherId": {"$numberLong": "30"}},
                          "title": "x"
                        },
                        {
                          "_id": {"bookNo": {"$numberLong": "5"}, "publisherId": {"$numberLong": "40"}},
                          "title": "y"
                        }
                      ]
                    }
                    """,
                    booksCollection,
                    List.of(
                            BsonDocument.parse(
                                    """
                                    { "_id": { "bookNo": { "$numberLong": "4" }, "publisherId": { "$numberLong": "30" } }, "title": "x" }
                                    """),
                            BsonDocument.parse(
                                    """
                                    { "_id": { "bookNo": { "$numberLong": "5" }, "publisherId": { "$numberLong": "40" } }, "title": "y" }
                                    """)),
                    Set.of("books"));

            var storedX = booksCollection
                    .find(BsonDocument.parse("{ \"title\": \"x\" }"))
                    .first();
            var storedY = booksCollection
                    .find(BsonDocument.parse("{ \"title\": \"y\" }"))
                    .first();
            assertThat(storedX).isNotNull();
            assertThat(storedX.getDocument("_id").getInt64("bookNo").getValue()).isEqualTo(4L);
            assertThat(storedX.getDocument("_id").getInt64("publisherId").getValue())
                    .isEqualTo(30L);
            assertThat(storedY).isNotNull();
            assertThat(storedY.getDocument("_id").getInt64("bookNo").getValue()).isEqualTo(5L);
            assertThat(storedY.getDocument("_id").getInt64("publisherId").getValue())
                    .isEqualTo(40L);
        }

        @Test
        void testDuplicateIdInsert() {
            getSessionFactoryScope().inTransaction(session -> session.persist(new Book(new BookId(9, 6), "orig")));

            assertThatThrownBy(() -> getSessionFactoryScope()
                            .inTransaction(session -> session.persist(new Book(new BookId(9, 6), "dup"))))
                    .isExactlyInstanceOf(JDBCException.class)
                    .rootCause()
                    .isInstanceOf(MongoException.class)
                    .hasMessageContaining("E11000");
        }
    }

    @Nested
    class Query implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope().inTransaction(CompositePrimaryKeyIntegrationTests.this::seedBooks);
            commandHistory.clear();
        }

        @Test
        void testWhereIdEq() {
            assertSelectionQuery(
                    "from Book b where b.id = :id",
                    Book.class,
                    q -> q.setParameter("id", new BookId(10, 2)),
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {"_id.bookNo": {"$eq": {"$numberLong": "2"}}},
                              {"_id.publisherId": {"$eq": {"$numberLong": "10"}}}
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id#bookNo": "$_id.bookNo",
                            "_id#publisherId": "$_id.publisherId",
                            "title": true,
                            "_id": 0
                          }
                        }
                      ]
                    }
                    """,
                    List.of(new Book(new BookId(10, 2), "a")),
                    Set.of("books"));
        }

        @Test
        void testWhereIdNe() {
            assertSelectionQuery(
                    "from Book b where b.id <> :id order by b.id",
                    Book.class,
                    q -> q.setParameter("id", new BookId(10, 2)),
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "$nor": [
                              {
                                "$and": [
                                  {"_id.bookNo": {"$eq": {"$numberLong": "2"}}},
                                  {"_id.publisherId": {"$eq": {"$numberLong": "10"}}}
                                ]
                              }
                            ]
                          }
                        },
                        {
                          "$sort": {
                            "_id.bookNo": 1,
                            "_id.publisherId": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#bookNo": "$_id.bookNo",
                            "_id#publisherId": "$_id.publisherId",
                            "title": true,
                            "_id": 0
                          }
                        }
                      ]
                    }
                    """,
                    List.of(new Book(new BookId(20, 1), "c"), new Book(new BookId(10, 3), "b")),
                    Set.of("books"));
        }

        @Test
        void testWhereIdLiteralTuple() {
            assertSelectionQuery(
                    "from Book b where b.id = (2, 10)",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {"_id.bookNo": {"$eq": {"$numberInt": "2"}}},
                              {"_id.publisherId": {"$eq": {"$numberInt": "10"}}}
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id#bookNo": "$_id.bookNo",
                            "_id#publisherId": "$_id.publisherId",
                            "title": true,
                            "_id": 0
                          }
                        }
                      ]
                    }
                    """,
                    List.of(new Book(new BookId(10, 2), "a")),
                    Set.of("books"));
        }

        @Test
        void testWhereIdIn() {
            assertSelectionQuery(
                    "from Book b where b.id in (:ids) order by b.id",
                    Book.class,
                    q -> q.setParameter("ids", List.of(new BookId(10, 2), new BookId(20, 1))),
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "$or": [
                              {
                                "$and": [
                                  {"_id.bookNo": {"$eq": {"$numberLong": "2"}}},
                                  {"_id.publisherId": {"$eq": {"$numberLong": "10"}}}
                                ]
                              },
                              {
                                "$and": [
                                  {"_id.bookNo": {"$eq": {"$numberLong": "1"}}},
                                  {"_id.publisherId": {"$eq": {"$numberLong": "20"}}}
                                ]
                              }
                            ]
                          }
                        },
                        {
                          "$sort": {
                            "_id.bookNo": 1,
                            "_id.publisherId": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#bookNo": "$_id.bookNo",
                            "_id#publisherId": "$_id.publisherId",
                            "title": true,
                            "_id": 0
                          }
                        }
                      ]
                    }
                    """,
                    List.of(new Book(new BookId(20, 1), "c"), new Book(new BookId(10, 2), "a")),
                    Set.of("books"));
        }

        @Test
        void testWhereComponent() {
            assertSelectionQuery(
                    "from Book b where b.id.publisherId = 10 order by b.id",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "_id.publisherId": {"$eq": {"$numberInt": "10"}}
                          }
                        },
                        {
                          "$sort": {
                            "_id.bookNo": 1,
                            "_id.publisherId": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#bookNo": "$_id.bookNo",
                            "_id#publisherId": "$_id.publisherId",
                            "title": true,
                            "_id": 0
                          }
                        }
                      ]
                    }
                    """,
                    List.of(new Book(new BookId(10, 2), "a"), new Book(new BookId(10, 3), "b")),
                    Set.of("books"));
        }

        @Test
        void testSelectId() {
            assertSelectionQuery(
                    "select b.id from Book b order by b.id",
                    BookId.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "_id.bookNo": 1,
                            "_id.publisherId": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#bookNo": "$_id.bookNo",
                            "_id#publisherId": "$_id.publisherId",
                            "_id": 0
                          }
                        }
                      ]
                    }
                    """,
                    List.of(new BookId(20, 1), new BookId(10, 2), new BookId(10, 3)),
                    Set.of("books"));
        }

        @Test
        void testSelectComponent() {
            assertSelectionQuery(
                    "select b.id.bookNo from Book b order by b.id",
                    Long.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "_id.bookNo": 1,
                            "_id.publisherId": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#bookNo": "$_id.bookNo",
                            "_id": 0
                          }
                        }
                      ]
                    }
                    """,
                    List.of(1L, 2L, 3L),
                    Set.of("books"));
        }

        @Test
        void testOrderById() {
            assertSelectionQuery(
                    "from Book b order by b.id",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "_id.bookNo": 1,
                            "_id.publisherId": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#bookNo": "$_id.bookNo",
                            "_id#publisherId": "$_id.publisherId",
                            "title": true,
                            "_id": 0
                          }
                        }
                      ]
                    }
                    """,
                    List.of(
                            new Book(new BookId(20, 1), "c"),
                            new Book(new BookId(10, 2), "a"),
                            new Book(new BookId(10, 3), "b")),
                    Set.of("books"));
        }

        @Test
        void testFindById() {
            getSessionFactoryScope().inTransaction(session -> {
                var book = session.find(Book.class, new BookId(10, 2));
                assertThat(book).isNotNull();
                assertThat(book.title).isEqualTo("a");
            });
        }

        @Test
        void testGetReferenceById() {
            getSessionFactoryScope().inTransaction(session -> {
                var book = session.getReference(Book.class, new BookId(10, 2));
                Hibernate.initialize(book);
                var unproxied = (Book) Hibernate.unproxy(book);
                assertThat(unproxied.id.publisherId).isEqualTo(10L);
                assertThat(unproxied.id.bookNo).isEqualTo(2L);
                assertThat(unproxied.title).isEqualTo("a");
            });
        }

        @Test
        void testCanonicalIdOrderRecordVsClass() {
            getSessionFactoryScope()
                    .inTransaction(session -> session.persist(new RecordBook(new RecordBookId(11, 4), "r")));
            getSessionFactoryScope()
                    .inTransaction(session -> session.persist(new Book(new BookId(11, 4), "same-order")));

            var recordStored = recordBooksCollection
                    .find(BsonDocument.parse("{ \"title\": \"r\" }"))
                    .first();
            var classStored = booksCollection
                    .find(BsonDocument.parse("{ \"title\": \"same-order\" }"))
                    .first();
            assertThat(recordStored).isNotNull();
            assertThat(classStored).isNotNull();
            assertThat(recordStored.getDocument("_id").keySet()).containsExactly("bookNo", "publisherId");
            assertThat(classStored.getDocument("_id").keySet()).containsExactly("bookNo", "publisherId");
            assertThat(recordStored.getDocument("_id").getInt64("bookNo").getValue())
                    .isEqualTo(4L);
            assertThat(recordStored.getDocument("_id").getInt64("publisherId").getValue())
                    .isEqualTo(11L);
        }
    }

    @Nested
    class Mutation implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope().inTransaction(CompositePrimaryKeyIntegrationTests.this::seedBooks);
            commandHistory.clear();
        }

        @Test
        void testManagedUpdate() {
            getSessionFactoryScope().inTransaction(session -> {
                var book = session.find(Book.class, new BookId(10, 2));
                commandHistory.clear();
                book.title = "a2";
                session.flush();
                assertActualCommandsInOrder(
                        BsonDocument.parse(
                                """
                                {
                                  "update": "books",
                                  "updates": [
                                    {
                                      "q": {
                                        "$and": [
                                          {"_id.bookNo": {"$eq": {"$numberLong": "2"}}},
                                          {"_id.publisherId": {"$eq": {"$numberLong": "10"}}}
                                        ]
                                      },
                                      "u": {"$set": {"title": "a2"}},
                                      "multi": true
                                    }
                                  ]
                                }
                                """));
            });

            var stored = booksCollection
                    .find(BsonDocument.parse("{ \"title\": \"a2\" }"))
                    .first();
            assertThat(stored).isNotNull();
        }

        @Test
        void testManagedDelete() {
            getSessionFactoryScope().inTransaction(session -> {
                var book = session.find(Book.class, new BookId(10, 2));
                commandHistory.clear();
                session.remove(book);
                session.flush();
                assertActualCommandsInOrder(
                        BsonDocument.parse(
                                """
                                {
                                  "delete": "books",
                                  "deletes": [
                                    {
                                      "limit": 0,
                                      "q": {
                                        "$and": [
                                          {"_id.bookNo": {"$eq": {"$numberLong": "2"}}},
                                          {"_id.publisherId": {"$eq": {"$numberLong": "10"}}}
                                        ]
                                      }
                                    }
                                  ]
                                }
                                """));
            });

            getSessionFactoryScope().inTransaction(session -> assertThat(session.find(Book.class, new BookId(10, 2)))
                    .isNull());
        }

        @Test
        void testBulkUpdateById() {
            assertMutationQuery(
                    "update Book b set b.title = 'y' where b.id = :id",
                    q -> q.setParameter("id", new BookId(10, 2)),
                    1,
                    """
                    {
                      "update": "books",
                      "updates": [
                        {
                          "q": {
                            "$and": [
                              {"_id.bookNo": {"$eq": {"$numberLong": "2"}}},
                              {"_id.publisherId": {"$eq": {"$numberLong": "10"}}}
                            ]
                          },
                          "u": {"$set": {"title": "y"}},
                          "multi": true
                        }
                      ]
                    }
                    """,
                    booksCollection,
                    List.of(
                            BsonDocument.parse(
                                    """
                                    { "_id": { "bookNo": { "$numberLong": "2" }, "publisherId": { "$numberLong": "10" } }, "title": "y" }
                                    """),
                            BsonDocument.parse(
                                    """
                                    { "_id": { "bookNo": { "$numberLong": "3" }, "publisherId": { "$numberLong": "10" } }, "title": "b" }
                                    """),
                            BsonDocument.parse(
                                    """
                                    { "_id": { "bookNo": { "$numberLong": "1" }, "publisherId": { "$numberLong": "20" } }, "title": "c" }
                                    """)),
                    Set.of("books"));
        }

        @Test
        void testBulkDeleteById() {
            assertMutationQuery(
                    "delete Book b where b.id = :id",
                    q -> q.setParameter("id", new BookId(10, 2)),
                    1,
                    """
                    {
                      "delete": "books",
                      "deletes": [
                        {
                          "limit": 0,
                          "q": {
                            "$and": [
                              {"_id.bookNo": {"$eq": {"$numberLong": "2"}}},
                              {"_id.publisherId": {"$eq": {"$numberLong": "10"}}}
                            ]
                          }
                        }
                      ]
                    }
                    """,
                    booksCollection,
                    List.of(
                            BsonDocument.parse(
                                    """
                                    { "_id": { "bookNo": { "$numberLong": "3" }, "publisherId": { "$numberLong": "10" } }, "title": "b" }
                                    """),
                            BsonDocument.parse(
                                    """
                                    { "_id": { "bookNo": { "$numberLong": "1" }, "publisherId": { "$numberLong": "20" } }, "title": "c" }
                                    """)),
                    Set.of("books"));
        }

        @Test
        void testVersionedManagedUpdate() {
            getSessionFactoryScope()
                    .inTransaction(session -> session.persist(new VersionedBook(new BookId(15, 6), "v0")));

            getSessionFactoryScope().inTransaction(session -> {
                var book = session.find(VersionedBook.class, new BookId(15, 6));
                commandHistory.clear();
                book.title = "v1";
                session.flush();
                assertActualCommandsInOrder(
                        BsonDocument.parse(
                                """
                                {
                                  "update": "versioned_books",
                                  "updates": [
                                    {
                                      "q": {
                                        "$and": [
                                          {"_id.bookNo": {"$eq": {"$numberLong": "6"}}},
                                          {"_id.publisherId": {"$eq": {"$numberLong": "15"}}},
                                          {"version": {"$eq": {"$numberLong": "0"}}}
                                        ]
                                      },
                                      "u": {"$set": {"title": "v1", "version": {"$numberLong": "1"}}},
                                      "multi": true
                                    }
                                  ]
                                }
                                """));
            });

            var stored = versionedBooksCollection
                    .find(BsonDocument.parse("{ \"title\": \"v1\" }"))
                    .first();
            assertThat(stored).isNotNull();
            assertThat(stored.getInt64("version").getValue()).isEqualTo(1L);
        }
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {
        @Test
        void testIdClassRejected() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithIdClass.class)
                            .buildMetadata())
                    .hasMessageContaining("TODO-HIBERNATE-235");
        }

        @Test
        void testNonScalarIdComponentRejected() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(ItemWithNestedEmbeddableIdComponent.class)
                            .buildMetadata())
                    .hasMessageContaining("TODO-HIBERNATE-236");
        }

        @Test
        void testMapsIdRejected() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(Owner.class)
                            .addAnnotatedClass(OwnedWithMapsId.class)
                            .buildMetadata())
                    .hasMessageContaining("TODO-HIBERNATE-237");
        }

        @Test
        void testAssociationIdComponentRejected() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(Pub.class)
                            .addAnnotatedClass(BookWithAssociationIdComponent.class)
                            .buildMetadata())
                    .hasMessageContaining("TODO-HIBERNATE-237");
        }

        @Test
        void testOrderingWholeIdUnsupported() {
            assertSelectQueryFailure(
                    "from Book b where b.id > :id",
                    Book.class,
                    q -> q.setParameter("id", new BookId(50, 8)),
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-211 https://jira.mongodb.org/browse/HIBERNATE-211");
        }

        @Entity
        @IdClass(ItemId.class)
        @Table(name = "item_with_id_class")
        static class ItemWithIdClass {
            @Id
            long publisherId;

            @Id
            long bookNo;
        }

        static class ItemId implements Serializable {
            @Serial
            private static final long serialVersionUID = 1L;

            long publisherId;
            long bookNo;

            ItemId() {}

            ItemId(long publisherId, long bookNo) {
                this.publisherId = publisherId;
                this.bookNo = bookNo;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof ItemId itemId && publisherId == itemId.publisherId && bookNo == itemId.bookNo;
            }

            @Override
            public int hashCode() {
                return Objects.hash(publisherId, bookNo);
            }
        }

        @Entity
        @Table(name = "item_with_nested_embeddable_id_component")
        static class ItemWithNestedEmbeddableIdComponent {
            @EmbeddedId
            IdWithNestedEmbeddableComponent id;
        }

        @Embeddable
        static class IdWithNestedEmbeddableComponent {
            Coordinates coordinates;
        }

        @Embeddable
        static class Coordinates {
            long x;
        }

        @Entity
        @Table(name = "owner")
        static class Owner {
            @Id
            long id;
        }

        @Entity
        @Table(name = "owned_with_maps_id")
        static class OwnedWithMapsId {
            @EmbeddedId
            OwnedId id;

            @MapsId("ownerId")
            @ManyToOne
            Owner owner;
        }

        @Embeddable
        static class OwnedId {
            long ownerId;
        }

        @Entity
        @Table(name = "pub")
        static class Pub {
            @Id
            long id;
        }

        @Entity
        @Table(name = "book_with_association_id_component")
        static class BookWithAssociationIdComponent {
            @EmbeddedId
            IdWithAssociationComponent id;
        }

        @Embeddable
        static class IdWithAssociationComponent {
            @ManyToOne
            Pub pub;

            long bookNo;
        }
    }

    @Embeddable
    static class BookId {
        long publisherId;
        long bookNo;

        BookId() {}

        BookId(long publisherId, long bookNo) {
            this.publisherId = publisherId;
            this.bookNo = bookNo;
        }
    }

    @Entity(name = "Book")
    @Table(name = "books")
    static class Book {
        @EmbeddedId
        BookId id;

        String title;

        Book() {}

        Book(BookId id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    @Embeddable
    record RecordBookId(long publisherId, long bookNo) {}

    @Entity(name = "RecordBook")
    @Table(name = "record_books")
    static class RecordBook {
        @EmbeddedId
        RecordBookId id;

        String title;

        RecordBook() {}

        RecordBook(RecordBookId id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    @Entity(name = "VersionedBook")
    @Table(name = "versioned_books")
    static class VersionedBook {
        @EmbeddedId
        BookId id;

        @Version
        long version;

        String title;

        VersionedBook() {}

        VersionedBook(BookId id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
