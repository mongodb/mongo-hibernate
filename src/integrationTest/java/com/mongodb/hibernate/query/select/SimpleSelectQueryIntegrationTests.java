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

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import com.mongodb.hibernate.query.Book;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.hibernate.query.SemanticException;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DomainModel(annotatedClasses = {SimpleSelectQueryIntegrationTests.Contact.class, Book.class})
class SimpleSelectQueryIntegrationTests extends AbstractQueryIntegrationTests {

    @Nested
    class QueryTests {
        @BeforeEach
        void beforeEach() {
            getSessionFactoryScope().inTransaction(session -> testingContacts.forEach(session::persist));
            getTestCommandListener().clear();
        }

        private static final List<Contact> testingContacts = List.of(
                new Contact(1, "Bob", 18, Country.USA),
                new Contact(2, "Mary", 35, Country.CANADA),
                new Contact(3, "Dylan", 7, Country.CANADA),
                new Contact(4, "Lucy", 78, Country.CANADA),
                new Contact(5, "John", 25, Country.USA),
                new Contact(6, "Alice", 40, Country.USA),
                new Contact(7, "Eve", null, null),
                new Contact(8, "Eve", null, Country.CANADA));

        private static List<Contact> getTestingContacts(int... ids) {
            return Arrays.stream(ids)
                    .mapToObj(id -> testingContacts.stream()
                            .filter(c -> c.id == id)
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                    .toList();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByEqToNonNull(boolean fieldAsLhs) {
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
                             "$and": [
                               {
                                 "country": {
                                   "$eq": "USA"
                                 }
                               },
                               {
                                 "country": {
                                   "$ne": null
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
                    getTestingContacts(1, 5, 6),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByEqToNull(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "country = :country" : ":country = country"),
                    Contact.class,
                    q -> q.setParameter("country", null),
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
                                 "country": {
                                   "$ne": null
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
                    emptyList(),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByNeToNonNull(boolean fieldAsLhs) {
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
                            "$and": [
                              {
                                "country": {
                                  "$ne": "USA"
                                }
                              },
                              {
                                "country": {
                                  "$ne": null
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
                    getTestingContacts(2, 3, 4, 8),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByNeToNull(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "country != ?1" : "?1 != country"),
                    Contact.class,
                    q -> q.setParameter(1, null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "country": {
                                  "$ne": null
                                }
                              },
                              {
                                "country": {
                                  "$ne": null
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
                    getTestingContacts(1, 2, 3, 4, 5, 6, 8),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLtNonNull(boolean fieldAsLhs) {
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
                            "$and": [
                              {
                                "age": {
                                  "$lt": 35
                                }
                              },
                              {
                                "age": {
                                  "$ne": null
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
                    getTestingContacts(1, 3, 5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLtNull(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "age < :age" : ":age > age"),
                    Contact.class,
                    q -> q.setParameter("age", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "age": {
                                  "$lt": null
                                }
                              },
                              {
                                "age": {
                                  "$ne": null
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
                    emptyList(),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLteNonNull(boolean fieldAsLhs) {
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
                            "$and": [
                              {
                                "age": {
                                  "$lte": 35
                                }
                              },
                              {
                                "age": {
                                  "$ne": null
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
                    getTestingContacts(1, 2, 3, 5),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLteNull(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "age <= ?1" : "?1 >= age"),
                    Contact.class,
                    q -> q.setParameter(1, null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "age": {
                                  "$lte": null
                                }
                              },
                              {
                                "age": {
                                  "$ne": null
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
                    emptyList(),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGtNonNull(boolean fieldAsLhs) {
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
                            "$and": [
                              {
                                "age": {
                                  "$gt": 18
                                }
                              },
                              {
                                "age": {
                                  "$ne": null
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
                    getTestingContacts(2, 4, 5, 6),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGtNull(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "age > :age" : ":age < age"),
                    Contact.class,
                    q -> q.setParameter("age", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "age": {
                                  "$gt": null
                                }
                              },
                              {
                                "age": {
                                  "$ne": null
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
                    emptyList(),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGteNonNull(boolean fieldAsLhs) {
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
                            "$and": [
                              {
                                "age": {
                                  "$gte": 18
                                }
                              },
                              {
                                "age": {
                                  "$ne": null
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
                    getTestingContacts(1, 2, 4, 5, 6),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGteNull(boolean fieldAsLhs) {
            assertSelectionQuery(
                    "from Contact where " + (fieldAsLhs ? "age >= :age" : ":age <= age"),
                    Contact.class,
                    q -> q.setParameter("age", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {
                                "age": {
                                  "$gte": null
                                }
                              },
                              {
                                "age": {
                                  "$ne": null
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
                    emptyList(),
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
                                "$and": [
                                  {
                                    "country": {
                                      "$eq": "CANADA"
                                    }
                                  },
                                  {
                                    "country": {
                                      "$ne": null
                                    }
                                  }
                                ]
                              },
                              {
                                "$and": [
                                  {
                                    "age": {
                                      "$gt": 18
                                    }
                                  },
                                  {
                                    "age": {
                                      "$ne": null
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
                                "$and": [
                                  {
                                    "country": {
                                      "$eq": "CANADA"
                                    }
                                  },
                                  {
                                    "country": {
                                      "$ne": null
                                    }
                                  }
                                ],
                              },
                              {
                                "$and": [
                                  {
                                    "age": {
                                      "$gt": 18
                                    }
                                  },
                                  {
                                    "age": {
                                      "$ne": null
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
                    getTestingContacts(2, 3, 4, 5, 6, 8),
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
                                 "$and": [
                                   {
                                     "age": {
                                       "$gt": {
                                         "$numberInt": "18"
                                       }
                                     }
                                   },
                                   {
                                     "age": {
                                       "$ne": null
                                     }
                                   }
                                 ]
                               },
                               {
                                 "$and": [
                                   {
                                     "country": {
                                       "$ne": "USA"
                                     }
                                   },
                                   {
                                     "country": {
                                       "$ne": null
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
                            "$or": [
                              {
                                "$and": [
                                  {
                                    "country": {
                                      "$ne": "USA"
                                    }
                                  },
                                  {
                                    "country": {
                                      "$ne": null
                                    }
                                  }
                                ]
                              },
                              {
                                "$and": [
                                  {
                                    "age": {
                                      "$lte": {
                                        "$numberInt": "18"
                                      }
                                    }
                                  },
                                  {
                                    "age": {
                                      "$ne": null
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
                    getTestingContacts(1, 2, 3, 4, 8),
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
                            "$and": [
                              {
                                "$and": [
                                  {
                                    "country": {
                                      "$ne": "USA"
                                    }
                                  },
                                  {
                                    "country": {
                                      "$ne": null
                                    }
                                  }
                                ]
                              },
                              {
                                "$and": [
                                  {
                                    "age": {
                                      "$lte": {
                                        "$numberInt": "18"
                                      }
                                    }
                                  },
                                  {
                                    "age": {
                                      "$ne": null
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
                            "$and": [
                              {
                                "$or": [
                                  {
                                    "$and": [
                                      {
                                        "country": {
                                          "$ne": "USA"
                                        }
                                      },
                                      {
                                        "country": {
                                          "$ne": null
                                        }
                                      }
                                    ]
                                  },
                                  {
                                    "$and": [
                                      {
                                        "age": {
                                          "$lte": {
                                            "$numberInt": "18"
                                          }
                                        }
                                      },
                                      {
                                        "age": {
                                          "$ne": null
                                        }
                                      }
                                    ]
                                  }
                                ]
                              },
                              {
                                "$and": [
                                  {
                                    "age": {
                                      "$gte": {
                                        "$numberInt": "25"
                                      }
                                    }
                                  },
                                  {
                                    "age": {
                                      "$ne": null
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
                                "$and": [
                                  {
                                    "age": {
                                      "$gt": {
                                        "$numberInt": "18"
                                      }
                                    }
                                  },
                                  {
                                    "age": {
                                      "$ne": null
                                    }
                                  }
                                ]
                              },
                              {
                                "$and": [
                                  {
                                    "country": {
                                      "$eq": "USA"
                                    }
                                  },
                                  {
                                    "country": {
                                      "$ne": null
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
                    getTestingContacts(5, 6),
                    Set.of(Contact.COLLECTION_NAME));
        }

        @Test
        void testDoubleNegationWithOr() {
            assertSelectionQuery(
                    "from Contact where not ( not ( (country = :country) OR (age = :age) ) )",
                    Contact.class,
                    q -> q.setParameter("country", "CANADA").setParameter("age", null),
                    """
                    {
                      "aggregate": "contacts",
                      "pipeline": [
                        {
                          "$match": {
                            "$or": [
                              {
                                "$and": [
                                  {
                                    "country": {
                                      "$eq": "CANADA"
                                    }
                                  },
                                  {
                                    "country": {
                                      "$ne": null
                                    }
                                  }
                                ]
                              },
                              {
                                "$and": [
                                  {
                                    "age": {
                                      "$eq": null
                                    }
                                  },
                                  {
                                    "age": {
                                      "$ne": null
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
                    getTestingContacts(2, 3, 4, 8),
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
                            "$and": [
                              {
                                "country": {
                                  "$eq": "CANADA"
                                }
                              },
                              {
                                "country": {
                                  "$ne": null
                                }
                              }
                            ]
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
                    List.of(
                            new Object[] {"Mary", 35},
                            new Object[] {"Dylan", 7},
                            new Object[] {"Lucy", 78},
                            new Object[] {"Eve", null}),
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
                            "$and": [
                              {
                                "country": {
                                  "$eq": "CANADA"
                                }
                              },
                              {
                                "country": {
                                  "$ne": null
                                }
                              }
                            ]
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
                    List.of(
                            new Object[] {"Mary", 35},
                            new Object[] {"Dylan", 7},
                            new Object[] {"Lucy", 78},
                            new Object[] {"Eve", null}),
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

    @Nested
    class FeatureNotSupportedTests {
        @Test
        void testComparisonBetweenFieldAndNonValueNotSupported1() {
            assertSelectQueryFailure(
                    "from Contact as c where c.age = c.id + 1",
                    Contact.class,
                    FeatureNotSupportedException.class,
                    "Only the following comparisons are supported: field vs literal, field vs parameter");
        }

        @Test
        void testComparisonBetweenValuesNotSupported() {
            assertSelectQueryFailure(
                    "from Contact where 1 = 1",
                    Contact.class,
                    FeatureNotSupportedException.class,
                    "Only the following comparisons are supported: field vs literal, field vs parameter");
        }

        @Test
        void testComparisonBetweenFieldsNotSupported() {
            assertSelectQueryFailure(
                    "from Contact where age = id",
                    Contact.class,
                    FeatureNotSupportedException.class,
                    "Only the following comparisons are supported: field vs literal, field vs parameter");
        }

        @Test
        void testComparisonBetweenParameterAndValueNotSupported() {
            assertSelectQueryFailure(
                    "from Contact where :param = 1",
                    Contact.class,
                    q -> q.setParameter("param", 1),
                    FeatureNotSupportedException.class,
                    "Only the following comparisons are supported: field vs literal, field vs parameter");
        }

        @Test
        void testComparisonBetweenParametersNotSupported() {
            assertSelectQueryFailure(
                    "from Contact where :param = :param",
                    Contact.class,
                    q -> q.setParameter("param", 1),
                    FeatureNotSupportedException.class,
                    "Only the following comparisons are supported: field vs literal, field vs parameter");
        }

        @Test
        void testQueryPlanCacheIsSupported() {
            getSessionFactoryScope().inTransaction(session -> assertThatCode(
                            () -> session.createSelectionQuery("from Contact", Contact.class)
                                    .setQueryPlanCacheable(true)
                                    .getResultList())
                    .doesNotThrowAnyException());
        }
    }

    private static final List<Book> testingBooks = List.of(
            new Book(1, "War and Peace", null, true, null, 0.2, new BigDecimal("123.50")),
            new Book(2, "Crime and Punishment", 1866, null),
            new Book(3, "Anna Karenina", null, false, 9780310904168L, 0.8, null),
            new Book(4, "The Brothers Karamazov", null, null, null, 0.7, null),
            new Book(5, "War and Peace", 2025, false),
            new Book(6, null, null, null));

    private static List<Book> getBooksByIds(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> testingBooks.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .toList();
    }

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> testingBooks.forEach(session::persist));
        getTestCommandListener().clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBoolean(boolean isNullLiteral) {
        var nonNullBooleanLiteralStr = "true";
        assertSelectionQuery(
                format("from Book where outOfStock = %s", isNullLiteral ? null : nonNullBooleanLiteralStr),
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
                              "$eq": %s
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
                }"""
                        .formatted(isNullLiteral ? null : nonNullBooleanLiteralStr),
                isNullLiteral ? emptyList() : getBooksByIds(1),
                Set.of(Book.COLLECTION_NAME));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testInteger(boolean isNullLiteral) {
        var nonNullIntegerLiteralStr = "1866";
        assertSelectionQuery(
                format("from Book where publishYear = %s", isNullLiteral ? null : nonNullIntegerLiteralStr),
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "publishYear": {
                              "$eq": %s
                            }
                          },
                          {
                            "publishYear": {
                              "$ne": null
                            }
                          }
                        ]
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
                }"""
                        .formatted(isNullLiteral ? null : nonNullIntegerLiteralStr),
                isNullLiteral ? emptyList() : getBooksByIds(2),
                Set.of(Book.COLLECTION_NAME));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testLong(boolean isNullLiteral) {
        var nonNullLongLiteralStr = "9780310904168";
        assertSelectionQuery(
                format("from Book where isbn13 = %s", isNullLiteral ? null : (nonNullLongLiteralStr + "L")),
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "isbn13": {
                              "$eq": %s
                            }
                          },
                          {
                            "isbn13": {
                              "$ne": null
                            }
                          }
                        ]
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
                }"""
                        .formatted(isNullLiteral ? null : nonNullLongLiteralStr),
                isNullLiteral ? emptyList() : getBooksByIds(3),
                Set.of(Book.COLLECTION_NAME));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testDouble(boolean isNullLiteral) {
        var nonNullLiteralStr = "0.5";
        assertSelectionQuery(
                format("from Book where discount > %s", isNullLiteral ? null : (nonNullLiteralStr + "D")),
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "discount": {
                              "$gt": %s
                            }
                          },
                          {
                            "discount": {
                              "$ne": null
                            }
                          }
                        ]
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
                }"""
                        .formatted(isNullLiteral ? null : nonNullLiteralStr),
                isNullLiteral ? emptyList() : getBooksByIds(3, 4),
                Set.of(Book.COLLECTION_NAME));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testString(boolean isNullLiteral) {
        var nonNullLiteralStr = "War and Peace";
        assertSelectionQuery(
                format("from Book where title = %s", isNullLiteral ? null : ("\"" + nonNullLiteralStr + "\"")),
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "title": {
                              "$eq": %s
                            }
                          },
                          {
                            "title": {
                              "$ne": null
                            }
                          }
                        ]
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
                }"""
                        .formatted(isNullLiteral ? null : ("\"" + nonNullLiteralStr + "\"")),
                isNullLiteral ? emptyList() : getBooksByIds(1, 5),
                Set.of(Book.COLLECTION_NAME));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBigDecimal(boolean isNullLiteral) {
        var nonNullLiteralStr = "123.50";
        assertSelectionQuery(
                format("from Book where price = %s", isNullLiteral ? null : (nonNullLiteralStr + "BD")),
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "price": {
                              "$eq": %s
                            }
                          },
                          {
                            "price": {
                              "$ne": null
                            }
                          }
                        ]
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
                }"""
                        .formatted(isNullLiteral ? null : "{\"$numberDecimal\": \"123.50\"}"),
                isNullLiteral ? emptyList() : getBooksByIds(1),
                Set.of(Book.COLLECTION_NAME));
    }

    @Entity(name = "Contact")
    @Table(name = Contact.COLLECTION_NAME)
    static class Contact {

        static final String COLLECTION_NAME = "contacts";

        @Id
        int id;

        String name;
        Integer age;
        String country;

        Contact() {}

        Contact(int id, String name, Integer age, Country country) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.country = country == null ? null : country.name();
        }

        @Override
        public String toString() {
            return "Contact{" + "id=" + id + '}';
        }
    }

    enum Country {
        USA,
        CANADA
    }
}
