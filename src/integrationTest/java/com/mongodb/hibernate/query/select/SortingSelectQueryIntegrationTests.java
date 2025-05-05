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

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder.ASC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder;
import java.util.Arrays;
import java.util.List;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.SortDirection;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DomainModel(annotatedClasses = Book.class)
class SortingSelectQueryIntegrationTests extends AbstractSelectionQueryIntegrationTests {

    private static final List<Book> testingBooks = List.of(
            new Book(1, "War and Peace", 1869, true),
            new Book(2, "Crime and Punishment", 1866, false),
            new Book(3, "Anna Karenina", 1877, false),
            new Book(4, "The Brothers Karamazov", 1880, false),
            new Book(5, "War and Peace", 2025, false));

    private static List<Book> getBooksByIds(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> testingBooks.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .toList();
    }

    @BeforeEach
    void beforeEach() {
        sessionFactoryScope.inTransaction(session -> testingBooks.forEach(session::persist));
        testCommandListener.clear();
    }

    @ParameterizedTest
    @EnumSource(AstSortOrder.class)
    void testOrderBySingleFieldWithoutTies(AstSortOrder sortOrder) {
        assertSelectionQuery(
                "from Book as b ORDER BY b.publishYear " + sortOrder,
                Book.class,
                null,
                "{ 'aggregate': 'books', 'pipeline': [ { '$sort': { 'publishYear': " + (sortOrder == ASC ? "1" : "-1")
                        + " } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                sortOrder == ASC ? getBooksByIds(2, 1, 3, 4, 5) : getBooksByIds(5, 4, 3, 1, 2));
    }

    @ParameterizedTest
    @EnumSource(AstSortOrder.class)
    void testOrderBySingleFieldWithTies(AstSortOrder sortOrder) {
        assertSelectionQuery(
                "from Book as b ORDER BY b.title " + sortOrder,
                Book.class,
                null,
                "{ 'aggregate': 'books', 'pipeline': [ { '$sort': { 'title': " + (sortOrder == ASC ? "1" : "-1")
                        + " } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                sortOrder == ASC
                        ? resultList -> assertThat(resultList)
                                .satisfiesAnyOf(
                                        list -> assertResultListEquals(getBooksByIds(3, 2, 4, 1, 5), list),
                                        list -> assertResultListEquals(getBooksByIds(3, 2, 4, 5, 1), list))
                        : resultList -> assertThat(resultList)
                                .satisfiesAnyOf(
                                        list -> assertResultListEquals(getBooksByIds(1, 5, 4, 2, 3), list),
                                        list -> assertResultListEquals(getBooksByIds(5, 1, 4, 2, 3), list)));
    }

    @Test
    void testOrderByMultipleFieldsWithoutTies() {
        assertSelectionQuery(
                "from Book where outOfStock = false ORDER BY title ASC, publishYear DESC, id ASC",
                Book.class,
                null,
                "{ 'aggregate': 'books', 'pipeline': [ {'$match': {'outOfStock': {'$eq': false}}}, { '$sort': { 'title': 1, 'publishYear': -1, '_id': 1 } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                getBooksByIds(3, 2, 4, 5));
    }

    @Test
    void testOrderByMultipleFieldsWithTies() {
        assertSelectionQuery(
                "from Book ORDER BY title ASC, publishYear DESC, id ASC",
                Book.class,
                null,
                "{ 'aggregate': 'books', 'pipeline': [ { '$sort': { 'title': 1, 'publishYear': -1, '_id': 1 } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                resultList -> assertThat(resultList)
                        .satisfiesAnyOf(
                                list -> assertResultListEquals(getBooksByIds(3, 2, 4, 1, 5), list),
                                list -> assertResultListEquals(getBooksByIds(3, 2, 4, 5, 1), list)));
    }

    @Test
    void testSortFieldByAlias() {
        assertSelectionQuery(
                "select b.title as title, b.publishYear as year from Book as b ORDER BY year DESC, title ASC",
                Object[].class,
                null,
                "{'aggregate': 'books', 'pipeline': [{'$sort': {'publishYear': -1, 'title': 1}}, {'$project': {'title': true, 'publishYear': true}}]}",
                List.of(
                        new Object[] {"War and Peace", 2025},
                        new Object[] {"The Brothers Karamazov", 1880},
                        new Object[] {"Anna Karenina", 1877},
                        new Object[] {"War and Peace", 1869},
                        new Object[] {"Crime and Punishment", 1866}));
    }

    @Test
    void testSortFieldByOrdinalReference() {
        assertSelectionQuery(
                "select b.title as title, b.publishYear as year from Book as b ORDER BY 1 ASC, 2 DESC",
                Object[].class,
                null,
                "{'aggregate': 'books', 'pipeline': [{'$sort': {'publishYear': -1, 'title': 1}}, {'$project': {'title': true, 'publishYear': true}}]}",
                List.of(
                        new Object[] {"Anna Karenina", 1877},
                        new Object[] {"Crime and Punishment", 1866},
                        new Object[] {"The Brothers Karamazov", 1880},
                        new Object[] {"War and Peace", 2025},
                        new Object[] {"War and Peace", 1869}));
    }

    @Test
    void testSortFieldNotFieldPathExpressionNotSupported() {
        assertSelectQueryFailure(
                "from Book ORDER BY length(title)",
                Book.class,
                FeatureNotSupportedException.class,
                "%s does not support sort key not of field path type",
                MONGO_DBMS_NAME);
    }

    @Test
    void testNullPrecedenceFeatureNotSupported() {
        assertSelectQueryFailure(
                "from Book ORDER BY publishYear NULLS LAST",
                Book.class,
                FeatureNotSupportedException.class,
                "%s does not support nulls precedence: NULLS LAST",
                MONGO_DBMS_NAME);
    }

    @Test
    void testCaseInsensitiveSortSpecNotSupported() {
        sessionFactoryScope.inTransaction(session -> {
            var cb = sessionFactoryScope.getSessionFactory().getCriteriaBuilder();
            var criteria = cb.createQuery(Book.class);
            var root = criteria.from(Book.class);
            criteria.select(root);
            criteria.orderBy(cb.sort(root.get("title"), SortDirection.ASCENDING, NullPrecedence.NONE, true));
            assertThatThrownBy(() -> session.createSelectionQuery(criteria).getResultList())
                    .isInstanceOf(FeatureNotSupportedException.class);
        });
    }

    @Nested
    class SqlTupleTests {

        @Test
        void testOrderBySimpleTuple() {
            assertSelectionQuery(
                    "from Book ORDER BY (publishYear, title) ASC",
                    Book.class,
                    null,
                    "{ 'aggregate': 'books', 'pipeline': [ { '$sort': { 'publishYear': 1, 'title': 1 } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                    getBooksByIds(2, 1, 3, 4, 5));
        }

        @Test
        void testOrderByNestedTuple() {
            assertSelectionQuery(
                    "from Book ORDER BY (title, (id, publishYear)) DESC",
                    Book.class,
                    null,
                    "{ 'aggregate': 'books', 'pipeline': [ { '$sort': { 'title': -1, '_id': -1, 'publishYear': -1 } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                    getBooksByIds(5, 1, 4, 2, 3));
        }
    }
}
