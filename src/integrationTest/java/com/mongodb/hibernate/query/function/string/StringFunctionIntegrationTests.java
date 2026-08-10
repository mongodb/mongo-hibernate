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
import org.hibernate.query.sqm.produce.function.FunctionArgumentException;
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
                        }
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
                        }
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
                        }
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
                        }
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
                        }
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
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLocateOffset() {
        assertQueryResult(
                "select locate('H', s, 1, 2) from Item",
                2,
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
                                "H",
                                {
                                  "$subtract": [
                                    1,
                                    {
                                      "$literal": 1
                                    }
                                  ]
                                },
                                {
                                  "$add": [
                                    {
                                      "$subtract": [
                                        1,
                                        {
                                          "$literal": 1
                                        }
                                      ]
                                    },
                                    2
                                  ]
                                }
                              ]
                            },
                            {
                              "$literal": 1
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLocateOffsetMissing() {
        assertQueryResult(
                "select locate('o', s, 1, 2) from Item",
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
                                "o",
                                {
                                  "$subtract": [
                                    1,
                                    {
                                      "$literal": 1
                                    }
                                  ]
                                },
                                {
                                  "$add": [
                                    {
                                      "$subtract": [
                                        1,
                                        {
                                          "$literal": 1
                                        }
                                      ]
                                    },
                                    2
                                  ]
                                }
                              ]
                            },
                            {
                              "$literal": 1
                            }
                          ]
                        }
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
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLeftTrim() {
        assertQueryResult(
                "select ltrim(s) from Item",
                "Hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$ltrim": {
                            "input": "$s"
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testLeftTrimChars() {
        assertQueryResult(
                "select ltrim(u, '_') from Item",
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
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testReplaceAll() {
        assertQueryResult(
                "select replace_all(s, ' ', '_') from Item",
                "_Hello_",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$replaceAll": {
                            "find": " ",
                            "input": "$s",
                            "replacement": "_"
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testReplaceOne() {
        assertQueryResult(
                "select replace_one(s, ' ', '_') from Item",
                "_Hello ",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$replaceOne": {
                            "find": " ",
                            "input": "$s",
                            "replacement": "_"
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testRightTrim() {
        assertQueryResult(
                "select rtrim(s) from Item",
                " Hello",
                """
                {
                  "aggregate": "items",
                  "pipeline": [
                    {
                      "$project": {
                        "#c_1": {
                          "$rtrim": {
                            "input": "$s"
                          }
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testRightTrimChars() {
        assertQueryResult(
                "select rtrim(u, '_') from Item",
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
                        }
                      }
                    }
                  ]
                }
                """);
    }

    @Test
    void testRightTrimWrongType() {
        assertSelectQueryFailure(
                "select rtrim(u, 3) from Item",
                String.class,
                FunctionArgumentException.class,
                "Parameter 2 of function 'rtrim()' has type 'STRING', but argument is of type 'java.lang.Integer' mapped to 'INTEGER'");
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
                          "$substrCP": [
                            "$s",
                            {
                              "$subtract": [
                                2,
                                {
                                  "$literal": 1
                                }
                              ]
                            },
                            {
                              "$literal": 2147483647
                            }
                          ]
                        }
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
                          "$substrCP": [
                            "$s",
                            {
                              "$subtract": [
                                3,
                                {
                                  "$literal": 1
                                }
                              ]
                            },
                            2
                          ]
                        }
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
                        }
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
                         }
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
                        }
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
                                    }
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
                        }
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
                        }
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
                        }
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
                        }
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
