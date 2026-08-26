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
import org.jspecify.annotations.Nullable;

/**
 * A single tree-rewriting rule. Given a candidate node, either produces a rewritten replacement or signals no-match by
 * returning {@code null}. A rule overrides only the hierarchies it cares about.
 *
 * <p>Pre-rules are applied top-down before descending into children; the first matching pre-rule wins and the walker
 * does not descend further into the returned node.
 *
 * <p>Post-rules are applied bottom-up after children have been rewritten; post-rules chain, each seeing the output of
 * the previous rule.
 *
 * <p>One overload per hierarchy the walker can visit, so every node is offered to every rule and a replacement is
 * required to stay in the hierarchy it replaces. A rule may still change the concrete class within one:
 * {@link GroupBySubstitutionRule} turns an {@code AstProjectStageIncludeSpecification} into an
 * {@code AstProjectStageFieldPathSpecification}.
 */
public interface RewriteRule {

    default @Nullable AstExpression tryMatch(AstExpression node) {
        return null;
    }

    default @Nullable AstFilter tryMatch(AstFilter node) {
        return null;
    }

    default @Nullable AstFilterOperation tryMatch(AstFilterOperation node) {
        return null;
    }

    default @Nullable AstStage tryMatch(AstStage node) {
        return null;
    }

    default @Nullable AstSortField tryMatch(AstSortField node) {
        return null;
    }

    default @Nullable AstProjectStageSpecification tryMatch(AstProjectStageSpecification node) {
        return null;
    }

    default @Nullable AstGroupStageSpecification tryMatch(AstGroupStageSpecification node) {
        return null;
    }

    default @Nullable AstLetVariable tryMatch(AstLetVariable node) {
        return null;
    }

    default @Nullable AstSwitchCase tryMatch(AstSwitchCase node) {
        return null;
    }

    default @Nullable AstValue tryMatch(AstValue node) {
        return null;
    }

    default @Nullable AstElement tryMatch(AstElement node) {
        return null;
    }

    default @Nullable AstDocument tryMatch(AstDocument node) {
        return null;
    }

    default @Nullable AstFieldUpdate tryMatch(AstFieldUpdate node) {
        return null;
    }

    default @Nullable AstComputedFieldUpdate tryMatch(AstComputedFieldUpdate node) {
        return null;
    }

    default @Nullable AstUpdate tryMatch(AstUpdate node) {
        return null;
    }

    default @Nullable AstUpdateStatement tryMatch(AstUpdateStatement node) {
        return null;
    }
}
