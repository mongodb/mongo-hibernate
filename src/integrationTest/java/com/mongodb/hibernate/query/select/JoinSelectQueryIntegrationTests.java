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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoServiceRegistryProducer;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SecondaryTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterJoinTable;
import org.hibernate.annotations.JoinFormula;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(
        annotatedClasses = {
            JoinSelectQueryIntegrationTests.Customer.class,
            JoinSelectQueryIntegrationTests.Order.class,
            JoinSelectQueryIntegrationTests.LineItem.class
        })
class JoinSelectQueryIntegrationTests extends AbstractQueryIntegrationTests {

    @Entity(name = "Customer")
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

    @BeforeEach
    void beforeEach() {
        var customers = List.of(new Customer(1, "Alice"), new Customer(2, "Bob"), new Customer(3, "Charlie"));
        var orders = List.of(
                new Order(1, customers.get(0), 100),
                new Order(2, customers.get(1), 200),
                new Order(3, customers.get(1), 300));
        var lineItems = List.of(
                new LineItem(1, orders.get(0), 5),
                new LineItem(2, orders.get(0), 10),
                new LineItem(3, orders.get(1), 15));
        getSessionFactoryScope().inTransaction(session -> {
            customers.forEach(session::persist);
            orders.forEach(session::persist);
            lineItems.forEach(session::persist);
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
                  "aggregate": "Customer",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Order",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "#o1_0"
                      }
                    },
                    { "$unwind": "$#o1_0" },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0#total": "$#o1_0.total"
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {2, 300}),
                Set.of("Customer", "Order"));
    }

    @Test
    void testInnerJoinWithWhereOnJoinedTable() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c JOIN c.orders o WHERE o.total > 100",
                Object[].class,
                """
                {
                  "aggregate": "Customer",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Order",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "#o1_0"
                      }
                    },
                    { "$unwind": "$#o1_0" },
                    {
                      "$match": {
                        "#o1_0.total": {
                          "$gt": { "$numberInt": "100" }
                        }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0#total": "$#o1_0.total"
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {2, 200}, new Object[] {2, 300}),
                Set.of("Customer", "Order"));
    }

    @Test
    void testInnerJoinWithWhereOnRootTable() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c JOIN c.orders o WHERE c.id = 1",
                Object[].class,
                """
                {
                  "aggregate": "Customer",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Order",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "#o1_0"
                      }
                    },
                    { "$unwind": "$#o1_0" },
                    {
                      "$match": {
                        "_id": { "$eq": { "$numberInt": "1" } }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0#total": "$#o1_0.total"
                      }
                    }
                  ]
                }""",
                List.<Object[]>of(new Object[] {1, 100}),
                Set.of("Customer", "Order"));
    }

    @Test
    void testLeftOuterJoin() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c LEFT JOIN c.orders o WHERE c.id = 1 OR c.id = 3",
                Object[].class,
                """
                {
                  "aggregate": "Customer",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Order",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "#o1_0"
                      }
                    },
                    {
                      "$unwind": {
                        "path": "$#o1_0",
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
                        "o1_0#total": "$#o1_0.total"
                      }
                    }
                  ]
                }""",
                results -> assertIterableEq(List.of(new Object[] {1, 100}, new Object[] {3, null}), results),
                Set.of("Customer", "Order"));
    }

    @Test
    void testJoinFetchManyToOne() {
        var orders = assertSelectionQuery(
                "FROM Order o JOIN FETCH o.customer ORDER BY o.id",
                Order.class,
                """
                {
                  "aggregate": "Order",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Customer",
                        "localField": "customerId",
                        "foreignField": "_id",
                         "as": "#c1_0"
                      }
                    },
                     { "$unwind": "$#c1_0" },
                    { "$sort": { "_id": { "$numberInt": "1" } } },
                    {
                      "$project": {
                        "_id": true,
                        "c1_0#_id": "$#c1_0._id",
                        "c1_0#name": "$#c1_0.name",
                        "total": true
                      }
                    }
                  ]
                }""");
        // Session is closed — these pass only if JOIN FETCH initialized the association
        assertThat(orders).hasSize(3);
        assertThat(orders.get(0).customer.name).isEqualTo("Alice");
        assertThat(orders.get(1).customer.name).isEqualTo("Bob");
        assertThat(orders.get(2).customer.name).isEqualTo("Bob");
    }

    @Test
    void testJoinFetchOneToMany() {
        var customers = assertSelectionQuery(
                "FROM Customer c JOIN FETCH c.orders WHERE c.id = 1",
                Customer.class,
                """
                {
                  "aggregate": "Customer",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Order",
                        "localField": "_id",
                        "foreignField": "customerId",
                         "as": "#o1_0"
                      }
                    },
                     { "$unwind": "$#o1_0" },
                    {
                      "$match": {
                        "_id": { "$eq": { "$numberInt": "1" } }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "name": true,
                        "o1_0#customerId": "$#o1_0.customerId",
                        "o1_0#_id": "$#o1_0._id",
                        "o1_0#total": "$#o1_0.total"
                      }
                    }
                  ]
                }""");
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
                  "aggregate": "Customer",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Order",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "#o1_0"
                      }
                    },
                    { "$unwind": "$#o1_0" },
                    {
                      "$lookup": {
                        "from": "LineItem",
                        "localField": "#o1_0._id",
                        "foreignField": "orderId",
                        "as": "#li1_0"
                      }
                    },
                    { "$unwind": "$#li1_0" },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0#_id": "$#o1_0._id",
                        "li1_0#quantity": "$#li1_0.quantity"
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {1, 1, 5}, new Object[] {1, 1, 10}, new Object[] {2, 2, 15}),
                Set.of("Customer", "Order", "LineItem"));
    }

    @Test
    void testTwoJoinsFromSameEntity() {
        assertSelectionQuery(
                "SELECT o.id, c.name, li.quantity FROM Order o JOIN o.customer c JOIN o.lineItems li WHERE o.id = 1",
                Object[].class,
                """
                {
                  "aggregate": "Order",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Customer",
                        "localField": "customerId",
                        "foreignField": "_id",
                        "as": "#c1_0"
                      }
                    },
                    { "$unwind": "$#c1_0" },
                    {
                      "$lookup": {
                        "from": "LineItem",
                        "localField": "_id",
                        "foreignField": "orderId",
                        "as": "#li1_0"
                      }
                    },
                    { "$unwind": "$#li1_0" },
                    {
                      "$match": {
                        "_id": { "$eq": { "$numberInt": "1" } }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "c1_0#name": "$#c1_0.name",
                        "li1_0#quantity": "$#li1_0.quantity"
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {1, "Alice", 5}, new Object[] {1, "Alice", 10}),
                Set.of("Order", "Customer", "LineItem"));
    }

    @Test
    void testOrderByJoinedColumn() {
        assertSelectionQuery(
                "SELECT c.id, o.total FROM Customer c JOIN c.orders o ORDER BY o.total DESC",
                Object[].class,
                """
                {
                  "aggregate": "Customer",
                  "pipeline": [
                    {
                      "$lookup": {
                        "from": "Order",
                        "localField": "_id",
                        "foreignField": "customerId",
                        "as": "#o1_0"
                      }
                    },
                    { "$unwind": "$#o1_0" },
                    {
                      "$sort": {
                        "#o1_0.total": { "$numberInt": "-1" }
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "o1_0#total": "$#o1_0.total"
                      }
                    }
                  ]
                }""",
                List.of(new Object[] {2, 300}, new Object[] {2, 200}, new Object[] {1, 100}),
                Set.of("Customer", "Order"));
    }

    @Nested
    class NonEquijoin implements MongoServiceRegistryProducer {

        @Test
        void testInnerLessThan() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON c.id < o.id ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$lt": [ "$$v0_c1_0__id", "$_id" ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#o1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "o1_0#total": "$#o1_0.total"
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {1, 200}, new Object[] {1, 300}, new Object[] {2, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testOperatorInversion() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON o.id > c.id ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$lt": [ "$$v0_c1_0__id", "$_id" ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#o1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "o1_0#total": "$#o1_0.total"
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {1, 200}, new Object[] {1, 300}, new Object[] {2, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testGreaterThanOrEqual() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON c.id >= o.id ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$gte": [ "$$v0_c1_0__id", "$_id" ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#o1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "o1_0#total": "$#o1_0.total"
                          }
                        }
                      ]
                    }""",
                    List.of(
                            new Object[] {1, 100},
                            new Object[] {2, 100},
                            new Object[] {2, 200},
                            new Object[] {3, 100},
                            new Object[] {3, 200},
                            new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testGreaterThan() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON c.id > o.id ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$gt": [ "$$v0_c1_0__id", "$_id" ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#o1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "o1_0#total": "$#o1_0.total"
                          }
                        }
                      ]
                    }""",
                    List.of(new Object[] {2, 100}, new Object[] {3, 100}, new Object[] {3, 200}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testLessThanOrEqual() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON c.id <= o.id ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$lte": [ "$$v0_c1_0__id", "$_id" ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#o1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "o1_0#total": "$#o1_0.total"
                          }
                        }
                      ]
                    }""",
                    List.of(
                            new Object[] {1, 100},
                            new Object[] {1, 200},
                            new Object[] {1, 300},
                            new Object[] {2, 200},
                            new Object[] {2, 300},
                            new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testNotEqual() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON c.id != o.id ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$ne": [ "$$v0_c1_0__id", "$_id" ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#o1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "o1_0#total": "$#o1_0.total"
                          }
                        }
                      ]
                    }""",
                    List.of(
                            new Object[] {1, 200},
                            new Object[] {1, 300},
                            new Object[] {2, 100},
                            new Object[] {2, 300},
                            new Object[] {3, 100},
                            new Object[] {3, 200}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testNonIdColumnsOnBothSides() {
            assertSelectionQuery(
                    "SELECT o.id, li.id FROM Order o JOIN LineItem li ON o.total > li.quantity ORDER BY o.id, li.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Order",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "LineItem",
                            "let": { "v0_o1_0_total": "$total" },
                            "pipeline": [
                              { "$match": { "$expr": { "$gt": [ "$$v0_o1_0_total", "$quantity" ] } } }
                            ],
                            "as": "#li1_0"
                          }
                        },
                        { "$unwind": "$#li1_0" },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#li1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "li1_0#_id": "$#li1_0._id"
                          }
                        }
                      ]
                    }""",
                    List.of(
                            new Object[] {1, 1},
                            new Object[] {1, 2},
                            new Object[] {1, 3},
                            new Object[] {2, 1},
                            new Object[] {2, 2},
                            new Object[] {2, 3},
                            new Object[] {3, 1},
                            new Object[] {3, 2},
                            new Object[] {3, 3}),
                    Set.of("Order", "LineItem"));
        }

        @Test
        void testLeftOuterNonEquijoin() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c LEFT JOIN Order o ON c.id < o.id ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$lt": [ "$$v0_c1_0__id", "$_id" ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        {
                          "$unwind": {
                            "path": "$#o1_0",
                            "preserveNullAndEmptyArrays": true
                          }
                        },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#o1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "o1_0#total": "$#o1_0.total"
                          }
                        }
                      ]
                    }""",
                    results -> assertIterableEq(
                            List.of(new Object[] {1, 200}, new Object[] {1, 300}, new Object[] {2, 300}, new Object[] {
                                3, null
                            }),
                            results),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testChainedNonEquijoin() {
            assertSelectionQuery(
                    """
                            SELECT c.id, o.id, li.id FROM Customer c JOIN Order o ON c.id < o.id
                            JOIN LineItem li ON o.id < li.id ORDER BY c.id, o.id, li.id
                    """,
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$lt": [ "$$v0_c1_0__id", "$_id" ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        {
                          "$lookup": {
                            "from": "LineItem",
                            "let": { "v1_o1_0__id": "$#o1_0._id" },
                            "pipeline": [
                              { "$match": { "$expr": { "$lt": [ "$$v1_o1_0__id", "$_id" ] } } }
                            ],
                            "as": "#li1_0"
                          }
                        },
                        { "$unwind": "$#li1_0" },
                        {
                          "$sort": {
                            "_id": { "$numberInt": "1" },
                            "#o1_0._id": { "$numberInt": "1" },
                            "#li1_0._id": { "$numberInt": "1" }
                          }
                        },
                        {
                          "$project": {
                            "_id": true,
                            "o1_0#_id": "$#o1_0._id",
                            "li1_0#_id": "$#li1_0._id"
                          }
                        }
                      ]
                    }""",
                    List.<Object[]>of(new Object[] {1, 2, 3}),
                    Set.of("Customer", "Order", "LineItem"));
        }
    }

    @Nested
    @DomainModel(annotatedClasses = {ManyToManyJoin.ItemA.class, ManyToManyJoin.ItemB.class})
    class ManyToManyJoin extends AbstractQueryIntegrationTests {

        private static final List<ItemB> TESTING_ITEMS_B = List.of(new ItemB(1, "a"), new ItemB(2, "b"));
        private static final List<ItemA> TESTING_ITEMS_A = List.of(new ItemA(1, TESTING_ITEMS_B));

        @BeforeEach
        void beforeEach() {
            getSessionFactoryScope().inTransaction(session -> {
                TESTING_ITEMS_B.forEach(session::persist);
                TESTING_ITEMS_A.forEach(session::persist);
            });
            getTestCommandListener().clear();
        }

        @Test
        void testManyToManyJoin() {
            assertSelectionQuery(
                    "FROM ItemA i JOIN FETCH i.itemBs ORDER BY i.id",
                    ItemA.class,
                    """
                    {
                      "aggregate": "ItemA",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "ItemA_ItemB",
                            "localField": "_id",
                            "foreignField": "ItemA_id",
                            "as": "#ib1_0"
                          }
                        },
                        { "$unwind": "$#ib1_0" },
                        {
                          "$lookup": {
                            "from": "ItemB",
                            "localField": "#ib1_0.itemBs_id",
                            "foreignField": "_id",
                            "as": "#ib1_1"
                          }
                        },
                        { "$unwind": "$#ib1_1" },
                        { "$sort": { "_id": { "$numberInt": "1" } } },
                        {
                          "$project": {
                            "_id": true,
                            "ib1_0#ItemA_id": "$#ib1_0.ItemA_id",
                            "ib1_1#_id": "$#ib1_1._id",
                            "ib1_1#name": "$#ib1_1.name"
                          }
                        }
                      ]
                    }""",
                    TESTING_ITEMS_A,
                    Set.of("ItemA", "ItemA_ItemB", "ItemB"));
        }

        @Entity(name = "ItemA")
        static class ItemA {
            @Id
            int id;

            @ManyToMany
            List<ItemB> itemBs = new ArrayList<>();

            public ItemA() {}

            ItemA(int id, List<ItemB> itemBs) {
                this.id = id;
                this.itemBs = new ArrayList<>(itemBs);
            }
        }

        @Entity(name = "ItemB")
        static class ItemB {
            @Id
            int id;

            String name;

            public ItemB() {}

            ItemB(int id, String name) {
                this.id = id;
                this.name = name;
            }
        }
    }

    @Nested
    @DomainModel(annotatedClasses = ItemWithElementCollection.class)
    class ElementCollectionJoin extends AbstractQueryIntegrationTests {

        // not static final: Hibernate's persist() mutates entity objects, replacing collections with PersistentBag
        private static List<ItemWithElementCollection> testingItems;

        @BeforeEach
        void beforeEach() {
            testingItems = List.of(new ItemWithElementCollection(1, List.of("java", "mongodb")));
            getSessionFactoryScope().inTransaction(session -> testingItems.forEach(session::persist));
            getTestCommandListener().clear();
        }

        @Test
        void testElementCollectionJoin() {
            assertSelectionQuery(
                    "FROM Item i JOIN i.stringsList t",
                    ItemWithElementCollection.class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Item_stringsList",
                            "localField": "_id",
                            "foreignField": "Item_id",
                            "as": "#sl1_0"
                          }
                        },
                        { "$unwind": "$#sl1_0" },
                        {
                          "$project": {
                            "_id": true
                          }
                        }
                      ]
                    }""",
                    testingItems,
                    Set.of("Item", "Item_stringsList"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testElementCollectionJoinSelectValue() {
            assertSelectionQuery(
                    "SELECT t FROM Item i JOIN i.stringsList t",
                    String.class,
                    """
                    {
                      "aggregate": "Item",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Item_stringsList",
                            "localField": "_id",
                            "foreignField": "Item_id",
                            "as": "#sl1_0"
                          }
                        },
                        { "$unwind": "$#sl1_0" },
                        {
                          "$project": {
                            "sl1_0#stringsList": "$#sl1_0.stringsList"
                          }
                        }
                      ]
                    }""",
                    results -> assertThat((List<String>) results).containsExactlyInAnyOrder("java", "mongodb"),
                    Set.of("Item", "Item_stringsList"));
        }
    }

    @Entity(name = "Item")
    static class ItemWithElementCollection {
        @Id
        int id;

        @ElementCollection
        List<String> stringsList = new ArrayList<>();

        protected ItemWithElementCollection() {}

        ItemWithElementCollection(int id, List<String> stringsList) {
            this.id = id;
            this.stringsList = new ArrayList<>(stringsList);
        }
    }

    @Nested
    @DomainModel(annotatedClasses = {OneToOneJoin.ItemA.class, OneToOneJoin.ItemB.class})
    class OneToOneJoin extends AbstractQueryIntegrationTests {

        private static final List<ItemA> TESTING_ITEMS =
                List.of(new ItemA(1, new ItemB(1, "c1")), new ItemA(2, new ItemB(2, "c2")));

        @BeforeEach
        void beforeEach() {
            getSessionFactoryScope().inTransaction(session -> {
                TESTING_ITEMS.stream().map(itemA -> itemA.itemB).forEach(session::persist);
                TESTING_ITEMS.forEach(session::persist);
            });
            getTestCommandListener().clear();
        }

        @Test
        void testOneToOneJoin() {
            assertSelectionQuery(
                    "FROM ItemA a JOIN FETCH a.itemB ORDER BY a.id",
                    ItemA.class,
                    """
                    {
                      "aggregate": "ItemA",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "ItemB",
                            "localField": "itemB_id",
                            "foreignField": "_id",
                            "as": "#ib1_0"
                          }
                        },
                        { "$unwind": "$#ib1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" } } },
                        {
                          "$project": {
                            "_id": true,
                            "ib1_0#_id": "$#ib1_0._id",
                            "ib1_0#string": "$#ib1_0.string"
                          }
                        }
                      ]
                    }""",
                    TESTING_ITEMS,
                    Set.of("ItemA", "ItemB"));
        }

        @Entity(name = "ItemB")
        static class ItemB {
            @Id
            int id;

            String string;

            ItemB() {}

            ItemB(int id, String string) {
                this.id = id;
                this.string = string;
            }
        }

        @Entity(name = "ItemA")
        static class ItemA {
            @Id
            int id;

            @OneToOne
            ItemB itemB;

            ItemA() {}

            ItemA(int id, ItemB itemB) {
                this.id = id;
                this.itemB = itemB;
            }
        }
    }

    @Nested
    class Unsupported implements MongoServiceRegistryProducer {

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
        void testBetweenOnConditionThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON c.id BETWEEN o.id AND o.total",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-200 https://jira.mongodb.org/browse/HIBERNATE-200");
        }

        @Test
        void testIsNullOnConditionThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON o.total IS NULL",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-200 https://jira.mongodb.org/browse/HIBERNATE-200");
        }

        @Test
        void testIsDistinctFromOnConditionThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON c.id IS DISTINCT FROM o.id",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-200 https://jira.mongodb.org/browse/HIBERNATE-200");
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
        void testLateralJoinThrows() {
            assertSelectQueryFailure(
                    "FROM Customer c LEFT JOIN LATERAL ("
                            + "SELECT o.total as t FROM c.orders o ORDER BY o.total DESC LIMIT 1"
                            + ") AS top",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-167");
        }

        @Test
        void testRootEntityJoinSyntaxThrows() {
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

        @Test
        void testOnConditionWithBothSidesFromOuterTableThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON c.id = c.id",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-170 https://jira.mongodb.org/browse/HIBERNATE-170");
        }

        @Test
        void testOnConditionWithBothSidesFromJoinedTableThrows() {
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON o.id = o.total",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-170 https://jira.mongodb.org/browse/HIBERNATE-170");
        }

        @Nested
        @DomainModel(
                annotatedClasses = {
                    TablePerClassInheritanceJoin.Item.class,
                    TablePerClassInheritanceJoin.ItemWithInheritance.class,
                    TablePerClassInheritanceJoin.ItemA.class,
                    TablePerClassInheritanceJoin.ItemB.class
                })
        class TablePerClassInheritanceJoin extends AbstractQueryIntegrationTests {
            @Test
            void testTablePerClassJoinThrows() {
                assertThat(assertThrows(RuntimeException.class, () -> getSessionFactoryScope()
                                .getSessionFactory()))
                        .rootCause()
                        .isInstanceOf(FeatureNotSupportedException.class)
                        .hasMessage("TABLE_PER_CLASS inheritance is not supported");
            }

            @Entity(name = "ItemWithInheritance")
            @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
            static class ItemWithInheritance {
                @Id
                int id;
            }

            @Entity(name = "ItemA")
            static class ItemA extends ItemWithInheritance {
                String string;
            }

            @Entity(name = "ItemB")
            static class ItemB extends ItemWithInheritance {
                String string;
            }

            @Entity(name = "Item")
            static class Item {
                @Id
                int id;

                @ManyToOne
                ItemWithInheritance itemWithInheritance;
            }
        }

        @Nested
        @DomainModel(annotatedClasses = ItemWithArray.class)
        class LateralUnnestJoin extends AbstractQueryIntegrationTests {
            @Test
            void testLateralUnnestThrows() {
                assertSelectQueryFailure(
                        "FROM ItemWithArray o JOIN o.itemStructs i",
                        Object[].class,
                        FeatureNotSupportedException.class,
                        "TODO-HIBERNATE-111");
            }
        }

        @Entity(name = "ItemWithArray")
        static class ItemWithArray {
            @Id
            int id;

            @Embeddable
            @Struct(name = "ItemStruct")
            static class ItemStruct {
                String string;

                protected ItemStruct() {}

                ItemStruct(String string) {
                    this.string = string;
                }
            }

            ItemStruct[] itemStructs;

            protected ItemWithArray() {}

            ItemWithArray(int id, ItemStruct[] itemStructs) {
                this.id = id;
                this.itemStructs = itemStructs;
            }
        }

        @Nested
        @DomainModel(
                annotatedClasses = {
                    JoinedInheritanceJoin.ItemWithJoinedInheritance.class,
                    JoinedInheritanceJoin.Item.class
                })
        class JoinedInheritanceJoin extends AbstractQueryIntegrationTests {
            @Test
            void testJoinedInheritanceThrows() {
                assertThat(assertThrows(RuntimeException.class, () -> getSessionFactoryScope()
                                .getSessionFactory()))
                        .rootCause()
                        .isInstanceOf(FeatureNotSupportedException.class)
                        .hasMessage(
                                "TODO-HIBERNATE-69 https://jira.mongodb.org/browse/HIBERNATE-69 JOINED inheritance is not supported");
            }

            @Test
            void testJoinTreatThrows() {
                assertThat(assertThrows(RuntimeException.class, () -> getSessionFactoryScope()
                                .getSessionFactory()))
                        .rootCause()
                        .isInstanceOf(FeatureNotSupportedException.class)
                        .hasMessage(
                                "TODO-HIBERNATE-69 https://jira.mongodb.org/browse/HIBERNATE-69 JOINED inheritance is not supported");
            }

            @Entity(name = "ItemWithJoinedInheritance")
            @Inheritance(strategy = InheritanceType.JOINED)
            static class ItemWithJoinedInheritance {
                @Id
                int id;
            }

            @Entity(name = "Item")
            static class Item extends ItemWithJoinedInheritance {
                String string;
            }
        }

        @Nested
        @DomainModel(annotatedClasses = ItemWithSecondaryTable.class)
        class SecondaryTableJoin extends AbstractQueryIntegrationTests {
            @Test
            void testSecondaryTableThrows() {
                assertThat(assertThrows(RuntimeException.class, () -> getSessionFactoryScope()
                                .getSessionFactory()))
                        .rootCause()
                        .isInstanceOf(FeatureNotSupportedException.class)
                        .hasMessage(
                                "TODO-HIBERNATE-181 https://jira.mongodb.org/browse/HIBERNATE-181 @SecondaryTable is not supported");
            }
        }

        @Entity(name = "ItemWithSecondaryTable")
        @SecondaryTable(name = "ItemWithSecondaryTable_secondary")
        static class ItemWithSecondaryTable {
            @Id
            int id;

            @Column(table = "ItemWithSecondaryTable_secondary")
            String string;
        }

        @Nested
        @DomainModel(annotatedClasses = {ItemWithJoinFormula.class, Item.class})
        class JoinFormulaJoin extends AbstractQueryIntegrationTests {
            @Test
            void testJoinFormulaThrows() {
                assertThat(assertThrows(RuntimeException.class, () -> getSessionFactoryScope()
                                .getSessionFactory()))
                        .rootCause()
                        .isInstanceOf(FeatureNotSupportedException.class)
                        .hasMessage(
                                "TODO-HIBERNATE-182 https://jira.mongodb.org/browse/HIBERNATE-182 @JoinFormula is not supported");
            }
        }

        @Entity(name = "Item")
        static class Item {
            @Id
            int id;
        }

        @Entity(name = "ItemWithJoinFormula")
        static class ItemWithJoinFormula {
            @Id
            int id;

            int value;

            @ManyToOne
            @JoinFormula("COALESCE(value, 0)")
            Item item;
        }

        @Nested
        @DomainModel(annotatedClasses = {FilterJoinTableJoin.ItemWithFilterJoin.class, FilterJoinTableJoin.Item.class})
        class FilterJoinTableJoin extends AbstractQueryIntegrationTests {
            @Test
            void testFilterJoinTableThrows() {
                assertThat(assertThrows(FeatureNotSupportedException.class, () -> getSessionFactoryScope()
                                .inTransaction(session -> {
                                    session.enableFilter("activeOnly");
                                    session.createSelectionQuery(
                                                    "FROM ItemWithFilterJoin i JOIN i.items is", Object[].class)
                                            .getResultList();
                                })))
                        .hasMessage("TODO-HIBERNATE-164 https://jira.mongodb.org/browse/HIBERNATE-164");
            }

            @FilterDef(name = "activeOnly", defaultCondition = "1 = 1")
            @Entity(name = "ItemWithFilterJoin")
            static class ItemWithFilterJoin {
                @Id
                int id;

                @OneToMany(fetch = FetchType.LAZY)
                @FilterJoinTable(name = "activeOnly")
                List<Item> items = new ArrayList<>();
            }

            @Entity(name = "Item")
            static class Item {
                @Id
                int id;
            }
        }
    }
}
