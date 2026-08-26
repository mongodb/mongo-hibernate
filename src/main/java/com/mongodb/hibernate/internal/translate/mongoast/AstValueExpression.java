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

import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * An {@link AstValue} (a literal or parameter) used verbatim in aggregation-expression position. Use this only when the
 * value cannot be misread there; a value that could be taken as a field path or an operator invocation (a string
 * beginning with {@code $}, or a document/array) must instead go through {@link AstLiteralExpression} so it is wrapped
 * in {@code $literal}. That leaves operand position, an operator's argument. A {@code $project} field value is misread
 * whatever the value is, a number or boolean being an inclusion/exclusion flag there, so it always uses
 * {@link AstLiteralExpression}.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstValueExpression(AstValue value) implements AstExpression {
    @Override
    public StructuralKey structuralKey() {
        return new StructuralKey("Value", List.of(value));
    }

    @Override
    public AstExpression mapChildren(AstNodeRewriter rewriter) {
        return new AstValueExpression(rewriter.rewrite(value));
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        value.render(writer, binderConsumer);
    }
}
