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

package com.mongodb.hibernate.internal.translate.mongoast;

/**
 * An operator over an array-valued operand in aggregation-expression position, whether that array is a field, a
 * literal, or the output of an accumulator.
 *
 * @see AstUnaryOperatorExpression
 * @see AstBinaryOperatorExpression
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public enum AstArrayExpressionOperator implements AstExpressionOperator {
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/size/">{@code $size}</a>. */
    SIZE("$size"),
    /**
     * See <a
     * href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/setDifference/">{@code $setDifference}</a>.
     */
    SET_DIFFERENCE("$setDifference"),
    /**
     * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/sum/">{@code $sum}</a>, which in
     * expression position sums the elements of an array rather than accumulating over a group.
     */
    SUM("$sum"),
    /**
     * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/avg/">{@code $avg}</a>, which in
     * expression position averages the elements of an array rather than accumulating over a group.
     */
    AVG("$avg");

    AstArrayExpressionOperator(String operatorName) {
        this.operatorName = operatorName;
    }

    @Override
    public String getOperatorName() {
        return operatorName;
    }

    private final String operatorName;
}
