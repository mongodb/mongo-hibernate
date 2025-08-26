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

package com.mongodb.hibernate.query.function.array;

import static com.mongodb.hibernate.MongoTestAssertions.assertIterableEq;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.mongodb.MongoCommandException;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.junit.MongoExtension;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.JDBCException;
import org.hibernate.query.sqm.UnknownPathException;
import org.hibernate.query.sqm.produce.function.FunctionArgumentException;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SessionFactory(exportSchema = false)
@DomainModel(annotatedClasses = {FunctionForArrayIntegrationTests.Item.class})
@ExtendWith(MongoExtension.class)
// TODO-HIBERNATE-74 We need to make sure the functions behave in accordance with the ternary logic
public class FunctionForArrayIntegrationTests implements SessionFactoryScopeAware {
    private static final String COLLECTION_NAME = "items";

    private SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    private void persistInTransaction(Iterable<?> objects) {
        sessionFactoryScope.inTransaction(session -> {
            for (var object : objects) {
                session.persist(object);
            }
        });
    }

    /** Hibernate ORM error. */
    private static void assertRequiresArrayArgument(ThrowingCallable shouldRaiseThrowable) {
        assertThatThrownBy(shouldRaiseThrowable)
                .isInstanceOf(FunctionArgumentException.class)
                .hasMessageMatching("Parameter . of function .* requires an array type, but argument is of type .*");
    }

    /** Hibernate ORM error. */
    private static void assertRequiresDifferentTypeOfArgument(ThrowingCallable shouldRaiseThrowable) {
        assertThatThrownBy(shouldRaiseThrowable)
                .isInstanceOf(FunctionArgumentException.class)
                .hasMessageMatching("Parameter . of function .* has type .*, but argument is of type .*");
    }

    /** Our error. */
    private static void assertRequiresSingularArgument(ThrowingCallable shouldRaiseThrowable) {
        assertThatThrownBy(shouldRaiseThrowable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(FunctionArgumentException.class)
                .hasMessageMatching(".*Parameter .* of function .* requires a singular value, but argument is plural");
    }

    /** Our error. */
    private static void assertRequiresArrayNotListArgument(ThrowingCallable shouldRaiseThrowable) {
        assertThatThrownBy(shouldRaiseThrowable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(FunctionArgumentException.class)
                .hasMessageMatching(".*Parameter . of function .* requires an array, but argument is a list");
    }

    /** Our error. */
    private static void assertRequiresNonHqlPathExpressionArgument(ThrowingCallable shouldRaiseThrowable) {
        assertThatThrownBy(shouldRaiseThrowable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(FunctionArgumentException.class)
                .hasMessageMatching(".*Parameter . of function .* requires .*value that is not an HQL path expression");
    }

    /** Our error. */
    private static void assertRequiresHqlPathExpressionArgument(ThrowingCallable shouldRaiseThrowable) {
        assertThatThrownBy(shouldRaiseThrowable)
                .isInstanceOf(FeatureNotSupportedException.class)
                .hasMessageMatching("Parameter . of function .* requires an HQL path expression");
    }

    @Nested
    @ParameterizedClass
    @ValueSource(booleans = {true, false})
    class ArrayContains {
        @Parameter
        private boolean testNullable;

        private Item item;

        @BeforeEach
        void beforeEach() {
            item = new Item(1, asList(2, null, 3));
            persistInTransaction(List.of(item, new Item(4, null)));
        }

        private String functionName() {
            return "array_contains" + (testNullable ? "_nullable" : "");
        }

        @Test
        void testWithFirstArgumentAsParameter() {
            Function<Object, List<Item>> function =
                    haystack -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where %s(:haystack, 3)", functionName()), Item.class)
                            .setParameter("haystack", haystack)
                            .getResultList());
            assertAll(
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply(new int[] {})),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply(List.of())),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply(3)));
        }

