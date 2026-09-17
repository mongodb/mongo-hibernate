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

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.Hibernate;
import org.hibernate.boot.MetadataSources;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            CompositeKeyJoinIntegrationTests.Book.class,
            CompositeKeyJoinIntegrationTests.License.class,
            CompositeKeyJoinIntegrationTests.Driver.class,
            CompositeKeyJoinIntegrationTests.Review.class
        })
class CompositeKeyJoinIntegrationTests extends AbstractQueryIntegrationTests {

    @Embeddable
    record BookId(long publisherId, long bookNo) {}

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
    record ReviewId(long publisherId, long bookNo) {}

    @Entity(name = "Review")
    @Table(name = "reviews")
    static class Review {
        @EmbeddedId
        ReviewId id;

        String comment;

        @ManyToOne
        Book book;

        Review() {}

        Review(ReviewId id, String comment) {
            this.id = id;
            this.comment = comment;
        }

        Review(ReviewId id, Book book, String comment) {
            this.id = id;
            this.book = book;
            this.comment = comment;
        }
    }

    @Embeddable
    record LicenseId(long licenseNo, long countryCode) {}

    @Entity(name = "Driver")
    @Table(name = "drivers")
    static class Driver {
        @EmbeddedId
        LicenseId id;

        @OneToOne(mappedBy = "driver")
        License license;

        Driver() {}

        Driver(LicenseId id) {
            this.id = id;
        }
    }

    @Entity(name = "License")
    @Table(name = "licenses")
    static class License {
        @EmbeddedId
        LicenseId id;

        @OneToOne
        Driver driver;

        License() {}

        License(LicenseId id, Driver driver) {
            this.id = id;
            this.driver = driver;
        }
    }

