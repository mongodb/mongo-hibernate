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

        // Plain column used by the compound-ON tests (HIBERNATE-164) to form a second equality
        // conjunct; no composite-key mapping is required to exercise the translation.
        int region;

        @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
        List<Order> orders = new ArrayList<>();

        Customer() {}

        Customer(int id, String name) {
            this(id, name, 0);
        }

        Customer(int id, String name, int region) {
            this.id = id;
            this.name = name;
            this.region = region;
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

        int region;

        @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
        List<LineItem> lineItems = new ArrayList<>();

        Order() {}

        Order(int id, Customer customer, int total) {
            this(id, customer, total, 0);
        }

        Order(int id, Customer customer, int total, int region) {
            this.id = id;
            this.customer = customer;
            this.total = total;
            this.region = region;
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
        var customers =
                List.of(new Customer(1, "Alice", 10), new Customer(2, "Bob", 20), new Customer(3, "Charlie", 30));
        // region is a second plain column for the compound-ON tests (HIBERNATE-164). Orders 1 and 2 share their
        // like-id customer's region, but order 3's region (99) matches no customer's region — so a compound join
        // such as "c.id = o.id AND c.region = o.region" keeps orders 1 and 2 and drops order 3, proving the
        // second (region) conjunct actually filters.
        var orders = List.of(
                new Order(1, customers.get(0), 100, 10),
                new Order(2, customers.get(1), 200, 20),
                new Order(3, customers.get(1), 300, 99));
        var lineItems = List.of(
                new LineItem(1, orders.get(0), 5),
                new LineItem(2, orders.get(0), 10),
                new LineItem(3, orders.get(1), 15));
        getSessionFactoryScope().inTransaction(session -> {
            customers.forEach(session::persist);
            orders.forEach(session::persist);
            lineItems.forEach(session::persist);
        });
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
                        "c1_0#region": "$#c1_0.region",
                        "region": true,
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
                        "region": true,
                        "o1_0#customerId": "$#o1_0.customerId",
                        "o1_0#_id": "$#o1_0._id",
                        "o1_0#region": "$#o1_0.region",
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
                              { "$match": { "$expr": { "$gt": [ "$_id", "$$v0_c1_0__id" ] } } }
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
    class CompoundOnCondition implements MongoServiceRegistryProducer {

        @Test
        void testCompositeKeyEquijoin() {
            // Order 3 (id 3) matches customer 3 on id, but its region (99) differs from customer 3's (30), so the
            // region conjunct filters it out — leaving only orders 1 and 2.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND c.region = o.region ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$eq": [ "$$v1_c1_0_region", "$region" ] }
                                    ]
                                  }
                                }
                              }
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
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testAndWithLessThanOperator() {
            // AND of an equality and an ordering comparison: $eq + $lt under one $and.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND c.region < o.total ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$lt": [ "$$v1_c1_0_region", "$total" ] }
                                    ]
                                  }
                                }
                              }
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
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testOrTwoEqualities() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id OR c.region = o.region ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$or": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$eq": [ "$$v1_c1_0_region", "$region" ] }
                                    ]
                                  }
                                }
                              }
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
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testAndWithOrBiggerThan() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND c.region = o.region OR c.id > o.id ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": {
                              "v0_c1_0__id": "$_id",
                              "v1_c1_0_region": "$region",
                              "v2_c1_0__id": "$_id"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$or": [
                                      {
                                        "$and": [
                                          { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                          { "$eq": [ "$$v1_c1_0_region", "$region" ] }
                                        ]
                                      },
                                      { "$gt": [ "$$v2_c1_0__id", "$_id" ] }
                                    ]
                                  }
                                }
                              }
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
                            new Object[] {3, 200}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testAndWithEqualAndLargerThan() {
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND o.total > c.region ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$gt": [ "$total", "$$v1_c1_0_region" ] }
                                    ]
                                  }
                                }
                              }
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
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testThreeWayConjunction() {
            // Three conjuncts → three let variables (v0/v1/v2), the second and third both binding c.region.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND c.region = o.region AND c.region < o.total ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": {
                              "v0_c1_0__id": "$_id",
                              "v1_c1_0_region": "$region",
                              "v2_c1_0_region": "$region"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$eq": [ "$$v1_c1_0_region", "$region" ] },
                                      { "$lt": [ "$$v2_c1_0_region", "$total" ] }
                                    ]
                                  }
                                }
                              }
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
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testLeftOuterCompound() {
            // LEFT join keeps the preserveNullAndEmptyArrays $unwind; customer 3 has no compound match (its
            // region differs from order 3's) so it survives with null joined columns.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c LEFT JOIN Order o"
                            + " ON c.id = o.id AND c.region = o.region ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$eq": [ "$$v1_c1_0_region", "$region" ] }
                                    ]
                                  }
                                }
                              }
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
                            List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, null}), results),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testNotEqualConjunct() {
            // A <> conjunct maps to $ne alongside the $eq.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND c.region <> o.region ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$ne": [ "$$v1_c1_0_region", "$region" ] }
                                    ]
                                  }
                                }
                              }
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
                    List.<Object[]>of(new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testGroupedConjuncts() {
            // Parenthesized conjuncts arrive as GroupedPredicate wrappers; each is unwrapped to its inner
            // comparison, yielding the same $and as the unparenthesized form.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON (c.id = o.id) AND (c.region = o.region) ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$eq": [ "$$v1_c1_0_region", "$region" ] }
                                    ]
                                  }
                                }
                              }
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
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testParenthesizedEquijoinUsesSimpleForm() {
            // A parenthesized lone equijoin is a GroupedPredicate wrapping a single EQUAL comparison. Parentheses
            // are semantically irrelevant, so it must still use the compact localField/foreignField $lookup form
            // rather than the heavier let/$expr pipeline form.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON (c.id = o.id) ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "localField": "_id",
                            "foreignField": "_id",
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
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testUnparenthesizedNotComparison() {
            // An unparenthesized NOT over a single comparison is lowered by Hibernate to a NOT_EQUAL
            // comparison (no NegatedPredicate reaches the translator), so it is supported and maps to $ne.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND NOT c.region = o.region ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$ne": [ "$$v1_c1_0_region", "$region" ] }
                                    ]
                                  }
                                }
                              }
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
                    List.<Object[]>of(new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testLiteralOperandConjunct() {
            // A column-vs-literal conjunct (o.total > 100) now translates; it filters the joined side, dropping
            // order 1 (total 100).
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND o.total > 100 ORDER BY c.id, o.id",
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
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$gt": [ "$total", { "$numberInt": "100" } ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" }, "#o1_0._id": { "$numberInt": "1" } } },
                        { "$project": { "_id": true, "o1_0#total": "$#o1_0.total" } }
                      ]
                    }""",
                    List.of(new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testIsNotNullConjunct() {
            // An IS NOT NULL conjunct delegates to visitNullnessPredicate ($ne null); every seeded total is
            // non-null, so the diagonal join keeps all three rows.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND o.total IS NOT NULL ORDER BY c.id, o.id",
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
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$ne": [ "$total", null ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" }, "#o1_0._id": { "$numberInt": "1" } } },
                        { "$project": { "_id": true, "o1_0#total": "$#o1_0.total" } }
                      ]
                    }""",
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testBothJoinedConjunct() {
            // A conjunct whose two sides are both joined columns (o.total > o.region) now translates: both resolve
            // to bare sub-pipeline field paths. Every seeded order has total > region, so the diagonal join keeps all.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND o.total > o.region ORDER BY c.id, o.id",
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
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$gt": [ "$total", "$region" ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" }, "#o1_0._id": { "$numberInt": "1" } } },
                        { "$project": { "_id": true, "o1_0#total": "$#o1_0.total" } }
                      ]
                    }""",
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testBetweenConjunct() {
            // Delegation reuses visitBetweenPredicate (a $gte/$lte pair); the BETWEEN operand c.region is
            // translated once, so both bounds read the same let variable. c.region falls within
            // [o.id, o.total] for every diagonal-joined row.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND c.region BETWEEN o.id AND o.total ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": {
                              "v0_c1_0__id": "$_id",
                              "v1_c1_0_region": "$region"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      {
                                        "$and": [
                                          { "$gte": [ "$$v1_c1_0_region", "$_id" ] },
                                          { "$lte": [ "$$v1_c1_0_region", "$total" ] }
                                        ]
                                      }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" }, "#o1_0._id": { "$numberInt": "1" } } },
                        { "$project": { "_id": true, "o1_0#total": "$#o1_0.total" } }
                      ]
                    }""",
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testNegatedComparison() {
            // NOT (c.id = o.id) delegates to visitNegatedPredicate, wrapping the comparison in $not — so each
            // customer matches every order except its like-id one.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o ON NOT (c.id = o.id) ORDER BY c.id, o.id",
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
                              { "$match": { "$expr": { "$not": [ { "$eq": [ "$$v0_c1_0__id", "$_id" ] } ] } } }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" }, "#o1_0._id": { "$numberInt": "1" } } },
                        { "$project": { "_id": true, "o1_0#total": "$#o1_0.total" } }
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
        void testNegatedJunction() {
            // NOT over a parenthesized junction wraps the whole $and in $not; only the rows where BOTH conjuncts
            // hold (customers 1 and 2 on their like-id order) are excluded.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON NOT (c.id = o.id AND c.region = o.region) ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$not": [
                                      {
                                        "$and": [
                                          { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                          { "$eq": [ "$$v1_c1_0_region", "$region" ] }
                                        ]
                                      }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" }, "#o1_0._id": { "$numberInt": "1" } } },
                        { "$project": { "_id": true, "o1_0#total": "$#o1_0.total" } }
                      ]
                    }""",
                    List.of(
                            new Object[] {1, 200},
                            new Object[] {1, 300},
                            new Object[] {2, 100},
                            new Object[] {2, 300},
                            new Object[] {3, 100},
                            new Object[] {3, 200},
                            new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testArithmeticOperandConjunct() {
            // An arithmetic operand delegates to visitBinaryArithmeticExpression; c.region * 10 equals o.total for
            // every seeded row, so the diagonal join keeps all three.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND o.total = c.region * 10 ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": { "v0_c1_0__id": "$_id", "v1_c1_0_region": "$region" },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      {
                                        "$eq": [
                                          "$total",
                                          { "$multiply": [ "$$v1_c1_0_region", { "$numberInt": "10" } ] }
                                        ]
                                      }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" }, "#o1_0._id": { "$numberInt": "1" } } },
                        { "$project": { "_id": true, "o1_0#total": "$#o1_0.total" } }
                      ]
                    }""",
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
        }

        @Test
        void testSameTableConjunct() {
            // A conjunct whose two sides are both outer columns (c.region = c.region) now translates: each side
            // binds its own let variable, so it becomes $eq:[$$v, $$v] rather than being rejected.
            assertSelectionQuery(
                    "SELECT c.id, o.total FROM Customer c JOIN Order o"
                            + " ON c.id = o.id AND c.region = c.region ORDER BY c.id, o.id",
                    Object[].class,
                    """
                    {
                      "aggregate": "Customer",
                      "pipeline": [
                        {
                          "$lookup": {
                            "from": "Order",
                            "let": {
                              "v0_c1_0__id": "$_id",
                              "v1_c1_0_region": "$region",
                              "v2_c1_0_region": "$region"
                            },
                            "pipeline": [
                              {
                                "$match": {
                                  "$expr": {
                                    "$and": [
                                      { "$eq": [ "$$v0_c1_0__id", "$_id" ] },
                                      { "$eq": [ "$$v1_c1_0_region", "$$v2_c1_0_region" ] }
                                    ]
                                  }
                                }
                              }
                            ],
                            "as": "#o1_0"
                          }
                        },
                        { "$unwind": "$#o1_0" },
                        { "$sort": { "_id": { "$numberInt": "1" }, "#o1_0._id": { "$numberInt": "1" } } },
                        { "$project": { "_id": true, "o1_0#total": "$#o1_0.total" } }
                      ]
                    }""",
                    List.of(new Object[] {1, 100}, new Object[] {2, 200}, new Object[] {3, 300}),
                    Set.of("Customer", "Order"));
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
                            "sl1_0#stringsList": "$#sl1_0.stringsList",
                            "_id": 0
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
        void testIsDistinctFromOnConditionThrows() {
            // DISTINCT_FROM has no aggregation-expression counterpart in the shared operator map, so it throws
            // the same way it would in a WHERE clause.
            assertSelectQueryFailure(
                    "SELECT c.id FROM Customer c JOIN Order o ON c.id IS DISTINCT FROM o.id",
                    Object[].class,
                    FeatureNotSupportedException.class,
                    "Unsupported comparison operator: DISTINCT_FROM");
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
                // A @JoinFormula column is rejected wherever it is referenced (visitColumnReference), the same as
                // a formula column in any other clause.
                assertThat(assertThrows(RuntimeException.class, () -> getSessionFactoryScope()
                                .getSessionFactory()))
                        .rootCause()
                        .isInstanceOf(FeatureNotSupportedException.class)
                        .hasMessage("Formula is not supported");
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
                // The @FilterJoinTable turns the association's ON into a junction with a FilterPredicate conjunct;
                // delegating the ON to the shared visitor reaches visitFilterPredicate, the same unsupported path a
                // WHERE-clause @Filter hits.
                assertThrows(FeatureNotSupportedException.class, () -> getSessionFactoryScope()
                        .inTransaction(session -> {
                            session.enableFilter("activeOnly");
                            session.createSelectionQuery("FROM ItemWithFilterJoin i JOIN i.items is", Object[].class)
                                    .getResultList();
                        }));
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
