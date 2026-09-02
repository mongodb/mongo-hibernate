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

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.VNRegistry;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageFieldPathSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageIncludeSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortField;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Pre-rule that replaces an expression whose value number matches a GROUP BY key with a reference to that key's
 * {@code _id.<subKey>} field. Runs top-down: a parent whole-match wins over a child leaf-match, preserving the
 * canonical form of the rewritten pipeline.
 *
 * <p>Any raw field-path leaf that survives without matching a GROUP BY key is a stray — a SELECT/HAVING/ORDER BY
 * reference to a column that is neither in GROUP BY nor inside an aggregate function. The rule throws
 * {@link FeatureNotSupportedException} in that case rather than emit a post-{@code $group} pipeline that references a
 * non-existent field.
 *
 * <p>Field paths that name an accumulator produced in the {@code $group} stage (e.g. {@code sum_0}) are whitelisted:
 * they are legitimate post-{@code $group} references and must survive substitution without being flagged as strays or
 * looked up as GROUP BY keys.
 */
public final class GroupBySubstitutionRule implements RewriteRule {

    private final Map<Integer, String> groupKeyVN;
    private final VNRegistry vnRegistry;
    private final Set<String> accumulatorFields;

    public GroupBySubstitutionRule(
            Map<Integer, String> groupKeyVN, VNRegistry vnRegistry, Set<String> accumulatorFields) {
        this.groupKeyVN = groupKeyVN;
        this.vnRegistry = vnRegistry;
        this.accumulatorFields = accumulatorFields;
    }

    @Override
    public @Nullable AstExpression tryMatch(AstExpression node) {
        String subKey = groupKeyVN.get(vnRegistry.valueNumber(node));
        if (subKey != null) {
            return new AstFieldPathExpression("_id." + subKey);
        }
        if (node instanceof AstFieldPathExpression fp) {
            if (accumulatorFields.contains(fp.fieldPath())) {
                return null;
            }
            throw strayColumn(fp.fieldPath());
        }
        return null;
    }

    @Override
    public @Nullable AstFilter tryMatch(AstFilter node) {
        if (node instanceof AstFieldOperationFilter fof) {
            if (accumulatorFields.contains(fof.fieldPath())) {
                return null;
            }
            return new AstFieldOperationFilter(subKeyOrThrow(fof.fieldPath()), fof.filterOperation());
        }
        return null;
    }

    @Override
    public @Nullable AstSortField tryMatch(AstSortField node) {
        if (accumulatorFields.contains(node.path())) {
            return null;
        }
        return new AstSortField(subKeyOrThrow(node.path()), node.order());
    }

    @Override
    public @Nullable AstProjectStageSpecification tryMatch(AstProjectStageSpecification node) {
        if (node instanceof AstProjectStageIncludeSpecification inc) {
            return idSpecification(inc.field());
        }
        if (node instanceof AstProjectStageFieldPathSpecification fps) {
            return idSpecification(fps.fieldPath());
        }
        return null;
    }

    private @Nullable AstProjectStageFieldPathSpecification idSpecification(String fieldPath) {
        if (accumulatorFields.contains(fieldPath)) {
            return null;
        }
        String subKey = lookupByFieldPath(fieldPath);
        if (subKey == null) {
            throw strayColumn(fieldPath);
        }
        return new AstProjectStageFieldPathSpecification("_id#" + subKey, "_id." + subKey);
    }

    private String subKeyOrThrow(String fieldPath) {
        String subKey = lookupByFieldPath(fieldPath);
        if (subKey == null) {
            throw strayColumn(fieldPath);
        }
        return "_id." + subKey;
    }

    private static FeatureNotSupportedException strayColumn(String fieldPath) {
        return new FeatureNotSupportedException("column '" + fieldPath
                + "' appears in SELECT/HAVING/ORDER BY but is not a GROUP BY key "
                + "and is not inside an aggregate function");
    }

    private @Nullable String lookupByFieldPath(String fieldPath) {
        return groupKeyVN.get(vnRegistry.valueNumber(new AstFieldPathExpression(fieldPath)));
    }
}
