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
import static org.hibernate.cfg.AvailableSettings.DIALECT;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.cfg.QuerySettings;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = {ExpressionIntegrationTests.Item.class, ExpressionIntegrationTests.LongItem.class})
class ExpressionIntegrationTests extends AbstractQueryIntegrationTests {

    @Nested
    class Positive implements MongoServiceRegistryProducer {

        @BeforeEach
        void setUp() {
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new Item(1, 10, 3));
                session.persist(new Item(2, 4, 7));
                session.persist(new Item(3, 5, 4));
            });
            getTestCommandListener().clear();
        }

        @Test
        void testAddLiteralRhs() {
            assertSelectionQuery(
                    "select x + 1 from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$add": ["$x", {"$numberInt": "1"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(11, 5, 6),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testSubtractLiteralRhs() {
            assertSelectionQuery(
                    "select x - 1 from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$subtract": ["$x", {"$numberInt": "1"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(9, 3, 4),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testMultiply() {
            assertSelectionQuery(
                    "select x * 2 from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$multiply": ["$x", {"$numberInt": "2"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(20, 8, 10),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testDivide() {
            assertSelectionQuery(
                    "select x / 2.0 from Item",
                    Double.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$divide": ["$x", 2.0]}
                          }
                        }
                      ]
                    }""",
                    List.of(5.0, 2.0, 2.5),
                    Set.of(Item.COLLECTION_NAME));
        }

        // Integer division without PORTABLE_INTEGER_DIVISION: Hibernate infers an integer result type,
        // so $divide is truncated with $toInt (MongoDB's $divide would otherwise return a double).
        @Test
        void testIntegerDivision() {
            assertSelectionQuery(
                    "select x / y from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$toInt": {"$divide": ["$x", "$y"]}}
                          }
                        }
                      ]
                    }""",
                    List.of(3, 0, 1),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testNestedArithmetic() {
            assertSelectionQuery(
                    "select x * y + 1 from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$add": [{"$multiply": ["$x", "$y"]}, {"$numberInt": "1"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(31, 29, 21),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testAsAliasUsedAsProjectionKey() {
            assertSelectionQuery(
                    "select x + 1 as total from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "total": {"$add": ["$x", {"$numberInt": "1"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(11, 5, 6),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testNamedParameterOperand() {
            assertSelectionQuery(
                    "select :p + x from Item",
                    Integer.class,
                    q -> q.setParameter("p", 5),
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$add": [{"$numberInt": "5"}, "$x"]}
                          }
                        }
                      ]
                    }""",
                    List.of(15, 9, 10),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testMixedColumnAndComputedProjection() {
            assertSelectionQuery(
                    "select x, x + 1 from Item",
                    Object[].class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "x": true,
                            "#c_2": {"$add": ["$x", {"$numberInt": "1"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {10, 11}, new Object[] {4, 5}, new Object[] {5, 6}),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testArithmeticInWhere() {
            assertSelectionQuery(
                    "from Item where x + 1 > 5",
                    Item.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$match": {
                            "$expr": {"$gt": [{"$add": ["$x", {"$numberInt": "1"}]}, {"$numberInt": "5"}]}
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "x": true,
                            "y": true
                          }
                        }
                      ]
                    }""",
                    List.of(new Item(1, 10, 3), new Item(3, 5, 4)),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testFieldVsFieldComparison() {
            assertSelectionQuery(
                    "from Item where x > y",
                    Item.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$match": {
                            "$expr": {"$gt": ["$x", "$y"]}
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "x": true,
                            "y": true
                          }
                        }
                      ]
                    }""",
                    List.of(new Item(1, 10, 3), new Item(3, 5, 4)),
                    Set.of(Item.COLLECTION_NAME));
        }

        // A BETWEEN whose operand is computed cannot use the compact {field: {$gte/$lte}} form for its
        // bounds, so each bound falls back to $expr — with the AND hoisted to the filter level, the same
        // shape a mixed junction (testMixedAndPredicates) produces.
        @Test
        void testComputedOperandBetweenInWhere() {
            assertSelectionQuery(
                    "from Item where (x + 1) between 6 and 11",
                    Item.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {"$expr": {"$gte": [{"$add": ["$x", {"$numberInt": "1"}]}, {"$numberInt": "6"}]}},
                              {"$expr": {"$lte": [{"$add": ["$x", {"$numberInt": "1"}]}, {"$numberInt": "11"}]}}
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "x": true,
                            "y": true
                          }
                        }
                      ]
                    }""",
                    List.of(new Item(1, 10, 3), new Item(3, 5, 4)),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testMixedAndPredicates() {
            assertSelectionQuery(
                    "from Item where x + 1 > 5 and y = 3",
                    Item.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$match": {
                            "$and": [
                              {"$expr": {"$gt": [{"$add": ["$x", {"$numberInt": "1"}]}, {"$numberInt": "5"}]}},
                              {"y": {"$eq": {"$numberInt": "3"}}}
                            ]
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "x": true,
                            "y": true
                          }
                        }
                      ]
                    }""",
                    List.of(new Item(1, 10, 3)),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testBothSidesComputed() {
            assertSelectionQuery(
                    "from Item where x + 1 = y + 2",
                    Item.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$match": {
                            "$expr": {"$eq": [{"$add": ["$x", {"$numberInt": "1"}]}, {"$add": ["$y", {"$numberInt": "2"}]}]}
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "x": true,
                            "y": true
                          }
                        }
                      ]
                    }""",
                    List.of(new Item(3, 5, 4)),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testUnaryNegation() {
            assertSelectionQuery(
                    "select -x from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$multiply": [{"$numberInt": "-1"}, "$x"]}
                          }
                        }
                      ]
                    }""",
                    List.of(-10, -4, -5),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testUnaryPlus() {
            assertSelectionQuery(
                    "select +x from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": "$x"
                          }
                        }
                      ]
                    }""",
                    List.of(10, 4, 5),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testTwoComputedAliases() {
            assertSelectionQuery(
                    "select x + 1 as a, y + 2 as b from Item",
                    Object[].class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "a": {"$add": ["$x", {"$numberInt": "1"}]},
                            "b": {"$add": ["$y", {"$numberInt": "2"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {11, 5}, new Object[] {5, 9}, new Object[] {6, 6}),
                    Set.of(Item.COLLECTION_NAME));
        }

        // A backtick-quoted alias can be anything, including the "#c_<n>" shape we generate for
        // unaliased columns. The generated key for `y + 1` would be "#c_1", which collides with the
        // explicit `#c_1` alias on `x + 1`, so it is bumped to "#c_2" to keep the $project keys distinct.
        @Test
        void testGeneratedKeyAvoidsCollidingAlias() {
            assertSelectionQuery(
                    "select y + 1, x + 1 as `#c_1` from Item",
                    Object[].class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_2": {"$add": ["$y", {"$numberInt": "1"}]},
                            "#c_1": {"$add": ["$x", {"$numberInt": "1"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {4, 11}, new Object[] {8, 5}, new Object[] {5, 6}),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testAliasOnSecondComputedValue() {
            assertSelectionQuery(
                    "select x + 1, y + 2 as b from Item",
                    Object[].class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$add": ["$x", {"$numberInt": "1"}]},
                            "b": {"$add": ["$y", {"$numberInt": "2"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {11, 5}, new Object[] {5, 9}, new Object[] {6, 6}),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testAliasedScalarAlongsideEntitySelection() {
            assertSelectionQuery(
                    "select i, i.x + 1 as total from Item i",
                    Object[].class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "_id": true,
                            "x": true,
                            "y": true,
                            "total": {"$add": ["$x", {"$numberInt": "1"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {new Item(1, 10, 3), 11}, new Object[] {new Item(2, 4, 7), 5}, new Object[] {
                        new Item(3, 5, 4), 6
                    }),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testRelationalExpressionInSelect() {
            assertSelectionQuery(
                    "select x > 1 from Item",
                    Boolean.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$gt": ["$x", {"$numberInt": "1"}]}
                          }
                        }
                      ]
                    }""",
                    List.of(true, true, true),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testNotEqualExpressionInSelect() {
            assertSelectionQuery(
                    "select x <> 4 from Item",
                    Boolean.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {"$project": {"#c_1": {"$ne": ["$x", {"$numberInt": "4"}]}}}
                      ]
                    }""",
                    List.of(true, false, true),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testLessThanExpressionInSelect() {
            assertSelectionQuery(
                    "select x < 5 from Item",
                    Boolean.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {"$project": {"#c_1": {"$lt": ["$x", {"$numberInt": "5"}]}}}
                      ]
                    }""",
                    List.of(false, true, false),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testLessThanOrEqualExpressionInSelect() {
            assertSelectionQuery(
                    "select x <= 5 from Item",
                    Boolean.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {"$project": {"#c_1": {"$lte": ["$x", {"$numberInt": "5"}]}}}
                      ]
                    }""",
                    List.of(false, true, true),
                    Set.of(Item.COLLECTION_NAME));
        }

        @Test
        void testGreaterThanOrEqualExpressionInSelect() {
            assertSelectionQuery(
                    "select x >= 5 from Item",
                    Boolean.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {"$project": {"#c_1": {"$gte": ["$x", {"$numberInt": "5"}]}}}
                      ]
                    }""",
                    List.of(true, false, true),
                    Set.of(Item.COLLECTION_NAME));
        }

        // A `$`-prefixed string literal in expression position must be wrapped in $literal, else MongoDB
        // reads it as a field path; a plain string is left verbatim. Both appear here, side by side.
        @Test
        void testDollarPrefixedStringLiteralIsWrappedInExpression() {
            assertSelectionQuery(
                    "select 'a' = '$foo' from Item",
                    Boolean.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {"$project": {"#c_1": {"$eq": ["a", {"$literal": "$foo"}]}}}
                      ]
                    }""",
                    List.of(false, false, false),
                    Set.of(Item.COLLECTION_NAME));
        }

        // A string-typed parameter could bind a $-prefixed value, and its value is unknown at translation
        // time, so in expression position it is wrapped in $literal (unlike a numeric parameter).
        @Test
        void testStringParameterIsWrappedInExpression() {
            assertSelectionQuery(
                    "select 'a' = :p from Item",
                    Boolean.class,
                    q -> q.setParameter("p", "$x"),
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {"$project": {"#c_1": {"$eq": ["a", {"$literal": "$x"}]}}}
                      ]
                    }""",
                    List.of(false, false, false),
                    Set.of(Item.COLLECTION_NAME));
        }
    }

    // This case needs PORTABLE_INTEGER_DIVISION=true, which the shared registry does not set, so it
    // declares its own @ServiceRegistry (and thus its own dialect binding) rather than reusing the
    // outer MongoServiceRegistryProducer setup like the other nested classes.
    @Nested
    @DomainModel(annotatedClasses = Item.class)
    @ServiceRegistry(
            settings = {
                @Setting(name = QuerySettings.PORTABLE_INTEGER_DIVISION, value = "true"),
                @Setting(
                        name = DIALECT,
                        value = "com.mongodb.hibernate.query.AbstractQueryIntegrationTests$TranslateResultAwareDialect")
            })
    class PortableIntegerDivision extends AbstractQueryIntegrationTests {

        @BeforeEach
        void setUp() {
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new Item(1, 10, 3));
                session.persist(new Item(2, 4, 7));
                session.persist(new Item(3, 5, 4));
            });
            getTestCommandListener().clear();
        }

        @Test
        void testDividePortable() {
            assertSelectionQuery(
                    "select x / y from Item",
                    Integer.class,
                    """
                    {
                      "aggregate": "items",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$toInt": {"$divide": ["$x", "$y"]}}
                          }
                        }
                      ]
                    }""",
                    List.of(3, 0, 1),
                    Set.of(Item.COLLECTION_NAME));
        }
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        @Test
        void testSearchedCaseExpressionInSelect() {
            assertSelectQueryFailure(
                    "select case when x > 5 then 1 else 0 end from Item",
                    Integer.class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-83");
        }

        @Test
        void testSimpleCaseExpressionInSelect() {
            assertSelectQueryFailure(
                    "select case x when 5 then 1 else 0 end from Item",
                    Integer.class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-83");
        }

        @Test
        void testFunctionCallAsArithmeticOperandIsUnsupported() {
            assertSelectQueryFailure(
                    "select abs(x) + 1 from Item",
                    Integer.class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-196");
        }

        // HQL's `%` is rewritten to a mod() function call (not the MODULO operator), so it hits the
        // unsupported-function path, unlike Criteria's cb.mod which produces the MODULO operator.
        @Test
        void testHqlModuloIsUnsupported() {
            assertSelectQueryFailure(
                    "select x % 3 from Item", Integer.class, FeatureNotSupportedException.class, "TODO-HIBERNATE-196");
        }

        @Test
        void testCaseExpressionInWhereComparison() {
            assertSelectQueryFailure(
                    "from Item where case when x > 5 then 1 else 0 end > 3",
                    Item.class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-83");
        }
    }

    // The Criteria API is a supported entry point. It produces some SQL AST operators that HQL never
    // emits — CriteriaBuilder.quot -> QUOT (HQL uses DIVIDE) and CriteriaBuilder.mod -> the MODULO
    // operator (HQL rewrites % to a mod() function call). These tests exercise those operators, which
    // are unreachable through the HQL-based helpers.
    @Nested
    class CriteriaApi implements MongoServiceRegistryProducer {

        @BeforeEach
        void setUp() {
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new Item(1, 10, 3));
                session.persist(new Item(2, 4, 7));
                session.persist(new Item(3, 5, 4));
            });
            getTestCommandListener().clear();
        }

        // quot on integer operands is integer division: Hibernate infers an integer result type, so
        // the $divide is truncated with $toInt (the same output HQL's `/` produces on integers).
        @Test
        void testQuotIsIntegerDivision() {
            getSessionFactoryScope().inTransaction(session -> {
                var cb = session.getCriteriaBuilder();
                var query = cb.createQuery(Number.class);
                var item = query.from(Item.class);
                query.select(cb.quot(item.get("x"), item.get("y")));

                var results = session.createSelectionQuery(query).getResultList();

                assertActualCommandsInOrder(
                        BsonDocument.parse(
                                """
                                {
                                  "aggregate": "items",
                                  "pipeline": [
                                    {"$project": {"#c_1": {"$toInt": {"$divide": ["$x", "$y"]}}}}
                                  ]
                                }"""));
                assertThat(results).containsExactly(3, 0, 1);
            });
        }

        @Test
        void testModIsModulo() {
            getSessionFactoryScope().inTransaction(session -> {
                var cb = session.getCriteriaBuilder();
                var query = cb.createQuery(Integer.class);
                var item = query.from(Item.class);
                query.select(cb.mod(item.get("x"), item.get("y")));

                var results = session.createSelectionQuery(query).getResultList();

                assertActualCommandsInOrder(
                        BsonDocument.parse(
                                """
                                {
                                  "aggregate": "items",
                                  "pipeline": [
                                    {"$project": {"#c_1": {"$mod": ["$x", "$y"]}}}
                                  ]
                                }"""));
                assertThat(results).containsExactly(1, 4, 1);
            });
        }
    }

    @Nested
    class LongDivision implements MongoServiceRegistryProducer {

        @BeforeEach
        void setUp() {
            getSessionFactoryScope().inTransaction(session -> session.persist(new LongItem(1, 10L, 3L)));
            getTestCommandListener().clear();
        }

        // A long/long division has a BIGINT result type, so it truncates with $toLong (not $toInt),
        // which reads back correctly as a 64-bit value.
        @Test
        void testLongIntegerDivision() {
            assertSelectionQuery(
                    "select a / b from LongItem",
                    Long.class,
                    """
                    {
                      "aggregate": "longitems",
                      "pipeline": [
                        {
                          "$project": {
                            "#c_1": {"$toLong": {"$divide": ["$a", "$b"]}}
                          }
                        }
                      ]
                    }""",
                    List.of(3L),
                    Set.of(LongItem.COLLECTION_NAME));
        }
    }

    @Entity(name = "Item")
    @Table(name = Item.COLLECTION_NAME)
    static class Item {

        static final String COLLECTION_NAME = "items";

        @Id
        int id;

        int x;
        int y;

        Item() {}

        Item(int id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    @Entity(name = "LongItem")
    @Table(name = LongItem.COLLECTION_NAME)
    static class LongItem {

        static final String COLLECTION_NAME = "longitems";

        @Id
        int id;

        long a;
        long b;

        LongItem() {}

        LongItem(int id, long a, long b) {
            this.id = id;
            this.a = a;
            this.b = b;
        }
    }
}
