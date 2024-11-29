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

package com.mongodb.hibernate.translate.mongoast;

import org.bson.BsonWriter;

/**
 * Represents an MQL parameter placeholder, whose values will be provided to {@link java.sql.PreparedStatement}'s
 * various setter methods together with the their position indexes.
 *
 * <p>Note that MQL has no SQL parameter placeholder (JDBC uses {@code ?} as placeholder marker) counterpart; currently
 * {@code {"$undefined": true}} is chosen
 */
public record AstPlaceholder() implements AstValue {

    public static AstPlaceholder INSTANCE = new AstPlaceholder();

    @Override
    public void render(BsonWriter writer) {
        writer.writeUndefined();
    }
}
