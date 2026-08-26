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

package com.mongodb.hibernate.internal.translate.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.VNRegistry;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageFieldPathSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageIncludeSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortField;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstEmptyFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

/**
 * Substitution semantics: which references become {@code _id.<subKey>}, and which are rejected as strays. The walk
 * itself belongs to {@link AstRewriterTests}.
 */
class GroupBySubstitutionRuleTests {

    private final VNRegistry vn = new VNRegistry();
    private final Map<Integer, String> groupKeyVN = new HashMap<>();

    private static AstExpression x() {
        return new AstFieldPathExpression("x");
    }

    private static AstExpression y() {
        return new AstFieldPathExpression("y");
    }

    private static AstExpression lit(int i) {
        return new AstLiteralExpression(new AstLiteral(new BsonInt32(i)));
    }

    private static AstExpression add(AstExpression l, AstExpression r) {
        return new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.ADD, l, r);
    }

    private AstRewriter rewriterWithKeys(Map<AstExpression, String> keys) {
        keys.forEach((expression, subKey) -> groupKeyVN.put(vn.valueNumber(expression), subKey));
        return new AstRewriter(new GroupBySubstitutionRule(groupKeyVN, vn), null);
    }

    @Test
    void columnKeyBecomesIdSubKey() {
        var rewriter = rewriterWithKeys(Map.of(x(), "x"));

        assertThat(rewriter.rewrite(add(x(), lit(1)))).isEqualTo(add(new AstFieldPathExpression("_id.x"), lit(1)));
    }

    @Test
    void expressionKeyMatchedWholeBecomesIdSubKey() {
        var rewriter = rewriterWithKeys(Map.of(add(x(), lit(1)), "k0"));

        assertThat(rewriter.rewrite(add(x(), lit(1)))).isEqualTo(new AstFieldPathExpression("_id.k0"));
    }

    @Test
    void wholeExpressionKeyWinsOverColumnKeyInsideIt() {
        var rewriter = rewriterWithKeys(Map.of(x(), "x", add(x(), lit(1)), "k1"));

        // _id.k1, not {$add: ["$_id.x", 1]}
        assertThat(rewriter.rewrite(add(x(), lit(1)))).isEqualTo(new AstFieldPathExpression("_id.k1"));
    }

    @Test
    void keyNestedInsideAnotherExpressionIsSubstituted() {
        var rewriter = rewriterWithKeys(Map.of(x(), "x"));
        var input = new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.MULTIPLY, add(x(), lit(1)), lit(2));

        assertThat(rewriter.rewrite(input))
                .isEqualTo(new AstBinaryOperatorExpression(
                        AstArithmeticExpressionOperator.MULTIPLY,
                        add(new AstFieldPathExpression("_id.x"), lit(1)),
                        lit(2)));
    }

    @Test
    void filterOverAKeyIsSubstituted() {
        var rewriter = rewriterWithKeys(Map.of(x(), "x"));
        var input = new AstLogicalFilter(
                AstLogicalFilterOperator.AND, List.of(new AstExprFilter(add(x(), lit(1))), AstEmptyFilter.INSTANCE));

        assertThat(rewriter.rewrite(input))
                .isEqualTo(new AstLogicalFilter(
                        AstLogicalFilterOperator.AND,
                        List.of(
                                new AstExprFilter(add(new AstFieldPathExpression("_id.x"), lit(1))),
                                AstEmptyFilter.INSTANCE)));
    }

    @Test
    void sortFieldOverAKeyIsSubstituted() {
        var rewriter = rewriterWithKeys(Map.of(x(), "x"));

        assertThat(rewriter.rewrite(new AstSortField("x", AstSortOrder.ASC)))
                .isEqualTo(new AstSortField("_id.x", AstSortOrder.ASC));
    }

    @Test
    void projectIncludeOfAKeyBecomesAFieldPathSpecification() {
        var rewriter = rewriterWithKeys(Map.of(x(), "x"));

        assertThat(rewriter.rewrite(new AstProjectStageIncludeSpecification("x")))
                .isEqualTo(new AstProjectStageFieldPathSpecification("_id#x", "_id.x"));
    }

    @Test
    void strayColumnThrows() {
        var rewriter = rewriterWithKeys(Map.of(y(), "y"));

        assertThatExceptionOfType(FeatureNotSupportedException.class)
                .isThrownBy(() -> rewriter.rewrite(add(x(), lit(1))))
                .withMessageContaining("column 'x'");
    }

    @Test
    void straySortFieldThrows() {
        var rewriter = rewriterWithKeys(Map.of(y(), "y"));

        assertThatExceptionOfType(FeatureNotSupportedException.class)
                .isThrownBy(() -> rewriter.rewrite(new AstSortField("x", AstSortOrder.ASC)))
                .withMessageContaining("column 'x'");
    }
}
