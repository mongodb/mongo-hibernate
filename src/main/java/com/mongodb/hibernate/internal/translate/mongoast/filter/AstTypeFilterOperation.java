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

package com.mongodb.hibernate.internal.translate.mongoast.filter;

import java.util.Collection;
import org.bson.BsonType;
import org.bson.BsonWriter;

/** See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/type/">{@code $type}</a>. */
public record AstTypeFilterOperation(Collection<BsonType> types) implements AstFilterOperation {
    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeName("$type");
            if (types.size() == 1) {
                render(writer, types.iterator().next());
            } else {
                writer.writeStartArray();
                {
                    types.forEach(type -> render(writer, type));
                }
                writer.writeEndArray();
            }
        }
        writer.writeEndDocument();
    }

    private static void render(BsonWriter writer, BsonType type) {
        writer.writeInt32(type.getValue());
    }
}
