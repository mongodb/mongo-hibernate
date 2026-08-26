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

package com.mongodb.hibernate.internal.translate.mongoast.command;

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;

import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * See <a href="https://www.mongodb.com/docs/manual/reference/command/update/">{@code update}</a>.
 *
 * @hidden
 */
@SuppressWarnings("InvalidParam")
public record AstUpdateCommand(String collection, List<AstUpdateStatement> updates) implements AstCommand {

    public AstUpdateCommand {
        assertFalse(updates.isEmpty());
    }

    @Override
    public AstUpdateCommand mapChildren(AstNodeRewriter rewriter) {
        return new AstUpdateCommand(
                collection, updates.stream().map(rewriter::rewrite).toList());
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeString("update", collection);
            writer.writeName("updates");
            writer.writeStartArray();
            {
                updates.forEach(statement -> statement.render(writer, binderConsumer));
            }
            writer.writeEndArray();
        }
        writer.writeEndDocument();
    }
}
