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

import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLetVariable;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortField;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstStage;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterOperation;

/**
 * What a node passes to its own children from {@code mapChildren}. One overload per child kind an AST node can hold, so
 * an implementation rebuilds itself without casting.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public interface AstNodeRewriter {

    AstExpression rewrite(AstExpression node);

    AstFilter rewrite(AstFilter node);

    AstFilterOperation rewrite(AstFilterOperation node);

    AstStage rewrite(AstStage node);

    AstSortField rewrite(AstSortField node);

    AstProjectStageSpecification rewrite(AstProjectStageSpecification node);

    AstGroupStageSpecification rewrite(AstGroupStageSpecification node);

    AstLetVariable rewrite(AstLetVariable node);

    AstSwitchCase rewrite(AstSwitchCase node);

    AstValue rewrite(AstValue node);

    AstElement rewrite(AstElement node);

    AstDocument rewrite(AstDocument node);

    AstFieldUpdate rewrite(AstFieldUpdate node);

    AstComputedFieldUpdate rewrite(AstComputedFieldUpdate node);

    AstUpdate rewrite(AstUpdate node);

    AstUpdateStatement rewrite(AstUpdateStatement node);
}
