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

import com.mongodb.hibernate.internal.translate.mongoast.AstComputedFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import com.mongodb.hibernate.internal.translate.mongoast.AstSwitchCase;
import com.mongodb.hibernate.internal.translate.mongoast.AstValue;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLetVariable;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortField;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstStage;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterOperation;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Single walker that traverses any {@link AstNode} tree — expressions, filters, and their interleaving — applying pre-
 * and post-rules per node type. Pre-rules fire top-down and short-circuit descent on match; post-rules fire bottom-up
 * after children are rewritten and chain (each sees the previous rule's output).
 *
 * <p>Rules are added per-type so a single walker can carry heterogeneous rewrites (e.g. GROUP BY expression
 * substitution and {@code $expr}-to-match filter downgrade) and apply them in one traversal.
 */
public final class AstRewriter implements AstNodeRewriter {

    private final List<RewriteRule> preRules;
    private final List<RewriteRule> postRules;

    public AstRewriter(List<RewriteRule> preRules, List<RewriteRule> postRules) {
        this.preRules = List.copyOf(preRules);
        this.postRules = List.copyOf(postRules);
    }

    @Override
    public AstExpression rewrite(AstExpression node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstFilter rewrite(AstFilter node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstFilterOperation rewrite(AstFilterOperation node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstStage rewrite(AstStage node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstSortField rewrite(AstSortField node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstProjectStageSpecification rewrite(AstProjectStageSpecification node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstGroupStageSpecification rewrite(AstGroupStageSpecification node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstLetVariable rewrite(AstLetVariable node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstSwitchCase rewrite(AstSwitchCase node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstValue rewrite(AstValue node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstElement rewrite(AstElement node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstDocument rewrite(AstDocument node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstFieldUpdate rewrite(AstFieldUpdate node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstComputedFieldUpdate rewrite(AstComputedFieldUpdate node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstUpdate rewrite(AstUpdate node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    @Override
    public AstUpdateStatement rewrite(AstUpdateStatement node) {
        return drive(node, rule -> rule::tryMatch, child -> child.mapChildren(this));
    }

    /**
     * Runs the rule pipeline over one node: pre-rules top-down with the first match short-circuiting descent, then
     * {@code descend}, then post-rules bottom-up, chaining.
     *
     * <p>{@code select} picks the {@link RewriteRule} overload for {@code N}; a rule that does not handle {@code N}
     * inherits the default returning {@code null}. Keeping the rule methods per hierarchy is what lets this return
     * {@code N} rather than {@code AstNode}, so no caller has to cast the result back.
     */
    private <N extends AstNode> N drive(
            N node, Function<RewriteRule, Function<N, @Nullable N>> select, UnaryOperator<N> descend) {
        for (RewriteRule rule : preRules) {
            N hit = select.apply(rule).apply(node);
            if (hit != null) {
                return hit;
            }
        }
        N rebuilt = descend.apply(node);
        for (RewriteRule rule : postRules) {
            N hit = select.apply(rule).apply(rebuilt);
            if (hit != null) {
                rebuilt = hit;
            }
        }
        return rebuilt;
    }
}
