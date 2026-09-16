/*
 * Copyright 2026-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.boot;

import static com.mongodb.hibernate.internal.boot.NameChecks.forbidDot;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.dialect.MongoDialect;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.QualifiedSequenceName;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;

/**
 * Applies to sequences the qualifier rules {@link MongoAdditionalMappingContributor} applies to tables. Sequences are
 * registered after that contributor runs, which is why this lives here rather than beside it.
 *
 * <p>Every sequence becomes one document in
 * {@value com.mongodb.hibernate.internal.MongoConstants#SEQUENCE_COLLECTION_NAME} keyed by {@code schema.name}, so a
 * '.' written by the user would let two sequences Hibernate ORM keeps apart by qualifier land on one counter document
 * and hand out interleaved identifiers.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public final class MongoSequenceIntegrator implements Integrator {

    public MongoSequenceIntegrator() {}

    @Override
    public void integrate(
            Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sessionFactory) {
        if (!(metadata.getDatabase().getDialect() instanceof MongoDialect)) {
            // avoid interfering with bootstrapping unrelated to the MongoDB Extension for Hibernate ORM
            return;
        }
        for (var namespace : metadata.getDatabase().getNamespaces()) {
            for (var sequence : namespace.getSequences()) {
                var name = sequence.getName();
                forbidCatalog(name);
                var schema = name.getSchemaName();
                if (schema != null) {
                    forbidDot(schema.getText(), "sequence schema");
                }
                forbidDot(name.getSequenceName().getText(), "sequence");
            }
        }
    }

    /**
     * A catalog reaches a sequence through {@code @SequenceGenerator(catalog)} or through a three-part
     * {@code sequenceName}, neither of which surfaces on a table namespace, so this fires where
     * {@link MongoAdditionalMappingContributor}'s equivalent check for tables cannot.
     */
    private static void forbidCatalog(QualifiedSequenceName name) {
        var catalog = name.getCatalogName();
        if (catalog != null) {
            throw new FeatureNotSupportedException(format(
                    "The sequence [%s] is qualified by the catalog [%s], which is not supported: a MongoDB database is"
                            + " the analog of a SQL catalog. Note that a sequence name of the form [a.b.c] is read as"
                            + " catalog [a], schema [b], sequence [c].",
                    name, catalog.getText()));
        }
    }
}
