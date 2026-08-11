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
public class AstUpdateStatement implements AstNode {
    private final AstFilter filter;
    private final AstUpdate update;
    private final boolean upsert;
    private final boolean multi;

    public static AstUpdateStatement createUpsertStatement(AstFilter filter, AstUpdate update) {
        return new AstUpdateStatement(filter, update, true, false);
    }

    public static AstUpdateStatement createMultiUpdateStatement(AstFilter filter, AstUpdate update) {
        return new AstUpdateStatement(filter, update, false, true);
    }

    private AstUpdateStatement(
            final AstFilter filter, final AstUpdate update, final boolean upsert, final boolean multi) {
        this.filter = filter;
        this.update = update;
        this.upsert = upsert;
        this.multi = multi;
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeName("q");
            filter.render(writer, binderConsumer);
            writer.writeName("u");
            update.render(writer, binderConsumer);
            if (upsert) {
                writer.writeBoolean("upsert", true);
            }
            writer.writeBoolean("multi", multi);
        }
        writer.writeEndDocument();
    }
}
