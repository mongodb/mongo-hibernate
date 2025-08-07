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

import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import com.mongodb.hibernate.query.Book;
import org.bson.BsonDocument;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DomainModel(annotatedClasses = Book.class)
@ServiceRegistry(settings = @Setting(name = AvailableSettings.STATEMENT_BATCH_SIZE, value = "3"))
class BatchUpdateIntegrationTests extends AbstractQueryIntegrationTests {

    @BeforeEach
    void beforeEach() {
        getTestCommandListener().clear();
    }

    @Test
    void testHappyPath() {
        getSessionFactoryScope().inTransaction(session -> {
            var book1 = new Book(1);
            var book2 = new Book(2);
            var book3 = new Book(3);
            var book4 = new Book(4);
            var book5 = new Book(5);
            session.persist(book1);
            session.persist(book2);
            session.persist(book3);
            session.persist(book4);
            session.persist(book5);
            session.flush();
            assertActualCommand(
                    BsonDocument.parse("""
                                        {
                                            "insert": "books",
                                            "documents": [
                                              { "_id": 1, "discount": 0.0, "isbn13": {$numberLong: "0"}, "outOfStock": false, "price": {$numberDecimal: "0.0"}, "publishYear": 0, "title": "" },
                                              { "_id": 2, "discount": 0.0, "isbn13": {$numberLong: "0"}, "outOfStock": false, "price": {$numberDecimal: "0.0"}, "publishYear": 0, "title": "" },
                                              { "_id": 3, "discount": 0.0, "isbn13": {$numberLong: "0"}, "outOfStock": false, "price": {$numberDecimal: "0.0"}, "publishYear": 0, "title": "" }
                                            ]
                                        }
                                       """),
                    BsonDocument.parse("""
                                        {
                                            "insert": "books",
                                            "ordered": true,
                                            "documents": [
                                              { "_id": 4, "discount": 0.0, "isbn13": {$numberLong: "0"}, "outOfStock": false, "price": {$numberDecimal: "0.0"}, "publishYear": 0, "title": "" },
                                              { "_id": 5, "discount": 0.0, "isbn13": {$numberLong: "0"}, "outOfStock": false, "price": {$numberDecimal: "0.0"}, "publishYear": 0, "title": "" }
                                            ]
                                        }
                                       """));
        });
    }

    @Nested
    class BatchUpdateTests {}

    @Nested
    class BatchDeleteTests {}
}
