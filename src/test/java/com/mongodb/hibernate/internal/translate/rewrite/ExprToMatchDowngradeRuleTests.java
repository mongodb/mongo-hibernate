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

package com.mongodb.hibernate.internal.translate.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstComparisonExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.VNRegistry;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import java.util.HashMap;
import java.util.List;
import org.bson.BsonInt32;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ExprToMatchDowngradeRuleTests {

    private static AstExpression field(String path) {
        return new AstFieldPathExpression(path);
    }

    private static AstExpression lit(int i) {
        return new AstLiteralExpression(new AstLiteral(new BsonInt32(i)));
    }

    private static AstExprFilter expr(AstComparisonExpressionOperator op, AstExpression l, AstExpression r) {
        return new AstExprFilter(new AstBinaryOperatorExpression(op, l, r));
    }

    private static @Nullable AstFilter downgrade(AstExprFilter node) {
        return new ExprToMatchDowngradeRule().tryMatch(node);
    }

    @Test
    void downgradesFieldGtLiteral() {
        var out = downgrade(expr(AstComparisonExpressionOperator.GT, field("x"), lit(5)));
        assertThat(out)
                .isEqualTo(new AstFieldOperationFilter(
                        "x",
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.GT, new AstLiteral(new BsonInt32(5)))));
    }

    @Test
    void downgradesLiteralGtFieldWithOperatorFlip() {
        // 5 > x  →  x < 5
        var out = downgrade(expr(AstComparisonExpressionOperator.GT, lit(5), field("x")));
        assertThat(out)
                .isEqualTo(new AstFieldOperationFilter(
                        "x",
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.LT, new AstLiteral(new BsonInt32(5)))));
    }

    @Test
    void downgradesEqSymmetric() {
        // EQ flips to EQ; both orientations produce the same downgraded filter shape.
        var lhsField = downgrade(expr(AstComparisonExpressionOperator.EQ, field("x"), lit(5)));
        var rhsField = downgrade(expr(AstComparisonExpressionOperator.EQ, lit(5), field("x")));
        assertThat(lhsField).isEqualTo(rhsField);
    }

    @Test
    void downgradesGteToLteWhenFieldOnRight() {
        // 5 >= x  →  x <= 5
        var out = downgrade(expr(AstComparisonExpressionOperator.GTE, lit(5), field("x")));
        assertThat(out)
                .isEqualTo(new AstFieldOperationFilter(
                        "x",
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.LTE, new AstLiteral(new BsonInt32(5)))));
    }

    @Test
    void doesNotDowngradeWhenBothSidesAreFieldPaths() {
        // x = y — needs $expr, can't be direct match.
        var rule = new ExprToMatchDowngradeRule();
        var input = expr(AstComparisonExpressionOperator.EQ, field("x"), field("y"));
        assertThat(rule.tryMatch(input)).isNull();
    }

    @Test
    void doesNotDowngradeWhenBothSidesAreLiterals() {
        var rule = new ExprToMatchDowngradeRule();
        var input = expr(AstComparisonExpressionOperator.EQ, lit(1), lit(2));
        assertThat(rule.tryMatch(input)).isNull();
    }

    @Test
    void doesNotDowngradeCompositeOperand() {
        // (x+1) > 5 — the left operand isn't a plain field path.
        var rule = new ExprToMatchDowngradeRule();
        var xPlus1 = new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.ADD, field("x"), lit(1));
        var input =
                new AstExprFilter(new AstBinaryOperatorExpression(AstComparisonExpressionOperator.GT, xPlus1, lit(5)));
        assertThat(rule.tryMatch(input)).isNull();
    }

    @Test
    void doesNotDowngradeArithmeticOperator() {
        // {$expr: {$add: [x, 1]}} — not a comparison; can't be a match filter regardless.
        var rule = new ExprToMatchDowngradeRule();
        var input = new AstExprFilter(
                new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.ADD, field("x"), lit(1)));
        assertThat(rule.tryMatch(input)).isNull();
    }

    @Test
    void doesNotFireOnNonExprFilter() {
        var rule = new ExprToMatchDowngradeRule();
        var input = new AstFieldOperationFilter(
                "x",
                new AstComparisonFilterOperation(AstComparisonFilterOperator.EQ, new AstLiteral(new BsonInt32(1))));
        assertThat(rule.tryMatch(input)).isNull();
    }

    @Test
    void integratesAsPostRuleWithGroupBySubstitution() {
        // Simulates the HAVING flow with expression-key GROUP BY:
        //   raw: $expr: {$gt: [ {$add: [x, 1]}, 5 ]}   -- built before rewrite
        //   after pre-rule (GroupBy VN match on x+1 → k0):  $expr: {$gt: [ _id.k0, 5 ]}
        //   after post-rule (this downgrade):               { _id.k0: {$gt: 5} }
        var vn = new VNRegistry();
        var groupKeyVN = new HashMap<Integer, String>();
        var xPlus1 = new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.ADD, field("x"), lit(1));
        groupKeyVN.put(vn.valueNumber(xPlus1), "k0");

        var rewriter = new AstRewriter(
                List.of(new GroupBySubstitutionRule(groupKeyVN, vn)), List.of(new ExprToMatchDowngradeRule()));

        var input =
                new AstExprFilter(new AstBinaryOperatorExpression(AstComparisonExpressionOperator.GT, xPlus1, lit(5)));
        var output = rewriter.rewrite(input);

        assertThat(output)
                .isEqualTo(new AstFieldOperationFilter(
                        "_id.k0",
                        new AstComparisonFilterOperation(
                                AstComparisonFilterOperator.GT, new AstLiteral(new BsonInt32(5)))));
    }
}
