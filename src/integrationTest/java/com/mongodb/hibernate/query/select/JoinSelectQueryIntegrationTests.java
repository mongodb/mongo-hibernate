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
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.annotations.Struct;
import org.hibernate.query.sqm.InterpretationException;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            JoinSelectQueryIntegrationTests.Customer.class,
            JoinSelectQueryIntegrationTests.Order.class,
            JoinSelectQueryIntegrationTests.LineItem.class,
            JoinSelectQueryIntegrationTests.OrderWithArray.class
        })
class JoinSelectQueryIntegrationTests extends AbstractQueryIntegrationTests {

    @Entity(name = "Customer")
    @Table(name = "customers")
    static class Customer {
        @Id
        int id;

        String name;

        @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
        List<Order> orders = new ArrayList<>();

        Customer() {}

        Customer(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Entity(name = "Order")
    @Table(name = "orders")
    static class Order {
        @Id
        int id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "customerId")
        Customer customer;

        int total;

        @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
        List<LineItem> lineItems = new ArrayList<>();

        Order() {}

        Order(int id, Customer customer, int total) {
            this.id = id;
            this.customer = customer;
            this.total = total;
        }
    }

    @Entity(name = "LineItem")
    @Table(name = "line_items")
    static class LineItem {
        @Id
        int id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "orderId")
        Order order;

        int quantity;

        LineItem() {}

        LineItem(int id, Order order, int quantity) {
            this.id = id;
            this.order = order;
            this.quantity = quantity;
        }
    }

    @Entity(name = "OrderWithArray")
    @Table(name = "orders_with_array")
    static class OrderWithArray {
        @Id
        int id;

        @Embeddable
        @Struct(name = "OrderItem")
        static class OrderItem {
            String name;

            OrderItem() {}

            OrderItem(String name) {
                this.name = name;
            }
        }

        OrderItem[] items;

        OrderWithArray() {}

        OrderWithArray(int id, OrderItem[] items) {
            this.id = id;
            this.items = items;
        }
    }

    private Customer alice;
    private Customer bob;
    private Customer charlie;
    private Order order1;
    private Order order2;
    private Order order3;
    private LineItem lineItem1;
    private LineItem lineItem2;
    private LineItem lineItem3;

    @BeforeEach
    void setUp() {
        getSessionFactoryScope().inTransaction(session -> {
            alice = new Customer(1, "Alice");
            bob = new Customer(2, "Bob");
            charlie = new Customer(3, "Charlie");
            session.persist(alice);
            session.persist(bob);
            session.persist(charlie);

            order1 = new Order(1, alice, 100);
            order2 = new Order(2, bob, 200);
            order3 = new Order(3, bob, 300);
            session.persist(order1);
            session.persist(order2);
            session.persist(order3);

            lineItem1 = new LineItem(1, order1, 5);
            lineItem2 = new LineItem(2, order1, 10);
            lineItem3 = new LineItem(3, order2, 15);
            session.persist(lineItem1);
            session.persist(lineItem2);
            session.persist(lineItem3);
        });
        getTestCommandListener().clear();
    }

