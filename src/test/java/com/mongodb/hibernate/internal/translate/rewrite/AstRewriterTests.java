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
import java.util.HashMap;
import java.util.List;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstRewriterTests {

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

    @Test
    void leafMatchSubstitutesLeaf() {
        var vn = new VNRegistry();
        var groupKeyVN = new HashMap<Integer, String>();
        groupKeyVN.put(x().valueNumber(vn), "x");

        var rewriter = new AstRewriter(List.of(new GroupBySubstitutionRule(groupKeyVN, vn)), List.of());
        var input = add(x(), lit(1));
        var output = rewriter.rewrite(input);

        assertThat(output).isEqualTo(add(new AstFieldPathExpression("_id.x"), lit(1)));
    }

    @Test
    void wholeMatchSubstitutesRootAndStops() {
        var vn = new VNRegistry();
        var groupKeyVN = new HashMap<Integer, String>();
        groupKeyVN.put(add(x(), lit(1)).valueNumber(vn), "k0");

        var rewriter = new AstRewriter(List.of(new GroupBySubstitutionRule(groupKeyVN, vn)), List.of());
        var input = add(x(), lit(1));
        var output = rewriter.rewrite(input);

        assertThat(output).isEqualTo(new AstFieldPathExpression("_id.k0"));
    }

    @Test
    void parentMatchWinsOverChildMatch() {
        var vn = new VNRegistry();
        var groupKeyVN = new HashMap<Integer, String>();
        // Both x and x+1 are group keys — parent wins
        groupKeyVN.put(x().valueNumber(vn), "x");
        groupKeyVN.put(add(x(), lit(1)).valueNumber(vn), "k1");

        var rewriter = new AstRewriter(List.of(new GroupBySubstitutionRule(groupKeyVN, vn)), List.of());
        var input = add(x(), lit(1));
        var output = rewriter.rewrite(input);

        // Parent match wins → _id.k1, not {$add: ["_id.x", 1]}
        assertThat(output).isEqualTo(new AstFieldPathExpression("_id.k1"));
    }

    @Test
    void noMatchThrows() {
        var vn = new VNRegistry();
        var groupKeyVN = new HashMap<Integer, String>();
        groupKeyVN.put(y().valueNumber(vn), "y");

        var rewriter = new AstRewriter(List.of(new GroupBySubstitutionRule(groupKeyVN, vn)), List.of());
        var input = add(x(), lit(1));

        assertThatExceptionOfType(FeatureNotSupportedException.class).isThrownBy(() -> rewriter.rewrite(input));
    }

    @Test
    void nestedCompositeWithLeafMatch() {
        var vn = new VNRegistry();
        var groupKeyVN = new HashMap<Integer, String>();
        groupKeyVN.put(x().valueNumber(vn), "x");

        var rewriter = new AstRewriter(List.of(new GroupBySubstitutionRule(groupKeyVN, vn)), List.of());
        // (x + 1) * 2
        var input = new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.MULTIPLY, add(x(), lit(1)), lit(2));
        var output = rewriter.rewrite(input);

        var expected = new AstBinaryOperatorExpression(
                AstArithmeticExpressionOperator.MULTIPLY, add(new AstFieldPathExpression("_id.x"), lit(1)), lit(2));
        assertThat(output).isEqualTo(expected);
    }
}
