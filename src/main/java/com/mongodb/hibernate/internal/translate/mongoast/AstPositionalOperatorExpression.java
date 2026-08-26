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

/**
 * A general-purpose Mongo operator that includes the arguments as an array
 *
 * @param operator the name of the operator, including the <code>$</code>
 * @param arguments a list of arguments that will be converted into an array
 */
public record AstPositionalOperatorExpression(String operator, List<AstExpression> arguments) implements AstExpression {

    @Override
    public StructuralKey structuralKey() {
        return new StructuralKey("PositionalOperator", List.of(operator, arguments));
    }

    @Override
    public AstExpression mapChildren(AstNodeRewriter rewriter) {
        return new AstPositionalOperatorExpression(
                operator, arguments.stream().map(rewriter::rewrite).toList());
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeStartArray(operator);
            for (final var argument : arguments) {
                argument.render(writer, binderConsumer);
            }
            writer.writeEndArray();
        }
        writer.writeEndDocument();
    }
}
