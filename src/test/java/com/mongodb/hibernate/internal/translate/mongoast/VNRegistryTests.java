/*
 * Copyright 2026-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.translate.mongoast;

import static com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator.ADD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.bson.BsonInt32;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.junit.jupiter.api.Test;

class VNRegistryTests {

    private final VNRegistry registry = new VNRegistry();

    private static AstExpression field(String path) {
        return new AstFieldPathExpression(path);
    }

    private static AstExpression lit(int i) {
        return new AstLiteralExpression(new AstLiteral(new BsonInt32(i)));
    }

    private static AstExpression add(AstExpression left, AstExpression right) {
        return new AstBinaryOperatorExpression(ADD, left, right);
    }

    /** An expression that reports whatever key it is given, and counts how often it is asked for one. */
    private static final class Described implements AstExpression {
        private final StructuralKey key;
        private int describeCount;

        private Described(String tag, Object... fields) {
            this.key = new StructuralKey(tag, List.of(fields));
        }

        @Override
        public StructuralKey structuralKey() {
            describeCount++;
            return key;
        }

        @Override
        public AstExpression mapChildren(AstNodeRewriter rewriter) {
            return this;
        }

        @Override
        public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void equalKeysShareANumber() {
        assertThat(registry.valueNumber(new Described("tag", "a", 1)))
                .isEqualTo(registry.valueNumber(new Described("tag", "a", 1)));
    }

    @Test
    void theTagDistinguishes() {
        assertThat(registry.valueNumber(new Described("one", "a")))
                .isNotEqualTo(registry.valueNumber(new Described("two", "a")));
    }

    @Test
    void aFieldValueDistinguishes() {
        assertThat(registry.valueNumber(new Described("tag", "a")))
                .isNotEqualTo(registry.valueNumber(new Described("tag", "b")));
    }

    @Test
    void theNumberOfFieldsDistinguishes() {
        assertThat(registry.valueNumber(new Described("tag", "a")))
                .isNotEqualTo(registry.valueNumber(new Described("tag", "a", "a")));
    }

    @Test
    void aFlatFieldListDiffersFromANestedOne() {
        // AstSwitchExpression spreads its branches into the field list rather than nesting them, which
        // distinguishes one shape from another only if the two forms differ.
        assertThat(registry.valueNumber(new Described("tag", 1, 2)))
                .isNotEqualTo(registry.valueNumber(new Described("tag", List.of(1, 2))));
    }

    @Test
    void anExpressionIsAskedForItsKeyOnlyOnce() {
        var node = new Described("tag", "a");

        registry.valueNumber(node);
        registry.valueNumber(node);

        assertThat(node.describeCount).isEqualTo(1);
    }

    @Test
    void separatelyBuiltEqualExpressionsAgree() {
        assertThat(registry.valueNumber(field("x"))).isEqualTo(registry.valueNumber(field("x")));
    }

    @Test
    void numberingIsStructuralNotAlgebraic() {
        assertThat(registry.valueNumber(add(field("a"), lit(1))))
                .isNotEqualTo(registry.valueNumber(add(lit(1), field("a"))));
    }

    @Test
    void aSubtreeSharesItsNumberWithTheSameSubtreeElsewhere() {
        int standalone = registry.valueNumber(add(field("a"), lit(1)));
        registry.valueNumber(add(add(field("a"), lit(1)), lit(2)));

        assertThat(registry.valueNumber(add(field("a"), lit(1)))).isEqualTo(standalone);
    }

    @Test
    void numbersAreScopedToOneRegistry() {
        // A number means nothing across registries, so a rewriter must not mix them.
        assertThat(new VNRegistry().valueNumber(field("only")))
                .isEqualTo(new VNRegistry().valueNumber(field("different")));
    }

    @Test
    void expressionsInsideAMapAreNumbered() {
        // A let binding holds its variables as a map and a named operator its arguments, so the
        // substitution has to reach the expressions inside one.
        var withX = new Described("tag", new TreeMap<>(Map.of("k", field("x"))));
        var alsoWithX = new Described("tag", new TreeMap<>(Map.of("k", field("x"))));
        var withY = new Described("tag", new TreeMap<>(Map.of("k", field("y"))));

        assertThat(registry.valueNumber(withX)).isEqualTo(registry.valueNumber(alsoWithX));
        assertThat(registry.valueNumber(withX)).isNotEqualTo(registry.valueNumber(withY));
    }

    @Test
    void expressionsInsideAnyCollectionAreNumbered() {
        var one = new Described("tag", Set.of(field("x")));
        var another = new Described("tag", Set.of(field("x")));

        assertThat(registry.valueNumber(one)).isEqualTo(registry.valueNumber(another));
    }

    @Test
    void expressionsInsideNestedContainersAreNumbered() {
        var withX = new Described("tag", List.of(List.of(field("x"))));
        var alsoWithX = new Described("tag", List.of(List.of(field("x"))));
        var withY = new Described("tag", List.of(List.of(field("y"))));

        assertThat(registry.valueNumber(withX)).isEqualTo(registry.valueNumber(alsoWithX));
        assertThat(registry.valueNumber(withX)).isNotEqualTo(registry.valueNumber(withY));
    }

    @Test
    void aValuePassesIntoTheKeyAsItIs() {
        var one = new Described("tag", new AstLiteral(new BsonInt32(1)));
        var another = new Described("tag", new AstLiteral(new BsonInt32(1)));

        assertThat(registry.valueNumber(one)).isEqualTo(registry.valueNumber(another));
    }

    @Test
    void aNodeThatIsNeitherNumberedNorAValueFails() {
        // AstSwitchCase holds expressions without being one, so passing it through would compare subtrees instead.
        var node = new Described("tag", new AstSwitchCase(field("x"), field("y")));

        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> registry.valueNumber(node))
                .withMessageContaining("AstSwitchCase");
    }

    /** Captures, so each call yields a distinct binder: a non-capturing lambda may be shared by the JVM. */
    private static JdbcParameterBinder binder() {
        var distinct = new Object();
        return (statement, startPosition, jdbcParameterBindings, executionContext) -> distinct.hashCode();
    }

    @Test
    void oneQueryParameterHasOneNumberAcrossItsOccurrences() {
        // Hibernate allocates a JdbcParameter, and so a binder, per occurrence, so a GROUP BY key would not match its
        // occurrences in SELECT or HAVING if the binder reached the value number.
        AstExpression inGroupBy = new AstValueExpression(new AstParameterMarker(binder(), 7));
        AstExpression inSelect = new AstValueExpression(new AstParameterMarker(binder(), 7));

        assertThat(registry.valueNumber(inGroupBy)).isEqualTo(registry.valueNumber(inSelect));
    }

    @Test
    void distinctQueryParametersHaveDistinctNumbers() {
        AstExpression one = new AstValueExpression(new AstParameterMarker(binder(), 7));
        AstExpression another = new AstValueExpression(new AstParameterMarker(binder(), 8));

        assertThat(registry.valueNumber(one)).isNotEqualTo(registry.valueNumber(another));
    }

    @Test
    void parametersWithoutIdsAreNotConflated() {
        // Without an id there is nothing to identify a parameter by, so distinct binders must stay distinct.
        AstExpression one = new AstValueExpression(new AstParameterMarker(binder(), null));
        AstExpression another = new AstValueExpression(new AstParameterMarker(binder(), null));

        assertThat(registry.valueNumber(one)).isNotEqualTo(registry.valueNumber(another));
    }

    @Test
    void anArithmeticExpressionOverOneParameterHasOneNumber() {
        AstExpression inGroupBy = add(field("a"), new AstValueExpression(new AstParameterMarker(binder(), 3)));
        AstExpression inSelect = add(field("a"), new AstValueExpression(new AstParameterMarker(binder(), 3)));

        assertThat(registry.valueNumber(inGroupBy)).isEqualTo(registry.valueNumber(inSelect));
    }
}
