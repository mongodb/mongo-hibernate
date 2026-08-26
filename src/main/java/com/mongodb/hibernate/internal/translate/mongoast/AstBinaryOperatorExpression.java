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

/** @hidden */
@SuppressWarnings("MissingSummary")
public record AstBinaryOperatorExpression(String operator, AstExpression left, AstExpression right)
        implements AstExpression {

    public AstBinaryOperatorExpression(
            AstComparisonExpressionOperator operator, AstExpression left, AstExpression right) {
        this(operator.getOperatorName(), left, right);
    }

    public AstBinaryOperatorExpression(
            AstArithmeticExpressionOperator operator, AstExpression left, AstExpression right) {
        this(operator.getOperatorName(), left, right);
    }

    @Override
    public StructuralKey structuralKey() {
        return new StructuralKey("BinaryOperator", List.of(operator, left, right));
    }

    @Override
    public AstExpression mapChildren(AstNodeRewriter rewriter) {
        return new AstBinaryOperatorExpression(operator, rewriter.rewrite(left), rewriter.rewrite(right));
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        writer.writeName(operator);
        writer.writeStartArray();
        left.render(writer, binderConsumer);
        right.render(writer, binderConsumer);
        writer.writeEndArray();
        writer.writeEndDocument();
    }
}
