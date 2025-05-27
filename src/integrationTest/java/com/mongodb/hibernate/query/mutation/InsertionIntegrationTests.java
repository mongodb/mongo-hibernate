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
import com.mongodb.hibernate.query.Book;
import java.util.List;
import org.bson.BsonDocument;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = Book.class)
class InsertionIntegrationTests extends AbstractMutationQueryIntegrationTests {

    @InjectMongoCollection(Book.COLLECTION_NAME)
    private static MongoCollection<BsonDocument> mongoCollection;

    @BeforeEach
    void beforeEach() {
        getTestCommandListener().clear();
    }

    @Test
    void testInsertSingleDocument() {
        getSessionFactoryScope()
                .inTransaction(
                        session -> assertMutationQuery(
                                "insert into Book (id, title, outOfStock, publishYear, isbn13, discount, price) values (1, 'Pride & Prejudice', false, 1813, 9780141439518L, 0.2D, 23.55BD)",
                                1,
                                """
                                {
                                  "insert": "books",
                                  "documents": [
                                    {
                                      "_id": 1,
                                      "title": "Pride & Prejudice",
                                      "outOfStock": false,
                                      "publishYear": 1813,
                                      "isbn13": 9780141439518,
                                      "discount": 0.2,
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
                                                  "outOfStock": false,
                                                  "publishYear": 1813,
                                                  "isbn13": 9780141439518,
                                                  "discount": 0.2,
                                                  "price": {"$numberDecimal": "23.55"}
                                                }
                                                """))));
    }

    @Test
    void testInsertMultipleDocuments() {
        getSessionFactoryScope()
                .inTransaction(
                        session -> assertMutationQuery(
                                """
                                insert into Book (id, title, outOfStock, publishYear, isbn13, discount, price)
                                values
                                    (1, 'Pride & Prejudice', false, 1813, 9780141439518L, 0.2D, 23.55BD),
                                    (2, 'War & Peace', false, 1867, 9780143039990L, 0.1D, 19.99BD)
                                """,
                                2,
                                """
                                {
                                  "insert": "books",
                                  "documents": [
                                    {
                                      "_id": 1,
                                      "title": "Pride & Prejudice",
                                      "outOfStock": false,
                                      "publishYear": 1813,
                                      "isbn13": 9780141439518,
                                      "discount": 0.2,
                                      "price": {"$numberDecimal": "23.55"}
                                    },
                                    {
                                      "_id": 2,
                                      "title": "War & Peace",
                                      "outOfStock": false,
                                      "publishYear": 1867,
                                      "isbn13": 9780143039990,
                                      "discount": 0.1,
                                      "price": {"$numberDecimal": "19.99"}
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
                                                  "publishYear": 1813,
                                                  "isbn13": 9780141439518,
                                                  "discount": 0.2,
                                                  "price": {"$numberDecimal": "23.55"}
                                                }
                                                """),
                                        BsonDocument.parse(
                                                """
                                                {
                                                  "_id": 2,
                                                  "title": "War & Peace",
                                                  "outOfStock": false,
                                                  "publishYear": 1867,
                                                  "isbn13": 9780143039990,
                                                  "discount": 0.1,
                                                  "price": {"$numberDecimal": "19.99"}
                                                }
                                                """))));
    }

    @Test
    void testAffectedTableNames() {
        assertAffectedTableNames(
                """
                insert into Book (id, title, outOfStock, publishYear, isbn13, discount, price)
                    values
                        (1, 'Pride & Prejudice', false, 1813, 9780141439518L, 0.2D, 23.55BD)""",
                "books");
    }
}
