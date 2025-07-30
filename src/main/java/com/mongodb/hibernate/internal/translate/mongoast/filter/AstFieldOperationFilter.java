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

import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.NE;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator.AND;

import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import java.util.List;
import org.bson.BsonNull;
import org.bson.BsonWriter;

public record AstFieldOperationFilter(String fieldPath, AstFilterOperation filterOperation) implements AstFilter {

    private static final AstComparisonFilterOperation NULL_EXCLUSION_FILTER_OPERATION =
            new AstComparisonFilterOperation(NE, new AstLiteralValue(BsonNull.VALUE));

    @Override
    public void render(BsonWriter writer) {
        writer.writeStartDocument();
        {
            writer.writeName(fieldPath);
            filterOperation.render(writer);
        }
        writer.writeEndDocument();
    }

    public static AstFilter toNullExclusionFilter(String fieldPath, AstFilterOperation filterOperation) {
        return new AstLogicalFilter(
                AND,
                List.of(
                        new AstFieldOperationFilter(fieldPath, filterOperation),
                        new AstFieldOperationFilter(fieldPath, NULL_EXCLUSION_FILTER_OPERATION)));
    }
}
