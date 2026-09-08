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
 * A single-operand type-conversion operator in aggregation-expression position, used where the BSON type MongoDB would
 * produce differs from the one Hibernate infers for the result and reads the column back as: truncating a division
 * quotient to an integral result type, or pinning an accumulator's result to the width of the aggregate function's
 * return type.
 *
 * @see AstUnaryOperatorExpression
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public enum AstConversionExpressionOperator {
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/toInt/">{@code $toInt}</a>. */
    TO_INT("$toInt"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/toLong/">{@code $toLong}</a>. */
    TO_LONG("$toLong"),
    /**
     * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/toDouble/">{@code $toDouble}</a>.
     */
    TO_DOUBLE("$toDouble"),
    /**
     * See <a
     * href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/toDecimal/">{@code $toDecimal}</a>.
     */
    TO_DECIMAL("$toDecimal");

    AstConversionExpressionOperator(String operatorName) {
        this.operatorName = operatorName;
    }

    String getOperatorName() {
        return operatorName;
    }

    private final String operatorName;
}
