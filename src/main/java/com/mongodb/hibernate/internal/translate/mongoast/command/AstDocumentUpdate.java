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

import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import java.util.List;
import org.bson.BsonWriter;

/**
 * A document-form update payload, rendered as {@code { "$set": { … } }}.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstDocumentUpdate(List<AstFieldUpdate> updates) implements AstUpdate {
    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeName("$set");
            writer.writeStartDocument();
            {
                updates.forEach(update -> update.render(writer));
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
