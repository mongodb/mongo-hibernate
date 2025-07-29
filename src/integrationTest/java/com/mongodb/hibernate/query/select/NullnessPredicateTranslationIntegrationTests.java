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

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DomainModel(annotatedClasses = {NullnessPredicateTranslationIntegrationTests.Book.class})
class NullnessPredicateTranslationIntegrationTests extends AbstractSelectionQueryIntegrationTests {

    @BeforeEach
    void beforeEach() {
        getSessionFactoryScope().inTransaction(session -> testingBooks.forEach(session::persist));
        getTestCommandListener().clear();
    }

    private static final List<Book> testingBooks = List.of(
            new Book(1, "War and Peace", null),
            new Book(2, null, null),
            new Book(3, "The Brothers Karamazov", 1880),
            new Book(4, "War and Peace", 1867),
            new Book(5, null, null));

    private static List<Book> getBooksByIds(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> testingBooks.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .toList();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testNonEmbeddable(boolean isNegated) {
        assertSelectionQuery(
                "from Book where title = 'War and Peace' and publishYear " + (isNegated ? "is not null" : "is null"),
                Book.class,
                """
                {
                  "aggregate": "books",
                  "pipeline": [
                    {
                      "$match": {
                        "$and": [
                          {
                            "$and": [
                              {
                                "title": {
                                  "$eq": "War and Peace"
                                }
                              },
                              {
                                "title": {
                                  "$ne": null
                                }
                              }
                            ]
                          },
                          {
                            "publishYear": {
                              "%s": null
                            }
                          }
                        ]
                      }
                    },
                    {
                      "$project": {
                        "_id": true,
                        "publishYear": true,
                        "title": true
                      }
                    }
                  ]
                }"""
                        .formatted(isNegated ? "$ne" : "$eq"),
                isNegated ? getBooksByIds(4) : getBooksByIds(1));
    }

    @Entity(name = "Book")
    @Table(name = "books")
    static class Book {
        @Id
        int id;

        String title;

        Integer publishYear;

        public Book() {}

        public Book(int id, String title, Integer publishYear) {
            this.id = id;
            this.title = title;
            this.publishYear = publishYear;
        }

        @Override
        public String toString() {
            return "Book{" + "id=" + id + '}';
        }
    }
}
