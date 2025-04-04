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

import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;

import java.util.List;
import org.bson.BsonWriter;

abstract class AbstractAstJunctionFilter implements AstFilter {
    private final String renderedName;
    private final List<? extends AstFilter> subFilters;

    AbstractAstJunctionFilter(String renderedName, List<? extends AstFilter> subFilters) {
        this.renderedName = renderedName;
        this.subFilters = subFilters;
    }

    @Override
    public void render(BsonWriter writer) {
        assertTrue(!subFilters.isEmpty());
        writer.writeStartDocument();
        {
            writer.writeName(renderedName);
            writer.writeStartArray();
            {
                subFilters.forEach(filter -> filter.render(writer));
            }
            writer.writeEndArray();
        }
        writer.writeEndDocument();
    }
}
