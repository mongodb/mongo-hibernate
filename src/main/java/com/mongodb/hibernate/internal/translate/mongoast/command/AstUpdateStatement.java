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

package com.mongodb.hibernate.internal.translate.mongoast.command;

import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * See the <a href="https://www.mongodb.com/docs/manual/reference/command/update/#update-statements">update
 * statements</a> of the {@code update} command.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstUpdateStatement(AstFilter filter, AstUpdate update, Kind kind) implements AstNode {

    /**
     * How many documents the statement applies to, and whether it inserts when none match. The {@code update} command
     * carries these as separate {@code upsert} and {@code multi} flags, but only one at a time is ever set.
     */
    public enum Kind {
        /** Updates the single matching document, inserting it when there is none. */
        UPSERT,
        /** Updates every matching document, inserting nothing. */
        MULTI
    }

    @Override
    public AstUpdateStatement mapChildren(AstNodeRewriter rewriter) {
        return new AstUpdateStatement(rewriter.rewrite(filter), rewriter.rewrite(update), kind);
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeName("q");
            filter.render(writer, binderConsumer);
            writer.writeName("u");
            update.render(writer, binderConsumer);
            if (kind == Kind.UPSERT) {
                writer.writeBoolean("upsert", true);
            }
            writer.writeBoolean("multi", kind == Kind.MULTI);
        }
        writer.writeEndDocument();
    }
}
