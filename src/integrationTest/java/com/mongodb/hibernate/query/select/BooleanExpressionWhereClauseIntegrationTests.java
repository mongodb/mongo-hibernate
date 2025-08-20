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

import static java.util.Collections.singletonList;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.query.AbstractQueryIntegrationTests;
import com.mongodb.hibernate.query.Book;
import java.util.Set;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DomainModel(annotatedClasses = Book.class)
class BooleanExpressionWhereClauseIntegrationTests extends AbstractQueryIntegrationTests {

    private Book bookOutOfStock;
    private Book bookInStock;

    @BeforeEach
    void beforeEach() {
        bookOutOfStock = new Book();
        bookOutOfStock.id = 1;
        bookOutOfStock.outOfStock = true;

        bookInStock = new Book();
        bookInStock.id = 2;
        bookInStock.outOfStock = false;

        getSessionFactoryScope().inTransaction(session -> {
            session.persist(bookOutOfStock);
            session.persist(bookInStock);
        });

        getTestCommandListener().clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testBooleanFieldPathExpression(boolean negated) {
        assertSelectionQuery(
                "from Book where" + (negated ? " not " : " ") + "outOfStock",
                Book.class,
                """
                {
                    "aggregate": "books",
                    "pipeline": [
                      {
                        "$match": {
                          "outOfStock": {
                            "$eq": %s
                          }
                        }
                      },
                      {
                        "$project": {
                          "f0": "$_id",
                          "f1": "$discount",
                          "f2": "$isbn13",
                          "f3": "$outOfStock",
                          "f4": "$price",
                          "f5": "$publishYear",
                          "f6": "$title",
                          "_id": false
                        }
                      }
                    ]
                  }
                """
                        .formatted(negated ? "false" : "true"),
                negated ? singletonList(bookInStock) : singletonList(bookOutOfStock),
                Set.of(Book.COLLECTION_NAME));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testNonFieldPathExpressionNotSupported(boolean booleanLiteral) {
        assertSelectQueryFailure(
                "from Book where " + booleanLiteral,
                Book.class,
                FeatureNotSupportedException.class,
                "Expression not of field path is not supported");
    }
}
