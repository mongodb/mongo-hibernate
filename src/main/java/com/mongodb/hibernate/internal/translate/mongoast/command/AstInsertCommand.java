/*
 * Copyright 2024-present MongoDB, Inc.
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

import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import java.util.Collection;
import org.bson.BsonWriter;

/** See <a href="https://www.mongodb.com/docs/manual/reference/command/insert/">{@code insert}</a>. */
public record AstInsertCommand(String collection, Collection<? extends AstDocument> documents) implements AstCommand {

    public AstInsertCommand {
        assertFalse(documents.isEmpty());
    }

    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeString("insert", collection);
            writer.writeName("documents");
            writer.writeStartArray();
            {
                documents.forEach(document -> document.render(writer));
            }
            writer.writeEndArray();
        }
        writer.writeEndDocument();
    }
}
