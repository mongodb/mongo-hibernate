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

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;

import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * Represents MongoDB's {@code $group} aggregation stage.
 *
 * <p>HQL: SELECT country, SUM(sales) FROM Contact GROUP BY country
 *
 * <p>MongoDB:
 *
 * <pre>
 * {
 *   "$group": {
 *     "_id": {
 *       "country": "$country"
 *     },
 *     "#acc_0": {"$sum": "$sales"}
 *   }
 * }
 * </pre>
 *
 * @param specifications the {@code _id} sub-key specifications, one per GROUP BY key; must be non-empty
 * @param accumulatorSpecifications the accumulator specifications, rendered as siblings of {@code _id}; may be empty
 * @hidden
 */
public record AstGroupStage(
        Collection<? extends AstGroupStageSpecification> specifications,
        Collection<? extends AstGroupStageSpecification> accumulatorSpecifications)
        implements AstStage {

    public AstGroupStage {
        assertFalse(specifications.isEmpty());
    }

    public AstGroupStage(Collection<? extends AstGroupStageSpecification> specifications) {
        this(specifications, List.of());
    }

    @Override
    public AstStage mapChildren(AstNodeRewriter rewriter) {
        return new AstGroupStage(
                specifications.stream().map(rewriter::rewrite).toList(),
                accumulatorSpecifications.stream().map(rewriter::rewrite).toList());
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeName("$group");
            writer.writeStartDocument();
            {
                writer.writeName("_id");
                writer.writeStartDocument();
                {
                    specifications.forEach(specification -> specification.render(writer, binderConsumer));
                }
                writer.writeEndDocument();
                accumulatorSpecifications.forEach(specification -> specification.render(writer, binderConsumer));
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
