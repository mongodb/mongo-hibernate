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
 * An {@link AstValue} (a literal or parameter) wrapped in {@code $literal} for aggregation-expression position, so it
 * is taken verbatim rather than as a field path or an operator invocation. Use this when the value could otherwise be
 * misread there (a string beginning with {@code $}, or a document/array); a value that cannot be misread uses
 * {@link AstValueExpression} instead.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstLiteralExpression(AstValue value) implements AstExpression {
    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        writer.writeName("$literal");
        value.render(writer);
        writer.writeEndDocument();
    }
}
