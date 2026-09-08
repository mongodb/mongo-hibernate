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

package com.mongodb.hibernate.internal.boot;

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;
import static com.mongodb.hibernate.internal.MongoAssertions.assertInstanceOf;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

import com.mongodb.hibernate.internal.EmbeddedIdColumnName;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.dialect.MongoDialect;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.GeneratedValue;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.types.BSONTimestamp;
import org.bson.types.Binary;
import org.bson.types.Code;
import org.bson.types.CodeWScope;
import org.bson.types.CodeWithScope;
import org.bson.types.Decimal128;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.Symbol;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GeneratedColumn;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.ResourceStreamLocator;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.spi.AdditionalMappingContributions;
import org.hibernate.boot.spi.AdditionalMappingContributor;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.mapping.AggregateColumn;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.ToOne;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.type.BasicPluralType;

/**
 * @hidden
 * @mongoCme The instance methods of {@link AdditionalMappingContributor} are called multiple times if multiple
 *     {@link Metadata} instances are {@linkplain MetadataSources#buildMetadata() built} using the same
 *     {@link BootstrapServiceRegistry}.
 */
@SuppressWarnings("MissingSummary")
public final class MongoAdditionalMappingContributor implements AdditionalMappingContributor {
    /**
     * We do not support these characters because BSON fields with names containing them must be handled specially as
     * described in <a href="https://www.mongodb.com/docs/manual/core/dot-dollar-considerations/">Field Names with
     * Periods and Dollar Signs</a>. We also reserve '#' as a separator for computed projections in MQL joins.
     */
    private static final Set<String> UNSUPPORTED_FIELD_NAME_CHARACTERS = Set.of(".", "$", "#");

