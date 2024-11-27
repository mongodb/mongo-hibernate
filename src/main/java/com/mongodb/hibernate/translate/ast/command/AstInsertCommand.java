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

package com.mongodb.hibernate.translate.ast.command;

import com.mongodb.hibernate.translate.ast.AstElement;
import com.mongodb.hibernate.translate.ast.AstNode;
import com.mongodb.hibernate.translate.ast.AstNodeType;
import java.util.List;
import org.bson.BsonWriter;

public record AstInsertCommand(String collection, List<? extends AstElement> elements) implements AstNode {
    @Override
    public AstNodeType nodeType() {
        return AstNodeType.InsertCommand;
    }

    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        writer.writeString("insert", collection);
        writer.writeName("documents");
        writer.writeStartArray();
        writer.writeStartDocument();
        elements.forEach(element -> element.render(writer));
        writer.writeEndDocument();
        writer.writeEndArray();
        writer.writeEndDocument();
    }
}
