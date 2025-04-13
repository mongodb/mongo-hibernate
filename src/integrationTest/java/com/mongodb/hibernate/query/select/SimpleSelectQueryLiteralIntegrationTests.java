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

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.annotations.ObjectIdGenerator;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.bson.types.ObjectId;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = SimpleSelectQueryLiteralIntegrationTests.Book.class)
@ExtendWith(MongoExtension.class)
class SimpleSelectQueryLiteralIntegrationTests {

    @Test
    void testBoolean(SessionFactoryScope scope) {
        var item = new Book();
        item.outOfStock = true;
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Book where outOfStock = true", Book.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Test
    void testInteger(SessionFactoryScope scope) {
        var item = new Book();
        item.publishYear = 1995;
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Book where publishYear = 1995", Book.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Test
    void testLong(SessionFactoryScope scope) {
        var item = new Book();
        item.isbn13 = 9780310904168L;
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(session -> assertThat(
                        session.createSelectionQuery("from Book where isbn13 = 9780310904168L", Book.class)
                                .getSingleResult())
                .usingRecursiveComparison()
                .isEqualTo(item));
    }

    @Test
    void testDouble(SessionFactoryScope scope) {
        var item = new Book();
        item.discount = 0.25;
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Book where discount = 0.25D", Book.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Test
    void testString(SessionFactoryScope scope) {
        var item = new Book();
        item.title = "Holy Bible";
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Book where title = 'Holy Bible'", Book.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Test
    void testBigDecimal(SessionFactoryScope scope) {
        var item = new Book();
        item.price = new BigDecimal("123.50");
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Book where price = 123.50BD", Book.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Entity(name = "Book")
    @Table(name = "books")
    static class Book {
        @Id
        @ObjectIdGenerator
        ObjectId id;

        String title;
        Boolean outOfStock;
        Integer publishYear;
        Long isbn13;
        Double discount;
        BigDecimal price;
    }
}
