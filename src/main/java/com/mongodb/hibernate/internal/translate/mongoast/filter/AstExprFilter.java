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

import com.mongodb.hibernate.internal.translate.mongoast.AstValue;
import org.bson.BsonWriter;

/**
 * A filter that compares two aggregation expressions with {@code $expr}, e.g. {@code {"$expr": {"$gt": ["$$v0",
 * "$total"]}}}. Used to express non-equijoin {@code ON} conditions inside the {@code $lookup} pipeline form.
 *
 * <p>See <a href="https://www.mongodb.com/docs/manual/reference/operator/query/expr/">{@code $expr}</a>.
 *
 * @hidden
 */
public record AstExprFilter(AstExprComparisonFilterOperator operator, AstValue lhs, AstValue rhs) implements AstFilter {
    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeName("$expr");
            writer.writeStartDocument();
            {
                writer.writeName(operator.getOperatorName());
                writer.writeStartArray();
                {
                    lhs.render(writer);
                    rhs.render(writer);
                }
                writer.writeEndArray();
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
