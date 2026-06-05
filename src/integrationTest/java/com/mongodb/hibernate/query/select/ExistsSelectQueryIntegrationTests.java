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
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.Struct;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = {ExistsSelectQueryIntegrationTests.Cart.class})
class ExistsSelectQueryIntegrationTests extends AbstractQueryIntegrationTests {

    private List<Cart> carts;

    @BeforeEach
    void setUp() {
        carts = List.of(
                new Cart(1, 2, "shipped", new LineItem[] {
                    new LineItem("WIDGET-1", 5, true), new LineItem("GADGET-2", 1, false)
                }),
                new Cart(2, 1, "pending", new LineItem[] {new LineItem("WIDGET-1", 1, false)}),
                new Cart(3, 0, "shipped", new LineItem[] {new LineItem("GADGET-2", 3, true)}),
                new Cart(4, 3, "pending", new LineItem[] {}));
        getSessionFactoryScope().inTransaction(session -> carts.forEach(session::persist));
        getTestCommandListener().clear();
    }

    @Test
    void testSimpleEquality() {
        assertSelectionQuery(
                "FROM Cart c WHERE EXISTS (FROM c.lineItems li WHERE li.sku = :sku) ORDER BY c.id",
                Cart.class,
                q -> q.setParameter("sku", "WIDGET-1"),
                """
                {
                  "aggregate": "carts",
                  "pipeline": [
                    {
                      "$match": {
                        "lineItems": {
                          "$elemMatch": {
                            "sku": {
                              "$eq": "WIDGET-1"
                            }
                          }
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
                        "lineItems": true,
                        "minQty": true,
                        "status": true
                      }
                    }
                  ]
                }""",
                List.of(carts.get(0), carts.get(1)),
                Set.of(Cart.COLLECTION_NAME));
    }

    @Test
    void testConjunction() {
        assertSelectionQuery(
                "FROM Cart c WHERE EXISTS (FROM c.lineItems li WHERE li.sku = :sku AND li.qty > :qty) ORDER BY c.id",
                Cart.class,
                q -> q.setParameter("sku", "WIDGET-1").setParameter("qty", 2),
                """
                {
                  "aggregate": "carts",
                  "pipeline": [
                    {
                      "$match": {
                        "lineItems": {
                          "$elemMatch": {
                            "$and": [
                              {
                                "sku": {
                                  "$eq": "WIDGET-1"
                                }
                              },
                              {
                                "qty": {
                                  "$gt": 2
                                }
                              }
                            ]
                          }
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
                        "lineItems": true,
                        "minQty": true,
                        "status": true
                      }
                    }
                  ]
                }""",
                List.of(carts.get(0)),
                Set.of(Cart.COLLECTION_NAME));
    }

    @Test
    void testDisjunction() {
        assertSelectionQuery(
                "FROM Cart c WHERE EXISTS (FROM c.lineItems li WHERE li.sku = :sku OR li.qty > :qty) ORDER BY c.id",
                Cart.class,
                q -> q.setParameter("sku", "WIDGET-1").setParameter("qty", 2),
                """
                {
                  "aggregate": "carts",
                  "pipeline": [
                    {
                      "$match": {
                        "lineItems": {
                          "$elemMatch": {
                            "$or": [
                              {
                                "sku": {
                                  "$eq": "WIDGET-1"
                                }
                              },
                              {
                                "qty": {
                                  "$gt": 2
                                }
                              }
                            ]
                          }
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
                        "lineItems": true,
                        "minQty": true,
                        "status": true
                      }
                    }
                  ]
                }""",
                List.of(carts.get(0), carts.get(1), carts.get(2)),
                Set.of(Cart.COLLECTION_NAME));
    }

    @Test
    void testNegatedBodyPredicate() {
        assertSelectionQuery(
                "FROM Cart c WHERE EXISTS (FROM c.lineItems li WHERE NOT (li.sku = :sku)) ORDER BY c.id",
                Cart.class,
                q -> q.setParameter("sku", "WIDGET-1"),
                """
                {
                  "aggregate": "carts",
                  "pipeline": [
                    {
                      "$match": {
                        "lineItems": {
                          "$elemMatch": {
                            "$nor": [
                              {
                                "sku": {
                                  "$eq": "WIDGET-1"
                                }
                              }
                            ]
                          }
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
                        "lineItems": true,
                        "minQty": true,
                        "status": true
                      }
                    }
                  ]
                }""",
                List.of(carts.get(0), carts.get(2)),
                Set.of(Cart.COLLECTION_NAME));
    }

