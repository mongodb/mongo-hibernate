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

import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * See <a
 * href="https://www.mongodb.com/docs/manual/reference/operator/aggregation/replaceWith/">{@code $replaceWith}</a>.
 *
 * @hidden
 */
public record AstReplaceWithStage(AstExpression expression) implements AstStage {
    @Override
    public AstStage mapChildren(AstNodeRewriter rewriter) {
        return new AstReplaceWithStage(rewriter.rewrite(expression));
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeName("$replaceWith");
            expression.render(writer, binderConsumer);
        }
        writer.writeEndDocument();
    }
}
