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

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Set;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = RowValuePredicateIntegrationTests.Widget.class)
class RowValuePredicateIntegrationTests extends AbstractQueryIntegrationTests {

    @BeforeEach
    void seed() {
        getSessionFactoryScope().inTransaction(session -> {
            session.persist(new Widget(1, 10, 20));
            session.persist(new Widget(2, 10, 99));
            session.persist(new Widget(3, 30, 40));
        });
        getTestCommandListener().clear();
    }

    @Test
    void testEqParameters() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) = (:a, :b) order by w.id",
                Widget.class,
                q -> q.setParameter("a", 10L).setParameter("b", 20L),
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "a": {
                              "$eq": {
                                "$numberLong": "10"
                              }
                            }
                          },
                          {
                            "b": {
                              "$eq": {
                                "$numberLong": "20"
                              }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(1, 10, 20)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testInParameters() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) in ((:p1, :p2), (:p3, :p4)) order by w.id",
                Widget.class,
                q -> q.setParameter("p1", 10L)
                        .setParameter("p2", 20L)
                        .setParameter("p3", 30L)
                        .setParameter("p4", 40L),
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$or": [
                          {
                            "$and": [
                              {
                                "a": {
                                  "$eq": {
                                    "$numberLong": "10"
                                  }
                                }
                              },
                              {
                                "b": {
                                  "$eq": {
                                    "$numberLong": "20"
                                  }
                                }
                              }
                            ]
                          },
                          {
                            "$and": [
                              {
                                "a": {
                                  "$eq": {
                                    "$numberLong": "30"
                                  }
                                }
                              },
                              {
                                "b": {
                                  "$eq": {
                                    "$numberLong": "40"
                                  }
                                }
                              }
                            ]
                          }
                        ]
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(1, 10, 20), new Widget(3, 30, 40)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testEqLiterals() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) = (10, 20) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "a": {
                              "$eq": 10
                            }
                          },
                          {
                            "b": {
                              "$eq": 20
                            }
                          }
                        ]
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(1, 10, 20)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testNe() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) <> (10, 20) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$nor": [
                          {
                            "$and": [
                              {
                                "a": {
                                  "$eq": 10
                                }
                              },
                              {
                                "b": {
                                  "$eq": 20
                                }
                              }
                            ]
                          }
                        ]
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(2, 10, 99), new Widget(3, 30, 40)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testInMultiRow() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) in ((10, 20), (30, 40)) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$or": [
                          {
                            "$and": [
                              {
                                "a": {
                                  "$eq": 10
                                }
                              },
                              {
                                "b": {
                                  "$eq": 20
                                }
                              }
                            ]
                          },
                          {
                            "$and": [
                              {
                                "a": {
                                  "$eq": 30
                                }
                              },
                              {
                                "b": {
                                  "$eq": 40
                                }
                              }
                            ]
                          }
                        ]
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(1, 10, 20), new Widget(3, 30, 40)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testInSingleRow() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) in ((10, 20)) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "a": {
                              "$eq": 10
                            }
                          },
                          {
                            "b": {
                              "$eq": 20
                            }
                          }
                        ]
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(1, 10, 20)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testSelectEq() {
        assertSelectionQuery(
                "select (w.a, w.b) = (10, 20) from Widget w order by w.id",
                Boolean.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    { "$sort": { "_id": 1 } },
                    {
                      "$project": {
                        "#c_1": { "$and": [ { "$eq": ["$a", 10] }, { "$eq": ["$b", 20] } ] }
                      }
                    }
                  ]
                }""",
                List.of(true, false, false),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testSelectNe() {
        assertSelectionQuery(
                "select (w.a, w.b) <> (10, 20) from Widget w order by w.id",
                Boolean.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    { "$sort": { "_id": 1 } },
                    {
                      "$project": {
                        "#c_1": { "$not": [ { "$and": [ { "$eq": ["$a", 10] }, { "$eq": ["$b", 20] } ] } ] }
                      }
                    }
                  ]
                }""",
                List.of(false, true, true),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testSelectIn() {
        assertSelectionQuery(
                "select (w.a, w.b) in ((10, 20), (30, 40)) from Widget w order by w.id",
                Boolean.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    { "$sort": { "_id": 1 } },
                    {
                      "$project": {
                        "#c_1": {
                          "$or": [
                            { "$and": [ { "$eq": ["$a", 10] }, { "$eq": ["$b", 20] } ] },
                            { "$and": [ { "$eq": ["$a", 30] }, { "$eq": ["$b", 40] } ] }
                          ]
                        }
                      }
                    }
                  ]
                }""",
                List.of(true, false, true),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testValueEqField() {
        assertSelectionQuery(
                "from Widget w where (10, 20) = (w.a, w.b) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "a": {
                              "$eq": 10
                            }
                          },
                          {
                            "b": {
                              "$eq": 20
                            }
                          }
                        ]
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(1, 10, 20)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testFieldEqField() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) = (w.b, w.a) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$expr": {
                          "$and": [
                            { "$eq": ["$a", "$b"] },
                            { "$eq": ["$b", "$a"] }
                          ]
                        }
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testFieldNeField() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) <> (w.b, w.a) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$expr": {
                          "$not": [
                            {
                              "$and": [
                                { "$eq": ["$a", "$b"] },
                                { "$eq": ["$b", "$a"] }
                              ]
                            }
                          ]
                        }
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(1, 10, 20), new Widget(2, 10, 99), new Widget(3, 30, 40)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testInFieldRows() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) in ((w.b, w.a)) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$expr": {
                          "$and": [
                            { "$eq": ["$a", "$b"] },
                            { "$eq": ["$b", "$a"] }
                          ]
                        }
                      }
                    },
                    {
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testNotIn() {
        assertSelectionQuery(
                "from Widget w where (w.a, w.b) not in ((10, 20), (30, 40)) order by w.id",
                Widget.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    {
                      "$match": {
                        "$nor": [
                          {
                            "$or": [
                              {
                                "$and": [
                                  {
                                    "a": {
                                      "$eq": 10
                                    }
                                  },
                                  {
                                    "b": {
                                      "$eq": 20
                                    }
                                  }
                                ]
                              },
                              {
                                "$and": [
                                  {
                                    "a": {
                                      "$eq": 30
                                    }
                                  },
                                  {
                                    "b": {
                                      "$eq": 40
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
                      "$sort": {
                        "_id": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "a": true,
                        "b": true
                      }
                    }
                  ]
                }""",
                List.of(new Widget(2, 10, 99)),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Test
    void testSelectNotIn() {
        assertSelectionQuery(
                "select (w.a, w.b) not in ((10, 20), (30, 40)) from Widget w order by w.id",
                Boolean.class,
                """
                {
                  "aggregate": "row_value",
                  "pipeline": [
                    { "$sort": { "_id": 1 } },
                    {
                      "$project": {
                        "#c_1": {
                          "$not": [
                            {
                              "$or": [
                                { "$and": [ { "$eq": ["$a", 10] }, { "$eq": ["$b", 20] } ] },
                                { "$and": [ { "$eq": ["$a", 30] }, { "$eq": ["$b", 40] } ] }
                              ]
                            }
                          ]
                        }
                      }
                    }
                  ]
                }""",
                List.of(false, true, false),
                Set.of(Widget.COLLECTION_NAME));
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {
        @Test
        void testOrderingComparisonFilter() {
            assertSelectQueryFailure(
                    "from Widget w where (w.a, w.b) > (:a, :b)",
                    Widget.class,
                    q -> q.setParameter("a", 10L).setParameter("b", 20L),
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-211 https://jira.mongodb.org/browse/HIBERNATE-211");
        }

        @Test
        void testOrderingComparisonExpression() {
            assertSelectQueryFailure(
                    "select (w.a, w.b) > (10, 20) from Widget w",
                    Boolean.class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-211 https://jira.mongodb.org/browse/HIBERNATE-211");
        }
    }

    @Entity(name = "Widget")
    @Table(name = Widget.COLLECTION_NAME)
    static class Widget {
        static final String COLLECTION_NAME = "row_value";

        @Id
        long id;

        long a;

        long b;

        Widget() {}

        Widget(long id, long a, long b) {
            this.id = id;
            this.a = a;
            this.b = b;
        }
    }
}
