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

package com.mongodb.hibernate.internal.translate.rewrite;

import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A single tree-rewriting rule. Given a candidate node, either produces a rewritten replacement or signals no-match.
 *
 * <p>Pre-rules are applied top-down before descending into children; the first matching pre-rule wins and the walker
 * does not descend further into the returned node.
 *
 * <p>Post-rules are applied bottom-up after children have been rewritten; post-rules chain, each seeing the output of
 * the previous rule.
 */
@FunctionalInterface
public interface RewriteRule<T extends AstNode> {

    /** Return the rewritten node, or {@code null} when the rule does not apply to this input. */
    @Nullable T tryMatch(T node);

    default Optional<T> match(T node) {
        return Optional.ofNullable(tryMatch(node));
    }
}
