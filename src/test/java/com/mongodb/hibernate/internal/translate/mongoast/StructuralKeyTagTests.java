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
import static com.mongodb.hibernate.internal.translate.mongoast.AstLogicalOperator.AND;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

/**
 * Each per-type test pins its own tag, but two kinds sharing one would satisfy both of those and still let a value
 * number stand for two different expressions.
 */
class StructuralKeyTagTests {

    private static AstExpression field() {
        return new AstFieldPathExpression("a");
    }

    private static List<AstExpression> oneOfEachKind() {
        return List.of(
                field(),
                new AstVariableExpression("v"),
                new AstLiteralExpression(new AstLiteral(new BsonInt32(1))),
                new AstValueExpression(new AstLiteral(new BsonInt32(1))),
                new AstUnaryOperatorExpression("$not", field()),
                new AstBinaryOperatorExpression(ADD, field(), field()),
                new AstRegexMatchExpression(field(), "r", "i"),
                new AstInExpression(field(), List.of(field())),
                new AstLogicalOperatorExpression(AND, List.of(field())),
                new AstPositionalOperatorExpression("$op", List.of(field())),
                new AstNamedOperatorExpression("$op", new TreeMap<>(Map.of("k", field()))),
                new AstLetBindingExpression(field(), new TreeMap<>(Map.of("v", field()))),
                new AstSwitchExpression(List.of(new AstSwitchCase(field(), field())), field()));
    }

    @Test
    void everyKindOfExpressionIsCovered() throws Exception {
        // Guards the list above: a new expression kind must be added to it, and so must get a tag of its own.
        var covered = oneOfEachKind().stream()
                .map(expression -> expression.getClass().getName())
                .collect(toSet());

        assertThat(compiledExpressionKinds()).isEqualTo(covered);
    }

    /**
     * Every concrete {@link AstExpression} on the compiled main output, so the list above cannot silently fall behind.
     */
    private static Set<String> compiledExpressionKinds() throws Exception {
        var packageDirectory = Path.of(AstExpression.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .resolve(AstExpression.class.getPackageName().replace('.', '/'));
        try (var entries = Files.list(packageDirectory)) {
            var kinds = new HashSet<String>();
            for (Path entry :
                    entries.filter(f -> f.toString().endsWith(".class")).toList()) {
                var name = AstExpression.class.getPackageName() + "."
                        + entry.getFileName().toString().replace(".class", "");
                Class<?> candidate = Class.forName(name);
                if (AstExpression.class.isAssignableFrom(candidate) && !candidate.isInterface()) {
                    kinds.add(candidate.getName());
                }
            }
            return kinds;
        }
    }

    @Test
    void everyTagIsTheKindOfExpressionItNames() {
        // The tag exists to tell one kind of expression from another, so deriving it from the name keeps it saying so.
        for (AstExpression expression : oneOfEachKind()) {
            var name = expression.getClass().getSimpleName();
            var expected = name.substring("Ast".length(), name.length() - "Expression".length());

            assertThat(expression.structuralKey().tag()).isEqualTo(expected);
        }
    }

    @Test
    void noTwoKindsShareATag() {
        var tags = oneOfEachKind().stream()
                .map(expression -> expression.structuralKey().tag())
                .toList();

        assertThat(tags).doesNotHaveDuplicates();
    }

    @Test
    void noTwoKindsShareAValueNumber() {
        var registry = new VNRegistry();

        var numbers = oneOfEachKind().stream().map(registry::valueNumber).toList();

        assertThat(numbers).doesNotHaveDuplicates();
    }
}
