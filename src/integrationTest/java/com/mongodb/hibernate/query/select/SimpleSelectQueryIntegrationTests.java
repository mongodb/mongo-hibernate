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

import com.mongodb.hibernate.MongoTestAssertions;
import com.mongodb.hibernate.annotations.ObjectIdGenerator;
import com.mongodb.hibernate.cfg.MongoConfigurator;
import com.mongodb.hibernate.junit.MongoExtension;
import com.mongodb.hibernate.service.spi.MongoConfigurationContributor;
import com.mongodb.hibernate.util.TestCommandListener;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serial;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.hibernate.query.SelectionQuery;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
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
@ServiceRegistry(
        services =
                @ServiceRegistry.Service(
                        role = MongoConfigurationContributor.class,
                        impl = SimpleSelectQueryIntegrationTests.TestingMongoConfigurationContributor.class))
@ExtendWith(MongoExtension.class)
class SimpleSelectQueryIntegrationTests implements SessionFactoryScopeAware {

    public static class TestingMongoConfigurationContributor implements MongoConfigurationContributor {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public void configure(MongoConfigurator configurator) {
            configurator.applyToMongoClientSettings(builder -> builder.addCommandListener(MONGO_COMMAND_LISTENER));
        }
    }

    private static final TestCommandListener MONGO_COMMAND_LISTENER = new TestCommandListener();

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    @Nested
    class QueryTests {

        private final List<Contact> testingContacts = List.of(
                new Contact(1, "Bob", 18, Country.USA),
                new Contact(2, "Mary", 35, Country.CANADA),
                new Contact(3, "Dylan", 7, Country.CANADA),
                new Contact(4, "Lucy", 78, Country.CANADA),
                new Contact(5, "John", 25, Country.USA));

        private List<Contact> getTestingContacts(int... ids) {
            return Arrays.stream(ids)
                    .mapToObj(id -> testingContacts.get(id - 1))
                    .toList();
        }

        @BeforeEach
        void beforeEach() {
            sessionFactoryScope.inTransaction(session -> testingContacts.forEach(session::persist));
            MONGO_COMMAND_LISTENER.clear();
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByEq(boolean fieldAsLhs) {
            assertContactQuery(
                    "from Contact where " + (fieldAsLhs ? "country = :country" : ":country = country"),
                    q -> q.setParameter("country", Country.USA.name()),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'country': {'$eq': 'USA'}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(1, 5));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByNe(boolean fieldAsLhs) {
            assertContactQuery(
                    "from Contact where " + (fieldAsLhs ? "country != ?1" : "?1 != country"),
                    q -> q.setParameter(1, Country.USA.name()),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'country': {'$ne': 'USA'}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 3, 4));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLt(boolean fieldAsLhs) {
            assertContactQuery(
                    "from Contact where " + (fieldAsLhs ? "age < :age" : ":age > age"),
                    q -> q.setParameter("age", 35),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'age': {'$lt': 35}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(1, 3, 5));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByLte(boolean fieldAsLhs) {
            assertContactQuery(
                    "from Contact where " + (fieldAsLhs ? "age <= ?1" : "?1 >= age"),
                    q -> q.setParameter(1, 35),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'age': {'$lte': 35}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(1, 2, 3, 5));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGt(boolean fieldAsLhs) {
            assertContactQuery(
                    "from Contact where " + (fieldAsLhs ? "age > :age" : ":age < age"),
                    q -> q.setParameter("age", 18),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'age': {'$gt': 18}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 4, 5));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testComparisonByGte(boolean fieldAsLhs) {
            assertContactQuery(
                    "from Contact where " + (fieldAsLhs ? "age >= :age" : ":age <= age"),
                    q -> q.setParameter("age", 18),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'age': {'$gte': 18}}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(1, 2, 4, 5));
        }

        @Test
        void testAndFilter() {
            assertContactQuery(
                    "from Contact where country = ?1 and age > ?2",
                    q -> q.setParameter(1, Country.CANADA.name()).setParameter(2, 18),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'$and': [{'country': {'$eq': 'CANADA'}}, {'age': {'$gt': 18}}]}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 4));
        }

        @Test
        void testOrFilter() {
            assertContactQuery(
                    "from Contact where country = :country or age > :age",
                    q -> q.setParameter("country", Country.CANADA.name()).setParameter("age", 18),
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'$or': [{'country': {'$eq': 'CANADA'}}, {'age': {'$gt': 18}}]}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 3, 4, 5));
        }

