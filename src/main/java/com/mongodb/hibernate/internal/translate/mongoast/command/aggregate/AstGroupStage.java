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

package com.mongodb.hibernate.internal.translate.mongoast.command.aggregate;

import com.mongodb.hibernate.internal.translate.mongoast.AstValue;
import org.bson.BsonWriter;

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

/**
 * Represents MongoDB's $group aggregation stage.
 *
 * <p>SQL: SELECT country, COUNT(*), AVG(age) FROM Contact GROUP BY country
 *
 * <p>MongoDB:
 *
 * <pre>
 * {
 *   "$group": {
 *     "_id": "$country",
 *     "count": { "$sum": 1 },
 *     "avgAge": { "$avg": "$age" }
 *   }
 * }
 * </pre>
 *
 * <p>Group Key Variants:
 *
 * <ul>
 *   <li>Single field: "_id": "$country"
 *   <li>Multiple fields: "_id": { "country": "$country", "age": "$age" }
 *   <li>Global aggregation: "_id": null (no GROUP BY clause)
 * </ul>
 */
public record AstGroupStage(AstValue groupKey // What to group by (goes in _id field)
        ) implements AstStage {

    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeName("$group");
            writer.writeStartDocument();
            {
                // Group key (_id field)
                writer.writeName("_id");
                groupKey.render(writer);
            }
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
