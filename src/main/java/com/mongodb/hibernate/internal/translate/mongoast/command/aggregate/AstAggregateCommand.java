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

import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.stage.AstStage;
import java.util.List;
import org.bson.BsonWriter;

public record AstAggregateCommand(String collection, List<AstStage> stages) implements AstCommand {

    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeString("aggregate", collection);
            writer.writeName("pipeline");
            writer.writeStartArray();
            {
                stages.forEach(stage -> stage.render(writer));
            }
            writer.writeEndArray();
        }
        writer.writeEndDocument();
    }
}
