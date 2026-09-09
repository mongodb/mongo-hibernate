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

/**
 * A {@code $group}-stage accumulator operator, i.e. one that reduces the documents of a group to a single value.
 *
 * @see AstAccumulatorExpression
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public enum AstAccumulatorOperator {
    /**
     * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/sum/">{@code $sum}</a>.
     *
     * <p>HQL {@code count()} is also expressed with this operator, as {@code {"$sum": {"$toLong": <0 or 1>}}}, rather
     * than with MQL's own <a
     * href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/count-accumulator/">{@code $count}</a>
     * accumulator, for two reasons. {@code $count} takes no argument, so it cannot express {@code COUNT(x)}, which
     * counts only the documents where {@code x} is not null. And it returns an {@code int}, while HQL types
     * {@code count()} as a {@code Long}, so the result needs a widening cast that cannot be applied here: an
     * accumulator has to be the outermost operator of a {@code $group} specification, and the server rejects
     * {@code {"$toLong": {"$count": {}}}} with {@code unknown group operator '$toLong'}. Casting the argument of
     * {@code $sum} instead keeps one code path for every aggregate function.
     */
    SUM("$sum"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/avg/">{@code $avg}</a>. */
    AVG("$avg"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/min/">{@code $min}</a>. */
    MIN("$min"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/max/">{@code $max}</a>. */
    MAX("$max");

    AstAccumulatorOperator(String operatorName) {
        this.operatorName = operatorName;
    }

    String getOperatorName() {
        return operatorName;
    }

    private final String operatorName;
}
