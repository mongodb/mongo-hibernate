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

package com.mongodb.hibernate.query.select.temporal;

import static java.lang.String.format;

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

abstract class AbstractSelectionTemporalIntegrationTest<I, T> extends AbstractQueryIntegrationTests {

    static final String COLLECTION_NAME = "items";

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> getData().forEach(session::persist));
        getTestCommandListener().clear();
    }

    abstract List<I> getData();

    @ParameterizedTest(name = "testComparisonByEq: temporal={0}, expectedItems={2}, expectedRender={3}, timeZone={1}")
    @MethodSource
    void testComparisonByEq(T temporal, List<I> expectedItems, String expectedRender, Class<I> tClass) {
        assertSelectionQuery(
                "from Item where temporal = :t",
                tClass,
                q -> q.setParameter("t", temporal),
                format(
                        """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {
                              "$match": {
                                "temporal": {
                                  "$eq": %s
                                }
                              }
                            },
                            {
                              "$project": {
                                "_id": true,
                                "temporal": true
                              }
                            }
                          ]
                        }""",
                        expectedRender),
                expectedItems,
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testComparisonByNe: temporal={0}, expectedItems={2}, expectedRender={3}, timeZone={1}")
    @MethodSource
    void testComparisonByNe(T temporal, List<I> expectedItems, String expectedRender, Class<I> tClass) {
        assertSelectionQuery(
                "from Item where temporal != :t",
                tClass,
                q -> q.setParameter("t", temporal),
                format(
                        """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {
                              "$match": {
                                "temporal": {
                                  "$ne": %s
                                }
                              }
                            },
                            {
                              "$project": {
                                "_id": true,
                                "temporal": true
                              }
                            }
                          ]
                        }""",
                        expectedRender),
                expectedItems,
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testComparisonByLt: timeZone={1}, expectedItems={2}, expectedRender={3}")
    @MethodSource
    void testComparisonByLt(T temporal, List<I> expectedItems, String expectedRender, Class<I> tClass) {
        assertSelectionQuery(
                "from Item where temporal < :t",
                tClass,
                q -> q.setParameter("t", temporal),
                format(
                        """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {
                              "$match": {
                                "temporal": {
                                  "$lt": %s
                                }
                              }
                            },
                            {
                              "$project": {
                                "_id": true,
                                "temporal": true
                              }
                            }
                          ]
                        }""",
                        expectedRender),
                expectedItems,
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testComparisonByLte: timeZone={1}, expectedItems={2}, expectedRender={3}")
    @MethodSource
    void testComparisonByLte(T temporal, List<I> expectedItems, String expectedRender, Class<I> tClass) {
        assertSelectionQuery(
                "from Item where temporal <= :t",
                tClass,
                q -> q.setParameter("t", temporal),
                format(
                        """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {
                              "$match": {
                                "temporal": {
                                  "$lte": %s
                                }
                              }
                            },
                            {
                              "$project": {
                                "_id": true,
                                "temporal": true
                              }
                            }
                          ]
                        }""",
                        expectedRender),
                expectedItems,
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testComparisonByGt: timeZone={1}, expectedItems={2}, expectedRender={3}")
    @MethodSource
    void testComparisonByGt(T temporal, List<I> expectedItems, String expectedRender, Class<I> tClass) {
        assertSelectionQuery(
                "from Item where temporal > :t",
                tClass,
                q -> q.setParameter("t", temporal),
                format(
                        """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {
                              "$match": {
                                "temporal": {
                                  "$gt": %s
                                }
                              }
                            },
                            {
                              "$project": {
                                "_id": true,
                                "temporal": true
                              }
                            }
                          ]
                        }""",
                        expectedRender),
                expectedItems,
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testComparisonByGte: timeZone={1}, expectedItems={2}, expectedRender={3}")
    @MethodSource
    void testComparisonByGte(T temporal, List<I> expectedItems, String expectedRender, Class<I> tClass) {
        assertSelectionQuery(
                "from Item where temporal >= :t",
                tClass,
                q -> q.setParameter("t", temporal),
                format(
                        """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {
                              "$match": {
                                "temporal": {
                                  "$gte": %s
                                }
                              }
                            },
                            {
                              "$project": {
                                "_id": true,
                                "temporal": true
                              }
                            }
                          ]
                        }""",
                        expectedRender),
                expectedItems,
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testOrderByAsc: timeZone={1}, expectedItems={2}")
    @MethodSource
    void testOrderByAsc(List<I> expectedItems, Class<I> tClass) {
        assertSelectionQuery(
                "from Item ORDER BY temporal ASC",
                tClass,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$sort": {
                        "temporal": 1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "temporal": true
                      }
                    }
                  ]
                }""",
                expectedItems,
                Set.of(COLLECTION_NAME));
    }

    @ParameterizedTest(name = "testOrderByDesc: timeZone={1}, expectedItems={2}")
    @MethodSource
    void testOrderByDesc(List<I> expectedItems, Class<I> tClass) {
        assertSelectionQuery(
                "from Item ORDER BY temporal DESC",
                tClass,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$sort": {
                        "temporal": -1
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "temporal": true
                      }
                    }
                  ]
                }""",
                expectedItems,
                Set.of(COLLECTION_NAME));
    }
}
