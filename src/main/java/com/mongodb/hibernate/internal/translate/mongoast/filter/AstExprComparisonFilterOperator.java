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

package com.mongodb.hibernate.internal.translate.mongoast.filter;

/**
 * The aggregation-expression comparison operators used inside {@code $expr}. Unlike the query-language operators in
 * {@link AstComparisonFilterOperator}, these take a two-element array of operand expressions.
 *
 * @see AstExprFilter
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public enum AstExprComparisonFilterOperator {
    GT("$gt"),
    GTE("$gte"),
    LT("$lt"),
    LTE("$lte"),
    NE("$ne");

    AstExprComparisonFilterOperator(String operatorName) {
        this.operatorName = operatorName;
    }

    String getOperatorName() {
        return operatorName;
    }

    private final String operatorName;
}
