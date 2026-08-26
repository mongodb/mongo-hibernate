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

import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/** @hidden */
@SuppressWarnings("MissingSummary")
public record AstRegexMatchExpression(AstExpression input, String regex, String options) implements AstExpression {
    @Override
    public StructuralKey structuralKey() {
        return new StructuralKey("RegexMatch", List.of(input, regex, options));
    }

    @Override
    public AstExpression mapChildren(AstNodeRewriter rewriter) {
        return new AstRegexMatchExpression(rewriter.rewrite(input), regex, options);
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        writer.writeName("$regexMatch");
        writer.writeStartDocument();
        writer.writeName("input");
        input.render(writer, binderConsumer);
        writer.writeString("regex", regex);
        writer.writeString("options", options);
        writer.writeEndDocument();
        writer.writeEndDocument();
    }
}
