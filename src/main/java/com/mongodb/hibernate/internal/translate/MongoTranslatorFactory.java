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

import org.hibernate.dialect.sql.ast.spi.SqlAstTranslationRequest;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslatorFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.spi.Statement;
import org.hibernate.sql.ast.spi.model.OptionalTableUpdate;
import org.hibernate.sql.ast.spi.model.TableMutation;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.spi.mutation.jdbc.JdbcMutationOperation;

/**
 * @hidden
 * @mongoCme Must be thread-safe.
 */
@SuppressWarnings("MissingSummary")
public final class MongoTranslatorFactory implements SqlAstTranslatorFactory {
    public static MongoTranslatorFactory INSTANCE = new MongoTranslatorFactory();

    private MongoTranslatorFactory() {}

    @Override
    @SuppressWarnings("unchecked")
    public <S extends Statement, O extends JdbcOperation> SqlAstTranslator<O> buildTranslator(
            SqlAstTranslationRequest<S, O> request) {
        if (request instanceof SqlAstTranslationRequest.Select selectRequest) {
            return (SqlAstTranslator<O>) (SqlAstTranslator<?>) new SelectMqlTranslator(selectRequest);
        } else if (request instanceof SqlAstTranslationRequest.QueryMutation mutationRequest) {
            return (SqlAstTranslator<O>) (SqlAstTranslator<?>) new MutationMqlTranslator(mutationRequest);
        } else if (request instanceof SqlAstTranslationRequest.ModelMutation<?> modelMutationRequest) {
            // A model mutation's semantic statement is a TableMutation<?> while its translator renders a
            // constituent JdbcMutationOperation, so the narrowing is safe at runtime.
            var tableMutation =
                    (TableMutation<JdbcMutationOperation>) (TableMutation<?>) modelMutationRequest.statement();
            return (SqlAstTranslator<O>) (SqlAstTranslator<?>)
                    new ModelMutationMqlTranslator<>(tableMutation, modelMutationRequest.sessionFactory());
        } else {
            throw new IllegalArgumentException("Unsupported translation request: " + request);
        }
    }

    /**
     * For {@code StatelessSession.upsert}: translates the same {@link OptionalTableUpdate} node that
     * {@link #buildTranslator} translates as a plain update, requesting the upsert form via the value descriptor
     * instead.
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
