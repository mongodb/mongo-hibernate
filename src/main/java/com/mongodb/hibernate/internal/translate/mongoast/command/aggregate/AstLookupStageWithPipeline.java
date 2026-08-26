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

package com.mongodb.hibernate.internal.translate.mongoast.command.aggregate;

import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * The pipeline form of <a
 * href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/lookup/">{@code $lookup}</a>, which binds
 * outer-document values into {@code let} variables and evaluates a sub-{@code pipeline} against the joined collection.
 * Used for non-equijoin {@code ON} conditions, which the simple {@code localField}/{@code foreignField} form cannot
 * express.
 *
 * @hidden
 */
public record AstLookupStageWithPipeline(String from, List<AstLetVariable> let, List<AstStage> pipeline, String as)
        implements AstStage {
    @Override
    public AstStage mapChildren(AstNodeRewriter rewriter) {
        return new AstLookupStageWithPipeline(
                from,
                let.stream().map(rewriter::rewrite).toList(),
                pipeline.stream().map(rewriter::rewrite).toList(),
                as);
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeName("$lookup");
            writer.writeStartDocument();
            {
                writer.writeString("from", from);
                writer.writeName("let");
                writer.writeStartDocument();
                {
                    let.forEach(variable -> variable.render(writer, binderConsumer));
                }
                writer.writeEndDocument();
                writer.writeName("pipeline");
                writer.writeStartArray();
                {
                    pipeline.forEach(stage -> stage.render(writer, binderConsumer));
                }
                writer.writeEndArray();
                writer.writeString("as", as);
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
