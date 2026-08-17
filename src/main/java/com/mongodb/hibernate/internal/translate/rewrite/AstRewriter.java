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

import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstInExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLogicalOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstRegexMatchExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstMatchStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageExpressionSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Single walker that traverses any {@link AstNode} tree — expressions, filters, and their interleaving — applying pre-
 * and post-rules per node type. Pre-rules fire top-down and short-circuit descent on match; post-rules fire bottom-up
 * after children are rewritten and chain (each sees the previous rule's output).
 *
 * <p>Rules are added per-type so a single walker can carry heterogeneous rewrites (e.g. GROUP BY expression
 * substitution and {@code $expr}-to-match filter downgrade) and apply them in one traversal.
 */
public final class AstRewriter {

    private final List<RewriteRule<AstNode>> preRules;
    private final List<RewriteRule<AstNode>> postRules;

    public AstRewriter(List<RewriteRule<AstNode>> preRules, List<RewriteRule<AstNode>> postRules) {
        this.preRules = List.copyOf(preRules);
        this.postRules = List.copyOf(postRules);
    }

    public AstExpression rewrite(AstExpression node) {
        return (AstExpression) rewriteNode(node);
    }

    public AstFilter rewrite(AstFilter node) {
        return (AstFilter) rewriteNode(node);
    }

    public AstProjectStage rewrite(AstProjectStage node) {
        return (AstProjectStage) rewriteNode(node);
    }

    public AstMatchStage rewrite(AstMatchStage node) {
        return (AstMatchStage) rewriteNode(node);
    }

    private AstNode rewriteNode(AstNode node) {
        for (RewriteRule<AstNode> rule : preRules) {
            AstNode hit = rule.tryMatch(node);
            if (hit != null) {
                return hit;
            }
        }
        AstNode rebuilt = descend(node);
        for (RewriteRule<AstNode> rule : postRules) {
            AstNode hit = rule.tryMatch(rebuilt);
            if (hit != null) {
                rebuilt = hit;
            }
        }
        return rebuilt;
    }

    private AstNode descend(AstNode node) {
        if (node instanceof AstExpression expr) {
            return descendExpression(expr);
        }
        if (node instanceof AstFilter filter) {
            return descendFilter(filter);
        }
        if (node instanceof AstProjectStage ps) {
            List<AstProjectStageSpecification> newSpecs =
                    new ArrayList<>(ps.specifications().size());
            for (AstProjectStageSpecification spec : ps.specifications()) {
                if (spec instanceof AstProjectStageExpressionSpecification exprSpec) {
                    newSpecs.add(
                            new AstProjectStageExpressionSpecification(exprSpec.key(), rewrite(exprSpec.expression())));
                } else {
                    newSpecs.add(spec);
                }
            }
            return new AstProjectStage(newSpecs);
        }
        if (node instanceof AstMatchStage ms) {
            return new AstMatchStage(rewrite(ms.filter()));
        }
        return node;
    }

    private AstExpression descendExpression(AstExpression node) {
        if (node instanceof AstBinaryOperatorExpression b) {
            return new AstBinaryOperatorExpression(b.operator(), rewrite(b.left()), rewrite(b.right()));
        }
        if (node instanceof AstUnaryOperatorExpression u) {
            return new AstUnaryOperatorExpression(u.operator(), rewrite(u.operand()));
        }
        if (node instanceof AstLogicalOperatorExpression l) {
            return new AstLogicalOperatorExpression(
                    l.operator(), l.operands().stream().map(this::rewrite).toList());
        }
        if (node instanceof AstInExpression i) {
            return new AstInExpression(
                    rewrite(i.value()), i.options().stream().map(this::rewrite).toList());
        }
        if (node instanceof AstRegexMatchExpression r) {
            return new AstRegexMatchExpression(rewrite(r.input()), r.regex(), r.options());
        }
        // Leaves: AstFieldPathExpression, AstLiteralExpression, AstValueExpression, AstVariableExpression
        return node;
    }

    private AstFilter descendFilter(AstFilter node) {
        if (node instanceof AstLogicalFilter lf) {
            return new AstLogicalFilter(
                    lf.operator(), lf.filters().stream().map(this::rewrite).toList());
        }
        if (node instanceof AstExprFilter ef) {
            return new AstExprFilter(rewrite(ef.expression()));
        }
        // Leaves: AstEmptyFilter, AstFieldOperationFilter
        return node;
    }
}
