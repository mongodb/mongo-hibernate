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
public record AstLogicalOperatorExpression(AstLogicalOperator operator, List<? extends AstExpression> operands)
        implements AstExpression {
    @Override
    public StructuralKey structuralKey() {
        return new StructuralKey("LogicalOperator", List.of(operator, operands));
    }

    @Override
    public AstExpression mapChildren(AstNodeRewriter rewriter) {
        return new AstLogicalOperatorExpression(
                operator, operands.stream().map(rewriter::rewrite).toList());
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        writer.writeName(operator.getOperatorName());
        writer.writeStartArray();
        operands.forEach(operand -> operand.render(writer, binderConsumer));
        writer.writeEndArray();
        writer.writeEndDocument();
    }
}
