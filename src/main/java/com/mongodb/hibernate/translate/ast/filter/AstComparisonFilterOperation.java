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

package com.mongodb.hibernate.translate.ast.filter;

import com.mongodb.hibernate.translate.ast.AstValue;
import org.bson.BsonWriter;

/**
 * Represents a type of {@link AstFilterOperation} which is based on comparison with some {@link AstValue}.
 *
 * @param operator some comparison operator; never null
 * @param value some {@link AstValue}; never null
 */
public record AstComparisonFilterOperation(AstComparisonFilterOperator operator, AstValue value)
        implements AstFilterOperation {
    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        writer.writeName(operator.operatorName());
        value.render(writer);
        writer.writeEndDocument();
    }
}
