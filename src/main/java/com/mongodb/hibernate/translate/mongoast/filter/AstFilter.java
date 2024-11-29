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

package com.mongodb.hibernate.translate.mongoast.filter;

import com.mongodb.hibernate.translate.mongoast.AstNode;

/**
 * Represents filter {@link AstNode} type, which restricts command processing for updating, deletion or aggregation.
 *
 * <p>Usually it works by the following combination:
 *
 * <ul>
 *   <li>{@link AstFilterField}: filter target on some field
 *   <li>{@link AstFilterOperation}: further operation on the target
 * </ul>
 */
public interface AstFilter extends AstNode {
    /**
     * Whether the filtering is based on equality of the _id field.
     *
     * <p>Useful for determining whether it's safe to do an updateOne/deleteOne instead of updateMany/deleteMany. The
     * former is preferred if possible because the server can automatically retry updateOne/deleteOne but not
     * updateMany/deleteMany.
     *
     * @return {@code true} if the filtering is based on equality of the _id field; otherwise returns {@code false}
     */
    default boolean isIdEqualityFilter() {
        return false;
    }
}
