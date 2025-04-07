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

import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.function.Consumer;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.query.SelectionQuery;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = SimpleSelectQueryIntegrationTests.Contact.class)
@ExtendWith(MongoExtension.class)
class SimpleSelectQueryIntegrationTests {

    @BeforeEach
    void beforeEach(SessionFactoryScope scope) {
        scope.inTransaction(session -> List.of(
                        new Contact(1, "Bob", 18, Country.USA),
                        new Contact(2, "Mary", 35, Country.CANADA),
                        new Contact(3, "Dylan", 7, Country.CANADA),
                        new Contact(4, "Lucy", 78, Country.CANADA),
                        new Contact(5, "John", 25, Country.USA))
                .forEach(session::persist));
    }

    @Test
    void testComparisonByEq(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session,
                "from Contact where country = :country",
                q -> q.setParameter("country", Country.USA.name()),
                List.of(1, 5)));
    }

    @Test
    void testComparisonByNe(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session,
                "from Contact where country != ?1",
                q -> q.setParameter(1, Country.USA.name()),
                List.of(2, 3, 4)));
    }

    @Test
    void testComparisonByLt(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session, "from Contact where age < :age", q -> q.setParameter("age", 35), List.of(1, 3, 5)));
    }

    @Test
    void testComparisonByLte(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session, "from Contact where age <= ?1", q -> q.setParameter(1, 35), List.of(1, 2, 3, 5)));
    }

    @Test
    void testComparisonByGt(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session, "from Contact where age > :age", q -> q.setParameter("age", 18), List.of(2, 4, 5)));
    }

    @Test
    void testComparisonByGte(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session, "from Contact where age >= :age", q -> q.setParameter("age", 18), List.of(1, 2, 4, 5)));
    }

    @Test
    void testAndFilter(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session,
                "from Contact where country = ?1 and age > ?2",
                q -> q.setParameter(1, Country.CANADA.name()).setParameter(2, 18),
                List.of(2, 4)));
    }

    @Test
    void testOrFilter(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session,
                "from Contact where country = :country or age > :age",
                q -> q.setParameter("country", Country.CANADA.name()).setParameter("age", 18),
                List.of(2, 3, 4, 5)));
    }

    @Test
    void testFieldNonNumericLiteralValue(SessionFactoryScope scope) {
        scope.inTransaction(session ->
                assertContactQueryResult(session, "from Contact where country = 'USA'", null, List.of(1, 5)));
    }

    @Test
    void testFieldNumericLiteralValue(SessionFactoryScope scope) {
        scope.inTransaction(
                session -> assertContactQueryResult(session, "from Contact where age < 35", null, List.of(1, 3, 5)));
    }

    @Test
    void testFieldStringLiteralValue(SessionFactoryScope scope) {
        scope.inTransaction(session ->
                assertContactQueryResult(session, "from Contact where country != 'USA'", null, List.of(2, 3, 4)));
    }

    @Test
    void testNotFilterOperation(SessionFactoryScope scope) {
        scope.inTransaction(session -> assertContactQueryResult(
                session, "from Contact where age > 18 and not (country = 'USA')", null, List.of(2, 4)));
    }

    @Test
    void testProjectOperation(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var results = session.createSelectionQuery(
                            "select name, age from Contact where country = :country", Object[].class)
                    .setParameter("country", Country.CANADA.name())
                    .getResultList();
            assertThat(results)
                    .containsExactly(new Object[] {"Mary", 35}, new Object[] {"Dylan", 7}, new Object[] {"Lucy", 78});
        });
    }

    private static void assertContactQueryResult(
            SessionImplementor session,
            String hql,
            @Nullable Consumer<SelectionQuery<Contact>> queryPostProcessor,
            List<Integer> expectedIds) {
        var selectionQuery = session.createSelectionQuery(hql, Contact.class);
        if (queryPostProcessor != null) {
            queryPostProcessor.accept(selectionQuery);
        }
        var queryResult = selectionQuery.getResultList();
        assertThat(queryResult).extracting(c -> c.id).containsExactly(expectedIds.toArray(new Integer[0]));
    }

    @Entity(name = "Contact")
    @Table(name = "contacts")
    static class Contact {
        @Id
        int id;

        String name;
        int age;
        String country;

        Contact() {}

        Contact(int id, String name, int age, Country country) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.country = country.name();
        }
    }

    enum Country {
        USA,
        CANADA
    }
}
