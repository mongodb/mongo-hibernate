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

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;

import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstComparisonExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstValue;
import com.mongodb.hibernate.internal.translate.mongoast.AstValueExpression;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Post-rule that downgrades an {@code $expr} filter to the direct match form when its inner comparison expression has
 * shape {@code {$op: [<fieldPath>, <value>]}} (or reversed). After an upstream substitution (e.g.
 * {@link GroupBySubstitutionRule}) turns a composite operand into a plain field path, the whole {@code $expr} can be
 * expressed as {@code {<fieldPath>: {<op>: <value>}}}, which is the compact and more optimizer-friendly form.
 *
 * <p>Only pure comparison shapes are downgraded — arithmetic, logical, or accumulator operands are left as
 * {@code $expr}.
 */
public final class ExprToMatchDowngradeRule implements RewriteRule {

    private static final Map<String, AstComparisonFilterOperator> NAME_TO_FILTER_OP = Map.of(
            AstComparisonExpressionOperator.EQ.getOperatorName(), AstComparisonFilterOperator.EQ,
            AstComparisonExpressionOperator.NE.getOperatorName(), AstComparisonFilterOperator.NE,
            AstComparisonExpressionOperator.GT.getOperatorName(), AstComparisonFilterOperator.GT,
            AstComparisonExpressionOperator.GTE.getOperatorName(), AstComparisonFilterOperator.GTE,
            AstComparisonExpressionOperator.LT.getOperatorName(), AstComparisonFilterOperator.LT,
            AstComparisonExpressionOperator.LTE.getOperatorName(), AstComparisonFilterOperator.LTE);

    private static final Map<AstComparisonFilterOperator, AstComparisonFilterOperator> FLIPPED = Map.of(
            AstComparisonFilterOperator.EQ, AstComparisonFilterOperator.EQ,
            AstComparisonFilterOperator.NE, AstComparisonFilterOperator.NE,
            AstComparisonFilterOperator.GT, AstComparisonFilterOperator.LT,
            AstComparisonFilterOperator.GTE, AstComparisonFilterOperator.LTE,
            AstComparisonFilterOperator.LT, AstComparisonFilterOperator.GT,
            AstComparisonFilterOperator.LTE, AstComparisonFilterOperator.GTE);

    @Override
    public @Nullable AstFilter tryMatch(AstFilter node) {
        if (!(node instanceof AstExprFilter ef)) {
            return null;
        }
        if (!(ef.expression() instanceof AstBinaryOperatorExpression bin)) {
            return null;
        }
        AstComparisonFilterOperator op = NAME_TO_FILTER_OP.get(bin.operator());
        if (op == null) {
            return null;
        }
        if (bin.left() instanceof AstFieldPathExpression fpLeft) {
            AstValue value = asValue(bin.right());
            if (value != null) {
                return new AstFieldOperationFilter(fpLeft.fieldPath(), new AstComparisonFilterOperation(op, value));
            }
        }
        if (bin.right() instanceof AstFieldPathExpression fpRight) {
            AstValue value = asValue(bin.left());
            if (value != null) {
                // Every operator NAME_TO_FILTER_OP yields has a FLIPPED entry. Without this, one added to the first
                // and not the second would quietly decline to downgrade, emitting $expr where the compact form fits.
                return new AstFieldOperationFilter(
                        fpRight.fieldPath(), new AstComparisonFilterOperation(assertNotNull(FLIPPED.get(op)), value));
            }
        }
        return null;
    }

    private static @Nullable AstValue asValue(AstExpression expr) {
        if (expr instanceof AstLiteralExpression le) {
            return le.value();
        }
        if (expr instanceof AstValueExpression ve) {
            return ve.value();
        }
        return null;
    }
}
