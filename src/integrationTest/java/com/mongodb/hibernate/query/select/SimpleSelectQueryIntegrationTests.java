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

import static com.mongodb.hibernate.BasicCrudIntegrationTests.Item.COLLECTION_NAME;
import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mongodb.hibernate.embeddable.StructAggregateEmbeddableIntegrationTests;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import com.mongodb.hibernate.query.Book;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.hibernate.annotations.Struct;
import org.hibernate.query.SemanticException;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DomainModel(
        annotatedClasses = {
            SimpleSelectQueryIntegrationTests.Contact.class,
            Book.class,
            SimpleSelectQueryIntegrationTests.ItemWithNestedValue.class,
            SimpleSelectQueryIntegrationTests.ItemWithDeeplyNestedValue.class,
            SimpleSelectQueryIntegrationTests.ItemWithPair.class
        })
class SimpleSelectQueryIntegrationTests extends AbstractQueryIntegrationTests {
    @Test
    void testQueryPlanCacheIsSupported() {
        getSessionFactoryScope().inTransaction(session -> assertThatCode(
                        () -> session.createSelectionQuery("from Contact", Contact.class)
                                .setQueryPlanCacheable(true)
                                .getResultList())
                .doesNotThrowAnyException());
    }

    @Nested
    class QueryTests implements MongoServiceRegistryProducer {

        private static final List<Contact> testingContacts = List.of(
                new Contact(1, "Bob", 18, Country.USA),
                new Contact(2, "Mary", 35, Country.CANADA),
                new Contact(3, "Dylan", 7, Country.CANADA),
                new Contact(4, "Lucy", 78, Country.CANADA),
                new Contact(5, "John", 25, Country.USA));

        @BeforeEach
        void beforeEach() {
            getSessionFactoryScope().inTransaction(session -> testingContacts.forEach(session::persist));
            getTestCommandListener().clear();
        }