    @Nested
    class WholeIdEntityJoin implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new Book(new BookId(10, 2), "a"));
                session.persist(new Book(new BookId(10, 3), "b"));
                session.persist(new Book(new BookId(20, 1), "z"));
                session.persist(
                        new Review(new ReviewId(10, 2), session.getReference(Book.class, new BookId(10, 2)), "a"));
                session.persist(
                        new Review(new ReviewId(10, 9), session.getReference(Book.class, new BookId(10, 9)), "x"));
                session.persist(
                        new Review(new ReviewId(20, 1), session.getReference(Book.class, new BookId(20, 1)), "c"));
            });
        }

        @Test
        void testWholeIdEquijoin() {
            assertSelectionQuery(
                    "SELECT b.id, r.id FROM Book b JOIN Review r ON b.id = r.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "reviews",
                            "let": {
                              "v0_b1_0__id_publisherId": "$_id.publisherId",
                              "v1_b1_0__id_bookNo": "$_id.bookNo"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_b1_0__id_publisherId", "$_id.publisherId" ] },
                                      { "$eq": [ "$$v1_b1_0__id_bookNo", "$_id.bookNo" ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#r1_0"
                          }
                        },
                        { "$unwind": "$#r1_0" },
                        {
                          "$project": {
                            "_id#publisherId": "$_id.publisherId",
                            "_id#bookNo": "$_id.bookNo",
                            "r1_0#_id#publisherId": "$#r1_0._id.publisherId",
                            "r1_0#_id#bookNo": "$#r1_0._id.bookNo",
                            "_id": 0
                          }
                        }
                      ]
                    }""",
                    List.of(
                            new Object[] {new BookId(10, 2), new ReviewId(10, 2)},
                            new Object[] {new BookId(20, 1), new ReviewId(20, 1)}),
                    Set.of("books", "reviews"));
        }

        @Test
        void testLeftOuterWholeIdEquijoin() {
            assertSelectionQuery(
                    "SELECT b.id, r.id FROM Book b LEFT JOIN Review r ON b.id = r.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "reviews",
                            "let": {
                              "v0_b1_0__id_publisherId": "$_id.publisherId",
                              "v1_b1_0__id_bookNo": "$_id.bookNo"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_b1_0__id_publisherId", "$_id.publisherId" ] },
                                      { "$eq": [ "$$v1_b1_0__id_bookNo", "$_id.bookNo" ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#r1_0"
                          }
                        },
                        {
                          "$unwind": {
                            "path": "$#r1_0",
                            "preserveNullAndEmptyArrays": true
                          }
                        },
                        {
                          "$project": {
                            "_id#publisherId": "$_id.publisherId",
                            "_id#bookNo": "$_id.bookNo",
                            "r1_0#_id#publisherId": "$#r1_0._id.publisherId",
                            "r1_0#_id#bookNo": "$#r1_0._id.bookNo",
                            "_id": 0
                          }
                        }
                      ]
                    }""",
                    List.of(
                            new Object[] {new BookId(10, 2), new ReviewId(10, 2)},
                            new Object[] {new BookId(10, 3), null},
                            new Object[] {new BookId(20, 1), new ReviewId(20, 1)}),
                    Set.of("books", "reviews"));
        }

        @Test
        void testWhereOnJoinedIdComponent() {
            assertSelectionQuery(
                    "SELECT b.id, r.id FROM Book b JOIN Review r ON b.id = r.id WHERE r.book.id.publisherId = 10",
                    Object[].class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "reviews",
                            "let": {
                              "v0_b1_0__id_publisherId": "$_id.publisherId",
                              "v1_b1_0__id_bookNo": "$_id.bookNo"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_b1_0__id_publisherId", "$_id.publisherId" ] },
                                      { "$eq": [ "$$v1_b1_0__id_bookNo", "$_id.bookNo" ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#r1_0"
                          }
                        },
                        { "$unwind": "$#r1_0" },
                        {
                          "$match": {
                            "#r1_0.book.publisherId": { "$eq": { "$numberInt": "10" } }
                          }
                        },
                        {
                          "$project": {
                            "_id#publisherId": "$_id.publisherId",
                            "_id#bookNo": "$_id.bookNo",
                            "r1_0#_id#publisherId": "$#r1_0._id.publisherId",
                            "r1_0#_id#bookNo": "$#r1_0._id.bookNo",
                            "_id": 0
                          }
                        }
                      ]
                    }""",
                    List.<Object[]>of(new Object[] {new BookId(10, 2), new ReviewId(10, 2)}),
                    Set.of("books", "reviews"));
        }

        @Test
        void testWholeIdEquijoinPlusConjunct() {
            assertSelectionQuery(
                    "SELECT b.id, r.id FROM Book b JOIN Review r ON b.id = r.id AND b.title = r.comment",
                    Object[].class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "reviews",
                            "let": {
                              "v0_b1_0__id_publisherId": "$_id.publisherId",
                              "v1_b1_0__id_bookNo": "$_id.bookNo",
                              "v2_b1_0_title": "$title"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      {
                                        "$and": [
                                          { "$eq": [ "$$v0_b1_0__id_publisherId", "$_id.publisherId" ] },
                                          { "$eq": [ "$$v1_b1_0__id_bookNo", "$_id.bookNo" ] }
                                        ]
                                      },
                                      { "$eq": [ "$$v2_b1_0_title", "$comment" ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#r1_0"
                          }
                        },
                        { "$unwind": "$#r1_0" },
                        {
                          "$project": {
                            "_id#publisherId": "$_id.publisherId",
                            "_id#bookNo": "$_id.bookNo",
                            "r1_0#_id#publisherId": "$#r1_0._id.publisherId",
                            "r1_0#_id#bookNo": "$#r1_0._id.bookNo",
                            "_id": 0
                          }
                        }
                      ]
                    }""",
                    List.<Object[]>of(new Object[] {new BookId(10, 2), new ReviewId(10, 2)}),
                    Set.of("books", "reviews"));
        }

        @Test
        void testWholeIdOrderingComparisonOnJoin() {
            assertSelectQueryFailure(
                    "SELECT b.id, r.id FROM Book b JOIN Review r ON b.id > r.id",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-211");
        }
    }

    @Nested
    class AssociationForeignKey implements MongoServiceRegistryProducer {

        @Test
        void testManyToOneCompositeForeignKeyInsert() {
            commandHistory.clear();
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new Book(new BookId(10, 2), "a"));
                var book = session.getReference(Book.class, new BookId(10, 2));
                session.persist(new Review(new ReviewId(30, 4), book, "r"));
                session.flush();
                assertActualCommandsInOrder(
                        BsonDocument.parse(
                                """
                                {
                                  "insert": "books",
                                  "documents": [
                                    { "_id": {"bookNo": {"$numberLong": "2"}, "publisherId": {"$numberLong": "10"}},
                                      "title": "a" }
                                  ]
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "insert": "reviews",
                                  "documents": [
                                    { "_id": {"bookNo": {"$numberLong": "4"}, "publisherId": {"$numberLong": "30"}},
                                      "comment": "r",
                                      "book": {"bookNo": {"$numberLong": "2"}, "publisherId": {"$numberLong": "10"}} }
                                  ]
                                }
                                """));
            });
        }
    }

    @Nested
    class AssociationJoins implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope().inTransaction(session -> {
                var bookA = new Book(new BookId(10, 2), "a");
                var bookB = new Book(new BookId(10, 3), "b");
                var bookZ = new Book(new BookId(20, 1), "z");
                session.persist(bookA);
                session.persist(bookB);
                session.persist(bookZ);
                session.persist(new Review(new ReviewId(30, 7), bookA, "r1"));
                session.persist(new Review(new ReviewId(30, 8), bookA, "r2"));
                session.persist(new Review(new ReviewId(40, 5), bookZ, "r3"));
            });
        }

        @Test
        void testAssociationJoin() {
            assertSelectionQuery(
                    "SELECT r.id, b.id FROM Review r JOIN r.book b",
                    Object[].class,
                    """
                    {
                      "aggregate": "reviews",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "books",
                            "let": {
                              "v0_r1_0_book_publisherId": "$book.publisherId",
                              "v1_r1_0_book_bookNo": "$book.bookNo"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$_id.publisherId", "$$v0_r1_0_book_publisherId" ] },
                                      { "$eq": [ "$_id.bookNo", "$$v1_r1_0_book_bookNo" ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#b1_0"
                          }
                        },
                        { "$unwind": "$#b1_0" },
                        {
                          "$project": {
                            "_id": 0,
                            "_id#bookNo": "$_id.bookNo",
                            "_id#publisherId": "$_id.publisherId",
                            "b1_0#_id#bookNo": "$#b1_0._id.bookNo",
                            "b1_0#_id#publisherId": "$#b1_0._id.publisherId"
                          }
                        }
                      ]
                    }""",
                    List.of(
                            new Object[] {new ReviewId(30, 7), new BookId(10, 2)},
                            new Object[] {new ReviewId(30, 8), new BookId(10, 2)},
                            new Object[] {new ReviewId(40, 5), new BookId(20, 1)}),
                    Set.of("reviews", "books"));
        }

        @Test
        void testJoinFetchCompositeAssociation() {
            assertSelectionQuery(
                    "FROM Review r JOIN FETCH r.book",
                    Review.class,
                    """
                    {
                      "aggregate": "reviews",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "books",
                            "let": {
                              "v0_r1_0_book_publisherId": "$book.publisherId",
                              "v1_r1_0_book_bookNo": "$book.bookNo"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$_id.publisherId", "$$v0_r1_0_book_publisherId" ] },
                                      { "$eq": [ "$_id.bookNo", "$$v1_r1_0_book_bookNo" ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#b1_0"
                          }
                        },
                        { "$unwind": "$#b1_0" },
                        {
                          "$project": {
                            "_id#publisherId": "$_id.publisherId",
                            "_id#bookNo": "$_id.bookNo",
                            "b1_0#_id#publisherId": "$#b1_0._id.publisherId",
                            "b1_0#_id#bookNo": "$#b1_0._id.bookNo",
                            "b1_0#title": "$#b1_0.title",
                            "comment": true,
                            "_id": 0
                          }
                        }
                      ]
                    }""",
                    resultList -> {
                        assertThat(resultList).hasSize(3);
                        resultList.forEach(review ->
                                assertThat(Hibernate.isInitialized(review.book)).isTrue());
                        assertThat(resultList)
                                .extracting(review -> review.id)
                                .containsExactlyInAnyOrder(
                                        new ReviewId(30, 7), new ReviewId(30, 8), new ReviewId(40, 5));
                        assertThat(resultList)
                                .extracting(review -> review.book.id)
                                .containsExactlyInAnyOrder(new BookId(10, 2), new BookId(10, 2), new BookId(20, 1));
                    },
                    Set.of("reviews", "books"));
        }
    }

    @Nested
    class OneToOneCompositeForeignKey implements MongoServiceRegistryProducer {

        @Test
        void testInverseSideHasNoForeignKeyColumns() {
            commandHistory.clear();
            getSessionFactoryScope().inTransaction(session -> {
                var driver = new Driver(new LicenseId(7, 9));
                session.persist(driver);
                session.persist(new License(new LicenseId(8, 10), driver));
                session.flush();
                assertActualCommandsInOrder(
                        BsonDocument.parse(
                                """
                                {
                                  "insert": "drivers",
                                  "documents": [
                                    { "_id": {"countryCode": {"$numberLong": "9"}, "licenseNo": {"$numberLong": "7"}} }
                                  ]
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "insert": "licenses",
                                  "documents": [
                                    { "_id": {"countryCode": {"$numberLong": "10"}, "licenseNo": {"$numberLong": "8"}},
                                      "driver": {"countryCode": {"$numberLong": "9"}, "licenseNo": {"$numberLong": "7"}} }
                                  ]
                                }
                                """));
            });
        }
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        @Test
        void testJoinColumnOnCompositeAssociationRejected() {
            assertThatThrownBy(() -> new MetadataSources()
                            .addAnnotatedClass(Book.class)
                            .addAnnotatedClass(ReviewWithJoinColumn.class)
                            .buildMetadata())
                    .isInstanceOf(FeatureNotSupportedException.class)
                    .hasMessageContaining(
                            "a @JoinColumn on an association whose target has a composite key is not supported");
        }

        @Entity
        @Table(name = "reviews_with_join_column")
        static class ReviewWithJoinColumn {
            @EmbeddedId
            ReviewId id;

            @ManyToOne
            @JoinColumns({@JoinColumn(name = "bookForeignKeyBookNo"), @JoinColumn(name = "bookForeignKeyPublisherId")})
            Book book;
        }
    }
}
