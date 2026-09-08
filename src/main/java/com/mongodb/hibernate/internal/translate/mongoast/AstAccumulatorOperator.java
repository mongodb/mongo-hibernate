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
     * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/sum/">{@code $sum}</a>. Sum will
     * also be used for count aggregate(E.g {"$sum": {"$toLong": 1}}) as MQL doesn't have a build in COUNT aggregate
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
