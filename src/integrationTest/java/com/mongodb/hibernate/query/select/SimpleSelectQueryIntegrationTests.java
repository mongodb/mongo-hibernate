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

import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.annotations.ObjectIdGenerator;
import com.mongodb.hibernate.internal.MongoTestCommandListener;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.hibernate.query.SelectionQuery;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistryScope;
import org.hibernate.testing.orm.junit.ServiceRegistryScopeAware;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SessionFactory(exportSchema = false)
@DomainModel(
        annotatedClasses = {
            SimpleSelectQueryIntegrationTests.Contact.class,
            SimpleSelectQueryIntegrationTests.Book.class
        })
@ExtendWith(MongoExtension.class)
class SimpleSelectQueryIntegrationTests implements SessionFactoryScopeAware, ServiceRegistryScopeAware {

    private SessionFactoryScope sessionFactoryScope;

    private MongoTestCommandListener mongoTestCommandListener;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Override
    public void injectServiceRegistryScope(ServiceRegistryScope serviceRegistryScope) {
        this.mongoTestCommandListener =
                serviceRegistryScope.getRegistry().requireService(MongoTestCommandListener.class);
    }

    @Nested
    class QueryTests {

        private static final List<Contact> testingContacts = List.of(
                new Contact(1, "Bob", 18, Country.USA),
                new Contact(2, "Mary", 35, Country.CANADA),
                new Contact(3, "Dylan", 7, Country.CANADA),
                new Contact(4, "Lucy", 78, Country.CANADA),
                new Contact(5, "John", 25, Country.USA));

        private List<Contact> getTestingContacts(int... ids) {
            return Arrays.stream(ids)
                    .mapToObj(id -> testingContacts.stream()
                            .filter(c -> c.id == id)
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("id not exists: " + id)))
                    .toList();
        }

        @BeforeEach
        void beforeEach() {
            sessionFactoryScope.inTransaction(session -> testingContacts.forEach(session::persist));
            mongoTestCommandListener.clear();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByEq(boolean fieldAsLhs) {
            assertSelectQuery(
                    "from Contact where " + (fieldAsLhs ? "country = :country" : ":country = country"),
                    Contact.class,
                    q -> q.setParameter("country", Country.USA.name()),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'country': {'$eq': 'USA'}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(1, 5));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByNe(boolean fieldAsLhs) {
            assertSelectQuery(
                    "from Contact where " + (fieldAsLhs ? "country != ?1" : "?1 != country"),
                    Contact.class,
                    q -> q.setParameter(1, Country.USA.name()),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'country': {'$ne': 'USA'}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 3, 4));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLt(boolean fieldAsLhs) {
            assertSelectQuery(
                    "from Contact where " + (fieldAsLhs ? "age < :age" : ":age > age"),
                    Contact.class,
                    q -> q.setParameter("age", 35),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'age': {'$lt': 35}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(1, 3, 5));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLte(boolean fieldAsLhs) {
            assertSelectQuery(
                    "from Contact where " + (fieldAsLhs ? "age <= ?1" : "?1 >= age"),
                    Contact.class,
                    q -> q.setParameter(1, 35),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'age': {'$lte': 35}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(1, 2, 3, 5));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGt(boolean fieldAsLhs) {
            assertSelectQuery(
                    "from Contact where " + (fieldAsLhs ? "age > :age" : ":age < age"),
                    Contact.class,
                    q -> q.setParameter("age", 18),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'age': {'$gt': 18}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 4, 5));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGte(boolean fieldAsLhs) {
            assertSelectQuery(
                    "from Contact where " + (fieldAsLhs ? "age >= :age" : ":age <= age"),
                    Contact.class,
                    q -> q.setParameter("age", 18),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'age': {'$gte': 18}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(1, 2, 4, 5));
        }

        @Test
        void testAndFilter() {
            assertSelectQuery(
                    "from Contact where country = ?1 and age > ?2",
                    Contact.class,
                    q -> q.setParameter(1, Country.CANADA.name()).setParameter(2, 18),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'$and': [{'country': {'$eq': 'CANADA'}}, {'age': {'$gt': 18}}]}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 4));
        }

        @Test
        void testOrFilter() {
            assertSelectQuery(
                    "from Contact where country = :country or age > :age",
                    Contact.class,
                    q -> q.setParameter("country", Country.CANADA.name()).setParameter("age", 18),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'$or': [{'country': {'$eq': 'CANADA'}}, {'age': {'$gt': 18}}]}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 3, 4, 5));
        }

