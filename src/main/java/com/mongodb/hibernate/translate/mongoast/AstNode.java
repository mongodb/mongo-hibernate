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
 * The ultimate ancestor interface representing any node in a Mongo AST tree model.
 *
 * <p>Its central concern is how to render the sub-tree rooted with it using a {@link BsonWriter} instance. <b>Note:
 * </b>Extended JSON format is maintained for type safety reason.
 */
@FunctionalInterface
public interface AstNode {
    /**
     * Renders the AST sub-tree with {@code this} object as the root.
     *
     * @param writer provided {@code BsonWriter} instance; never {@code null}
     */
    void render(BsonWriter writer);
}
