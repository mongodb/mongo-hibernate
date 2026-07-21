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

import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import org.bson.BsonWriter;

/**
 * A single {@code name → expression} binding in the {@code let} of a {@code $lookup} pipeline stage. Unlike an
 * {@link com.mongodb.hibernate.internal.translate.mongoast.AstElement} (a {@code name → value} pair for literal
 * documents), a {@code let} variable binds an aggregation {@link AstExpression} evaluated against the outer document.
 *
 * @see AstLookupStageWithPipeline
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstLetVariable(String name, AstExpression expression) implements AstNode {
    @Override
    public void render(BsonWriter writer) {
        writer.writeName(name);
        expression.render(writer);
    }
}
