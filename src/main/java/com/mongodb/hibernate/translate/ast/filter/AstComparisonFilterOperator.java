/*
 * Copyright 2024-present MongoDB, Inc.
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

package com.mongodb.hibernate.translate.ast.filter;

/**
 * Enum type denoting the various comparison operators.
 *
 * <p>Usually used together with some {@link AstComparisonFilterOperation}.
 *
 * @see AstComparisonFilterOperation
 */
public enum AstComparisonFilterOperator {
    EQ("$eq"),
    GT("$gt"),
    GTE("$gte"),
    LT("$lt"),
    LTE("$lte"),
    NE("$ne");

    AstComparisonFilterOperator(String operatorName) {
        this.operatorName = operatorName;
    }

    public String operatorName() {
        return operatorName;
    }

    private final String operatorName;
}