        private static List<Contact> getTestingContacts(int... ids) {
            return Arrays.stream(ids)
                    .mapToObj(id -> testingContacts.stream()
                            .filter(c -> c.id == id)
                            .findAny()
                            .orElseThrow(() -> fail("id does not exist: " + id)))
                    .toList();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByEq(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "country = :country" : ":country = country"),
                    Contact.class,
                    q -> q.setParameter("country", Country.USA.name()),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$eq": "USA"
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(1, 5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByNe(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "country != ?1" : "?1 != country"),
                    Contact.class,
                    q -> q.setParameter(1, Country.USA.name()),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$ne": "USA"
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(2, 3, 4),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLt(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "age < :age" : ":age > age"),
                    Contact.class,
                    q -> q.setParameter("age", 35),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "age": {
                              "$lt": 35
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(1, 3, 5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLte(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "age <= ?1" : "?1 >= age"),
                    Contact.class,
                    q -> q.setParameter(1, 35),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "age": {
                              "$lte": 35
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(1, 2, 3, 5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGt(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "age > :age" : ":age < age"),
                    Contact.class,
                    q -> q.setParameter("age", 18),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "age": {
                              "$gt": 18
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(2, 4, 5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGte(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "age >= :age" : ":age <= age"),
                    Contact.class,
                    q -> q.setParameter("age", 18),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "age": {
                              "$gte": 18
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(1, 2, 4, 5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void testComparisonByIn(boolean negated) {
            assertSelectionQuery(
                    "from Contact where age %s in (?1, :foo, 7)".formatted(negated ? "not" : ""),
                    Contact.class,
                    q -> q.setParameter(1, 18).setParameter("foo", 35),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "age": {
                              "$%s": [18, 35, 7]
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }"""
                            .formatted(negated ? "nin" : "in"),
                    negated ? getTestingContacts(4, 5) : getTestingContacts(1, 2, 3),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void testComparisonByInEmpty(boolean negated) {
            assertSelectionQuery(
                    "from Contact where age %s in ()".formatted(negated ? "not" : ""),
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "age": {
                              "$%s": []
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }"""
                            .formatted(negated ? "nin" : "in"),
                    negated ? testingContacts : List.of(),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testAndFilter() {
            assertSelectionQuery(
                    "from Contact where country = ?1 and age > ?2",
                    Contact.class,
                    q -> q.setParameter(1, Country.CANADA.name()).setParameter(2, 18),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "country": {
                                  "$eq": "CANADA"
                                }
                              },
                              {
                                "age": {
                                  "$gt": 18
                                }
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(2, 4),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testOrFilter() {
            assertSelectionQuery(
                    "from Contact where country = :country or age > :age",
                    Contact.class,
                    q -> q.setParameter("country", Country.CANADA.name()).setParameter("age", 18),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$or": [
                              {
                                "country": {
                                  "$eq": "CANADA"
                                }
                              },
                              {
                                "age": {
                                  "$gt": 18
                                }
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(2, 3, 4, 5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testSingleNegation() {
            assertSelectionQuery(
                    "from Contact where age > 18 and not (country = 'USA')",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "age": {
                                  "$gt": 18
                                }
                              },
                              {
                                "$nor": [
                                  {
                                    "country": {
                                      "$eq": "USA"
                                    }
                                  }
                                ]
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(2, 4),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testSingleNegationWithAnd() {
            assertSelectionQuery(
                    "from Contact where not (country = 'USA' and age > 18)",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$nor": [
                              {
                                "$and": [
                                  {
                                    "country": {
                                      "$eq": "USA"
                                    }
                                  },
                                  {
                                    "age": {
                                      "$gt": {
                                        "$numberInt": "18"
                                      }
                                    }
                                  }
                                ]
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(1, 2, 3, 4),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testSingleNegationWithOr() {
            assertSelectionQuery(
                    "from Contact where not (country = 'USA' or age > 18)",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$nor": [
                              {
                                "$or": [
                                  {
                                    "country": {
                                      "$eq": "USA"
                                    }
                                  },
                                  {
                                    "age": {
                                      "$gt": {
                                        "$numberInt": "18"
                                      }
                                    }
                                  }
                                ]
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(3),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testSingleNegationWithAndOr() {
            assertSelectionQuery(
                    "from Contact where not (country = 'USA' and age > 18 or age < 25)",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$nor": [
                              {
                                "$or": [
                                  {
                                    "$and": [
                                      {
                                        "country": {
                                          "$eq": "USA"
                                        }
                                      },
                                      {
                                        "age": {
                                          "$gt": {
                                            "$numberInt": "18"
                                          }
                                        }
                                      }
                                    ]
                                  },
                                  {
                                    "age": {
                                      "$lt": {
                                        "$numberInt": "25"
                                      }
                                    }
                                  }
                                ]
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(2, 4),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testDoubleNegation() {
            assertSelectionQuery(
                    "from Contact where age > 18 and not ( not (country = 'USA') )",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "age": {
                                  "$gt": 18
                                }
                              },
                              {
                                "$nor": [
                                  {
                                    "$nor": [
                                      {
                                        "country": {
                                          "$eq": "USA"
                                        }
                                      }
                                    ]
                                  }
                                ]
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testProjectWithoutAlias() {
            assertSelectionQuery(
                    "select name, age from Contact where country = :country",
                    Object[].class,
                    q -> q.setParameter("country", Country.CANADA.name()),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$eq": "CANADA"
                            }
                          }
                        },
                        {
                          "$project": {
                            "name": true,
                            "age": true
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {"Mary", 35}, new Object[] {"Dylan", 7}, new Object[] {"Lucy", 78}),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testProjectUsingAlias() {
            assertSelectionQuery(
                    "select c.name, c.age from Contact as c where c.country = :country",
                    Object[].class,
                    q -> q.setParameter("country", Country.CANADA.name()),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$eq": "CANADA"
                            }
                          }
                        },
                        {
                          "$project": {
                            "name": true,
                            "age": true
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {"Mary", 35}, new Object[] {"Dylan", 7}, new Object[] {"Lucy", 78}),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testProjectUsingWrongAlias() {
            assertSelectQueryFailure(
                    "select k.name, c.age from Contact as c where c.country = :country",
                    Contact.class,
                    SemanticException.class,
                    "Could not interpret path expression '%s'",
                    "k.name");
        }
    }

    /**
     * MongoDB's comparison operators treat documents with a {@code null}-valued field and documents missing the field
     * as equivalent (see <a href=
     * "https://www.mongodb.com/docs/manual/reference/bson-type-comparison-order/#non-existent-fields">non-existent
     * fields</a>). This nested class verifies that comparing a field against a {@code null} parameter exposes those
     * native MQL semantics rather than Hibernate's SQL ternary logic.
     */
    @Nested
    class NullComparison implements MongoServiceRegistryProducer {

        private static final List<Contact> testingContacts = List.of(
                new Contact(1, "Bob", 18, Country.USA),
                new Contact(2, "Mary", 35, null),
                new Contact(3, "Dylan", 7, Country.CANADA),
                new Contact(4, "Lucy", 78, null));

        @BeforeEach
        void beforeEach() {
            getSessionFactoryScope().inTransaction(session -> testingContacts.forEach(session::persist));
            getTestCommandListener().clear();
        }

        @Test
        void testEqNullParameter() {
            assertSelectionQuery(
                    "from Contact where country = :country",
                    Contact.class,
                    q -> q.setParameter("country", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$eq": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country == null),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testNeNullParameter() {
            assertSelectionQuery(
                    "from Contact where country != :country",
                    Contact.class,
                    q -> q.setParameter("country", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$ne": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country != null),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testGtNullParameter() {
            assertSelectionQuery(
                    "from Contact where country > :country",
                    Contact.class,
                    q -> q.setParameter("country", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$gt": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    List.of(),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testGteNullParameter() {
            assertSelectionQuery(
                    "from Contact where country >= :country",
                    Contact.class,
                    q -> q.setParameter("country", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$gte": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country == null),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testLtNullParameter() {
            assertSelectionQuery(
                    "from Contact where country < :country",
                    Contact.class,
                    q -> q.setParameter("country", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$lt": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    List.of(),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testLteNullParameter() {
            assertSelectionQuery(
                    "from Contact where country <= :country",
                    Contact.class,
                    q -> q.setParameter("country", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$lte": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country == null),
                    Set.of(Contact.COLLECTION_NAME));
        }

        private static List<Contact> getTestingContacts(final Predicate<Contact> filter) {
            return testingContacts.stream().filter(filter).toList();
        }
    }

    /**
     * HQL {@code is null} / {@code is not null} are translated to MongoDB's native equality against {@code null}, so
     * {@code is null} matches both null-valued and missing fields and {@code is not null} matches the complement
     * (existing and non-null). This is consistent with the MQL semantics adopted in HIBERNATE-74.
     */
    @Nested
    class IsNull implements MongoServiceRegistryProducer {

        private static final List<Contact> testingContacts = List.of(
                new Contact(1, "Bob", 18, Country.USA),
                new Contact(2, "Mary", 35, null),
                new Contact(3, "Dylan", 7, Country.CANADA),
                new Contact(4, "Lucy", 78, null));

        @BeforeEach
        void beforeEach() {
            getSessionFactoryScope().inTransaction(session -> testingContacts.forEach(session::persist));
            getTestCommandListener().clear();
        }

        private static List<Contact> getTestingContacts(final Predicate<Contact> filter) {
            return testingContacts.stream().filter(filter).toList();
        }

        @Test
        void testIsNull() {
            assertSelectionQuery(
                    "from Contact where country is null",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$eq": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country == null),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testIsNullOnAliasQualifiedPath() {
            // Goes through BasicValuedPathInterpretation rather than a bare ColumnReference
            assertSelectionQuery(
                    "from Contact c where c.country is null",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$eq": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country == null),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testIsNotNull() {
            assertSelectionQuery(
                    "from Contact where country is not null",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "country": {
                              "$ne": null
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country != null),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testIsNullAndOtherPredicate() {
            assertSelectionQuery(
                    "from Contact where country is null and age > 30",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "country": {
                                  "$eq": null
                                }
                              },
                              {
                                "age": {
                                  "$gt": 30
                                }
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country == null && contact.age > 30),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testNotIsNull() {
            assertSelectionQuery(
                    "from Contact where not (country is null)",
                    Contact.class,
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$nor": [
                              {
                                "country": {
                                  "$eq": null
                                }
                              }
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "age": true,
                            "country": true,
                            "name": true
                          }
                        }
                      ]
                    }""",
                    getTestingContacts(contact -> contact.country != null),
                    Set.of(Contact.COLLECTION_NAME));
        }
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {
        @Test
        void testIsNullOnParameterNotSupported() {
            assertSelectQueryFailure(
                    "from Contact where :param is null",
                    Contact.class,
                    q -> q.setParameter("param", "x"),
                    FeatureNotSupportedException.class,
                    "Only the following nullness predicates are supported: field is [not] null");
        }
    }

    @Nested
    class QueryLiteralTests implements MongoServiceRegistryProducer {

        private Book testingBook;

        @BeforeEach
        void beforeEach() {
            testingBook = new Book();
            testingBook.title = "Holy Bible";
            testingBook.outOfStock = true;
            testingBook.publishYear = 1995;
            testingBook.isbn13 = 9780310904168L;
            testingBook.discount = 0.25;
            testingBook.price = new BigDecimal("123.50");
            getSessionFactoryScope().inTransaction(session -> session.persist(testingBook));

            getTestCommandListener().clear();
        }

        @Test
        void testBoolean() {
            assertSelectionQuery(
                    "from Book where outOfStock = true",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "outOfStock": {
                              "$eq": true
                            }
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
                    List.of(testingBook),
                    Set.of(Book.COLLECTION_NAME));
        }

        @Test
        void testInteger() {
            assertSelectionQuery(
                    "from Book where publishYear = 1995",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "publishYear": {
                              "$eq": 1995
                            }
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
                    List.of(testingBook),
                    Set.of(Book.COLLECTION_NAME));
        }

        @Test
        void testLong() {
            assertSelectionQuery(
                    "from Book where isbn13 = 9780310904168L",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "isbn13": {
                              "$eq": 9780310904168
                            }
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
                    List.of(testingBook),
                    Set.of(Book.COLLECTION_NAME));
        }

        @Test
        void testDouble() {
            assertSelectionQuery(
                    "from Book where discount = 0.25D",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "discount": {
                              "$eq": 0.25
                            }
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
                    List.of(testingBook),
                    Set.of(Book.COLLECTION_NAME));
        }

        @Test
        void testString() {
            assertSelectionQuery(
                    "from Book where title = 'Holy Bible'",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "title": {
                              "$eq": "Holy Bible"
                            }
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
                    List.of(testingBook),
                    Set.of(Book.COLLECTION_NAME));
        }

        @Test
        void testBigDecimal() {
            assertSelectionQuery(
                    "from Book where price = 123.50BD",
                    Book.class,
                    """
                    {
                      "aggregate": "books",
                      "pipeline": [
                        {
                          "$match": {
                            "price": {
                              "$eq": {
                                "$numberDecimal": "123.50"
                              }
                            }
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
                    List.of(testingBook),
                    Set.of(Book.COLLECTION_NAME));
        }
    }

    @Entity(name = "Contact")
    @Table(name = Contact.COLLECTION_NAME)
    static class Contact {

        static final String COLLECTION_NAME = "contacts";

        @Id
        int id;

        String name;
        int age;
        String country;

        Contact() {}

        Contact(int id, String name, int age, Country country) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.country = country == null ? null : country.name();
        }
    }

    enum Country {
        USA,
        CANADA
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

    @Embeddable
    @Struct(name = "OuterStruct")
    static class OuterStruct {
        InnerStruct inner;

        OuterStruct() {}

        OuterStruct(InnerStruct inner) {
            this.inner = inner;
        }
    }

    @Embeddable
    @Struct(name = "InnerStruct")
    static class InnerStruct {
        int a;

        InnerStruct() {}

        InnerStruct(int a) {
            this.a = a;
        }
    }

    @Entity(name = "ItemWithDeeplyNestedValue")
    @Table(name = COLLECTION_NAME)
    static class ItemWithDeeplyNestedValue {
        @Id
        int id;

        OuterStruct outer;

        ItemWithDeeplyNestedValue() {}

        ItemWithDeeplyNestedValue(int id, OuterStruct outer) {
            this.id = id;
            this.outer = outer;
        }
    }

    @Nested
    class StructAggregateEmbeddablePathExpressionTests implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new ItemWithNestedValue(1, new StructAggregateEmbeddableIntegrationTests.Single(0)));
                session.persist(new ItemWithNestedValue(2, new StructAggregateEmbeddableIntegrationTests.Single(2)));
            });
            getTestCommandListener().clear();
        }

        @Test
        void testStructAggregateEmbeddablePathExpressionComparison() {
            assertSelectionQuery(
                    "from ItemWithNestedValue where nested.a = 0",
                    ItemWithNestedValue.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$match": {
                            "nested.a": {
                              "$eq": 0
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "nested": true
                          }
                        }
                      ],
                      "cursor": {}
                    }""",
                    resultList -> assertThat(resultList).singleElement().satisfies(item -> assertThat(item.nested.a)
                            .isEqualTo(0)),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void testStructAggregateEmbeddablePathExpressionSelection() {
            assertSelectionQuery(
                    "select nested.a from ItemWithNestedValue",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "nested#a": "$nested.a"
                          }
                        }
                      ],
                      "cursor": {}
                    }""",
                    List.of(0, 2),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void testStructAggregateEmbeddablePathExpressionSelectAndFilter() {
            assertSelectionQuery(
                    "select nested.a from ItemWithNestedValue where nested.a = 2",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$match": {
                            "nested.a": {
                              "$eq": 2
                            }
                          }
                        },
                        {
                          "$project": {
                            "nested#a": "$nested.a"
                          }
                        }
                      ],
                      "cursor": {}
                    }""",
                    List.of(2),
                    Set.of(COLLECTION_NAME));
        }
    }

    @Nested
    class StructAggregateEmbeddableMultiFieldPathExpressionTests implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new ItemWithPair(1, new Pair(10, 20)));
                session.persist(new ItemWithPair(2, new Pair(30, 40)));
            });
            getTestCommandListener().clear();
        }

        @Test
        void testStructAggregateEmbeddableMultiFieldProjection() {
            assertSelectionQuery(
                    "select pair.a, pair.b from ItemWithPair",
                    Object[].class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "pair#a": "$pair.a",
                            "pair#b": "$pair.b"
                          }
                        }
                      ],
                      "cursor": {}
                    }""",
                    resultList -> assertThat(resultList)
                            .satisfiesExactlyInAnyOrder(
                                    row -> {
                                        assertThat(((Object[]) row)[0]).isEqualTo(10);
                                        assertThat(((Object[]) row)[1]).isEqualTo(20);
                                    },
                                    row -> {
                                        assertThat(((Object[]) row)[0]).isEqualTo(30);
                                        assertThat(((Object[]) row)[1]).isEqualTo(40);
                                    }),
                    Set.of(COLLECTION_NAME));
        }
    }

    @Nested
    class StructAggregateEmbeddableTwoLevelPathExpressionTests implements MongoServiceRegistryProducer {

        @BeforeEach
        void seed() {
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new ItemWithDeeplyNestedValue(1, new OuterStruct(new InnerStruct(1))));
                session.persist(new ItemWithDeeplyNestedValue(2, new OuterStruct(new InnerStruct(5))));
            });
            getTestCommandListener().clear();
        }

        @Test
        void testStructAggregateEmbeddableTwoLevelPathExpression() {
            assertSelectionQuery(
                    "from ItemWithDeeplyNestedValue where outer.inner.a = 1",
                    ItemWithDeeplyNestedValue.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$match": {
                            "outer.inner.a": {
                              "$eq": 1
                            }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "outer": true
                          }
                        }
                      ],
                      "cursor": {}
                    }""",
                    resultList -> assertThat(resultList)
                            .singleElement()
                            .satisfies(item -> assertThat(item.outer.inner.a).isEqualTo(1)),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void testStructAggregateEmbeddableTwoLevelPathExpressionSelection() {
            assertSelectionQuery(
                    "select outer.inner.a from ItemWithDeeplyNestedValue",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "outer#inner#a": "$outer.inner.a"
                          }
                        }
                      ],
                      "cursor": {}
                    }""",
                    List.of(1, 5),
                    Set.of(COLLECTION_NAME));
        }
    }
}
