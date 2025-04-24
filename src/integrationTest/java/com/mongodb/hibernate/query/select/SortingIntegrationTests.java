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
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder;
import java.util.Arrays;
import java.util.List;
import org.hibernate.testing.orm.junit.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DomainModel(annotatedClasses = Book.class)
class SortingIntegrationTests extends AbstractSelectionQueryIntegrationTests {

    private static final List<Book> BOOKS = List.of(
            new Book(1, "War and Peace", 1869, true),
            new Book(2, "Crime and Punishment", 1866, false),
            new Book(3, "Anna Karenina", 1877, false),
            new Book(4, "The Brothers Karamazov", 1880, false),
            new Book(5, "War and Peace", 2025, false));

    private static List<Book> getBooksByIds(int... ids) {
        return Arrays.stream(ids)
                .mapToObj(id -> BOOKS.stream()
                        .filter(c -> c.id == id)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("id does not exist: " + id)))
                .toList();
    }

    @BeforeEach
    void beforeEach() {
        sessionFactoryScope.inTransaction(session -> BOOKS.forEach(session::persist));
        testCommandListener.clear();
    }

    @ParameterizedTest
    @EnumSource(AstSortOrder.class)
    void testOrderBySingleFieldWithoutTies(AstSortOrder sortOrder) {
        assertSelectionQuery(
                "from Book as b ORDER BY b.publishYear " + sortOrder,
                Book.class,
                null,
                "{ 'aggregate': 'books', 'pipeline': [ { '$sort': { 'publishYear': "
                        + sortOrder.getRenderedValue()
                        + " } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                sortOrder == AstSortOrder.ASC ? getBooksByIds(2, 1, 3, 4, 5) : getBooksByIds(5, 4, 3, 1, 2));
    }

    @ParameterizedTest
    @EnumSource(AstSortOrder.class)
    void testOrderBySingleFieldWithTies(AstSortOrder sortOrder) {
        assertSelectionQuery(
                "from Book as b ORDER BY b.title " + sortOrder,
                Book.class,
                null,
                "{ 'aggregate': 'books', 'pipeline': [ { '$sort': { 'title': " + sortOrder.getRenderedValue()
                        + " } }, {'$project': {'_id': true, 'discount': true, 'isbn13': true, 'outOfStock': true, 'price': true, 'publishYear': true, 'title': true} } ] }",
                sortOrder == AstSortOrder.ASC
                        ? resultList -> assertThat(resultList)
                                .satisfiesAnyOf(
                                        list -> assertResultList(resultList, getBooksByIds(3, 2, 4, 1, 5)),
                                        list -> assertResultList(resultList, getBooksByIds(3, 2, 4, 5, 1)))
                        : resultList -> assertThat(resultList)
                                .satisfiesAnyOf(
                                        list -> assertResultList(resultList, getBooksByIds(1, 5, 4, 2, 3)),
                                        list -> assertResultList(resultList, getBooksByIds(5, 1, 4, 2, 3))));
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
                                list -> assertResultList(resultList, getBooksByIds(3, 2, 4, 1, 5)),
                                list -> assertResultList(resultList, getBooksByIds(3, 2, 4, 5, 1))));
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
                "select b.title as title, b.publishYear as year from Book as b ORDER BY 2 DESC, 1 ASC",
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
    void testSortFieldNotFieldPathExpression() {
        assertSelectQueryFailure(
                "from Book b ORDER BY type(b)",
                Book.class,
                null,
                FeatureNotSupportedException.class,
                "%s does not support sort key not of field path type",
                MONGO_DBMS_NAME);
    }

    @Test
    void testSortFieldDuplicated() {
        assertSelectQueryFailure(
                "from Book ORDER BY title, publishYear, title",
                Book.class,
                null,
                FeatureNotSupportedException.class,
                "%s does not support duplicated sort keys ('%s' field is used more than once)",
                MONGO_DBMS_NAME,
                "title");
    }

    @Test
    void testNullPrecedenceFeatureNotSupported() {
        assertSelectQueryFailure(
                "from Book ORDER BY publishYear NULLS LAST",
                Book.class,
                null,
                FeatureNotSupportedException.class,
                "%s does not support Nulls Precedence",
                MONGO_DBMS_NAME);
    }

    static void assertResultList(List<Book> resultList, List<Book> expectedBooks) {
        assertThat(resultList).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(expectedBooks);
    }
}
