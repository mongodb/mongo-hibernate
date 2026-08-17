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

import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.VNRegistry;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Pre-rule that replaces an expression whose value number matches a GROUP BY key with a reference to that key's
 * {@code _id.<subKey>} field. Runs top-down: a parent whole-match wins over a child leaf-match, preserving the
 * canonical form of the rewritten pipeline.
 */
public final class GroupBySubstitutionRule implements RewriteRule<AstNode> {

    private final Map<Integer, String> groupKeyVN;
    private final VNRegistry vnRegistry;

    public GroupBySubstitutionRule(Map<Integer, String> groupKeyVN, VNRegistry vnRegistry) {
        this.groupKeyVN = groupKeyVN;
        this.vnRegistry = vnRegistry;
    }

    @Override
    public @Nullable AstNode tryMatch(AstNode node) {
        if (!(node instanceof AstExpression expr)) {
            return null;
        }
        String subKey = groupKeyVN.get(expr.valueNumber(vnRegistry));
        return subKey != null ? new AstFieldPathExpression("_id." + subKey) : null;
    }
}
