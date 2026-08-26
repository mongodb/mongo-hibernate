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

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Assigns a canonical value number (VN) to each distinct expression structure, so that two structurally equal
 * expressions compare in O(1) once their children are numbered, and GROUP BY key matching needs no repeated subtree
 * comparison.
 *
 * <p>An expression describes itself as a {@link StructuralKey}; numbering and caching those descriptions is this
 * class's business.
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

    private final Map<StructuralKey, Integer> table = new HashMap<>();
    private final IdentityHashMap<AstExpression, Integer> nodeCache = new IdentityHashMap<>();
    private int next;

    /**
     * Returns the value number of {@code node}, the same integer for every expression of the same structure.
     *
     * <p>The number is cached against the node's identity, so asking twice costs nothing and a subtree reached from
     * several enclosing rewrites is walked once. Keying that cache on identity rather than structure is deliberate:
     * structurally equal nodes already converge through the interning table, and identity keying additionally avoids
     * re-walking. The number itself is a synthesized attribute in the attribute-grammar sense (Knuth, "Semantics of
     * context-free languages", Mathematical Systems Theory 2(2), 1968) -- a node's attribute is a function of the same
     * attribute at its children.
     */
    public int valueNumber(AstExpression node) {
        Integer cached = nodeCache.get(node);
        if (cached != null) {
            return cached;
        }
        int result = table.computeIfAbsent(numbered(node.structuralKey()), key -> next++);
        nodeCache.put(node, result);
        return result;
    }

    /**
     * Replaces each child expression in {@code key} with its value number, which is what keeps comparing two keys
     * proportional to the number of components rather than to the size of the subtrees beneath them.
     */
    private StructuralKey numbered(StructuralKey key) {
        return new StructuralKey(
                key.tag(), key.fields().stream().map(this::numbered).toList());
    }

    /**
     * A component with each expression beneath it replaced by its value number.
     *
     * <p>Anything left as it is has to compare by value and hold no expression of its own. That is true of the data an
     * expression carries, and of an {@link AstValue}, whose own components are values in turn. It is not true of a node
     * such as {@code AstSwitchCase} that holds expressions without being one, and passing such a node through would
     * silently restore the subtree comparison this exists to avoid, so it fails instead.
     */
    private Object numbered(Object field) {
        if (field instanceof AstExpression child) {
            return valueNumber(child);
        }
        if (field instanceof Collection<?> elements) {
            return elements.stream().map(this::numbered).toList();
        }
        if (field instanceof Map<?, ?> entries) {
            // A map rather than a list of pairs, so that iteration order cannot reach the key.
            var substituted = new HashMap<>();
            entries.forEach((entryKey, value) -> substituted.put(numbered(entryKey), numbered(value)));
            return substituted;
        }
        if (field instanceof AstNode && !(field instanceof AstValue)) {
            throw new AssertionError(
                    "a " + field.getClass().getSimpleName() + " in a structural key is neither numbered nor a value");
        }
        return field;
    }
}
