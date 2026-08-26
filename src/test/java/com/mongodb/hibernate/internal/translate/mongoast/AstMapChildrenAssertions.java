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

import static com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement.Kind.UPSERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mongodb.hibernate.internal.translate.mongoast.command.AstPipelineUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLetVariable;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageIncludeSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSkipStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortField;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstStage;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstAllFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterOperation;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.bson.BsonInt32;
import org.jspecify.annotations.NullMarked;

/**
 * Asserts that {@link AstNode#mapChildren} visits every child a node holds.
 *
 * <p>The compiler forces a node to implement {@code mapChildren}, but {@code return this} always compiles, so an
 * implementation that drops a child — or ignores what the rewriter hands back — is indistinguishable from a leaf
 * without this check.
 */
public final class AstMapChildrenAssertions {

    private AstMapChildrenAssertions() {}

    /**
     * Asserts that {@code node} hands each of its child nodes to the rewriter and rebuilds itself from the results, or
     * returns itself unchanged when it has no child nodes.
     */
    public static void assertMapsChildren(AstNode node) {
        List<AstNode> children = childrenOf(node);
        var rewriter = new RecordingRewriter();
        AstNode result = node.mapChildren(rewriter);

        assertEquals(
                sorted(children), sorted(rewriter.visited), "mapChildren must hand every child node to the rewriter");
        if (children.isEmpty()) {
            assertSame(node, result, "a node with no child nodes must return itself from mapChildren");
        } else {
            assertEquals(
                    sorted(rewriter.produced),
                    sorted(childrenOf(result)),
                    "mapChildren must rebuild the node from what the rewriter returned");
        }
    }

    /** Ordered by rendering, so a node is free to visit its children in whatever order suits it. */
    private static List<String> sorted(List<AstNode> nodes) {
        return nodes.stream().map(Object::toString).sorted().toList();
    }

    /** The child {@link AstNode}s held by {@code node}, flattening collections and maps. */
    private static List<AstNode> childrenOf(AstNode node) {
        var children = new ArrayList<AstNode>();
        for (Object value : componentsOf(node)) {
            collect(value, children);
        }
        return children;
    }

    private static void collect(Object value, List<AstNode> into) {
        if (value instanceof Enum<?>) {
            // An operator or sort order is a value, not a rewritable subtree, even where it implements AstNode to
            // render itself.
            return;
        }
        if (value instanceof AstNode child) {
            into.add(child);
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(element -> collect(element, into));
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(element -> collect(element, into));
        }
    }

    /** Instance fields rather than record components, so a node that is not a record is read the same way. */
    private static List<Object> componentsOf(AstNode node) {
        var values = new ArrayList<>();
        for (Field field : node.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                values.add(field.get(node));
            } catch (IllegalAccessException | InaccessibleObjectException e) {
                throw new AssertionError("cannot read " + field + " to find the children of " + node, e);
            }
        }
        return values;
    }

    /**
     * Records what it is handed and returns a distinct replacement of the same hierarchy, so a {@code mapChildren} that
     * discards the rewriter's result is caught as well as one that never calls it.
     */
    @NullMarked
    private static final class RecordingRewriter implements AstNodeRewriter {

        private final List<AstNode> visited = new ArrayList<>();
        private final List<AstNode> produced = new ArrayList<>();
        private int counter;

        private <N extends AstNode> N record(AstNode visitedNode, N replacement) {
            visited.add(visitedNode);
            produced.add(replacement);
            counter++;
            return replacement;
        }

        private String name() {
            return "rewritten" + counter;
        }

        @Override
        public AstExpression rewrite(AstExpression node) {
            return record(node, new AstFieldPathExpression(name()));
        }

        @Override
        public AstFilter rewrite(AstFilter node) {
            return record(node, new AstExprFilter(new AstFieldPathExpression(name())));
        }

        @Override
        public AstFilterOperation rewrite(AstFilterOperation node) {
            return record(node, new AstAllFilterOperation(new AstArray(List.of())));
        }

        @Override
        public AstStage rewrite(AstStage node) {
            return record(node, new AstSkipStage(new AstLiteral(new BsonInt32(counter))));
        }

        @Override
        public AstSortField rewrite(AstSortField node) {
            return record(node, new AstSortField(name(), AstSortOrder.ASC));
        }

        @Override
        public AstProjectStageSpecification rewrite(AstProjectStageSpecification node) {
            return record(node, new AstProjectStageIncludeSpecification(name()));
        }

        @Override
        public AstGroupStageSpecification rewrite(AstGroupStageSpecification node) {
            return record(node, new AstGroupStageSpecification(name(), new AstFieldPathExpression(name())));
        }

        @Override
        public AstLetVariable rewrite(AstLetVariable node) {
            return record(node, new AstLetVariable(name(), new AstFieldPathExpression(name())));
        }

        @Override
        public AstSwitchCase rewrite(AstSwitchCase node) {
            return record(
                    node, new AstSwitchCase(new AstFieldPathExpression(name()), new AstFieldPathExpression(name())));
        }

        @Override
        public AstValue rewrite(AstValue node) {
            // An array rather than a literal, because AstAllFilterOperation accepts only an array or parameter marker.
            return record(node, new AstArray(List.of(new AstLiteral(new BsonInt32(counter)))));
        }

        @Override
        public AstElement rewrite(AstElement node) {
            return record(node, new AstElement(name(), new AstLiteral(new BsonInt32(counter))));
        }

        @Override
        public AstDocument rewrite(AstDocument node) {
            return record(node, new AstDocument(List.of(new AstElement(name(), new AstLiteral(new BsonInt32(0))))));
        }

        @Override
        public AstFieldUpdate rewrite(AstFieldUpdate node) {
            return record(node, new AstFieldUpdate(name(), new AstLiteral(new BsonInt32(counter))));
        }

        @Override
        public AstComputedFieldUpdate rewrite(AstComputedFieldUpdate node) {
            return record(node, new AstComputedFieldUpdate(name(), new AstFieldPathExpression(name())));
        }

        @Override
        public AstUpdate rewrite(AstUpdate node) {
            return record(node, new AstPipelineUpdate(List.of()));
        }

        @Override
        public AstUpdateStatement rewrite(AstUpdateStatement node) {
            return record(
                    node,
                    new AstUpdateStatement(
                            new AstExprFilter(new AstFieldPathExpression(name())),
                            new AstPipelineUpdate(List.of()),
                            UPSERT));
        }
    }
}
