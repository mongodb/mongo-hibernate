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

package com.mongodb.hibernate.query.function.string;

import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {StringFunctionIntegrationTests.Item.class})
@ExtendWith(MongoExtension.class)
public class StringFunctionIntegrationTests extends AbstractQueryIntegrationTests {
    private static final String COLLECTION_NAME = "items";
    private static final Item HELLO = new Item(1, " Hello ", "_Hello_");

    @SuppressWarnings("unchecked")
    private <T> void assertQueryResult(String hql, T expected, String expectedMql) {
        assertSelectionQuery(
                hql, (Class<T>) expected.getClass(), expectedMql, List.of(expected), Set.of(COLLECTION_NAME));
    }

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> session.persist(HELLO));
    }

    @Test
    void testCharacterLength() {
        assertQueryResult(
                "select length(s) from Item",
                7,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$strLenCP": "$s"
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testConcat() {
        assertQueryResult(
                "select s||s from Item",
                " Hello  Hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$concat": [
                            {
                              "$toString": "$s"
                            },
                            {
                              "$toString": "$s"
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testConcatCoerce() {
        assertQueryResult(
                "select s||3 from Item",
                " Hello 3",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$concat": [
                            {
                              "$toString": "$s"
                            },
                            {
                              "$toString": 3
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testConcatLiteral() {
        assertQueryResult(
                "select concat(s, '!') from Item",
                " Hello !",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$concat": [
                            {
                              "$toString": "$s"
                            },
                            {
                              "$toString": "!"
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLocate() {
        assertQueryResult(
                "select locate('o', s) from Item",
                6,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$add": [
                            {
                              "$indexOfCP": [
                                "$s",
                                "o"
                              ]
                            },
                            {
                              "$literal": 1
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLocateWithStart() {
        assertQueryResult(
                "select locate(' ', s, 4) from Item",
                7,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$add": [
                            {
                              "$indexOfCP": [
                                "$s",
                                " ",
                                {
                                  "$max": [
                                    {
                                      "$subtract": [
                                        4,
                                        {
                                          "$literal": 1
                                        }
                                      ]
                                    },
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "$literal": 1
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLocateZeroStart() {
        assertQueryResult(
                "select locate('o', s, 0) from Item",
                6,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$add": [
                            {
                              "$indexOfCP": [
                                "$s",
                                "o",
                                {
                                  "$max": [
                                    {
                                      "$subtract": [
                                        0,
                                        {
                                          "$literal": 1
                                        }
                                      ]
                                    },
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "$literal": 1
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLocateNegativeStart() {
        assertQueryResult(
                "select locate('o', s, -3) from Item",
                6,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$add": [
                            {
                              "$indexOfCP": [
                                "$s",
                                "o",
                                {
                                  "$max": [
                                    {
                                      "$subtract": [
                                        -3,
                                        {
                                          "$literal": 1
                                        }
                                      ]
                                    },
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "$literal": 1
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLocatePositiveStart() {
        assertQueryResult(
                "select locate(' ', s, 4) from Item",
                7,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$add": [
                            {
                              "$indexOfCP": [
                                "$s",
                                " ",
                                {
                                  "$max": [
                                    {
                                      "$subtract": [
                                        4,
                                        {
                                          "$literal": 1
                                        }
                                      ]
                                    },
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                }
                              ]
                            },
                            {
                              "$literal": 1
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLocateMissing() {
        assertQueryResult(
                "select locate('z', s) from Item",
                0,
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$add": [
                            {
                              "$indexOfCP": [
                                "$s",
                                "z"
                              ]
                            },
                            {
                              "$literal": 1
                            }
                          ]
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLower() {
        assertQueryResult(
                "select lower(s) from Item",
                " hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$toLower": "$s"
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testRepeat() {
        assertQueryResult(
                "select repeat(s, 2) from Item",
                " Hello  Hello ",
                """
                        {
                                  "aggregate": "items",
                                  "pipeline": [
                                    {
                                      "$project": {
                                        "#c_1": {
                                          "$let": {
                                            "in": {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$value",
                                                    "$$repeatStr"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    "$$count"
                                                  ]
                                                }
                                              }
                                            },
                                            "vars": {
                                              "count": 2,
                                              "repeatStr": "$s"
                                            }
                                          }
                                        },
                                        "_id": 0
                                      }
                                    }
                                  ]
                                }
                """);
    }

    @Test
    void testPadLeadingWS() {
        assertQueryResult(
                "select pad(s with 20 leading) from Item",
                "              Hello ",
                """
                        {
                          "aggregate": "items",
                          "pipeline": [
                            {
                              "$project": {
                                "#c_1": {
                                  "$let": {
                                    "in": {
                                      "$cond": [
                                        {
                                          "$lte": [
                                            "$$targetLen",
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": ""
                                        },
                                        {
                                          "$cond": [
                                            {
                                              "$or": [
                                                {
                                                  "$gte": [
                                                    {
                                                      "$strLenCP": "$$baseStr"
                                                    },
                                                    "$$targetLen"
                                                  ]
                                                },
                                                {
                                                  "$eq": [
                                                    {
                                                      "$strLenCP": "$$padding"
                                                    },
                                                    {
                                                      "$literal": 0
                                                    }
                                                  ]
                                                }
                                              ]
                                            },
                                            {
                                              "$substrCP": [
                                                "$$baseStr",
                                                {
                                                  "$literal": 0
                                                },
                                                "$$targetLen"
                                              ]
                                            },
                                            {
                                              "$concat": [
                                                {
                                                  "$substrCP": [
                                                    {
                                                      "$reduce": {
                                                        "in": {
                                                          "$concat": [
                                                            "$$padding",
                                                            "$$value"
                                                          ]
                                                        },
                                                        "initialValue": {
                                                          "$literal": ""
                                                        },
                                                        "input": {
                                                          "$range": [
                                                            {
                                                              "$literal": 0
                                                            },
                                                            {
                                                              "$ceil": {
                                                                "$divide": [
                                                                  {
                                                                    "$subtract": [
                                                                      "$$targetLen",
                                                                      {
                                                                        "$strLenCP": "$$baseStr"
                                                                      }
                                                                    ]
                                                                  },
                                                                  {
                                                                    "$strLenCP": "$$padding"
                                                                  }
                                                                ]
                                                              }
                                                            }
                                                          ]
                                                        }
                                                      }
                                                    },
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$subtract": [
                                                        "$$targetLen",
                                                        {
                                                          "$strLenCP": "$$baseStr"
                                                        }
                                                      ]
                                                    }
                                                  ]
                                                },
                                                "$$baseStr"
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    "vars": {
                                      "baseStr": "$s",
                                      "padding": {
                                        "$literal": " "
                                      },
                                      "targetLen": 20
                                    }
                                  }
                                },
                                "_id": 0
                              }
                            }
                          ]
                        }
                """);
    }

    @Test
    void testPadLeadingCh() {
        assertQueryResult(
                "select pad(s with 20 leading '*') from Item",
                "************* Hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$padding",
                                                    "$$value"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        },
                                        "$$baseStr"
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "$s",
                              "padding": "*",
                              "targetLen": 20
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testPadTrailingWS() {
        assertQueryResult(
                "select pad(s with 20 trailing) from Item",
                " Hello              ",
                """
                        {
                                  "aggregate": "items",
                                  "pipeline": [
                                    {
                                      "$project": {
                                        "#c_1": {
                                          "$let": {
                                            "in": {
                                              "$cond": [
                                                {
                                                  "$lte": [
                                                    "$$targetLen",
                                                    {
                                                      "$literal": 0
                                                    }
                                                  ]
                                                },
                                                {
                                                  "$literal": ""
                                                },
                                                {
                                                  "$cond": [
                                                    {
                                                      "$or": [
                                                        {
                                                          "$gte": [
                                                            {
                                                              "$strLenCP": "$$baseStr"
                                                            },
                                                            "$$targetLen"
                                                          ]
                                                        },
                                                        {
                                                          "$eq": [
                                                            {
                                                              "$strLenCP": "$$padding"
                                                            },
                                                            {
                                                              "$literal": 0
                                                            }
                                                          ]
                                                        }
                                                      ]
                                                    },
                                                    {
                                                      "$substrCP": [
                                                        "$$baseStr",
                                                        {
                                                          "$literal": 0
                                                        },
                                                        "$$targetLen"
                                                      ]
                                                    },
                                                    {
                                                      "$concat": [
                                                        "$$baseStr",
                                                        {
                                                          "$substrCP": [
                                                            {
                                                              "$reduce": {
                                                                "in": {
                                                                  "$concat": [
                                                                    "$$value",
                                                                    "$$padding"
                                                                  ]
                                                                },
                                                                "initialValue": {
                                                                  "$literal": ""
                                                                },
                                                                "input": {
                                                                  "$range": [
                                                                    {
                                                                      "$literal": 0
                                                                    },
                                                                    {
                                                                      "$ceil": {
                                                                        "$divide": [
                                                                          {
                                                                            "$subtract": [
                                                                              "$$targetLen",
                                                                              {
                                                                                "$strLenCP": "$$baseStr"
                                                                              }
                                                                            ]
                                                                          },
                                                                          {
                                                                            "$strLenCP": "$$padding"
                                                                          }
                                                                        ]
                                                                      }
                                                                    }
                                                                  ]
                                                                }
                                                              }
                                                            },
                                                            {
                                                              "$literal": 0
                                                            },
                                                            {
                                                              "$subtract": [
                                                                "$$targetLen",
                                                                {
                                                                  "$strLenCP": "$$baseStr"
                                                                }
                                                              ]
                                                            }
                                                          ]
                                                        }
                                                      ]
                                                    }
                                                  ]
                                                }
                                              ]
                                            },
                                            "vars": {
                                              "baseStr": "$s",
                                              "padding": {
                                                "$literal": " "
                                              },
                                              "targetLen": 20
                                            }
                                          }
                                        },
                                        "_id": 0
                                      }
                                    }
                                  ]
                                }
                """);
    }

    @Test
    void testPadTrailingCh() {
        assertQueryResult(
                "select pad(s with 20 trailing '*') from Item",
                " Hello *************",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        "$$baseStr",
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$value",
                                                    "$$padding"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "$s",
                              "padding": "*",
                              "targetLen": 20
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLpadPadsToRequestedLengthWithMultiCharacterPadding() {
        assertQueryResult(
                "select lpad('x', 6, 'ab') from Item",
                "ababax",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$padding",
                                                    "$$value"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        },
                                        "$$baseStr"
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "x",
                              "padding": "ab",
                              "targetLen": 6
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testRpadPadsToRequestedLengthWithMultiCharacterPadding() {
        assertQueryResult(
                "select rpad('x', 6, 'ab') from Item",
                "xababa",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        "$$baseStr",
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$value",
                                                    "$$padding"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "x",
                              "padding": "ab",
                              "targetLen": 6
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLpadWithEmptyPaddingReturnsInput() {
        assertQueryResult(
                "select lpad(s, 60, '') from Item",
                " Hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$padding",
                                                    "$$value"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        },
                                        "$$baseStr"
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "$s",
                              "padding": "",
                              "targetLen": 60
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testRpadWithEmptyPaddingReturnsInput() {
        assertQueryResult(
                "select rpad(s, 60, '') from Item",
                " Hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        "$$baseStr",
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$value",
                                                    "$$padding"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "$s",
                              "padding": "",
                              "targetLen": 60
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testPadLeadingTruncatesWhenTargetIsShorterThanInput() {
        assertQueryResult(
                "select pad(s with 3 leading) from Item",
                " He",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$padding",
                                                    "$$value"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        },
                                        "$$baseStr"
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "$s",
                              "padding": {
                                "$literal": " "
                              },
                              "targetLen": 3
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testPadTrailingTruncatesWhenTargetIsShorterThanInput() {
        assertQueryResult(
                "select pad(s with 3 trailing) from Item",
                " He",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        "$$baseStr",
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$value",
                                                    "$$padding"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "$s",
                              "padding": {
                                "$literal": " "
                              },
                              "targetLen": 3
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testPadLeadingWithNegativeTargetReturnsEmptyString() {
        assertQueryResult(
                "select pad(s with -1 leading) from Item",
                "",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$padding",
                                                    "$$value"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        },
                                        "$$baseStr"
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "$s",
                              "padding": {
                                "$literal": " "
                              },
                              "targetLen": -1
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testPadTrailingWithNegativeTargetReturnsEmptyString() {
        assertQueryResult(
                "select pad(s with -1 trailing) from Item",
                "",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        "$$baseStr",
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$value",
                                                    "$$padding"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "$s",
                              "padding": {
                                "$literal": " "
                              },
                              "targetLen": -1
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLpadMuliCharExact() {
        assertQueryResult(
                "select lpad('ab', 6, 'ab') from Item",
                "ababab",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$padding",
                                                    "$$value"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        },
                                        "$$baseStr"
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "ab",
                              "padding": "ab",
                              "targetLen": 6
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testRpadMuliCharExact() {
        assertQueryResult(
                "select rpad('ab', 6, 'ab') from Item",
                "ababab",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        "$$baseStr",
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$value",
                                                    "$$padding"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "ab",
                              "padding": "ab",
                              "targetLen": 6
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLpadLongPadding() {
        assertQueryResult(
                "select lpad('x', 3, 'abcdef') from Item",
                "abx",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$padding",
                                                    "$$value"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        },
                                        "$$baseStr"
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "x",
                              "padding": "abcdef",
                              "targetLen": 3
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testRpadLongPadding() {
        assertQueryResult(
                "select rpad('x', 3, 'abcdef') from Item",
                "xab",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$cond": [
                                {
                                  "$lte": [
                                    "$$targetLen",
                                    {
                                      "$literal": 0
                                    }
                                  ]
                                },
                                {
                                  "$literal": ""
                                },
                                {
                                  "$cond": [
                                    {
                                      "$or": [
                                        {
                                          "$gte": [
                                            {
                                              "$strLenCP": "$$baseStr"
                                            },
                                            "$$targetLen"
                                          ]
                                        },
                                        {
                                          "$eq": [
                                            {
                                              "$strLenCP": "$$padding"
                                            },
                                            {
                                              "$literal": 0
                                            }
                                          ]
                                        }
                                      ]
                                    },
                                    {
                                      "$substrCP": [
                                        "$$baseStr",
                                        {
                                          "$literal": 0
                                        },
                                        "$$targetLen"
                                      ]
                                    },
                                    {
                                      "$concat": [
                                        "$$baseStr",
                                        {
                                          "$substrCP": [
                                            {
                                              "$reduce": {
                                                "in": {
                                                  "$concat": [
                                                    "$$value",
                                                    "$$padding"
                                                  ]
                                                },
                                                "initialValue": {
                                                  "$literal": ""
                                                },
                                                "input": {
                                                  "$range": [
                                                    {
                                                      "$literal": 0
                                                    },
                                                    {
                                                      "$ceil": {
                                                        "$divide": [
                                                          {
                                                            "$subtract": [
                                                              "$$targetLen",
                                                              {
                                                                "$strLenCP": "$$baseStr"
                                                              }
                                                            ]
                                                          },
                                                          {
                                                            "$strLenCP": "$$padding"
                                                          }
                                                        ]
                                                      }
                                                    }
                                                  ]
                                                }
                                              }
                                            },
                                            {
                                              "$literal": 0
                                            },
                                            {
                                              "$subtract": [
                                                "$$targetLen",
                                                {
                                                  "$strLenCP": "$$baseStr"
                                                }
                                              ]
                                            }
                                          ]
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            },
                            "vars": {
                              "baseStr": "x",
                              "padding": "abcdef",
                              "targetLen": 3
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testSubstring() {
        assertQueryResult(
                "select substring(s, 2) from Item",
                "Hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$let": {
                                "in": {
                                  "$substrCP": [
                                    "$$str",
                                    "$$adjustedStart",
                                    {
                                      "$literal": 2147483647
                                    }
                                  ]
                                },
                                "vars": {
                                  "adjustedStart": {
                                    "$cond": [
                                      {
                                        "$lt": [
                                          "$$start",
                                          {
                                            "$literal": 0
                                          }
                                        ]
                                      },
                                      {
                                        "$literal": 0
                                      },
                                      "$$start"
                                    ]
                                  }
                                }
                              }
                            },
                            "vars": {
                              "start": {
                                "$subtract": [
                                  2,
                                  {
                                    "$literal": 1
                                  }
                                ]
                              },
                              "str": "$s"
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testSubstringLen() {
        assertQueryResult(
                "select substring(s, 3, 2) from Item",
                "el",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$let": {
                                "in": {
                                  "$substrCP": [
                                    "$$str",
                                    "$$adjustedStart",
                                    {
                                      "$max": [
                                        {
                                          "$add": [
                                            "$$len",
                                            {
                                              "$subtract": [
                                                "$$start",
                                                "$$adjustedStart"
                                              ]
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": 0
                                        }
                                      ]
                                    }
                                  ]
                                },
                                "vars": {
                                  "adjustedStart": {
                                    "$cond": [
                                      {
                                        "$lt": [
                                          "$$start",
                                          {
                                            "$literal": 0
                                          }
                                        ]
                                      },
                                      {
                                        "$literal": 0
                                      },
                                      "$$start"
                                    ]
                                  }
                                }
                              }
                            },
                            "vars": {
                              "len": 2,
                              "start": {
                                "$subtract": [
                                  3,
                                  {
                                    "$literal": 1
                                  }
                                ]
                              },
                              "str": "$s"
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testSubstringWithZeroStart() {
        assertQueryResult(
                "select substring(s, 0) from Item",
                " Hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$let": {
                                "in": {
                                  "$substrCP": [
                                    "$$str",
                                    "$$adjustedStart",
                                    {
                                      "$literal": 2147483647
                                    }
                                  ]
                                },
                                "vars": {
                                  "adjustedStart": {
                                    "$cond": [
                                      {
                                        "$lt": [
                                          "$$start",
                                          {
                                            "$literal": 0
                                          }
                                        ]
                                      },
                                      {
                                        "$literal": 0
                                      },
                                      "$$start"
                                    ]
                                  }
                                }
                              }
                            },
                            "vars": {
                              "start": {
                                "$subtract": [
                                  0,
                                  {
                                    "$literal": 1
                                  }
                                ]
                              },
                              "str": "$s"
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testSubstringFromNegativeStartForLength() {
        assertQueryResult(
                "select substring(s, -1, 3) from Item",
                " ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$let": {
                                "in": {
                                  "$substrCP": [
                                    "$$str",
                                    "$$adjustedStart",
                                    {
                                      "$max": [
                                        {
                                          "$add": [
                                            "$$len",
                                            {
                                              "$subtract": [
                                                "$$start",
                                                "$$adjustedStart"
                                              ]
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": 0
                                        }
                                      ]
                                    }
                                  ]
                                },
                                "vars": {
                                  "adjustedStart": {
                                    "$cond": [
                                      {
                                        "$lt": [
                                          "$$start",
                                          {
                                            "$literal": 0
                                          }
                                        ]
                                      },
                                      {
                                        "$literal": 0
                                      },
                                      "$$start"
                                    ]
                                  }
                                }
                              }
                            },
                            "vars": {
                              "len": 3,
                              "start": {
                                "$subtract": [
                                  -1,
                                  {
                                    "$literal": 1
                                  }
                                ]
                              },
                              "str": "$s"
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testSubstringWithZeroStartAndLength() {
        assertQueryResult(
                "select substring(s, 0, 3) from Item",
                " H",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$let": {
                                "in": {
                                  "$substrCP": [
                                    "$$str",
                                    "$$adjustedStart",
                                    {
                                      "$max": [
                                        {
                                          "$add": [
                                            "$$len",
                                            {
                                              "$subtract": [
                                                "$$start",
                                                "$$adjustedStart"
                                              ]
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": 0
                                        }
                                      ]
                                    }
                                  ]
                                },
                                "vars": {
                                  "adjustedStart": {
                                    "$cond": [
                                      {
                                        "$lt": [
                                          "$$start",
                                          {
                                            "$literal": 0
                                          }
                                        ]
                                      },
                                      {
                                        "$literal": 0
                                      },
                                      "$$start"
                                    ]
                                  }
                                }
                              }
                            },
                            "vars": {
                              "len": 3,
                              "start": {
                                "$subtract": [
                                  0,
                                  {
                                    "$literal": 1
                                  }
                                ]
                              },
                              "str": "$s"
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testSubstringNegative() {
        assertQueryResult(
                "select substring(s, -5, 2) from Item",
                "",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$let": {
                            "in": {
                              "$let": {
                                "in": {
                                  "$substrCP": [
                                    "$$str",
                                    "$$adjustedStart",
                                    {
                                      "$max": [
                                        {
                                          "$add": [
                                            "$$len",
                                            {
                                              "$subtract": [
                                                "$$start",
                                                "$$adjustedStart"
                                              ]
                                            }
                                          ]
                                        },
                                        {
                                          "$literal": 0
                                        }
                                      ]
                                    }
                                  ]
                                },
                                "vars": {
                                  "adjustedStart": {
                                    "$cond": [
                                      {
                                        "$lt": [
                                          "$$start",
                                          {
                                            "$literal": 0
                                          }
                                        ]
                                      },
                                      {
                                        "$literal": 0
                                      },
                                      "$$start"
                                    ]
                                  }
                                }
                              }
                            },
                            "vars": {
                              "len": 2,
                              "start": {
                                "$subtract": [
                                  -5,
                                  {
                                    "$literal": 1
                                  }
                                ]
                              },
                              "str": "$s"
                            }
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testTrim() {
        assertQueryResult(
                "select trim(s) from Item",
                "Hello",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$trim": {
                            "chars": " ",
                            "input": "$s"
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testTrimLeading() {
        assertQueryResult(
                "select trim(leading from s) from Item",
                "Hello ",
                """
                 {
                   "aggregate": "items",
                   "pipeline": [
                     {
                       "$project": {
                         "#c_1": {
                           "$ltrim": {
                             "chars": " ",
                             "input": "$s"
                           }
                         },
                         "_id": 0
                       }
                     }
                   ]
                 }
                """);
    }

    @Test
    void testTrimTrailing() {
        assertQueryResult(
                "select trim(trailing from s) from Item",
                " Hello",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$rtrim": {
                            "chars": " ",
                            "input": "$s"
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testTrimBoth() {
        assertQueryResult(
                "select trim(both from s) from Item",
                "Hello",
                """
                {
                    "aggregate": "items",
                    "pipeline": [
                        {
                            "$project": {
                                "#c_1": {
                                    "$trim": {
                                        "chars": " ",
                                        "input": "$s"
                                       }
                                    },
                                "_id": 0
                                }
                        }
                    ]
                }
                """);
    }

    @Test
    void testTrimLeadingNonWS() {
        assertQueryResult(
                "select trim(leading '_' from u) from Item",
                "Hello_",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$ltrim": {
                            "chars": "_",
                            "input": "$u"
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testTrimTrailingNonWS() {
        assertQueryResult(
                "select trim(trailing '_' from u) from Item",
                "_Hello",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$rtrim": {
                            "chars": "_",
                            "input": "$u"
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testTrimBothNonWS() {
        assertQueryResult(
                "select trim(both '_' from u) from Item",
                "Hello",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$trim": {
                            "chars": "_",
                            "input": "$u"
                          }
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testUpper() {
        assertQueryResult(
                "select upper(s) from Item",
                " HELLO ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$toUpper": "$s"
                        },
                        "_id": 0
                      }
                    }
                  ]
                }
                """);
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        String s;
        String u;

        Item() {}

        Item(int id, String s, String u) {
            this.id = id;
            this.s = s;
            this.u = u;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Item item = (Item) o;
            return id == item.id && Objects.equals(s, item.s) && Objects.equals(u, item.u);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, s, u);
        }

        @Override
        public String toString() {
            return "Item{" + "id=" + id + ", s='" + s + '\'' + ", u='" + u + '\'' + '}';
        }
    }
}