        @Test
        void testWithFirstArgumentInlined() {
            Function<Object, List<Item>> function =
                    haystack -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where %s(%s, 3)", functionName(), haystack), Item.class)
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(item), function.apply("item.ints")),
                    () -> assertIterableEq(List.of(item), function.apply("item.intsCollection")),
                    () -> assertRequiresArrayArgument(() -> function.apply("item.i")),
                    () -> assertRequiresArrayArgument(() -> function.apply("3")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("item.stringsCollection")),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply("array()")),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply("array_list()")),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply("array(3)")),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply("array_list(3)")));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void testWithSecondArgumentAsParameter(boolean haystackIsArrayNotCollection) {
            Function<Object, List<Item>> function =
                    needle -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format(
                                            "from Item item where %s(item.%s, :needle)",
                                            functionName(), haystackIsArrayNotCollection ? "ints" : "intsCollection"),
                                    Item.class)
                            .setParameter("needle", needle)
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(item), function.apply(3)),
                    () -> assertIterableEq(List.of(), function.apply(4)),
                    () -> assertIterableEq(List.of(), function.apply("3")),
                    () -> assertRequiresSingularArgument(() -> function.apply(new int[] {})),
                    () -> assertRequiresSingularArgument(() -> function.apply(List.of())),
                    () -> assertRequiresSingularArgument(() -> function.apply(new int[] {3})),
                    () -> assertRequiresSingularArgument(() -> function.apply(List.of(3))),
                    () -> assertRequiresSingularArgument(() -> function.apply(new String[] {"3"})),
                    () -> assertRequiresSingularArgument(() -> function.apply(List.of("3"))));
        }

        /** For simplicity, we test only {@link Item#intsCollection}, and not {@link Item#ints}. */
        @Test
        void testWithSecondArgumentInlined() {
            Function<Object, List<Item>> function =
                    needle -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where %s(item.intsCollection, %s)", functionName(), needle),
                                    Item.class)
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(item), function.apply("3")),
                    () -> assertIterableEq(List.of(), function.apply("4")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("array(3)")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("array('3')")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("array_list('3')")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("item.ints")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("item.stringsCollection")),
                    () -> {
                        // The error message does not seem correct. The second argument clearly must not be `int[]`,
                        // regardless of what the Hibernate ORM error message says.
                        // Given this behavior, it is unclear whether the intent was to allow the second parameter
                        // to be an HQL path expression, or not. The examples in the documentation do not use
                        // HQL path expressions, so we forbid them explicitly.
                        assertRequiresDifferentTypeOfArgument(() -> function.apply("item.i"));
                    },
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("item.s")),
                    () -> {
                        // Hibernate ORM allows the second argument to have a wrong type when it is singular
                        // and is passed as a query parameter, instead of being inlined, but forbids it being inlined.
                        assertRequiresDifferentTypeOfArgument(() -> function.apply("'3'"));
                    },
                    () -> assertRequiresSingularArgument(() -> function.apply("array()")),
                    () -> assertRequiresSingularArgument(() -> function.apply("array_list()")),
                    () -> assertRequiresSingularArgument(() -> function.apply("array_list(3)")),
                    () -> assertRequiresNonHqlPathExpressionArgument(() -> function.apply("item.intsCollection")));
        }
    }

    @Nested
    class ArrayContainsNullable {
        private Item item;

        @BeforeEach
        void beforeEach() {
            item = new Item(1, asList(2, null, 3));
            persistInTransaction(List.of(item, new Item(4, null)));
        }

        @Test
        void testWithSecondArgumentAsParameter() {
            Function<String, List<Item>> function =
                    (haystack) -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where array_contains_nullable(%s, :needle)", haystack),
                                    Item.class)
                            .setParameter("needle", null)
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(item), function.apply("item.intsCollection")),
                    () -> assertIterableEq(List.of(), function.apply("item.stringsCollection")),
                    () -> assertIterableEq(List.of(), function.apply("item.ints")),
                    () -> assertThatThrownBy(() -> function.apply("item.missing"))
                            .isInstanceOf(UnknownPathException.class));
        }

        @Test
        void testWithSecondArgumentInlined() {
            Function<Object, List<Item>> function =
                    haystack -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where array_contains_nullable(%s, null)", haystack),
                                    Item.class)
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(item), function.apply("item.intsCollection")),
                    () -> assertIterableEq(List.of(), function.apply("item.stringsCollection")),
                    () -> assertIterableEq(List.of(), function.apply("item.ints")),
                    () -> assertThatThrownBy(() -> function.apply("item.missing"))
                            .isInstanceOf(UnknownPathException.class));
        }
    }

    @Nested
    @ParameterizedClass
    @ValueSource(booleans = {true, false})
    class ArrayIncludes {
        @Parameter
        private boolean testNullable;

        private Item item;

        @BeforeEach
        void beforeEach() {
            item = new Item(1, asList(2, null, 3));
            persistInTransaction(List.of(item, new Item(4, null)));
        }

        private String functionName() {
            return "array_includes" + (testNullable ? "_nullable" : "");
        }

        @Test
        void testWithFirstArgumentAsParameter() {
            Function<Object, List<Item>> function =
                    haystack -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where %s(:haystack, array(3))", functionName()), Item.class)
                            .setParameter("haystack", haystack)
                            .getResultList());
            assertAll(
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply(new int[] {})),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply(List.of())),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply(3)));
        }

        @Test
        void testWithFirstArgumentInlined() {
            Function<Object, List<Item>> function =
                    haystack -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where %s(%s, array_list(3))", functionName(), haystack),
                                    Item.class)
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(item), function.apply("item.intsCollection")),
                    () -> assertRequiresArrayArgument(() -> function.apply("item.i")),
                    () -> assertRequiresArrayArgument(() -> function.apply("3")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("item.ints")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("item.stringsCollection")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("array(3)")),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply("array()")),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply("array_list()")),
                    () -> assertRequiresHqlPathExpressionArgument(() -> function.apply("array_list(3)")));
        }

        /** For simplicity, we test only {@link Item#intsCollection}, and not {@link Item#ints}. */
        @Test
        void testWithSecondArgumentAsParameter() {
            Function<Object, List<Item>> function =
                    needle -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where %s(item.intsCollection, :needle)", functionName()),
                                    Item.class)
                            .setParameter("needle", needle)
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(), function.apply(new int[] {})),
                    () -> assertIterableEq(List.of(), function.apply(new String[] {})),
                    () -> assertIterableEq(List.of(), function.apply(new String[] {"3"})),
                    () -> assertIterableEq(List.of(item), function.apply(new int[] {3})),
                    () -> assertIterableEq(List.of(item), function.apply(new int[] {2, 3})),
                    () -> {
                        // Hibernate ORM constructs an array from the second argument when it is singular
                        assertIterableEq(List.of(item), function.apply(3));
                    },
                    () -> assertRequiresArrayNotListArgument(() -> function.apply(List.of())),
                    () -> assertRequiresArrayNotListArgument(() -> function.apply(List.of(2, 3))),
                    () -> assertRequiresArrayNotListArgument(() -> function.apply(List.of("3"))),
                    () -> assertThatThrownBy(() -> function.apply("3"))
                            .isInstanceOf(JDBCException.class)
                            .hasRootCauseInstanceOf(MongoCommandException.class));
        }

        /** For simplicity, we test only {@link Item#intsCollection}, and not {@link Item#ints}. */
        @Test
        void testWithSecondArgumentInlined() {
            Function<Object, List<Item>> function =
                    needle -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where %s(item.intsCollection, %s)", functionName(), needle),
                                    Item.class)
                            .getResultList());
            assertAll(
                    () -> {
                        // Hibernate ORM does not allow non-empty arrays, but allows an empty array
                        assertIterableEq(List.of(), function.apply("array()"));
                    },
                    () -> assertIterableEq(List.of(), function.apply("array_list()")),
                    () -> assertIterableEq(List.of(item), function.apply("array_list(3)")),
                    () -> assertIterableEq(List.of(item), function.apply("array_list(2, 3)")),
                    () -> assertRequiresArrayArgument(() -> function.apply("item.i")),
                    () -> assertRequiresArrayArgument(() -> function.apply("item.s")),
                    () -> {
                        // Hibernate ORM constructs an array from the second argument when it is singular
                        // and is passed as a query parameter, instead of being inlined, but forbids it being inlined.
                        assertRequiresArrayArgument(() -> function.apply("3"));
                    },
                    () -> assertRequiresArrayArgument(() -> function.apply("'3'")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("array(3)")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("array('3')")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("array_list('3')")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("item.ints")),
                    () -> assertRequiresDifferentTypeOfArgument(() -> function.apply("item.stringsCollection")),
                    () -> assertRequiresNonHqlPathExpressionArgument(() -> function.apply("item.intsCollection")));
        }
    }

    @Nested
    class ArrayIncludesNullable {
        private Item item;

        @BeforeEach
        void beforeEach() {
            item = new Item(1, asList(2, null, 3));
            persistInTransaction(List.of(item, new Item(4, null)));
        }

        @Test
        void testWithSecondArgumentAsParameter() {
            Function<String, List<Item>> function =
                    (haystack) -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where array_includes_nullable(%s, :needle)", haystack),
                                    Item.class)
                            .setParameter("needle", new Integer[] {null})
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(item), function.apply("item.intsCollection")),
                    () -> assertIterableEq(List.of(), function.apply("item.stringsCollection")),
                    () -> assertIterableEq(List.of(), function.apply("item.ints")),
                    () -> assertThatThrownBy(() -> function.apply("item.missing"))
                            .isInstanceOf(UnknownPathException.class));
        }

        @Test
        void testWithSecondArgumentInlined() {
            Function<Object, List<Item>> function =
                    haystack -> sessionFactoryScope.fromTransaction(session -> session.createSelectionQuery(
                                    format("from Item item where array_includes_nullable(%s, array(null))", haystack),
                                    Item.class)
                            .getResultList());
            assertAll(
                    () -> assertIterableEq(List.of(item), function.apply("item.intsCollection")),
                    () -> assertIterableEq(List.of(), function.apply("item.stringsCollection")),
                    () -> assertIterableEq(List.of(), function.apply("item.ints")),
                    () -> assertThatThrownBy(() -> function.apply("item.missing"))
                            .isInstanceOf(UnknownPathException.class));
        }
    }

    @Entity(name = "Item")
    @Table(name = COLLECTION_NAME)
    static class Item {
        @Id
        int id;

        int[] ints;
        Collection<Integer> intsCollection;
        Collection<String> stringsCollection;
        int i;
        String s;

        Item() {}

        Item(int id, Collection<Integer> intsCollection) {
            this.id = id;
            this.ints = intsCollection == null
                    ? null
                    : intsCollection.stream()
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .toArray();
            this.intsCollection = intsCollection;
            stringsCollection = intsCollection == null
                    ? null
                    : intsCollection.stream().map(String::valueOf).toList();
            i = (ints != null && ints.length > 0) ? ints[1] : 0;
            s = String.valueOf(i);
        }
    }
}
