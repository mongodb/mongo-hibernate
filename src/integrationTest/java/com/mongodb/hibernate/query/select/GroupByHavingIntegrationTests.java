/*
 * Copyright 2026-present MongoDB, Inc.
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

import static com.mongodb.hibernate.query.select.GroupByHavingIntegrationTests.Item.COLLECTION_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MongoExtension.class)
@DomainModel(
        annotatedClasses = {GroupByHavingIntegrationTests.Item.class, GroupByHavingIntegrationTests.ItemStruct.class})
@SuppressWarnings({"unchecked", "rawtypes"})
public class GroupByHavingIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection(COLLECTION_NAME)
    private static MongoCollection mongoCollection;

    @Entity(name = "Item")
    static class Item {
        public static final String COLLECTION_NAME = "Item";

        @Id
        int id;

        int primitiveInt;
        String string;
        boolean primitiveBoolean;

        @Embedded
        ItemStruct itemStruct;

        public Item(
                final int id,
                final int primitiveInt,
                final String string,
                final boolean primitiveBoolean,
                ItemStruct itemStruct) {
            this.id = id;
            this.primitiveInt = primitiveInt;
            this.string = string;
            this.primitiveBoolean = primitiveBoolean;
            this.itemStruct = itemStruct;
        }

        Item() {}
    }

    @Embeddable
    @Struct(name = "ItemStruct")
    static class ItemStruct {
        int primitiveInt;

        ItemStruct() {}

        ItemStruct(int primitiveInt) {
            this.primitiveInt = primitiveInt;
        }
    }

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> List.of(
                        new Item(1, 1, "a", true, new ItemStruct(1)),
                        new Item(2, 1, "a", false, new ItemStruct(1)),
                        new Item(3, 1, "a", true, new ItemStruct(1)),
                        new Item(4, 1, "a", false, new ItemStruct(1)),
                        new Item(5, 2, "b", true, new ItemStruct(2)),
                        new Item(6, 2, "b", false, new ItemStruct(2)),
                        new Item(7, 3, "c", true, new ItemStruct(3)),
                        new Item(8, 4, "c", false, new ItemStruct(4)))
                .forEach(session::persist));
        commandHistory.clear();
    }

    @Test
    void testSingle() {
        assertSelectionQuery(
                "select b.primitiveInt from Item as b GROUP BY b.primitiveInt",
                Object.class,
                """
                {
                  "aggregate": "Item",
                  "pipeline": [
                    {
                      "$group": {
                        "_id": {
                          "primitiveInt": "$primitiveInt"
                        }
                      }
                    },
                    {
                      "$project": {
                        "_id#primitiveInt": "$_id.primitiveInt"
                      }
                    }
                  ]
                }
                """,
                results -> {
                    assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(1, 2, 3, 4);
                },
                Set.of(COLLECTION_NAME));
    }

    @Test
    void testSingleWithStruct() {
        assertSelectionQuery(
                "select b.itemStruct.primitiveInt from Item as b GROUP BY b.itemStruct.primitiveInt",
                Object.class,
                """
                {
                  "aggregate": "Item",
                  "pipeline": [
                    {"$group": {"_id": {"itemStruct#primitiveInt": "$itemStruct.primitiveInt"}}},
                    {"$project": {"_id#itemStruct#primitiveInt": "$_id.itemStruct#primitiveInt"}}
                  ]
                }
                """,
                results -> assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(1, 2, 3, 4),
                Set.of(COLLECTION_NAME));
    }

    @Test
    void testOrderBy() {
        assertSelectionQuery(
                "select b.primitiveInt from Item as b GROUP BY b.primitiveInt ORDER by b.primitiveInt",
                Integer.class,
                """
                {
                     "aggregate": "Item",
                     "pipeline": [
                       {
                         "$group": {
                           "_id": {
                             "primitiveInt": "$primitiveInt"
                           }
                         }
                       },
                       {
                         "$sort": {
                           "_id.primitiveInt": 1
                         }
                       },
                       {
                         "$project": {
                           "_id#primitiveInt": "$_id.primitiveInt"
                         }
                       }
                     ]
                   }
                """,
                List.of(1, 2, 3, 4),
                Set.of(COLLECTION_NAME));
    }

    @Test
    void testOrderByWithStruct() {
        assertSelectionQuery(
                "select b.itemStruct.primitiveInt from Item as b GROUP BY b.itemStruct.primitiveInt ORDER BY b.itemStruct.primitiveInt",
                Object.class,
                """
                {
                  "aggregate": "Item",
                  "pipeline": [
                    {"$group": {"_id": {"itemStruct#primitiveInt": "$itemStruct.primitiveInt"}}},
                    {"$sort": {"_id.itemStruct#primitiveInt": 1}},
                    {"$project": {"_id#itemStruct#primitiveInt": "$_id.itemStruct#primitiveInt"}}
                  ]
                }
                """,
                List.of(1, 2, 3, 4),
                Set.of(COLLECTION_NAME));
    }

    @Test
    void testMultiple() {
        assertSelectionQuery(
                "select b.primitiveInt, b.primitiveBoolean from Item as b GROUP BY b.primitiveInt, b.primitiveBoolean",
                Object.class,
                """
                {
                    "aggregate": "Item",
                    "pipeline": [
                      {
                        "$group": {
                          "_id": {
                            "primitiveInt": "$primitiveInt",
                            "primitiveBoolean": "$primitiveBoolean"
                          }
                        }
                      },
                      {
                        "$project": {
                          "_id#primitiveInt": "$_id.primitiveInt",
                          "_id#primitiveBoolean": "$_id.primitiveBoolean"
                        }
                      }
                    ]
                  }
                """,
                results -> {
                    assertThat((Iterable<Object>) results)
                            .containsExactlyInAnyOrder(
                                    new Object[] {2, false},
                                    new Object[] {1, false},
                                    new Object[] {1, true},
                                    new Object[] {2, true},
                                    new Object[] {4, false},
                                    new Object[] {3, true});
                },
                Set.of(COLLECTION_NAME));
    }

    @Nested
    @DomainModel(annotatedClasses = {ManyToOneJoin.ItemA.class, ManyToOneJoin.ItemB.class})
    class ManyToOneJoin extends AbstractQueryIntegrationTests {

        private static final List<ManyToOneJoin.ItemA> TESTING_ITEMS = List.of(
                new ManyToOneJoin.ItemA(1, new ManyToOneJoin.ItemB(1, 1)),
                new ManyToOneJoin.ItemA(2, new ManyToOneJoin.ItemB(2, 1)),
                new ManyToOneJoin.ItemA(3, new ManyToOneJoin.ItemB(3, 2)),
                new ManyToOneJoin.ItemA(4, new ManyToOneJoin.ItemB(4, 2)));

        @BeforeEach
        void beforeEach() {
            getSessionFactoryScope().inTransaction(session -> {
                TESTING_ITEMS.stream().map(itemA -> itemA.itemB).forEach(session::persist);
                TESTING_ITEMS.forEach(session::persist);
            });
            commandHistory.clear();
        }

        @Test
        void testWithJoinedColumn() {
            assertSelectionQuery(
                    "select b.primitiveInt FROM ItemA a JOIN a.itemB b GROUP BY b.primitiveInt ORDER BY b.primitiveInt",
                    Object.class,
                    """
                    {
                      "aggregate": "ItemA",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "ItemB",
                            "localField": "id",
                            "foreignField": "_id",
                            "as": "#ib1_0"
                          }
                        },
                        {
                          "$unwind": "$#ib1_0"
                        },
                        {
                          "$group": {
                            "_id": {
                              "#ib1_0#primitiveInt": "$#ib1_0.primitiveInt"
                            }
                          }
                        },
                        {
                          "$sort": {
                            "_id.#ib1_0#primitiveInt": 1
                          }
                        },
                        {
                          "$project": {
                            "_id##ib1_0#primitiveInt": "$_id.#ib1_0#primitiveInt"
                          }
                        }
                      ]
                    }
                    """,
                    List.of(1, 2),
                    Set.of("ItemA", "ItemB"));
        }

        @Test
        void testWithNonJoinedColumn() {
            assertSelectionQuery(
                    "select a.id FROM ItemA a JOIN a.itemB b GROUP BY a.id ORDER BY a.id",
                    Object.class,
                    """
                    {
                      "aggregate": "ItemA",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "ItemB",
                            "localField": "id",
                            "foreignField": "_id",
                            "as": "#ib1_0"
                          }
                        },
                        {
                          "$unwind": "$#ib1_0"
                        },
                        {
                          "$group": {
                            "_id": {
                              "_id": "$_id"
                            }
                          }
                        },
                        {
                          "$sort": {
                            "_id._id": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#_id": "$_id._id"
                          }
                        }
                      ]
                    }
                    """,
                    List.of(1, 2, 3, 4),
                    Set.of("ItemA", "ItemB"));
        }

        @Test
        void testWithJoinedColumnAndHaving() {
            assertSelectionQuery(
                    "select b.primitiveInt FROM ItemA a JOIN a.itemB b GROUP BY b.primitiveInt HAVING b.primitiveInt > 1 ORDER BY b.primitiveInt",
                    Object.class,
                    """
                    {
                      "aggregate": "ItemA",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "ItemB",
                            "localField": "id",
                            "foreignField": "_id",
                            "as": "#ib1_0"
                          }
                        },
                        {
                          "$unwind": "$#ib1_0"
                        },
                        {
                          "$group": {
                            "_id": {
                              "#ib1_0#primitiveInt": "$#ib1_0.primitiveInt"
                            }
                          }
                        },
                        {
                          "$match": {
                            "_id.#ib1_0#primitiveInt": {"$gt": 1}
                          }
                        },
                        {
                          "$sort": {
                            "_id.#ib1_0#primitiveInt": 1
                          }
                        },
                        {
                          "$project": {
                            "_id##ib1_0#primitiveInt": "$_id.#ib1_0#primitiveInt"
                          }
                        }
                      ]
                    }
                    """,
                    List.of(2),
                    Set.of("ItemA", "ItemB"));
        }

        @Test
        void testWithNonJoinedColumnAndHaving() {
            assertSelectionQuery(
                    "select a.id FROM ItemA a JOIN a.itemB b GROUP BY a.id HAVING a.id > 2 ORDER BY a.id",
                    Object.class,
                    """
                    {
                      "aggregate": "ItemA",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "ItemB",
                            "localField": "id",
                            "foreignField": "_id",
                            "as": "#ib1_0"
                          }
                        },
                        {
                          "$unwind": "$#ib1_0"
                        },
                        {
                          "$group": {
                            "_id": {
                              "_id": "$_id"
                            }
                          }
                        },
                        {
                          "$match": {
                            "_id._id": {"$gt": 2}
                          }
                        },
                        {
                          "$sort": {
                            "_id._id": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#_id": "$_id._id"
                          }
                        }
                      ]
                    }
                    """,
                    List.of(3, 4),
                    Set.of("ItemA", "ItemB"));
        }

        @Test
        void testWithNonJoinedAndJoinedColumnAndHaving() {
            assertSelectionQuery(
                    "select a.id, b.primitiveInt FROM ItemA a JOIN a.itemB b GROUP BY a.id, b.primitiveInt HAVING a.id > 1 AND b.primitiveInt > 1 ORDER BY a.id, b.primitiveInt",
                    Object.class,
                    """
                    {
                      "aggregate": "ItemA",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "ItemB",
                            "localField": "id",
                            "foreignField": "_id",
                            "as": "#ib1_0"
                          }
                        },
                        {
                          "$unwind": "$#ib1_0"
                        },
                        {
                          "$group": {
                            "_id": {
                              "_id": "$_id",
                              "#ib1_0#primitiveInt": "$#ib1_0.primitiveInt"
                            }
                          }
                        },
                        {
                          "$match": {
                            "$and": [
                              {"_id._id": {"$gt": 1}},
                              {"_id.#ib1_0#primitiveInt": {"$gt": 1}}
                            ]
                          }
                        },
                        {
                          "$sort": {
                            "_id._id": 1,
                            "_id.#ib1_0#primitiveInt": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#_id": "$_id._id",
                            "_id##ib1_0#primitiveInt": "$_id.#ib1_0#primitiveInt"
                          }
                        }
                      ]
                    }
                    """,
                    List.of(new Object[] {3, 2}, new Object[] {4, 2}),
                    Set.of("ItemA", "ItemB"));
        }

        @Test
        void testWithNonJoinedAndJoinedColumn() {
            assertSelectionQuery(
                    "select a.id, b.primitiveInt FROM ItemA a JOIN a.itemB b GROUP BY a.id, b.primitiveInt ORDER BY a.id, b.primitiveInt",
                    Object.class,
                    """
                    {
                      "aggregate": "ItemA",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "ItemB",
                            "localField": "id",
                            "foreignField": "_id",
                            "as": "#ib1_0"
                          }
                        },
                        {
                          "$unwind": "$#ib1_0"
                        },
                        {
                          "$group": {
                            "_id": {
                              "_id": "$_id",
                              "#ib1_0#primitiveInt": "$#ib1_0.primitiveInt"
                            }
                          }
                        },
                        {
                          "$sort": {
                            "_id._id": 1,
                            "_id.#ib1_0#primitiveInt": 1
                          }
                        },
                        {
                          "$project": {
                            "_id#_id": "$_id._id",
                            "_id##ib1_0#primitiveInt": "$_id.#ib1_0#primitiveInt"
                          }
                        }
                      ]
                    }
                    """,
                    List.of(new Object[] {1, 1}, new Object[] {2, 1}, new Object[] {3, 2}, new Object[] {4, 2}),
                    Set.of("ItemA", "ItemB"));
        }

        @Entity(name = "ItemB")
        static class ItemB {
            @Id
            int id;

            int primitiveInt;

            ItemB() {}

            ItemB(int id, int primitiveInt) {
                this.id = id;
                this.primitiveInt = primitiveInt;
            }
        }

        @Entity(name = "ItemA")
        static class ItemA {
            @Id
            int id;

            @ManyToOne(fetch = FetchType.LAZY)
            @JoinColumn(name = "id")
            ManyToOneJoin.ItemB itemB;

            ItemA() {}

            ItemA(int id, ManyToOneJoin.ItemB itemB) {
                this.id = id;
                this.itemB = itemB;
            }
        }
    }

    @Nested
    @DomainModel(annotatedClasses = {Item.class})
    class Unsupported extends AbstractQueryIntegrationTests {

        @Test
        void selectDistinctWithGroupByThrows() {
            assertSelectQueryFailure(
                    "select DISTINCT b.primitiveInt from Item as b GROUP BY b.primitiveInt",
                    Object.class,
                    FeatureNotSupportedException.class,
                    "SELECT DISTINCT is not supported");
        }
    }

    @Nested
    @DomainModel(annotatedClasses = {Item.class})
    class ExpressionKeys extends AbstractQueryIntegrationTests {

        @BeforeEach
        void beforeEach() {
            getSessionFactoryScope().inTransaction(session -> {
                session.createMutationQuery("delete from Item").executeUpdate();
                List.of(
                                new Item(1, 1, "a", true, new ItemStruct(1)),
                                new Item(2, 2, "b", false, new ItemStruct(2)),
                                new Item(3, 3, "c", true, new ItemStruct(3)),
                                new Item(4, 4, "d", false, new ItemStruct(4)))
                        .forEach(session::persist);
            });
            commandHistory.clear();
        }

        @Test
        void groupByArithmeticWholeMatch() {
            assertSelectionQuery(
                    "select b.primitiveInt + 1 from Item as b GROUP BY b.primitiveInt + 1",
                    Object.class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {"k0": {"$add": ["$primitiveInt", 1]}}}},
                        {"$project": {"#c_1": "$_id.k0"}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(2, 3, 4, 5),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void groupByArithmeticLeafRewriteOverColumnKey() {
            assertSelectionQuery(
                    "select b.primitiveInt + 1 from Item as b GROUP BY b.primitiveInt",
                    Object.class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {"primitiveInt": "$primitiveInt"}}},
                        {"$project": {"#c_1": {"$add": ["$_id.primitiveInt", 1]}}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(2, 3, 4, 5),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void groupByArithmeticCompositeOverKey() {
            assertSelectionQuery(
                    "select (b.primitiveInt + 1) * 2 from Item as b GROUP BY b.primitiveInt + 1",
                    Object.class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {"k0": {"$add": ["$primitiveInt", 1]}}}},
                        {"$project": {"#c_1": {"$multiply": ["$_id.k0", 2]}}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(4, 6, 8, 10),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void groupByArithmeticParentWinsOverLeafCanonicalization() {
            assertSelectionQuery(
                    "select b.primitiveInt + 1 from Item as b GROUP BY b.primitiveInt, b.primitiveInt + 1",
                    Object.class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {
                          "primitiveInt": "$primitiveInt",
                          "k1": {"$add": ["$primitiveInt", 1]}
                        }}},
                        {"$project": {"#c_1": "$_id.k1"}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(2, 3, 4, 5),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void groupByUnaryKey() {
            assertSelectionQuery(
                    "select -b.primitiveInt from Item as b GROUP BY -b.primitiveInt",
                    Object.class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {"k0": {"$multiply": [-1, "$primitiveInt"]}}}},
                        {"$project": {"#c_1": "$_id.k0"}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(-1, -2, -3, -4),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void selectExpressionAndColumnGroupByBothKeys() {
            assertSelectionQuery(
                    "select b.primitiveInt + 1, b.primitiveInt from Item as b GROUP BY b.primitiveInt, b.primitiveInt + 1",
                    Object[].class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {
                          "primitiveInt": "$primitiveInt",
                          "k1": {"$add": ["$primitiveInt", 1]}
                        }}},
                        {"$project": {"#c_1": "$_id.k1", "_id#primitiveInt": "$_id.primitiveInt"}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Object[]>) results)
                            .extracting(row -> List.of(row[0], row[1]))
                            .containsExactlyInAnyOrder(List.of(2, 1), List.of(3, 2), List.of(4, 3), List.of(5, 4)),
                    Set.of(COLLECTION_NAME));
        }

        // Documents current (unfixed) behavior for a stray column: b.primitiveInt appears in SELECT
        // but not in GROUP BY. The rewriter has no VN match for the raw column ref, so the project
        // spec is emitted as raw {primitiveInt: true} which references a field absent post-$group.
        // Stray-column detection is deferred; this test locks in the current output so future work
        // that fixes it will surface here.
        @Test
        void selectExpressionAndStrayColumn_currentBehavior() {
            assertSelectionQuery(
                    "select b.primitiveInt + 1, b.primitiveInt from Item as b GROUP BY b.primitiveInt + 1",
                    Object[].class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {"k0": {"$add": ["$primitiveInt", 1]}}}},
                        {"$project": {"#c_1": "$_id.k0", "primitiveInt": true}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Object[]>) results)
                            .extracting(row -> java.util.Arrays.asList(row[0], row[1]))
                            .containsExactlyInAnyOrder(
                                    java.util.Arrays.asList(2, null),
                                    java.util.Arrays.asList(3, null),
                                    java.util.Arrays.asList(4, null),
                                    java.util.Arrays.asList(5, null)),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void groupByArithmeticWithHaving() {
            assertSelectionQuery(
                    "select b.primitiveInt + 1 from Item as b GROUP BY b.primitiveInt + 1 HAVING b.primitiveInt + 1 > 2",
                    Object.class,
                    /*
                     If ExprToMatchDowngradeRule weren't wired, the emitted $match would be the $expr form: {"$match": {"$expr": {"$gt": ["$_id.k0", 2]}}}
                    */
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {"k0": {"$add": ["$primitiveInt", 1]}}}},
                        {"$match": {"_id.k0": {"$gt": 2}}},
                        {"$project": {"#c_1": "$_id.k0"}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(3, 4, 5),
                    Set.of(COLLECTION_NAME));
        }

        @Test
        void groupByArithmeticWithCompoundHaving() {
            assertSelectionQuery(
                    "select b.primitiveInt + 1 from Item as b GROUP BY b.primitiveInt + 1 "
                            + "HAVING b.primitiveInt + 1 > 2 AND b.primitiveInt + 1 < 5",
                    Object.class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {"$group": {"_id": {"k0": {"$add": ["$primitiveInt", 1]}}}},
                        {"$match": {"$and": [
                          {"_id.k0": {"$gt": 2}},
                          {"_id.k0": {"$lt": 5}}
                        ]}},
                        {"$project": {"#c_1": "$_id.k0"}}
                      ]
                    }
                    """,
                    results -> assertThat((Iterable<Integer>) results).containsExactlyInAnyOrder(3, 4),
                    Set.of(COLLECTION_NAME));
        }
    }

    @Test
    void testWithHaving() {
        assertSelectionQuery(
                "select b.primitiveInt from Item as b GROUP BY b.primitiveInt HAVING b.primitiveInt > 1 ORDER BY b.primitiveInt",
                Object.class,
                """
                {
                       "aggregate": "Item",
                       "pipeline": [
                         {
                           "$group": {
                             "_id": {
                               "primitiveInt": "$primitiveInt"
                             }
                           }
                         },
                         {
                           "$match": {
                             "_id.primitiveInt": {
                               "$gt": 1
                             }
                           }
                         },
                         {
                           "$sort": {
                             "_id.primitiveInt": 1
                           }
                         },
                         {
                           "$project": {
                             "_id#primitiveInt": "$_id.primitiveInt"
                           }
                         }
                       ]
                     }
                """,
                List.of(2, 3, 4),
                Set.of(COLLECTION_NAME));
    }

    @Test
    void testWithHavingWithStruct() {
        assertSelectionQuery(
                "select b.itemStruct.primitiveInt from Item as b GROUP BY b.itemStruct.primitiveInt HAVING b.itemStruct.primitiveInt > 1 ORDER BY b.itemStruct.primitiveInt",
                Object.class,
                """
                {
                  "aggregate": "Item",
                  "pipeline": [
                    {"$group": {"_id": {"itemStruct#primitiveInt": "$itemStruct.primitiveInt"}}},
                    {"$match": {"_id.itemStruct#primitiveInt": {"$gt": 1}}},
                    {"$sort": {"_id.itemStruct#primitiveInt": 1}},
                    {"$project": {"_id#itemStruct#primitiveInt": "$_id.itemStruct#primitiveInt"}}
                  ]
                }
                """,
                List.of(2, 3, 4),
                Set.of(COLLECTION_NAME));
    }

    @Test
    void testWithWhere() {
        assertSelectionQuery(
                "select b.primitiveInt from Item as b WHERE b.primitiveInt > 1 GROUP BY b.primitiveInt ORDER BY b.primitiveInt",
                Object.class,
                """
                {
                  "aggregate": "Item",
                  "pipeline": [
                    {"$match": {"primitiveInt": {"$gt": 1}}},
                    {"$group": {"_id": {"primitiveInt": "$primitiveInt"}}},
                    {"$sort": {"_id.primitiveInt": 1}},
                    {"$project": {"_id#primitiveInt": "$_id.primitiveInt"}}
                  ]
                }
                """,
                List.of(2, 3, 4),
                Set.of(COLLECTION_NAME));
    }

    @Test
    void testWithWhereWithStruct() {
        assertSelectionQuery(
                "select b.itemStruct.primitiveInt from Item as b WHERE b.itemStruct.primitiveInt > 1 GROUP BY b.itemStruct.primitiveInt ORDER BY b.itemStruct.primitiveInt",
                Object.class,
                """
                {
                  "aggregate": "Item",
                  "pipeline": [
                    {"$match": {"itemStruct.primitiveInt": {"$gt": 1}}},
                    {"$group": {"_id": {"itemStruct#primitiveInt": "$itemStruct.primitiveInt"}}},
                    {"$sort": {"_id.itemStruct#primitiveInt": 1}},
                    {"$project": {"_id#itemStruct#primitiveInt": "$_id.itemStruct#primitiveInt"}}
                  ]
                }
                """,
                List.of(2, 3, 4),
                Set.of(COLLECTION_NAME));
    }

    @Test
    void testGroupByIdColumn() {
        assertSelectionQuery(
                "select b.id from Item as b GROUP BY b.id ORDER BY b.id",
                Object.class,
                """
                {
                  "aggregate": "Item",
                  "pipeline": [
                    {"$group": {"_id": {"_id": "$_id"}}},
                    {"$sort": {"_id._id": 1}},
                    {"$project": {"_id#_id": "$_id._id"}}
                  ]
                }
                """,
                List.of(1, 2, 3, 4, 5, 6, 7, 8),
                Set.of(COLLECTION_NAME));
    }
}
