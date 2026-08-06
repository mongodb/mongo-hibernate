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

package com.mongodb.hibernate.internal.translate.mongoast;

import java.util.List;
import org.bson.BsonWriter;

/**
 * The MongoDB {@code $switch} aggregation expression, evaluating each branch's {@code case} in order and yielding the
 * matching branch's {@code then}, or {@code defaultExpression} when none match. Both HQL {@code CASE} flavours (simple
 * and searched) translate to this; a missing {@code ELSE} maps to a {@code null} default, matching SQL semantics.
 *
 * @see AstSwitchCase
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstSwitchExpression(List<AstSwitchCase> branches, AstExpression defaultExpression)
        implements AstExpression {
    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        writer.writeName("$switch");
        writer.writeStartDocument();
        writer.writeName("branches");
        writer.writeStartArray();
        branches.forEach(branch -> branch.render(writer));
        writer.writeEndArray();
        writer.writeName("default");
        defaultExpression.render(writer);
        writer.writeEndDocument();
        writer.writeEndDocument();
    }
}
