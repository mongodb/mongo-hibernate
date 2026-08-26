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

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;

import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.Collection;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * See <a href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/project/">{@code project}</a>.
 *
 * @hidden
 */
public record AstProjectStage(Collection<? extends AstProjectStageSpecification> specifications) implements AstStage {

    public AstProjectStage {
        assertFalse(specifications.isEmpty());
    }

    @Override
    public AstStage mapChildren(AstNodeRewriter rewriter) {
        return new AstProjectStage(
                specifications.stream().map(rewriter::rewrite).toList());
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeName("$project");
            writer.writeStartDocument();
            {
                specifications.forEach(specification -> specification.render(writer, binderConsumer));
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
