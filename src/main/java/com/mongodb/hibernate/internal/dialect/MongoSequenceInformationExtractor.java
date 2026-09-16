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

package com.mongodb.hibernate.internal.dialect;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;

import java.sql.SQLException;
import java.util.ArrayList;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.relational.QualifiedSequenceName;
import org.hibernate.tool.schema.extract.spi.ExtractionContext;
import org.hibernate.tool.schema.extract.spi.SequenceInformation;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;
import org.jspecify.annotations.Nullable;

/**
 * Reports the increment stored in each counter document, which is what lets Hibernate ORM's own check reject a mapping
 * whose {@code allocationSize} disagrees with the counter it will allocate from. Without it that check has nothing to
 * compare against and is skipped, and a disagreement corrupts identifiers instead of failing.
 *
 * <p>Only the sequence name and the increment are reported. The start, minimum and maximum values have no counterpart
 * in a counter document, and nothing reads them.
 *
 * <p>A sequence declared with a schema qualifier is not covered, here or on any other dialect: the check requires the
 * extracted schema to be the default one, and a qualified sequence's is not.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
final class MongoSequenceInformationExtractor implements SequenceInformationExtractor {

    static final MongoSequenceInformationExtractor INSTANCE = new MongoSequenceInformationExtractor();

    private MongoSequenceInformationExtractor() {}

    @Override
    public Iterable<SequenceInformation> extractMetadata(ExtractionContext extractionContext) throws SQLException {
        var query = assertNotNull(
                extractionContext.getJdbcEnvironment().getDialect().getQuerySequencesString());
        return extractionContext.getQueryResults(query, null, resultSet -> {
            var nameColumn = resultSet.findColumn(ID_FIELD_NAME);
            var incrementColumn = resultSet.findColumn(MongoSequenceSupport.INCREMENT_FIELD_NAME);
            var sequences = new ArrayList<SequenceInformation>();
            while (resultSet.next()) {
                var name = assertNotNull(resultSet.getString(nameColumn));
                sequences.add(new MongoSequenceInformation(
                        new QualifiedSequenceName(null, null, Identifier.toIdentifier(name)),
                        resultSet.getLong(incrementColumn)));
            }
            return sequences;
        });
    }

    /** A counter document missing its increment reports zero, which disagrees with any allocation size. */
    private record MongoSequenceInformation(QualifiedSequenceName name, long increment) implements SequenceInformation {

        @Override
        public QualifiedSequenceName getSequenceName() {
            return name;
        }

        @Override
        public @Nullable Number getStartValue() {
            return null;
        }

        @Override
        public @Nullable Number getMinValue() {
            return null;
        }

        @Override
        public @Nullable Number getMaxValue() {
            return null;
        }

        @Override
        public Number getIncrementValue() {
            return increment;
        }
    }
}