    private static final Set<Class<?>> UNSUPPORTED_TYPES = Set.of(
            // Temporal types
            Calendar.class,
            Time.class,
            Date.class,
            java.sql.Date.class,
            Timestamp.class,
            LocalTime.class,
            LocalDateTime.class,
            ZonedDateTime.class,
            OffsetTime.class,
            OffsetDateTime.class,
            // BSON value types
            BSONTimestamp.class,
            Binary.class,
            Code.class,
            CodeWithScope.class,
            CodeWScope.class,
            MinKey.class,
            MaxKey.class,
            Symbol.class,
            Decimal128.class,
            // BSON document types
            Document.class,
            BsonDocument.class,
            RawBsonDocument.class,
            BsonDocumentWrapper.class,
            // java.util types
            UUID.class);

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
        if (!(metadata.getDatabase().getDialect() instanceof MongoDialect)) {
            // avoid interfering with bootstrapping unrelated to the MongoDB Extension for Hibernate ORM
            return;
        }
        forbidIdClassIdentifiers(metadata);
        forbidEmbeddablesWithoutPersistentAttributes(metadata);
        metadata.getEntityBindings().forEach(persistentClass -> {
            checkPropertyTypes(persistentClass);
            checkColumnNames(persistentClass);
            forbidStructIdentifier(persistentClass);
            forbidNonScalarIdComponent(persistentClass);
            forbidDerivedIdentity(persistentClass);
            forbidJdbcTypeCodeAnnotation(persistentClass);
            forbidColumnFragmentAnnotations(persistentClass);
            setIdentifierColumnName(persistentClass);
            materializeUniqueColumns(persistentClass);
        });
        forbidCatalog(metadata, buildingContext);
        forbidDottedTableQualifiers(metadata);
    }

    /**
     * Creates a {@link UniqueKey} for each column mapped with {@code @Column(unique = true)} or
     * {@link org.hibernate.annotations.NaturalId}.
     *
     * <p>For SQL dialects, a unique column is specified as part of the table definition. In MongoDB it is an index, so
     * it is represented as an ordinary unique key.
     */
    private static void materializeUniqueColumns(PersistentClass persistentClass) {
        var table = persistentClass.getTable();
        for (var column : table.getColumns()) {
            if (column.isUnique() && !table.isPrimaryKey(column)) {
                var keyName = column.getUniqueKeyName();
                assertNotNull(keyName);
                table.getOrCreateUniqueKey(keyName).addColumn(column);
            }
        }
    }

    /**
     * A MongoDB database is the analog of a SQL catalog, and catalog {@code ->} database is not yet supported.
     * Reporting {@link org.hibernate.engine.jdbc.env.spi.NameQualifierSupport#SCHEMA} makes Hibernate silently drop a
     * catalog qualifier, so reject it here instead: the per-table {@code @Table(catalog)} (and
     * secondary/join/collection tables) surfaces on a namespace, while the global {@code hibernate.default_catalog} is
     * applied only at SQL-render time (not a namespace at this stage), so it is read from configuration. Both are
     * checked.
     */
    private static void forbidCatalog(InFlightMetadataCollector metadata, MetadataBuildingContext buildingContext) {
        var defaultCatalog = buildingContext
                .getBootstrapContext()
                .getServiceRegistry()
                .requireService(ConfigurationService.class)
                .getSettings()
                .get(AvailableSettings.DEFAULT_CATALOG);
        if (defaultCatalog != null && !defaultCatalog.toString().isBlank()) {
            throw catalogNotSupported(defaultCatalog.toString());
        }
        for (var namespace : metadata.getDatabase().getNamespaces()) {
            var catalog = namespace.getName().catalog();
            if (catalog != null) {
                throw catalogNotSupported(catalog.getText());
            }
        }
    }

    private static FeatureNotSupportedException catalogNotSupported(String catalog) {
        return new FeatureNotSupportedException(format(
                "Catalog is not supported: [%s]. A MongoDB database is the analog of a SQL catalog; use a separate"
                        + " SessionFactory per database.",
                catalog));
    }

    /**
     * A schema folds into the collection name as {@code schema.name}, so the '.' is the extension's own separator. A
     * '.' written by the user makes the resolved name ambiguous: {@code @Table(schema = "a", name = "b")} and
     * {@code @Table(name = "a.b")} would resolve to the same collection, and nothing downstream could tell the two
     * qualifiers apart.
     */
    private static void forbidDottedTableQualifiers(InFlightMetadataCollector metadata) {
        for (var namespace : metadata.getDatabase().getNamespaces()) {
            var schema = namespace.getName().schema();
            if (schema != null) {
                forbidDot(schema.getText(), "schema");
            }
            for (var table : namespace.getTables()) {
                forbidDot(table.getName(), "table");
            }
        }
    }

    private static void forbidDot(String name, String kind) {
        if (name.contains(".")) {
            throw new FeatureNotSupportedException(format(
                    "The character [.] in a %s name is not supported, but is present in [%s]. A schema folds into the"
                            + " collection name as [schema.name], so a '.' written by the user would make the resolved"
                            + " collection name ambiguous.",
                    kind, name));
        }
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

    /**
     * Forbid a non-aggregated composite identifier ({@code @IdClass}, or multiple {@code @Id} attributes). Must run
     * before {@link #forbidEmbeddablesWithoutPersistentAttributes}, which would otherwise misfire on the synthetic
     * identifier-mapper {@link Component} with a misleading message.
     */
    private static void forbidIdClassIdentifiers(InFlightMetadataCollector metadata) {
        metadata.getEntityBindings().forEach(persistentClass -> {
            if (persistentClass.getIdentifierMapper() != null) {
                throw new FeatureNotSupportedException(format(
                        "%s: a non-aggregated composite identifier (@IdClass, or multiple @Id attributes) is not"
                                + " supported; declare the composite key with @EmbeddedId."
                                + " TODO-HIBERNATE-235 https://jira.mongodb.org/browse/HIBERNATE-235",
                        persistentClass));
            }
        });
    }

    /**
     * Forbid a composite id component that is not a basic value: a nested embeddable or a collection, or an association
     * (derived identity nested inside the id, as opposed to the entity-level {@code @MapsId} shape caught by
     * {@link #forbidDerivedIdentity}). Every component of a composite id must be a basic value.
     */
    private static void forbidNonScalarIdComponent(PersistentClass persistentClass) {
        if (persistentClass.getIdentifier() instanceof Component idComponent) {
            for (var property : idComponent.getProperties()) {
                var value = property.getValue();
                if (value instanceof ToOne) {
                    throw new FeatureNotSupportedException(format(
                            "%s: an association inside the id (derived identity) is not supported;"
                                    + " every component of a composite id must be a basic value."
                                    + " TODO-HIBERNATE-237 https://jira.mongodb.org/browse/HIBERNATE-237",
                            persistentClass));
                }
                if (value instanceof Component || value instanceof Collection) {
                    throw new FeatureNotSupportedException(format(
                            "%s: a non-scalar id component (nested embeddable or collection) is not supported;"
                                    + " every component of a composite id must be a basic value."
                                    + " TODO-HIBERNATE-236 https://jira.mongodb.org/browse/HIBERNATE-236",
                            persistentClass));
                }
            }
        }
    }

    /**
     * Forbid derived identity ({@code @MapsId}): an entity-level {@link ToOne} property whose column(s) overlap the
     * identifier's columns. Must run before {@link #setIdentifierColumnName}, which renames the id column(s) to
     * {@code _id} (or {@code _id.<component>}) and would destroy the overlap this check relies on.
     */
    private static void forbidDerivedIdentity(PersistentClass persistentClass) {
        var idColumnNames = persistentClass.getIdentifier().getColumns().stream()
                .map(column -> column.getName())
                .collect(toSet());
        for (var property : persistentClass.getProperties()) {
            if (property.getValue() instanceof ToOne toOne
                    && !toOne.hasFormula()
                    && toOne.getColumns().stream().anyMatch(column -> idColumnNames.contains(column.getName()))) {
                throw new FeatureNotSupportedException(format(
                        "%s: derived identity (an association participating in the id, e.g. @MapsId) is not"
                                + " supported. TODO-HIBERNATE-237 https://jira.mongodb.org/browse/HIBERNATE-237",
                        persistentClass));
            }
        }
    }

    /** Forbid usage of {@link JdbcTypeCode} annotation. */
    private static void forbidJdbcTypeCodeAnnotation(PersistentClass persistentClass) {
        ClassElementChecker.check(persistentClass, true, ClassElementChecker.forbid(JdbcTypeCode.class));
    }

    private static void forbidEmbeddablesWithoutPersistentAttributes(InFlightMetadataCollector metadata) {
        metadata.visitRegisteredComponents(component -> {
            if (!component.hasAnyInsertableColumns()) {
                throw new FeatureNotSupportedException(
                        format("%s: must have at least one persistent attribute", component));
            }
        });
    }

    /**
     * Forbid usage of {@link org.hibernate.annotations.Generated} and
     * {@link org.hibernate.annotations.CurrentTimestamp} annotations.
     */
    private static void forbidColumnFragmentAnnotations(PersistentClass persistentClass) {
        ClassElementChecker.check(
                persistentClass,
                false,
                ClassElementChecker.forbid(Generated.class),
                ClassElementChecker.forbid(GeneratedColumn.class),
                ClassElementChecker.forbid(GeneratedValue.class),
                ClassElementChecker.CURRENT_TIMESTAMP_WITH_DB_SOURCE);
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
                forbidUnsupportedTypes(
                        persistentClass, pluralType.getElementType().getJavaType(), true, propertyPath);
            }
        } else if (value instanceof Component component) {
            checkComponentPropertyTypes(persistentClass, component, propertyPath);
        } else {
            forbidUnsupportedTypes(persistentClass, type.getReturnedClass(), false, propertyPath);
        }
    }

    private static void checkComponentPropertyTypes(
            PersistentClass persistentClass, Component component, StringJoiner propertyPath) {
        component
                .getProperties()
                .forEach(componentProperty -> checkPropertyType(persistentClass, componentProperty, propertyPath));
    }

    private static void forbidUnsupportedTypes(
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
            // Non-scalar id components (nested embeddable or collection) were already rejected by
            // forbidNonScalarIdComponent, so each component of this composite id has exactly one column.
            var idComponent = assertInstanceOf(identifier, Component.class);
            for (var property : idComponent.getProperties()) {
                var componentColumns = property.getValue().getColumns();
                assertTrue(componentColumns.size() == 1);
                componentColumns.get(0).setName(EmbeddedIdColumnName.forComponent(property.getName()));
            }
            return;
        }
        assertTrue(idColumns.size() == 1);
        var idColumn = idColumns.get(0);
        if (!ID_FIELD_NAME.equals(idColumn.getName()) && identifier instanceof SimpleValue simpleValue) {
            var memberDetails = simpleValue.getMemberDetails();
            if (memberDetails != null) {
                var columnAnnotation = memberDetails.getDirectAnnotationUsage(Column.class);
                if (columnAnnotation != null && !columnAnnotation.name().isBlank()) {
                    throw new FeatureNotSupportedException(format(
                            "%s: the @Id column name cannot be overridden to [%s];"
                                    + " MongoDB requires the primary key field to be named [%s]"
                                    + " — remove @Column(name = \"%s\") or change it to @Column(name = \"%s\")",
                            persistentClass, idColumn.getName(), ID_FIELD_NAME, idColumn.getName(), ID_FIELD_NAME));
                }
            }
        }
        idColumn.setName(ID_FIELD_NAME);
    }
}
