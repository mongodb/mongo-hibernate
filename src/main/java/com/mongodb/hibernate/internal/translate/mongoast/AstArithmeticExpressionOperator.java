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
 * An arithmetic operator in aggregation-expression position (the array form {@code {$op: [a, b]}}).
 *
 * @see AstBinaryOperatorExpression
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public enum AstArithmeticExpressionOperator {
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/add/">{@code $add}</a>. */
    ADD("$add"),
    /**
     * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/subtract/">{@code $subtract}</a>.
     */
    SUBTRACT("$subtract"),
    /**
     * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/multiply/">{@code $multiply}</a>.
     */
    MULTIPLY("$multiply"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/divide/">{@code $divide}</a>. */
    DIVIDE("$divide"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/mod/">{@code $mod}</a>. */
    MOD("$mod");

    AstArithmeticExpressionOperator(String operatorName) {
        this.operatorName = operatorName;
    }

    String getOperatorName() {
        return operatorName;
    }

    private final String operatorName;
}