        @Test
        void testSingleNegation() {
            assertSelectQuery(
                    "from Contact where age > 18 and not (country = 'USA')",
                    Contact.class,
                    null,
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'$and': [{'age': {'$gt': 18}}, {'$nor': [{'country': {'$eq': 'USA'}}]}]}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 4));
        }

        @Test
        void testDoubleNegation() {
            assertSelectQuery(
                    "from Contact where age > 18 and not ( not (country = 'USA') )",
                    Contact.class,
                    null,
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'$and': [{'age': {'$gt': 18}}, {'$nor': [{'$nor': [{'country': {'$eq': 'USA'}}]}]}]}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(5));
        }

        @Test
        void testProjectOperation() {
            assertSelectQuery(
                    "select name, age from Contact where country = :country",
                    Object[].class,
                    q -> q.setParameter("country", Country.CANADA.name()),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'country': {'$eq': 'CANADA'}}}, {'$project': {'name': true, 'age': true}}]}",
                    List.of(new Object[] {"Mary", 35}, new Object[] {"Dylan", 7}, new Object[] {"Lucy", 78}));
        }
    }

    @Nested
    class QueryLiteralTests {

        private Book testingBook;

        @BeforeEach
        void beforeEach() {
            testingBook = new Book();
            testingBook.title = "Holy Bible";
            testingBook.outOfStock = true;
            testingBook.publishYear = 1995;
            testingBook.isbn13 = 9780310904168L;
            testingBook.discount = 0.25;
            testingBook.price = new BigDecimal("123.50");
            sessionFactoryScope.inTransaction(session -> session.persist(testingBook));

            mongoTestCommandListener.clear();
        }

        @Test
        void testBoolean() {
            assertSelectQuery(
                    "from Book where outOfStock = true",
                    Book.class,
                    null,
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'outOfStock': {'$eq': true}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}",
                    singletonList(testingBook));
        }

        @Test
        void testInteger() {
            assertSelectQuery(
                    "from Book where publishYear = 1995",
                    Book.class,
                    null,
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'publishYear': {'$eq': 1995}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}",
                    singletonList(testingBook));
        }

        @Test
        void testLong() {
            assertSelectQuery(
                    "from Book where isbn13 = 9780310904168L",
                    Book.class,
                    null,
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'isbn13': {'$eq': 9780310904168}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}",
                    singletonList(testingBook));
        }

        @Test
        void testDouble() {
            assertSelectQuery(
                    "from Book where discount = 0.25D",
                    Book.class,
                    null,
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'discount': {'$eq': 0.25}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}",
                    singletonList(testingBook));
        }

        @Test
        void testString() {
            assertSelectQuery(
                    "from Book where title = 'Holy Bible'",
                    Book.class,
                    null,
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'title': {'$eq': 'Holy Bible'}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}",
                    singletonList(testingBook));
        }

        @Test
        void testBigDecimal() {
            assertSelectQuery(
                    "from Book where price = 123.50BD",
                    Book.class,
                    null,
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'price': {'$eq': {'$numberDecimal': '123.50'}}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}",
                    singletonList(testingBook));
        }
    }

    private <T> void assertSelectQuery(
            String hql,
            Class<T> resultType,
            Consumer<SelectionQuery<T>> queryPostProcessor,
            String expectedMql,
            List<T> expectedResultList) {
        sessionFactoryScope.inTransaction(session -> {
            var selectionQuery = session.createSelectionQuery(hql, resultType);
            if (queryPostProcessor != null) {
                queryPostProcessor.accept(selectionQuery);
            }
            var resultList = selectionQuery.getResultList();

            assertActualCommand(BsonDocument.parse(expectedMql));

            assertThat(resultList)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactlyElementsOf(expectedResultList);
        });
    }

    private void assertActualCommand(BsonDocument expectedCommand) {
        assertTrue(mongoTestCommandListener.areAllCommandsFinishedAndSucceeded());
        var capturedCommands = mongoTestCommandListener.getCommandsSucceeded();

        assertThat(capturedCommands)
                .singleElement()
                .extracting(MongoTestCommandListener::getActualAggregateCommand)
                .isEqualTo(expectedCommand);
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
