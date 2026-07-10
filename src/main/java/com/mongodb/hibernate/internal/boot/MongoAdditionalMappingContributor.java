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
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.dialect.MongoDialect;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.lang.reflect.AnnotatedElement;
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
import java.util.HashMap;
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
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.type.BasicPluralType;
import org.hibernate.type.ComponentType;

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
    private static final Collection<String> UNSUPPORTED_FIELD_NAME_CHARACTERS = Set.of(".", "$", "#");

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
        forbidEmbeddablesWithoutPersistentAttributes(metadata);
        metadata.getEntityBindings().forEach(persistentClass -> {
            checkPropertyTypes(persistentClass);
            checkColumnNames(persistentClass);
            forbidStructIdentifier(persistentClass);
            forbidJdbcTypeCodeAnnotation(persistentClass);
            setIdentifierColumnName(persistentClass);
        });
        forbidCatalog(metadata, buildingContext);
        forbidCollidingCollectionNames(metadata);
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
                        + " SessionFactory per database. See https://jira.mongodb.org/browse/HIBERNATE-39.",
                catalog));
    }

    /**
     * A schema folds into the collection name ({@code schema.name}), so two distinct table qualifiers can resolve to
     * the same collection — e.g. {@code @Table(schema = "a", name = "b")} and {@code @Table(name = "a.b")} both resolve
     * to {@code a.b}. Hibernate treats these as different tables and would not catch the clash, yet they would silently
     * co-mingle in one collection. Distinct qualifiers that resolve to the same name are rejected. Hibernate-sanctioned
     * table sharing (and a {@code SINGLE_TABLE} hierarchy's subclasses) share one {@link org.hibernate.mapping.Table}
     * instance, so they appear once and are not flagged.
     */
    private static void forbidCollidingCollectionNames(InFlightMetadataCollector metadata) {
        var resolvedToQualifier = new HashMap<String, String>();
        for (var namespace : metadata.getDatabase().getNamespaces()) {
            var schema = namespace.getName().schema();
            var schemaText = schema == null ? null : schema.getText();
            for (var table : namespace.getTables()) {
                var name = table.getName();
                var resolved = schemaText == null ? name : schemaText + "." + name;
                // NUL is illegal in a MongoDB namespace, so it is a safe delimiter for distinguishing qualifiers
                // that resolve to the same collection string (e.g. schema "a"/name "b" vs. no schema/name "a.b").
                var qualifier = (schemaText == null ? "" : schemaText) + '\u0000' + name;
                var previous = resolvedToQualifier.putIfAbsent(resolved, qualifier);
                if (previous != null && !previous.equals(qualifier)) {
                    throw new FeatureNotSupportedException(format(
                            "Two entities resolve to the same collection [%s] via distinct table qualifiers (schema"
                                    + " folding makes e.g. @Table(schema = \"a\", name = \"b\") collide with @Table(name"
                                    + " = \"a.b\")). Rename one so they resolve to different collections. See"
                                    + " https://jira.mongodb.org/browse/HIBERNATE-39.",
                            resolved));
                }
            }
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

    /** Forbid usage of {@link JdbcTypeCode} annotation. */
    private static void forbidJdbcTypeCodeAnnotation(PersistentClass persistentClass) {
        forbidJdbcTypeCodeAnnotationOnClass(persistentClass, persistentClass.getMappedClass());
        forbidJdbcTypeCodeAnnotationOnProperties(persistentClass, persistentClass.getProperties());
    }

    private static void forbidJdbcTypeCodeAnnotationOnProperties(
            PersistentClass persistentClass, java.util.List<Property> properties) {
        properties.forEach(property -> {
            if (property.getValue() instanceof Component component) {
                forbidJdbcTypeCodeAnnotationOnClass(persistentClass, component.getComponentClass());
                forbidJdbcTypeCodeAnnotationOnProperties(persistentClass, component.getProperties());
            }
        });
    }

    /** Forbid usage of {@link JdbcTypeCode} annotation on the given class and its superclasses. */
    private static void forbidJdbcTypeCodeAnnotationOnClass(PersistentClass persistentClass, Class<?> clazz) {
        for (var c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields()) {
                forbidJdbcTypeCodeAnnotationOnElement(persistentClass, field);
            }
            for (var method : c.getDeclaredMethods()) {
                forbidJdbcTypeCodeAnnotationOnElement(persistentClass, method);
            }
        }
    }

    private static void forbidJdbcTypeCodeAnnotationOnElement(
            PersistentClass persistentClass, AnnotatedElement element) {
        if (element.isAnnotationPresent(JdbcTypeCode.class)) {
            throw new FeatureNotSupportedException(format("%s: @JdbcTypeCode is not supported", persistentClass));
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
                forbidUnsupportedTypes(
                        persistentClass, pluralType.getElementType().getJavaType(), true, propertyPath);
            }
        } else if (type instanceof ComponentType) {
            checkComponentPropertyTypes(persistentClass, assertInstanceOf(value, Component.class), propertyPath);
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
            throw new FeatureNotSupportedException(format(
                    "%s: %s does not support primary key spanning multiple columns %s",
                    persistentClass, MONGO_DBMS_NAME, idColumns));
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
