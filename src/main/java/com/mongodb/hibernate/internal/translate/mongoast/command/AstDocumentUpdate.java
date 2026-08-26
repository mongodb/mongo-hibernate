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

import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * A document-form update payload, carrying {@code $set} and/or {@code $setOnInsert}, each rendered only when non-empty.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstDocumentUpdate(List<AstFieldUpdate> set, List<AstFieldUpdate> setOnInsert) implements AstUpdate {

    public AstDocumentUpdate {
        assertFalse(set.isEmpty() && setOnInsert.isEmpty());
    }

    public AstDocumentUpdate(List<AstFieldUpdate> set) {
        this(set, List.of());
    }

    @Override
    public AstUpdate mapChildren(AstNodeRewriter rewriter) {
        return new AstDocumentUpdate(
                set.stream().map(rewriter::rewrite).toList(),
                setOnInsert.stream().map(rewriter::rewrite).toList());
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            renderOperator(writer, "$set", set, binderConsumer);
            renderOperator(writer, "$setOnInsert", setOnInsert, binderConsumer);
        }
        writer.writeEndDocument();
    }

    private static void renderOperator(
            BsonWriter writer,
            String operator,
            List<AstFieldUpdate> updates,
            Consumer<JdbcParameterBinder> binderConsumer) {
        if (updates.isEmpty()) {
            return;
        }
        writer.writeName(operator);
        writer.writeStartDocument();
        {
            updates.forEach(update -> update.render(writer, binderConsumer));
        }
        writer.writeEndDocument();
    }
}
