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
import static com.mongodb.hibernate.internal.MongoAssertions.assertInstanceOf;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import jakarta.persistence.Embeddable;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.StringJoiner;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.AggregateColumn;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.type.BasicPluralType;
import org.hibernate.type.ComponentType;

/**
 * The instance methods like {@link #getContributorName()}, {@link #contribute(AdditionalMappingContributions,
 * InFlightMetadataCollector, ResourceStreamLocator, MetadataBuildingContext)} are called multiple times if multiple
 * {@link Metadata} instances are {@linkplain MetadataSources#buildMetadata() built} using the same
 * {@link BootstrapServiceRegistry}.
 */
public final class MongoAdditionalMappingContributor implements AdditionalMappingContributor {
    /**
     * We do not support these characters because BSON fields with names containing them must be handled specially as
     * described in <a href="https://www.mongodb.com/docs/manual/core/dot-dollar-considerations/">Field Names with
     * Periods and Dollar Signs</a>.
     */
    private static final Collection<String> UNSUPPORTED_FIELD_NAME_CHARACTERS = Set.of(".", "$");

    private static final Set<Class<?>> UNSUPPORTED_TYPES = Set.of(
            Calendar.class,
            Time.class,
            Date.class,
            java.sql.Date.class,
            Timestamp.class,
            LocalTime.class,
            LocalDateTime.class,
            ZonedDateTime.class,
            OffsetTime.class,
            OffsetDateTime.class);

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

    private static void checkPropertyTypes(PersistentClass persistentClass) {
        checkPropertyType(persistentClass, persistentClass.getIdentifierProperty(), new StringJoiner("."));
        persistentClass.getProperties().forEach(property -> {
            checkPropertyType(persistentClass, property, new StringJoiner("."));
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

    private static void checkPropertyType(
            PersistentClass persistentClass, Property property, StringJoiner propertyPath) {
        propertyPath.add(property.getName());
        var value = property.getValue();
        var type = value.getType();
        if (type instanceof BasicPluralType<?, ?> pluralType) {
            var columns = value.getColumns();
            assertTrue(columns.size() == 1);
            if (columns.get(0) instanceof AggregateColumn aggregateColumn) {
                checkComponentPropertyTypes(persistentClass, aggregateColumn.getComponent(), propertyPath);
            } else {
                forbidTemporalTypes(persistentClass, pluralType.getElementType().getJavaType(), true, propertyPath);
            }
        } else if (type instanceof ComponentType) {
            checkComponentPropertyTypes(persistentClass, assertInstanceOf(value, Component.class), propertyPath);
        } else {
            forbidTemporalTypes(persistentClass, type.getReturnedClass(), false, propertyPath);
        }
    }

    private static void checkComponentPropertyTypes(
            PersistentClass persistentClass, Component component, StringJoiner propertyPath) {
        component
                .getProperties()
                .forEach(componentProperty -> checkPropertyType(persistentClass, componentProperty, propertyPath));
    }

    private static void forbidTemporalTypes(
            PersistentClass persistentClass, Class<?> typeToCheck, boolean plural, StringJoiner propertyPath) {
        if (UNSUPPORTED_TYPES.contains(typeToCheck)) {
            throw new FeatureNotSupportedException(format(
                    plural
                            ? "%s: the plural persistent attribute [%s] has element type [%s] that is not supported"
                            : "%s: the persistent attribute [%s] has type [%s] that is not supported",
                    persistentClass,
                    propertyPath,
                    typeToCheck.getTypeName()));
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
