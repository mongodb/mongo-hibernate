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

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.MutationStatement;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.exec.spi.JdbcOperationQueryMutation;
import org.hibernate.sql.exec.spi.JdbcSelect;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;

/**
 * @hidden
 * @mongoCme Must be thread-safe.
 */
@SuppressWarnings("MissingSummary")
public final class MongoTranslatorFactory implements SqlAstTranslatorFactory {
    public static MongoTranslatorFactory INSTANCE = new MongoTranslatorFactory();

    private MongoTranslatorFactory() {}

    @Override
    public SqlAstTranslator<JdbcSelect> buildSelectTranslator(
            SessionFactoryImplementor sessionFactoryImplementor, SelectStatement selectStatement) {
        return new SelectMqlTranslator(sessionFactoryImplementor, selectStatement);
    }

    @Override
    public SqlAstTranslator<? extends JdbcOperationQueryMutation> buildMutationTranslator(
            SessionFactoryImplementor sessionFactoryImplementor, MutationStatement mutationStatement) {
        return new MutationMqlTranslator(sessionFactoryImplementor, mutationStatement);
    }

    @Override
    public <O extends JdbcMutationOperation> SqlAstTranslator<O> buildModelMutationTranslator(
            TableMutation<O> tableMutation, SessionFactoryImplementor sessionFactoryImplementor) {
        return new ModelMutationMqlTranslator<>(tableMutation, sessionFactoryImplementor);
    }

    /**
     * For {@code StatelessSession.upsert}: translates the same {@link OptionalTableUpdate} node that
     * {@link #buildModelMutationTranslator} translates as a plain update, requesting the upsert form via the value
     * descriptor instead.
     */
    public SqlAstTranslator<JdbcMutationOperation> buildUpsertModelMutationTranslator(
            OptionalTableUpdate optionalTableUpdate, SessionFactoryImplementor sessionFactoryImplementor) {
        // OptionalTableUpdate is a TableMutation<MutationOperation>; the operation this translator
        // makes it create is a JdbcUpdateMutation, so the narrowing is safe at runtime. Hibernate
        // itself routes this node through the same <O extends JdbcMutationOperation> bound.
        @SuppressWarnings("unchecked")
        var tableMutation = (TableMutation<JdbcMutationOperation>) (TableMutation<?>) optionalTableUpdate;
        return new ModelMutationMqlTranslator<>(
                tableMutation, sessionFactoryImplementor, AstVisitorValueDescriptor.UPSERT_MODEL_MUTATION_RESULT);
    }
}
