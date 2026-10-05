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
import java.util.Collection;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/facet/">{@code $facet}</a>.
 *
 * <p>Used for its one guarantee that no other stage offers: it emits exactly one document whatever its input, even
 * none. That is what lets an aggregate over the whole collection return a row for an empty collection, where the
 * {@code $group} it wraps would emit nothing at all.
 *
 * @param field the output field holding the sub-pipeline's results as an array
 * @param pipeline the sub-pipeline
 * @hidden
 */
public record AstFacetStage(String field, Collection<? extends AstStage> pipeline) implements AstStage {
    @Override
    public AstStage mapChildren(AstNodeRewriter rewriter) {
        return new AstFacetStage(field, pipeline.stream().map(rewriter::rewrite).toList());
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeName("$facet");
            writer.writeStartDocument();
            {
                writer.writeName(field);
                writer.writeStartArray();
                {
                    pipeline.forEach(stage -> stage.render(writer, binderConsumer));
                }
                writer.writeEndArray();
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
