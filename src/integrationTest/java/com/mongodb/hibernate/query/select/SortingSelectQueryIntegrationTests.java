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

import static com.mongodb.hibernate.MongoTestAssertions.assertIterableEq;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.cfg.QuerySettings.DEFAULT_NULL_ORDERING;
import static org.hibernate.query.NullPrecedence.NONE;
import static org.hibernate.query.SortDirection.ASCENDING;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.util.Arrays;
import java.util.List;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DomainModel(annotatedClasses = Book.class)
class SortingSelectQueryIntegrationTests extends AbstractSelectionQueryIntegrationTests {
    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> testingBooks.forEach(session::persist));
        getTestCommandListener().clear();
    }

    private static final List<Book> testingBooks = List.of(
            new Book(1, "War and Peace", 1869, true),
            new Book(2, "Crime and Punishment", 1866, false),
            new Book(3, "Anna Karenina", 1877, false),
            new Book(4, "The Brothers Karamazov", null, false),
            new Book(5, "War and Peace", 2025, false),
            new Book(6, null, 2000, false));

    private static List<Book> getBooksByIds(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> testingBooks.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASC", "DESC"})
    void testOrderBySingleFieldWithoutTies(String sortDirection) {
        assertSelectionQuery(
                "from Book as b ORDER BY b.publishYear " + sortDirection,
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$sort": {
                        "publishYear": %d
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "discount": true,
                        "isbn13": true,
                        "outOfStock": true,
                        "price": true,
                        "publishYear": true,
                        "title": true
                      }
                    }
                  ]
                }
                """
                        .formatted(sortDirection.equals("ASC") ? 1 : -1),
                sortDirection.equals("ASC") ? getBooksByIds(4, 2, 1, 3, 6, 5) : getBooksByIds(5, 6, 3, 1, 2, 4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASC", "DESC"})
    void testOrderBySingleFieldWithTies(String sortDirection) {
        assertSelectionQuery(
                "from Book as b ORDER BY b.title " + sortDirection,
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$sort": {
                        "title": %d
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "discount": true,
                        "isbn13": true,
                        "outOfStock": true,
                        "price": true,
                        "publishYear": true,
                        "title": true
                      }
                    }
                  ]
                }
                """
                        .formatted(sortDirection.equals("ASC") ? 1 : -1),
                sortDirection.equals("ASC")
                        ? resultList -> assertThat(resultList)
                                .satisfiesAnyOf(
                                        list -> assertIterableEq(getBooksByIds(6, 3, 2, 4, 1, 5), list),
                                        list -> assertIterableEq(getBooksByIds(6, 3, 2, 4, 5, 1), list))
                        : resultList -> assertThat(resultList)
                                .satisfiesAnyOf(
                                        list -> assertIterableEq(getBooksByIds(1, 5, 4, 2, 3, 6), list),
                                        list -> assertIterableEq(getBooksByIds(5, 1, 4, 2, 3, 6), list)));
    }

    @Test
    void testOrderByMultipleFieldsWithoutTies() {
        assertSelectionQuery(
                "from Book where outOfStock = false ORDER BY title ASC, publishYear DESC, id ASC",
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "outOfStock": {
                              "$eq": false
                            }
                          },
                          {
                            "outOfStock": {
                              "$ne": null
                            }
                          }
                        ]
                      }
                    },
                    {
                      "$sort": {
                        "title": 1,
                        "publishYear": -1,
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "discount": true,
                        "isbn13": true,
                        "outOfStock": true,
                        "price": true,
                        "publishYear": true,
                        "title": true
                      }
                    }
                  ]
                }""",
                getBooksByIds(6, 3, 2, 4, 5));
    }

    @Test
    void testOrderByMultipleFieldsWithTies() {
        assertSelectionQuery(
                "from Book ORDER BY title ASC",
                Book.class,
                "{ 'aggregate': 'books', 'pipeline': [ { '$sort': { 'title': 1 } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                resultList -> assertThat(resultList)
                        .satisfiesAnyOf(
                                list -> assertIterableEq(getBooksByIds(6, 3, 2, 4, 1, 5), list),
                                list -> assertIterableEq(getBooksByIds(3, 2, 4, 5, 1, 6), list)));
    }

    @Test
    void testSortFieldByAlias() {
        assertSelectionQuery(
                "select b.title as title, b.publishYear as year from Book as b ORDER BY year DESC, title ASC",
                Object[].class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$sort": {
                        "publishYear": -1,
                        "title": 1
                      }
                    },
                    {
                      "$project": {
                        "title": true,
                        "publishYear": true
                      }
                    }
                  ]
                }
                """,
                List.of(
                        new Object[] {"War and Peace", 2025},
                        new Object[] {null, 2000},
                        new Object[] {"Anna Karenina", 1877},
                        new Object[] {"War and Peace", 1869},
                        new Object[] {"Crime and Punishment", 1866},
                        new Object[] {"The Brothers Karamazov", null}));
    }

    @Test
    void testSortFieldByOrdinalReference() {
        assertSelectionQuery(
                "select b.title as title, b.publishYear as year from Book as b ORDER BY 1 ASC, 2 DESC",
                Object[].class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$sort": {
                        "title": 1,
                        "publishYear": -1
                      }
                    },
                    {
                      "$project": {
                        "title": true,
                        "publishYear": true
                      }
                    }
                  ]
                }""",
                List.of(
                        new Object[] {null, 2000},
                        new Object[] {"Anna Karenina", 1877},
                        new Object[] {"Crime and Punishment", 1866},
                        new Object[] {"The Brothers Karamazov", null},
                        new Object[] {"War and Peace", 2025},
                        new Object[] {"War and Peace", 1869}));
    }

    @Nested
    @DomainModel(annotatedClasses = Book.class)
    @ServiceRegistry(settings = @Setting(name = DEFAULT_NULL_ORDERING, value = "first"))
    class DefaultNullPrecedenceTests extends AbstractSelectionQueryIntegrationTests {
        @Test
        void testDefaultNullPrecedenceFeatureNotSupported() {
            assertSelectQueryFailure(
                    "from Book ORDER BY publishYear",
                    Book.class,
                    FeatureNotSupportedException.class,
                    "%s does not support null precedence: NULLS FIRST",
                    MONGO_DBMS_NAME);
        }
    }

    @Nested
    class UnsupportedTests {
        @Test
        void testSortFieldNotFieldPathExpressionNotSupported() {
            assertSelectQueryFailure(
                    "from Book ORDER BY length(title)",
                    Book.class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-79 https://jira.mongodb.org/browse/HIBERNATE-79");
        }

        @ParameterizedTest
        @ValueSource(strings = {"FIRST", "LAST"})
        void testQueryNullPrecedenceFeatureNotSupported(String nullPrecedence) {
            assertSelectQueryFailure(
                    "from Book ORDER BY publishYear NULLS " + nullPrecedence,
                    Book.class,
                    FeatureNotSupportedException.class,
                    "%s does not support null precedence: NULLS " + nullPrecedence,
                    MONGO_DBMS_NAME);
        }

        @Test
        void testCaseInsensitiveSortSpecNotSupported() {
            getSessionFactoryScope().inTransaction(session -> {
                var cb = session.getCriteriaBuilder();
                var criteria = cb.createQuery(Book.class);
                var root = criteria.from(Book.class);
                criteria.select(root);
                criteria.orderBy(cb.sort(root.get("title"), ASCENDING, NONE, true));
                assertThatThrownBy(() -> session.createSelectionQuery(criteria).getResultList())
                        .isInstanceOf(FeatureNotSupportedException.class)
                        .hasMessage("TODO-HIBERNATE-79 https://jira.mongodb.org/browse/HIBERNATE-79");
            });
        }
    }

    @Nested
    class SortKeyTupleTests {
        @Test
        void testOrderBySimpleTuple() {
            assertSelectionQuery(
                    "from Book ORDER BY (publishYear, title) ASC",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "publishYear": 1,
                            "title": 1
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "discount": true,
                            "isbn13": true,
                            "outOfStock": true,
                            "price": true,
                            "publishYear": true,
                            "title": true
                          }
                        }
                      ]
                    }
                    """,
                    getBooksByIds(4, 2, 1, 3, 6, 5));
        }

        @Test
        void testOrderByNestedTuple() {
            assertSelectionQuery(
                    "from Book ORDER BY (title, (id, publishYear)) DESC",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$sort": {
                            "title": -1,
                            "_id": -1,
                            "publishYear": -1
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "discount": true,
                            "isbn13": true,
                            "outOfStock": true,
                            "price": true,
                            "publishYear": true,
                            "title": true
                          }
                        }
                      ]
                    }
                    """,
                    getBooksByIds(5, 1, 4, 2, 3, 6));
        }
    }
}
