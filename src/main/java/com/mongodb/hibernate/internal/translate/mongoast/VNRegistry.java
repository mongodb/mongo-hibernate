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

package com.mongodb.hibernate.internal.translate.mongoast;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Assigns a canonical value number (VN) to each distinct structural expression key. Two calls to {@link #intern(String,
 * Object...)} with equal tags and equal field lists return the same integer.
 *
 * <p>Used to give {@link AstExpression} instances a structure-based identity in O(1) after child VNs are known,
 * enabling GROUP BY key matching without repeated subtree comparisons.
 *
 * <p>Matching is structural, not algebraic: {@code a + 1} and {@code 1 + a} get distinct value numbers. This is
 * structural value numbering (Alpern, Wegman, and Zadeck, "Detecting equality of variables in programs", POPL '88), in
 * tree form rather than over SSA. The interning table is a hash-cons (Ershov, "On programming of arithmetic
 * operations", CACM 1(8), 1958; Filliatre and Conchon, "Type-safe modular hash-consing", ML Workshop 2006), except that
 * it stores the canonical integer rather than the canonical object.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public final class VNRegistry {

    private record Key(String tag, List<Object> fields) {}

    private final Map<Key, Integer> table = new HashMap<>();
    private final IdentityHashMap<AstExpression, Integer> nodeCache = new IdentityHashMap<>();
    private int next;

    public int intern(String tag, Object... fields) {
        return table.computeIfAbsent(new Key(tag, List.of(fields)), k -> next++);
    }

    /**
     * Returns the value number for {@code node}, computing it via {@code compute} on first call and caching it against
     * the node's identity so subsequent calls on the same instance return in O(1) without re-walking children.
     *
     * <p>The value number is a synthesized attribute in the attribute-grammar sense (Knuth, "Semantics of context-free
     * languages", Mathematical Systems Theory 2(2), 1968): a node's attribute is a function of the same attribute at
     * its children. Keying the cache on identity rather than structure is deliberate — structurally equal nodes already
     * converge through {@link #intern}, and identity keying additionally avoids re-walking a subtree reached from
     * several enclosing rewrites.
     */
    public int memoize(AstExpression node, ToIntFunction<VNRegistry> compute) {
        Integer cached = nodeCache.get(node);
        if (cached != null) {
            return cached;
        }
        int result = compute.applyAsInt(this);
        nodeCache.put(node, result);
        return result;
    }
}
