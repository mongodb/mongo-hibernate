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
 * A MongoDB {@code $group}-stage accumulator applied to a single argument, e.g. {@code {$sum: <expr>}}. Rendered in
 * value position so it can appear as the right-hand side of an
 * {@link com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStageSpecification} in the
 * accumulator slot of an {@link com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStage}.
 *
 * @param operator the accumulator operator name, including the {@code $} (e.g. {@code $sum})
 * @param argument the single expression the accumulator is applied to
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstAccumulatorExpression(String operator, AstExpression argument) implements AstExpression {

    @Override
    public StructuralKey structuralKey() {
        return new StructuralKey("Accumulator", List.of(operator, argument));
    }

    @Override
    public AstExpression mapChildren(AstNodeRewriter rewriter) {
        return new AstAccumulatorExpression(operator, rewriter.rewrite(argument));
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        writer.writeName(operator);
        argument.render(writer, binderConsumer);
        writer.writeEndDocument();
    }
}
