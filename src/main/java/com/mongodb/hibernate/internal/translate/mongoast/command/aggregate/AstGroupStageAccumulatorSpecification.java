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

package com.mongodb.hibernate.internal.translate.mongoast.command.aggregate;

import com.mongodb.hibernate.internal.translate.mongoast.AstAccumulatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * One accumulator field of a {@code $group} stage, rendered as a sibling of {@code _id}, e.g. {@code "#acc_0": {"$sum":
 * "$sales"}}.
 *
 * <p>Distinct from {@link AstGroupStageSpecification}, which holds an arbitrary
 * {@link com.mongodb.hibernate.internal.translate.mongoast.AstExpression} for an {@code _id} sub-key, because the two
 * slots of a {@code $group} stage accept opposite things and MongoDB does not reject both mistakes the same way. A
 * non-accumulator among the siblings of {@code _id} fails loudly ({@code Location40234: The field '...' must be an
 * accumulator object}), but an accumulator inside {@code _id} is silently accepted and means something else entirely
 * --- {@code $sum} there is the aggregation expression rather than the accumulator, so the query groups by a different
 * value and returns wrong results with no error. Requiring an {@link AstAccumulatorExpression} here makes both
 * unrepresentable.
 *
 * @param key the {@code $group} output field name
 * @param accumulator the accumulator producing that field's value
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstGroupStageAccumulatorSpecification(String key, AstAccumulatorExpression accumulator)
        implements AstNode {

    /** Returns a copy of this specification with {@code rewriter} applied to its accumulator. */
    @Override
    public AstGroupStageAccumulatorSpecification mapChildren(AstNodeRewriter rewriter) {
        return new AstGroupStageAccumulatorSpecification(key, rewriter.rewrite(accumulator));
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeName(key);
        accumulator.render(writer, binderConsumer);
    }
}