    @Test
    void testNotExists() {
        assertSelectionQuery(
                "FROM Cart c WHERE NOT EXISTS (FROM c.lineItems li WHERE li.sku = :sku) ORDER BY c.id",
                Cart.class,
                q -> q.setParameter("sku", "WIDGET-1"),
                """
                {
                  "aggregate": "carts",
                  "pipeline": [
                    {
                      "$match": {
                        "$nor": [
                          {
                            "lineItems": {
                              "$elemMatch": {
                                "sku": {
                                  "$eq": "WIDGET-1"
                                }
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
                        "lineItems": true,
                        "minQty": true,
                        "status": true
                      }
                    }
                  ]
                }""",
                List.of(carts.get(2), carts.get(3)),
                Set.of(Cart.COLLECTION_NAME));
    }

    @Test
    void testBooleanField() {
        assertSelectionQuery(
                "FROM Cart c WHERE EXISTS (FROM c.lineItems li WHERE li.active) ORDER BY c.id",
                Cart.class,
                """
                {
                  "aggregate": "carts",
                  "pipeline": [
                    {
                      "$match": {
                        "lineItems": {
                          "$elemMatch": {
                            "active": {
                              "$eq": true
                            }
                          }
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
                        "lineItems": true,
                        "minQty": true,
                        "status": true
                      }
                    }
                  ]
                }""",
                List.of(carts.get(0), carts.get(2)),
                Set.of(Cart.COLLECTION_NAME));
    }

    @Test
    void testNoWhereBody() {
        assertSelectionQuery(
                "FROM Cart c WHERE EXISTS (FROM c.lineItems li) ORDER BY c.id",
                Cart.class,
                """
                {
                  "aggregate": "carts",
                  "pipeline": [
                    {
                      "$match": {
                        "lineItems": {
                          "$elemMatch": {}
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
                        "lineItems": true,
                        "minQty": true,
                        "status": true
                      }
                    }
                  ]
                }""",
                List.of(carts.get(0), carts.get(1), carts.get(2)),
                Set.of(Cart.COLLECTION_NAME));
    }

    @Test
    void testExistsComposedWithOuterPredicate() {
        assertSelectionQuery(
                "FROM Cart c WHERE c.status = :status AND EXISTS (FROM c.lineItems li WHERE li.sku = :sku) ORDER BY c.id",
                Cart.class,
                q -> q.setParameter("status", "shipped").setParameter("sku", "WIDGET-1"),
                """
                {
                  "aggregate": "carts",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "status": {
                              "$eq": "shipped"
                            }
                          },
                          {
                            "lineItems": {
                              "$elemMatch": {
                                "sku": {
                                  "$eq": "WIDGET-1"
                                }
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
                        "lineItems": true,
                        "minQty": true,
                        "status": true
                      }
                    }
                  ]
                }""",
                List.of(carts.get(0)),
                Set.of(Cart.COLLECTION_NAME));
    }

    @Nested
    class Unsupported {

        @Test
        void testExistsOverEntity() {
            assertSelectQueryFailure(
                    "FROM Cart c WHERE EXISTS (FROM Cart c2 WHERE c2.id = :id)",
                    Cart.class,
                    q -> q.setParameter("id", 1),
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-178");
        }

        @Test
        void testCorrelatedOuterRef() {
            assertSelectQueryFailure(
                    "FROM Cart c WHERE EXISTS (FROM c.lineItems li WHERE c.status = 'shipped')",
                    Cart.class,
                    FeatureNotSupportedException.class,
                    "TODO-HIBERNATE-177");
        }
    }

    @Entity(name = "Cart")
    @Table(name = Cart.COLLECTION_NAME)
    static class Cart {

        static final String COLLECTION_NAME = "carts";

        @Id
        int id;

        int minQty;
        String status;
        LineItem[] lineItems;

        Cart() {}

        Cart(int id, int minQty, String status, LineItem[] lineItems) {
            this.id = id;
            this.minQty = minQty;
            this.status = status;
            this.lineItems = lineItems;
        }
    }

    @Embeddable
    @Struct(name = "LineItem")
    static class LineItem {

        String sku;
        int qty;
        boolean active;

        LineItem() {}

        LineItem(String sku, int qty, boolean active) {
            this.sku = sku;
            this.qty = qty;
            this.active = active;
        }
    }
}
