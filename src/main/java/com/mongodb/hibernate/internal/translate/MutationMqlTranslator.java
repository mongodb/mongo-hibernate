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

package com.mongodb.hibernate.internal.translate;

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_MUTATION;
import static java.util.Collections.emptyMap;
import static org.hibernate.sql.ast.SqlTreePrinter.logSqlAst;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.delete.DeleteStatement;
import org.hibernate.sql.ast.tree.insert.InsertStatement;
import org.hibernate.sql.ast.tree.update.UpdateStatement;
import org.hibernate.sql.exec.internal.JdbcOperationQueryInsertImpl;
import org.hibernate.sql.exec.spi.JdbcOperationQueryDelete;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcOperationQueryUpdate;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.jspecify.annotations.Nullable;

final class MutationMqlTranslator extends AbstractMqlTranslator<JdbcOperationQueryMutation> {

    private final MutationStatement mutationStatement;

    MutationMqlTranslator(SessionFactoryImplementor sessionFactory, MutationStatement mutationStatement) {
        super(sessionFactory);
        this.mutationStatement = mutationStatement;
    }

    @Override
    public JdbcOperationQueryMutation translate(
            @Nullable JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {

        logSqlAst(mutationStatement);

        checkJdbcParameterBindingsSupportability(jdbcParameterBindings);
        checkQueryOptionsSupportability(queryOptions);

        var mutationCommand = acceptAndYield(mutationStatement, COLLECTION_MUTATION);
        var mql = renderMongoAstNode(mutationCommand);
        var parameterBinders = getParameterBinders();
        var affectedCollectionNames = getAffectedTableNames();

        if (mutationStatement instanceof InsertStatement) {
            return new JdbcOperationQueryInsertImpl(mql, parameterBinders, affectedCollectionNames);
        } else if (mutationStatement instanceof UpdateStatement) {
            return new JdbcOperationQueryUpdate(mql, parameterBinders, affectedCollectionNames, emptyMap());
        } else if (mutationStatement instanceof DeleteStatement) {
            return new JdbcOperationQueryDelete(mql, parameterBinders, affectedCollectionNames, emptyMap());
        } else {
            throw new FeatureNotSupportedException("Unsupported mutation statement type: "
                    + mutationStatement.getClass().getName());
        }
    }
}
