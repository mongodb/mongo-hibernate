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
import static org.hibernate.sql.ast.SqlTreePrinter.logSqlAst;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.util.Collections;
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

        if (mutationStatement instanceof InsertStatement) {
            return new JdbcOperationQueryInsertImpl(getMql(), getParameterBinders(), getAffectedTableNames());
        } else if (mutationStatement instanceof UpdateStatement) {
            return new JdbcOperationQueryUpdate(
                    getMql(), getParameterBinders(), getAffectedTableNames(), Collections.emptyMap());
        } else if (mutationStatement instanceof DeleteStatement) {
            return new JdbcOperationQueryDelete(
                    getMql(), getParameterBinders(), getAffectedTableNames(), Collections.emptyMap());
        } else {
            throw new FeatureNotSupportedException();
        }
    }

    private String getMql() {
        var mutationCommand = acceptAndYield(mutationStatement, COLLECTION_MUTATION);
        return renderMongoAstNode(mutationCommand);
    }
}
