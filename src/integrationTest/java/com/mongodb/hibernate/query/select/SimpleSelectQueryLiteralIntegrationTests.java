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
@DomainModel(annotatedClasses = SimpleSelectQueryLiteralIntegrationTests.Item.class)
@ExtendWith(MongoExtension.class)
class SimpleSelectQueryLiteralIntegrationTests {

    @Test
    void testBoolean(SessionFactoryScope scope) {
        var item = new Item();
        item.booleanField = true;
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Item where booleanField = true", Item.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Test
    void testInteger(SessionFactoryScope scope) {
        var item = new Item();
        item.integerField = 1;
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Item where integerField = 1", Item.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Test
    void testLong(SessionFactoryScope scope) {
        var item = new Item();
        item.longField = 1L;
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Item where longField = 1", Item.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Test
    void testDouble(SessionFactoryScope scope) {
        var item = new Item();
        item.doubleField = 3.14;
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(
                session -> assertThat(session.createSelectionQuery("from Item where doubleField = 3.14", Item.class)
                                .getSingleResult())
                        .usingRecursiveComparison()
                        .isEqualTo(item));
    }

    @Test
    void testString(SessionFactoryScope scope) {
        var item = new Item();
        item.stringField = "Hello World";
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(session -> assertThat(
                        session.createSelectionQuery("from Item where stringField = 'Hello World'", Item.class)
                                .getSingleResult())
                .usingRecursiveComparison()
                .isEqualTo(item));
    }

    @Test
    void testBigDecimal(SessionFactoryScope scope) {
        var item = new Item();
        item.bigDecimalField = new BigDecimal("3.14");
        scope.inTransaction(session -> session.persist(item));
        scope.inTransaction(session -> assertThat(
                        session.createSelectionQuery("from Item where bigDecimalField = 3.14BD", Item.class)
                                .getSingleResult())
                .usingRecursiveComparison()
                .isEqualTo(item));
    }

    @Entity(name = "Item")
    @Table(name = "items")
    static class Item {
        @Id
        @ObjectIdGenerator
        ObjectId id;

        String stringField;
        Boolean booleanField;
        Integer integerField;
        Long longField;
        Double doubleField;
        BigDecimal bigDecimalField;
    }
}
