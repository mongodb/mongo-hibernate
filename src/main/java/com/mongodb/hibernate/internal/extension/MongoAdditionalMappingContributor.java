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
import jakarta.persistence.Embeddable;
import java.sql.Time;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;

public final class MongoAdditionalMappingContributor implements AdditionalMappingContributor {
    /**
     * We do not support these characters because BSON fields with names containing them must be handled specially as
     * described in <a href="https://www.mongodb.com/docs/manual/core/dot-dollar-considerations/">Field Names with
     * Periods and Dollar Signs</a>.
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
        forbidEmbeddablesWithoutPersistentAttributes(metadata);
        metadata.getEntityBindings().forEach(persistentClass -> {
            checkPropertyTypes(persistentClass);
            checkColumnNames(persistentClass);
            forbidStructIdentifier(persistentClass);
            setIdentifierColumnName(persistentClass);
        });
    }

    private static void checkPropertyTypes(final PersistentClass persistentClass) {
        persistentClass.getProperties().forEach(property -> {
            forbidTemporalTypes(persistentClass, property);
        });
    }

    private static void checkColumnNames(PersistentClass persistentClass) {
        persistentClass
                .getTable()
                .getColumns()
                .forEach(column -> forbidUnsupportedFieldNameCharacters(column.getName(), persistentClass));
    }

    private static void forbidUnsupportedFieldNameCharacters(String fieldName, PersistentClass persistentClass) {
        UNSUPPORTED_FIELD_NAME_CHARACTERS.forEach(unsupportedCharacter -> {
            if (fieldName.contains(unsupportedCharacter)) {
                throw new FeatureNotSupportedException(format(
                        "%s: the character [%s] in field names is not supported, but is present in the field name [%s]",
                        persistentClass, unsupportedCharacter, fieldName));
            }
        });
    }

    private static void forbidStructIdentifier(PersistentClass persistentClass) {
        if (persistentClass.getIdentifier() instanceof Component aggregateEmbeddableIdentifier
                && aggregateEmbeddableIdentifier.getStructName() != null) {
            throw new FeatureNotSupportedException(format(
                    "%s: aggregate embeddable primary keys are not supported, you may want to use [@%s] instead of [@%s @%s]",
                    persistentClass,
                    Embeddable.class.getSimpleName(),
                    Embeddable.class.getSimpleName(),
                    Struct.class.getSimpleName()));
        }
    }

    private static void forbidEmbeddablesWithoutPersistentAttributes(InFlightMetadataCollector metadata) {
        metadata.visitRegisteredComponents(component -> {
            if (!component.hasAnyInsertableColumns()) {
                throw new FeatureNotSupportedException(
                        format("%s: must have at least one persistent attribute", component));
            }
        });
    }

    private static void forbidTemporalTypes(final PersistentClass persistentClass, final Property property) {
        Class<?> persistenceAttributeType = property.getType().getReturnedClass();
        if (Calendar.class.equals(persistenceAttributeType)
                || Date.class.equals(persistenceAttributeType)
                || java.sql.Date.class.equals(persistenceAttributeType)
                || java.sql.Timestamp.class.equals(persistenceAttributeType)
                || Time.class.equals(persistenceAttributeType)
                || LocalTime.class.equals(persistenceAttributeType)
                || LocalDateTime.class.equals(persistenceAttributeType)
                || ZonedDateTime.class.equals(persistenceAttributeType)
                || OffsetTime.class.equals(persistenceAttributeType)) {
            throw new FeatureNotSupportedException(format(
                    "%s: the persistent attribute [%s] has type [%s] that is not supported",
                    persistentClass, property.getName(), property.getType()));
        }
    }

    private static void setIdentifierColumnName(PersistentClass persistentClass) {
        var identifier = persistentClass.getIdentifier();
        assertFalse(identifier.hasFormula());
        var idColumns = identifier.getColumns();
        if (idColumns.size() > 1) {
            throw new FeatureNotSupportedException(format(
                    "%s: %s does not support primary key spanning multiple columns %s",
                    persistentClass, MONGO_DBMS_NAME, idColumns));
        }
        assertTrue(idColumns.size() == 1);
        var idColumn = idColumns.get(0);
        idColumn.setName(ID_FIELD_NAME);
    }
}
