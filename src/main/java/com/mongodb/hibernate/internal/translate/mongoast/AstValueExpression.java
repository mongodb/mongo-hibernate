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
 * A {@link AstValue} (a literal or parameter) used in aggregation-expression position. A value and an expression do not
 * always render the same: in expression position a string beginning with {@code $} is a field path, and a
 * document/array is an operator invocation, so such a value must be wrapped in {@code $literal} to be taken verbatim.
 * {@code literalWrapped} says whether that wrapping is needed.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstValueExpression(AstValue value, boolean literalWrapped) implements AstExpression {
    @Override
    public void render(BsonWriter writer) {
        if (literalWrapped) {
            writer.writeStartDocument();
            writer.writeName("$literal");
            value.render(writer);
            writer.writeEndDocument();
        } else {
            value.render(writer);
        }
    }
}
