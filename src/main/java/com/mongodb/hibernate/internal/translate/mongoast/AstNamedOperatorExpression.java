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
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * A general-purpose Mongo operator that includes arguments as a document
 *
 * @param operator the name of the operator, including the <code>$</code>
 * @param arguments a map of name-argument pairs that will be converted into a document
 */
public record AstNamedOperatorExpression(String operator, SortedMap<String, AstExpression> arguments)
        implements AstExpression {

    @Override
    public StructuralKey structuralKey() {
        return new StructuralKey("NamedOperator", List.of(operator, arguments));
    }

    @Override
    public AstExpression mapChildren(AstNodeRewriter rewriter) {
        var newArguments = new TreeMap<String, AstExpression>();
        arguments.forEach((name, argument) -> newArguments.put(name, rewriter.rewrite(argument)));
        return new AstNamedOperatorExpression(operator, newArguments);
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeStartDocument(operator);
            for (final var argument : arguments.entrySet()) {
                writer.writeName(argument.getKey());
                argument.getValue().render(writer, binderConsumer);
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