    @Test
    void testInnerJoin() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c JOIN c.orders o",
                Object[].class,
                """
                {
                  "aggregate": "customers",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "orders",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "o1_0"
                      }
                    },
                    { "$unwind": "$o1_0" },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0.total": true
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {2, 300}),
                Set.of("customers", "orders"));
    }

    @Test
    void testInnerJoinWithWhereOnJoinedTable() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c JOIN c.orders o WHERE o.total > 100",
                Object[].class,
                """
                {
                  "aggregate": "customers",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "orders",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "o1_0"
                      }
                    },
                    { "$unwind": "$o1_0" },
                    {
                      "$match": {
                        "o1_0.total": {
                          "$gt": { "$numberInt": "100" }
                        }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0.total": true
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {2, 200}, new Object[] {2, 300}),
                Set.of("customers", "orders"));
    }

    @Test
    void testInnerJoinWithWhereOnRootTable() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c JOIN c.orders o WHERE c.id = 1",
                Object[].class,
                """
                {
                  "aggregate": "customers",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "orders",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "o1_0"
                      }
                    },
                    { "$unwind": "$o1_0" },
                    {
                      "$match": {
                        "_id": { "$eq": { "$numberInt": "1" } }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0.total": true
                      }
                    }
                  ]
                }""",
                List.<Object[]>of(new Object[] {1, 100}),
                Set.of("customers", "orders"));
    }

    @Test
    void testLeftOuterJoin() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c LEFT JOIN c.orders o WHERE c.id = 1 OR c.id = 3",
                Object[].class,
                """
                {
                  "aggregate": "customers",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "orders",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "o1_0"
                      }
                    },
                    {
                      "$unwind": {
                        "path": "$o1_0",
                        "preserveNullAndEmptyArrays": true
                      }
                    },
                    {
                      "$match": {
                        "$or": [
                          { "_id": { "$eq": { "$numberInt": "1" } } },
                          { "_id": { "$eq": { "$numberInt": "3" } } }
                        ]
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0.total": true
                      }
                    }
                  ]
                }""",
                results -> assertIterableEq(List.of(new Object[] {1, 100}, new Object[] {3, null}), results),
                Set.of("customers", "orders"));
    }

    @Test
    void testJoinFetchManyToOne() {
        var orders = new ArrayList<Order>();
        getSessionFactoryScope().inTransaction(session -> {
            orders.addAll(session.createSelectionQuery("FROM Order o JOIN FETCH o.customer ORDER BY o.id", Order.class)
                    .getResultList());
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {
                              "aggregate": "orders",
                              "pipeline": [
                                {
                                  "$lookup": {
                                    "from": "customers",
                                    "localField": "customerId",
                                    "foreignField": "_id",
                                    "as": "c1_0"
                                  }
                                },
                                { "$unwind": "$c1_0" },
                                { "$sort": { "_id": { "$numberInt": "1" } } },
                                {
                                  "$project": {
                                    "_id": true,
                                    "c1_0._id": true,
                                    "c1_0.name": true,
                                    "total": true
                                  }
                                }
                              ]
                            }"""));
        });
        // Session is closed — these pass only if JOIN FETCH initialized the association
        assertThat(orders).hasSize(3);
        assertThat(orders.get(0).customer.name).isEqualTo("Alice");
        assertThat(orders.get(1).customer.name).isEqualTo("Bob");
        assertThat(orders.get(2).customer.name).isEqualTo("Bob");
    }

    @Test
    void testJoinFetchOneToMany() {
        var customers = new ArrayList<Customer>();
        getSessionFactoryScope().inTransaction(session -> {
            customers.addAll(
                    session.createSelectionQuery("FROM Customer c JOIN FETCH c.orders WHERE c.id = 1", Customer.class)
                            .getResultList());
            assertActualCommandsInOrder(
                    BsonDocument.parse(
                            """
                            {
                              "aggregate": "customers",
                              "pipeline": [
                                {
                                  "$lookup": {
                                    "from": "orders",
                                    "localField": "_id",
                                    "foreignField": "customerId",
                                    "as": "o1_0"
                                  }
                                },
                                { "$unwind": "$o1_0" },
                                {
                                  "$match": {
                                    "_id": { "$eq": { "$numberInt": "1" } }
                                  }
                                },
                                {
                                  "$project": {
                                    "_id": true,
                                    "name": true,
                                    "o1_0.customerId": true,
                                    "o1_0._id": true,
                                    "o1_0.total": true
                                  }
                                }
                              ]
                            }"""));
        });
        // Session is closed — these pass only if JOIN FETCH initialized the collection
        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).orders).hasSize(1);
        assertThat(customers.get(0).orders.get(0).total).isEqualTo(100);
    }

    @Test
    void testThreeWayInnerJoin() {
        assertSelectionQuery(
                "SELECT c.id, o.id, li.quantity FROM Customer c JOIN c.orders o JOIN o.lineItems li",
                Object[].class,
                """
                {
                  "aggregate": "customers",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "orders",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "o1_0"
                      }
                    },
                    { "$unwind": "$o1_0" },
                    {
                      "$lookup": {
                        "from": "line_items",
                        "localField": "o1_0._id",
                        "foreignField": "orderId",
                        "as": "li1_0"
                      }
                    },
                    { "$unwind": "$li1_0" },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0._id": true,
                        "li1_0.quantity": true
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {1, 1, 5}, new Object[] {1, 1, 10}, new Object[] {2, 2, 15}),
                Set.of("customers", "orders", "line_items"));
    }

    @Test
    void testTwoJoinsFromSameEntity() {
        assertSelectionQuery(
                "SELECT o.id, c.name, li.quantity FROM Order o JOIN o.customer c JOIN o.lineItems li WHERE o.id = 1",
                Object[].class,
                """
                {
                  "aggregate": "orders",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "customers",
                        "localField": "customerId",
                        "foreignField": "_id",
                        "as": "c1_0"
                      }
                    },
                    { "$unwind": "$c1_0" },
                    {
                      "$lookup": {
                        "from": "line_items",
                        "localField": "_id",
                        "foreignField": "orderId",
                        "as": "li1_0"
                      }
                    },
                    { "$unwind": "$li1_0" },
                    {
                      "$match": {
                        "_id": { "$eq": { "$numberInt": "1" } }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "c1_0.name": true,
                        "li1_0.quantity": true
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {1, "Alice", 5}, new Object[] {1, "Alice", 10}),
                Set.of("orders", "customers", "line_items"));
    }

    @Test
    void testOrderByJoinedColumn() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c JOIN c.orders o ORDER BY o.total DESC",
                Object[].class,
                """
                {
                  "aggregate": "customers",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "orders",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "o1_0"
                      }
                    },
                    { "$unwind": "$o1_0" },
                    {
                      "$sort": {
                        "o1_0.total": { "$numberInt": "-1" }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0.total": true
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {2, 300}, new Object[] {2, 200}, new Object[] {1, 100}),
                Set.of("customers", "orders"));
    }

    @Nested
    class Unsupported {

        @Test
        void testRightJoinThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c RIGHT JOIN Order o ON c = o.customer",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-161 https://jira.mongodb.org/browse/HIBERNATE-161");
        }

        @Test
        void testFullJoinThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c FULL JOIN Order o ON c = o.customer",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-162 https://jira.mongodb.org/browse/HIBERNATE-162");
        }

        @Test
        void testCrossJoinThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c CROSS JOIN Order o",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-163 https://jira.mongodb.org/browse/HIBERNATE-163");
        }

        @Test
        void testNonEquijoinThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON c.id > o.total",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-165 https://jira.mongodb.org/browse/HIBERNATE-165");
        }

        @Test
        void testCompoundOnConditionThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON c.id = o.id AND c.id < 100",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-164 https://jira.mongodb.org/browse/HIBERNATE-164");
        }

        @Test
        void testNonColumnOnExpressionThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON c.id = o.total + 1",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-166 https://jira.mongodb.org/browse/HIBERNATE-166");
        }

        @Test
        void testLateralUnnestThrows() {
            // TODO-HIBERNATE-111 https://jira.mongodb.org/browse/HIBERNATE-111
            // Hibernate 7.3 fails at HQL semantic translation (SQM level) with InterpretationException because the
            // MongoDB dialect does not register an "unnest" set-returning function descriptor. Our
            // FunctionTableReference guard in buildJoinStages is therefore unreachable via HQL for this construct.
            getSessionFactoryScope().fromTransaction(session -> org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> session.createSelectionQuery("FROM OrderWithArray o JOIN o.items i", Object[].class)
                                    .getResultList())
                    .isInstanceOf(InterpretationException.class));
        }

        @Test
        void testSubqueryJoinThrows() {
            // TODO-HIBERNATE-167 https://jira.mongodb.org/browse/HIBERNATE-167
            // HQL parser rejects subquery join syntax before translation with SemanticException
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN (SELECT o.id FROM Order o) ord ON c.id = ord.id",
                    Object[].class,
                    org.hibernate.query.SemanticException.class,
                    "Select item at position 1 in select list has no alias");
        }

        @Test
        void testEntitySyntaxJoinThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON c = o.customer",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-168 https://jira.mongodb.org/browse/HIBERNATE-168");
        }

        @Test
        void testOnConditionWithAssociationNavigationThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN c.orders o ON o.customer.name = 'Alice'",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-168 https://jira.mongodb.org/browse/HIBERNATE-168");
        }
    }
}
