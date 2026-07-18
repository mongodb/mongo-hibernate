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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

@DomainModel(annotatedClasses = {PredicateExpressionIntegrationTests.Widget.class})
class PredicateExpressionIntegrationTests extends AbstractQueryIntegrationTests {

    @Nested
    class Positive implements MongoServiceRegistryProducer {

        @BeforeEach
        void setUp() {
            getSessionFactoryScope().inTransaction(session -> {
                session.persist(new Widget(1, 10, 3, true, "alpha", 5));
                session.persist(new Widget(2, 4, 7, false, "beta", null));
                session.persist(new Widget(3, 5, 4, true, "gamma", 8));
            });
        }

        @Test
        void testAndJunction() {
            assertSelectionQuery(
                    "select x > 1 and y < 5 from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$and": [{"$gt": ["$x", {"$numberInt": "1"}]}, {"$lt": ["$y", {"$numberInt": "5"}]}]}}}
                      ]
                    }""",
                    List.of(true, false, true),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testOrJunction() {
            assertSelectionQuery(
                    "select x > 8 or y < 5 from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$or": [{"$gt": ["$x", {"$numberInt": "8"}]}, {"$lt": ["$y", {"$numberInt": "5"}]}]}}}
                      ]
                    }""",
                    List.of(true, false, true),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testNot() {
            assertSelectionQuery(
                    "select (x > 1) and not (y < 5) from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$and": [{"$gt": ["$x", {"$numberInt": "1"}]}, {"$not": [{"$lt": ["$y", {"$numberInt": "5"}]}]}]}}}
                      ]
                    }""",
                    List.of(false, true, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testBooleanField() {
            assertSelectionQuery(
                    "select (x > 1) and flag from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$and": [{"$gt": ["$x", {"$numberInt": "1"}]}, {"$eq": ["$flag", true]}]}}}
                      ]
                    }""",
                    List.of(true, false, true),
                    Set.of(Widget.COLLECTION_NAME));
        }

        // `not flag` is a BooleanExpressionPredicate with isNegated()=true (not a NegatedPredicate wrap),
        // so it renders as `$eq [flag, false]` — this covers the negated arm of visitBooleanExpressionPredicate.
        @Test
        void testNegatedBooleanField() {
            assertSelectionQuery(
                    "select (x > 1) and not flag from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$and": [{"$gt": ["$x", {"$numberInt": "1"}]}, {"$eq": ["$flag", false]}]}}}
                      ]
                    }""",
                    List.of(false, true, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testInListNumbers() {
            assertSelectionQuery(
                    "select x in (4, 5) from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$in": ["$x", [{"$numberInt": "4"}, {"$numberInt": "5"}]]}}}
                      ]
                    }""",
                    List.of(false, true, true),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testInListStrings() {
            assertSelectionQuery(
                    "select name in ('alpha', 'beta') from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$in": ["$name", ["alpha", "beta"]]}}}
                      ]
                    }""",
                    List.of(true, true, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testNotInList() {
            assertSelectionQuery(
                    "select x not in (4, 5) from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$not": [{"$in": ["$x", [{"$numberInt": "4"}, {"$numberInt": "5"}]]}]}}}
                      ]
                    }""",
                    List.of(true, false, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testInListWrapsDollarPrefixedOption() {
            assertSelectionQuery(
                    "select name in ('$x', 'alpha') from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$in": ["$name", [{"$literal": "$x"}, "alpha"]]}}}
                      ]
                    }""",
                    List.of(true, false, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testLike() {
            assertSelectionQuery(
                    "select name like 'a%' from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$regexMatch": {"input": "$name", "regex": "^a.*$", "options": "s"}}}}
                      ]
                    }""",
                    List.of(true, false, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testNotLike() {
            assertSelectionQuery(
                    "select name not like 'a%' from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$not": [{"$regexMatch": {"input": "$name", "regex": "^a.*$", "options": "s"}}]}}}
                      ]
                    }""",
                    List.of(false, true, true),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testIsNull() {
            assertSelectionQuery(
                    "select nn is null from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$eq": ["$nn", null]}}}
                      ]
                    }""",
                    List.of(false, true, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testIsNotNull() {
            assertSelectionQuery(
                    "select nn is not null from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$ne": ["$nn", null]}}}
                      ]
                    }""",
                    List.of(true, false, true),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testComputedOperandIsNull() {
            assertSelectionQuery(
                    "select (x + nn) is null from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$eq": [{"$add": ["$x", "$nn"]}, null]}}}
                      ]
                    }""",
                    List.of(false, true, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testComposed() {
            assertSelectionQuery(
                    "select (x > 1) and (name in ('alpha', 'beta')) from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$and": [{"$gt": ["$x", {"$numberInt": "1"}]}, {"$in": ["$name", ["alpha", "beta"]]}]}}}
                      ]
                    }""",
                    List.of(true, true, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testBetween() {
            assertSelectionQuery(
                    "select x between 4 and 6 from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$and": [{"$gte": ["$x", {"$numberInt": "4"}]}, {"$lte": ["$x", {"$numberInt": "6"}]}]}}}
                      ]
                    }""",
                    List.of(false, true, true),
                    Set.of(Widget.COLLECTION_NAME));
        }

        @Test
        void testNotBetween() {
            assertSelectionQuery(
                    "select x not between 4 and 6 from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$or": [{"$lt": ["$x", {"$numberInt": "4"}]}, {"$gt": ["$x", {"$numberInt": "6"}]}]}}}
                      ]
                    }""",
                    List.of(true, false, false),
                    Set.of(Widget.COLLECTION_NAME));
        }

        // A parameter in the BETWEEN operand appears in both bound comparisons, so its value must be
        // bound twice; visiting the operand once and reusing the node emits two markers but one binder.
        @Test
        void testParameterOperandBetween() {
            assertSelectionQuery(
                    "select (x + :p) between 10 and 16 from Widget",
                    Boolean.class,
                    q -> q.setParameter("p", 5),
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$and": [{"$gte": [{"$add": ["$x", {"$numberInt": "5"}]}, {"$numberInt": "10"}]}, {"$lte": [{"$add": ["$x", {"$numberInt": "5"}]}, {"$numberInt": "16"}]}]}}}
                      ]
                    }""",
                    List.of(true, false, true),
                    Set.of(Widget.COLLECTION_NAME));
        }

        // The expression form routes all three operands through acceptAndYieldExpression, so a computed
        // test operand works here even though the filter form requires a plain field path.
        @Test
        void testComputedOperandBetween() {
            assertSelectionQuery(
                    "select (x + y) between 8 and 12 from Widget",
                    Boolean.class,
                    """
                    {
                      "aggregate": "widgets",
                      "pipeline": [
                        {"$project": {"#c_1": {"$and": [{"$gte": [{"$add": ["$x", "$y"]}, {"$numberInt": "8"}]}, {"$lte": [{"$add": ["$x", "$y"]}, {"$numberInt": "12"}]}]}}}
                      ]
                    }""",
                    List.of(false, true, true),
                    Set.of(Widget.COLLECTION_NAME));
        }
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

        // IN-subquery is unsupported and `visitInSubQueryPredicate` throws a message-less
        // FeatureNotSupportedException, so assert the exception type only.
        @Test
        void testInSubqueryInSelectIsUnsupported() {
            getSessionFactoryScope().inTransaction(session -> assertThatThrownBy(() -> session.createSelectionQuery(
                                    "select x in (select w2.x from Widget w2) from Widget", Boolean.class)
                            .getResultList())
                    .isInstanceOf(FeatureNotSupportedException.class));
        }

        // A row-value / tuple comparison in select position reaches the `SqlTuple` guard in
        // `acceptAndYieldExpression` before it can be rendered as an aggregation expression.
        @Test
        void testTupleComparisonInSelectIsUnsupported() {
            getSessionFactoryScope().inTransaction(session -> assertThatThrownBy(
                            () -> session.createSelectionQuery("select (x, y) = (1, 2) from Widget", Boolean.class)
                                    .getResultList())
                    .isInstanceOf(FeatureNotSupportedException.class));
        }
    }

    @Entity(name = "Widget")
    @Table(name = Widget.COLLECTION_NAME)
    static class Widget {

        static final String COLLECTION_NAME = "widgets";

        @Id
        int id;

        int x;
        int y;
        boolean flag;
        String name;
        Integer nn;

        Widget() {}

        Widget(int id, int x, int y, boolean flag, String name, Integer nn) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.flag = flag;
            this.name = name;
            this.nn = nn;
        }
    }
}
