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

/** @see AstComparisonFilterOperation */
@SuppressWarnings("MissingSummary")
public enum AstComparisonFilterOperator {
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/eq/">{@code $eq}</a>. */
    EQ("$eq"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/gt/">{@code $gt}</a>. */
    GT("$gt"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/gte/">{@code $gte}</a>. */
    GTE("$gte"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/lt/">{@code $lt}</a>. */
    LT("$lt"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/lte/">{@code $lte}</a>. */
    LTE("$lte"),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/ne/">{@code $ne}</a>. */
    NE("$ne");

    AstComparisonFilterOperator(String operatorName) {
        this.operatorName = operatorName;
    }

    String getOperatorName() {
        return operatorName;
    }

    private final String operatorName;
}
