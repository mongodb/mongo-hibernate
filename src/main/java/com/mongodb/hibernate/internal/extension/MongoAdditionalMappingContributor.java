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

package com.mongodb.hibernate.internal.extension;

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.util.Collection;
import java.util.Set;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.PersistentClass;

public final class MongoAdditionalMappingContributor implements AdditionalMappingContributor {
    /**
     * We do not support these characters because BSON fields with names containing them must be handled specially as
     * described in <a href=https://www.mongodb.com/docs/manual/core/dot-dollar-considerations/>Field Names with Periods
     * and Dollar Signs</a>.
     */
    private static final Collection<String> UNSUPPORTED_FIELD_NAME_CHARACTERS = Set.of(".", "$");

    public MongoAdditionalMappingContributor() {}

    @Override
    public String getContributorName() {
        return getClass().getSimpleName();
    }

    @Override
    public void contribute(
            AdditionalMappingContributions contributions,
            InFlightMetadataCollector metadata,
            ResourceStreamLocator resourceStreamLocator,
            MetadataBuildingContext buildingContext) {
        metadata.getEntityBindings().forEach(persistentClass -> {
            if (persistentClass.useDynamicInsert()) {
                throw new FeatureNotSupportedException(
                        format("%s is not supported", DynamicInsert.class.getSimpleName()));
            }
            checkColumnNames(persistentClass);
            setIdentifierColumnName(persistentClass);
        });
    }

    private static void checkColumnNames(PersistentClass persistentClass) {
        persistentClass.getTable().getColumns().forEach(column -> {
            var columnName = column.getName();
            UNSUPPORTED_FIELD_NAME_CHARACTERS.forEach(unsupportedCharacter -> {
                if (columnName.contains(unsupportedCharacter)) {
                    throw new FeatureNotSupportedException(format(
                            "%s: the character [%s] in field names is not supported, but is present in the field name [%s]",
                            persistentClass, unsupportedCharacter, columnName));
                }
            });
        });
    }

    private static void setIdentifierColumnName(PersistentClass persistentClass) {
        var identifier = persistentClass.getIdentifier();
        assertFalse(identifier.hasFormula());
        var idColumns = identifier.getColumns();
        if (idColumns.size() > 1) {
            throw new FeatureNotSupportedException(format(
                    "%s: %s does not support [%s] field spanning multiple columns %s",
                    persistentClass, MONGO_DBMS_NAME, ID_FIELD_NAME, idColumns));
        }
        assertTrue(idColumns.size() == 1);
        var idColumn = idColumns.get(0);
        idColumn.setName(ID_FIELD_NAME);
    }
}
