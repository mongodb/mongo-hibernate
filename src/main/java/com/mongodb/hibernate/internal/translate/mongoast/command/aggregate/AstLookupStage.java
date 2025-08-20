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

import java.util.List;
import org.bson.BsonWriter;
import org.jspecify.annotations.Nullable;

public record AstLookupStage(
        String from, String localField, String foreignField, @Nullable List<? extends AstStage> pipeline, String as)
        implements AstStage {

    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeName("$lookup");
            writer.writeStartDocument();
            {
                writer.writeString("from", from);
                writer.writeString("localField", localField);
                writer.writeString("foreignField", foreignField);
                if (pipeline != null && !pipeline.isEmpty()) {
                    writer.writeName("pipeline");
                    writer.writeStartArray();
                    {
                        for (AstStage stage : pipeline) {
                            stage.render(writer);
                        }
                    }
                    writer.writeEndArray();
                }
                writer.writeString("as", as);
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
