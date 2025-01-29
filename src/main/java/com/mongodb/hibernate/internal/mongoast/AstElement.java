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

package com.mongodb.hibernate.internal.mongoast;

import org.bson.BsonWriter;

/**
 * Represents some Bson field with name and value, which is usually rendered with other {@link AstElement}s to compose a
 * {@link org.bson.BsonDocument}.
 *
 * <p>This class is not part of the public API and may be removed or changed at any time
 *
 * @param name field name; not {@code null}
 * @param value field value; not {@code null}
 */
public record AstElement(String name, AstValue value) implements AstNode {
    @Override
    public void render(BsonWriter writer) {
        writer.writeName(name);
        value.render(writer);
    }
}