        @Test
        void testSingleNegation() {
            assertContactQuery(
                    "from Contact where age > 18 and not (country = 'USA')",
                    null,
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'$and': [{'age': {'$gt': 18}}, {'$nor': [{'country': {'$eq': 'USA'}}]}]}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(2, 4));
        }

        @Test
        void testDoubleNegation() {
            assertContactQuery(
                    "from Contact where age > 18 and not ( not (country = 'USA') )",
                    null,
                    "{'aggregate': 'contacts', 'pipeline': [{'$match': {'$and': [{'age': {'$gt': 18}}, {'$nor': [{'$nor': [{'country': {'$eq': 'USA'}}]}]}]}}, {'$project': {'_id': true, 'age': true, 'country': true, 'name': true}}]}",
                    getTestingContacts(5));
        }

        private void assertContactQuery(
                String hql,
                Consumer<SelectionQuery<Contact>> queryPostProcessor,
                String expectedMql,
                List<? extends Contact> expectedContacts) {
            sessionFactoryScope.inTransaction(session -> {
                var selectionQuery = session.createSelectionQuery(hql, Contact.class);
                if (queryPostProcessor != null) {
                    queryPostProcessor.accept(selectionQuery);
                }
                var resultList = selectionQuery.getResultList();

                var capturedCommands = MONGO_COMMAND_LISTENER.getFinishedCommands();

                assertThat(capturedCommands)
                        .singleElement()
                        .extracting(TestCommandListener::getActualAggregateCommand)
                        .usingRecursiveComparison()
                        .isEqualTo(BsonDocument.parse(expectedMql));

                assertThat(resultList)
                        .usingRecursiveFieldByFieldElementComparator()
                        .containsExactlyElementsOf(expectedContacts);
            });
        }

        @Test
        void testProjectOperation() {
            sessionFactoryScope.inTransaction(session -> {
                var results = session.createSelectionQuery(
                                "select name, age from Contact where country = :country", Object[].class)
                        .setParameter("country", Country.CANADA.name())
                        .getResultList();
                assertThat(results)
                        .containsExactly(
                                new Object[] {"Mary", 35}, new Object[] {"Dylan", 7}, new Object[] {"Lucy", 78});
            });
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

            MONGO_COMMAND_LISTENER.clear();
        }

        @Test
        void testBoolean() {
            assertBookQuery(
                    "from Book where outOfStock = true",
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'outOfStock': {'$eq': true}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}");
        }

        @Test
        void testInteger() {
            assertBookQuery(
                    "from Book where publishYear = 1995",
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'publishYear': {'$eq': 1995}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}");
        }

        @Test
        void testLong() {
            assertBookQuery(
                    "from Book where isbn13 = 9780310904168L",
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'isbn13': {'$eq': 9780310904168}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}");
        }

        @Test
        void testDouble() {
            assertBookQuery(
                    "from Book where discount = 0.25",
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'discount': {'$eq': 0.25}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}");
        }

        @Test
        void testString() {
            assertBookQuery(
                    "from Book where title = 'Holy Bible'",
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'title': {'$eq': 'Holy Bible'}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}");
        }

        @Test
        void testBigDecimal() {
            assertBookQuery(
                    "from Book where price = 123.50BD",
                    "{'aggregate': 'books', 'pipeline': [{'$match': {'price': {'$eq': {'$numberDecimal': '123.50'}}}}, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true}}]}");
        }

        private void assertBookQuery(String hql, String expectedMql) {
            sessionFactoryScope.inTransaction(session -> {
                var selectionQuery = session.createSelectionQuery(hql, Book.class);
                var resultList = selectionQuery.getResultList();

                var capturedCommands = MONGO_COMMAND_LISTENER.getFinishedCommands();

                assertThat(capturedCommands)
                        .singleElement()
                        .extracting(TestCommandListener::getActualAggregateCommand)
                        .usingRecursiveComparison()
                        .isEqualTo(BsonDocument.parse(expectedMql));

                assertThat(resultList).hasSize(1);
                MongoTestAssertions.assertEquals(testingBook, resultList.get(0));
            });
        }
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
