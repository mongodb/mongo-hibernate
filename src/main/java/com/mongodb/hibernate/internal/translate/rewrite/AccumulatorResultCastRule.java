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

import com.mongodb.hibernate.internal.translate.mongoast.AstConversionExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Pre-rule that wraps a reference to a {@code $group} accumulator in the conversion that makes it read back as the type
 * Hibernate ORM inferred for the aggregate function, e.g. {@code "$#acc_0"} becomes {@code {"$toLong": "$#acc_0"}}.
 *
 * <p>Applied to the {@code $project} stage only, because that is the sole position where the BSON type matters:
 * {@code MongoResultSet} reads a column through {@code ValueConversions}, which is strict --- {@code asInt64} on an
 * {@code int32} throws. A {@code $match} for HAVING and a {@code $sort} compare numerically across BSON numeric types,
 * so they need the raw accumulator and would be made worse by a cast: {@code $sort} requires a field path, and a cast
 * would stop {@code $match} being compacted out of {@code $expr} form.
 *
 * <p>The cast cannot live inside {@code $group} instead. MongoDB requires the accumulator to be the outermost operator
 * there, so it could only wrap the accumulator's argument --- and then it never runs when the accumulator has no input
 * to convert: {@code $sum} over a group whose every value is null yields {@code int32 0} without evaluating the
 * argument at all, and reading that as a {@code Long} throws.
 */
public final class AccumulatorResultCastRule implements RewriteRule {

    private final Map<String, AstConversionExpressionOperator> castByAccumulatorField;

    public AccumulatorResultCastRule(Map<String, AstConversionExpressionOperator> castByAccumulatorField) {
        this.castByAccumulatorField = castByAccumulatorField;
    }

    @Override
    public @Nullable AstExpression tryMatch(AstExpression node) {
        if (node instanceof AstFieldPathExpression fieldPath) {
            var cast = castByAccumulatorField.get(fieldPath.fieldPath());
            if (cast != null) {
                // A pre-rule match is not descended into, which is what keeps this from matching its own output.
                return new AstUnaryOperatorExpression(cast, fieldPath);
            }
        }
        return null;
    }
}
