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

package com.mongodb.hibernate.query.mutation;

import com.mongodb.client.MongoCollection;
import com.mongodb.hibernate.junit.InjectMongoCollection;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import com.mongodb.hibernate.query.Book;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = Book.class)
class InsertionIntegrationTests extends AbstractQueryIntegrationTests {

    @InjectMongoCollection(Book.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollection;

    @BeforeEach
    void beforeEach() {
        getTestCommandListener().clear();
    }

    @Test
    void testInsertPartialSingleDocument() {
        assertMutationQuery(
                "insert into Book (id, title, outOfStock, isbn13, discount) values (1, 'Pride & Prejudice', false, null, 0.2D)",
                null,
                1,
                """
                {
                  "insert": "books",
                  "documents": [
                    {
                      "_id": 1,
                      "title": "Pride & Prejudice",
                      "outOfStock": false,
                      "isbn13": null,
                      "discount": 0.2
                    }
                  ]
                }
                """,
                mongoCollection,
                List.of(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 1,
                                  "title": "Pride & Prejudice",
                                  "outOfStock": false,
                                  "isbn13": null,
                                  "discount": 0.2
                                }
                                """)),
                Set.of(Book.COLLECTION_NAME));
    }

    @Test
    void testInsertSingleDocument() {
        assertMutationQuery(
                "insert into Book (id, title, outOfStock, publishYear, isbn13, discount, price) values (1, 'Pride & Prejudice', null, null, 9780141439518L, null, 23.55BD)",
                null,
                1,
                """
                {
                  "insert": "books",
                  "documents": [
                    {
                      "_id": 1,
                      "title": "Pride & Prejudice",
                      "outOfStock": null,
                      "publishYear": null,
                      "isbn13": 9780141439518,
                      "discount": null,
                      "price": {"$numberDecimal": "23.55"}
                    }
                  ]
                }
                """,
                mongoCollection,
                List.of(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 1,
                                  "title": "Pride & Prejudice",
                                  "outOfStock": null,
                                  "publishYear": null,
                                  "isbn13": 9780141439518,
                                  "discount": null,
                                  "price": {"$numberDecimal": "23.55"}
                                }
                                """)),
                Set.of(Book.COLLECTION_NAME));
    }

    @Test
    void testInsertMultipleDocuments() {
        assertMutationQuery(
                """
                insert into Book (id, title, outOfStock, publishYear, isbn13, discount, price)
                values
                    (1, null, false, null, 9780141439518L, null, 23.55BD),
                    (2, 'War & Peace', null, 1867, null, 0.1D, null)
                """,
                null,
                2,
                """
                {
                  "insert": "books",
                  "documents": [
                    {
                      "_id": 1,
                      "title": null,
                      "outOfStock": false,
                      "publishYear": null,
                      "isbn13": 9780141439518,
                      "discount": null,
                      "price": {"$numberDecimal": "23.55"}
                    },
                    {
                      "_id": 2,
                      "title": "War & Peace",
                      "outOfStock": null,
                      "publishYear": 1867,
                      "isbn13": null,
                      "discount": 0.1,
                      "price": null
                    }
                  ]
                }
                """,
                mongoCollection,
                List.of(
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 1,
                                  "title": null,
                                  "outOfStock": false,
                                  "publishYear": null,
                                  "isbn13": 9780141439518,
                                  "discount": null,
                                  "price": {"$numberDecimal": "23.55"}
                                }
                                """),
                        BsonDocument.parse(
                                """
                                {
                                  "_id": 2,
                                  "title": "War & Peace",
                                  "outOfStock": null,
                                  "publishYear": 1867,
                                  "isbn13": null,
                                  "discount": 0.1,
                                  "price": null
                                }
                                """)),
                Set.of(Book.COLLECTION_NAME));
    }
}
