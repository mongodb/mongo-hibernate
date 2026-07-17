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

import org.bson.BsonWriter;

/**
 * A reference to a {@code let}-bound variable in aggregation-expression position, rendered as {@code $$name} (e.g. the
 * {@code $$v0} in a {@code $lookup} sub-pipeline's {@code $expr}).
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstVariableExpression(String name) implements AstExpression {
    @Override
    public void render(BsonWriter writer) {
        writer.writeString("$$" + name);
    }
}
