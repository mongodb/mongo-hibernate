/*
 * Copyright 2024-present MongoDB, Inc.
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

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static com.mongodb.hibernate.internal.dialect.function.FunctionParameterDefinition.orMissing;
import static com.mongodb.hibernate.internal.dialect.function.FunctionParameterDefinition.required;
import static com.mongodb.hibernate.internal.dialect.function.MongoExpressionPositionalFunction.swap;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.MongoConstants;
import com.mongodb.hibernate.internal.dialect.function.FunctionParameterDefinition;
import com.mongodb.hibernate.internal.dialect.function.MongoExpressionNamedFunction;
import com.mongodb.hibernate.internal.dialect.function.MongoExpressionPositionalFunction;
import com.mongodb.hibernate.internal.dialect.function.MongoExpressionUnaryFunction;
import com.mongodb.hibernate.internal.dialect.function.MongoExpressionVariadicFunction;
import com.mongodb.hibernate.internal.dialect.function.MongoExtractFunction;
import com.mongodb.hibernate.internal.dialect.function.MongoPadFunction;
import com.mongodb.hibernate.internal.dialect.function.MongoRepeatFunction;
import com.mongodb.hibernate.internal.dialect.function.MongoSubstringFunction;
import com.mongodb.hibernate.internal.dialect.function.MongoTrimFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayConstructorFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayContainsFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayIncludesFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoUnnestFunction;
import com.mongodb.hibernate.internal.translate.MongoTranslatorFactory;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
import com.mongodb.hibernate.internal.type.MongoArrayJdbcType;
import com.mongodb.hibernate.internal.type.MongoStructJdbcType;
import com.mongodb.hibernate.internal.type.ObjectIdJavaType;
import com.mongodb.hibernate.internal.type.ObjectIdJdbcType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.hibernate.JDBCException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.aggregate.spi.AggregateSupport;
import org.hibernate.dialect.array.spi.ArraySupport;
import org.hibernate.dialect.sql.ast.spi.OptionalTableUpdateOperationRequest;
import org.hibernate.dialect.sql.ast.spi.SqlAstTranslatorFactory;
import org.hibernate.dialect.temporaltype.spi.TemporalFormatSupport;
import org.hibernate.dialect.type.spi.StandardDdlTypes;
import org.hibernate.dialect.unique.spi.UniqueDelegate;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.env.spi.NameQualifierSupport;
import org.hibernate.engine.jdbc.mutation.JdbcValueBindings;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.jdbc.mutation.group.UnknownParameterException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.exception.spi.SQLExceptionConversionDelegate;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.sqm.produce.function.FunctionParameterType;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.spi.model.ColumnValueParameter;
import org.hibernate.sql.ast.spi.model.OptionalTableUpdate;
import org.hibernate.sql.spi.mutation.MutationOperation;
import org.hibernate.sql.spi.mutation.MutationTarget;
import org.hibernate.sql.spi.mutation.MutationType;
import org.hibernate.sql.spi.mutation.SelfExecutingUpdateOperation;
import org.hibernate.sql.spi.mutation.TableMapping;
import org.hibernate.sql.spi.mutation.ValuesAnalysis;
import org.hibernate.sql.spi.mutation.jdbc.JdbcValueDescriptor;
import org.hibernate.tool.schema.spi.Exporter;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.descriptor.jdbc.TimestampUtcAsInstantJdbcType;
import org.jspecify.annotations.Nullable;

/**
 * @hidden
 * @mongoCme Must be immutable, as per the documentation of {@link Dialect}. It is unclear whether it should be
 *     shallowly or deeply immutable; most likely—shallowly.
 */
@SuppressWarnings("MissingSummary")
public sealed class MongoDialect extends Dialect permits TestMongoDialect {
    private static final DatabaseVersion MINIMUM_DBMS_VERSION = DatabaseVersion.make(7);

    public MongoDialect(DialectResolutionInfo info) {
        super(info);
    }

    /**
     * This constructor is called only if Hibernate ORM falls back to it due to a failure of
     * {@link MongoDialect#MongoDialect(DialectResolutionInfo)}.
     *
     * @deprecated Exists only to avoid the confusing {@link NoSuchMethodException} thrown by Hibernate ORM when
     *     {@link MongoDialect#MongoDialect(DialectResolutionInfo)} fails.
     * @throws RuntimeException Always.
     */
    @Deprecated
    public MongoDialect() {
        super(MINIMUM_DBMS_VERSION);
        throw new RuntimeException(format(
                "Could not instantiate [%s], see the earlier exceptions to find out why",
                MongoDialect.class.getName()));
    }

    @Override
    protected DatabaseVersion getMinimumSupportedVersion() {
        return MINIMUM_DBMS_VERSION;
    }

    @Override
    public DatabaseVersion determineDatabaseVersion(DialectResolutionInfo info) {
        var version = info.makeCopyOrDefault(getMinimumSupportedVersion());
        var minimumVersion = getMinimumSupportedVersion();
        if (version.isBefore(minimumVersion)) {
            throw new RuntimeException(format(
                    "The minimum supported version of %s is %s, but you are using %s",
                    MONGO_DBMS_NAME, minimumVersion, version));
        }
        return version;
    }

    @Override
    public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
        return MongoTranslatorFactory.INSTANCE;
    }

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        contributeObjectIdType(typeContributions);
        typeContributions.contributeJdbcTypeConstructor(MongoArrayJdbcType.Constructor.INSTANCE);
        typeContributions.contributeJdbcType(MongoStructJdbcType.INSTANCE);
        contributeInstantType(typeContributions);
    }

    private void contributeObjectIdType(TypeContributions typeContributions) {
        typeContributions.contributeJavaType(ObjectIdJavaType.INSTANCE);
        typeContributions.contributeJdbcType(ObjectIdJdbcType.INSTANCE);
        var objectIdTypeCode = ObjectIdJdbcType.SQL_TYPE.getVendorTypeNumber();
        typeContributions
                .getTypeConfiguration()
                .getDdlTypeRegistry()
                .addDescriptorIfAbsent(StandardDdlTypes.simple(
                        objectIdTypeCode,
                        format(
                                "unused from %s.contributeObjectIdType for SQL type code [%d]",
                                MongoDialect.class.getSimpleName(), objectIdTypeCode),
                        this));
    }

    /**
     * This makes Hibernate ORM use {@link PreparedStatement#setObject(int, Object,
     * int)}/{@link ResultSet#getObject(int, Class)} instead of {@link PreparedStatement#setTimestamp(int, Timestamp,
     * Calendar)}/{@link ResultSet#getTimestamp(int, Calendar)} when storing/reading values of the {@link Instant} type,
     * without the need to rely on {@link AvailableSettings#JAVA_TIME_USE_DIRECT_JDBC}.
     */
    private static void contributeInstantType(TypeContributions typeContributions) {
        var jdbcTypeRegistry = typeContributions.getTypeConfiguration().getJdbcTypeRegistry();
        jdbcTypeRegistry.addDescriptor(SqlTypes.TIMESTAMP_UTC, TimestampUtcAsInstantJdbcType.INSTANCE);
    }

    @Override
    public @Nullable String toQuotedIdentifier(@Nullable String name) {
        return name;
    }

    /**
     * Report {@code SCHEMA} so Hibernate renders a schema-qualified table as {@code schema.name}, which the extension
     * treats as the collection name. Catalog is deliberately not reported; it is rejected at boot instead (see
     * {@code MongoAdditionalMappingContributor}).
     */
    @Override
    public NameQualifierSupport getNameQualifierSupport() {
        return NameQualifierSupport.SCHEMA;
    }

    @Override
    public AggregateSupport getAggregateSupport() {
        return MongoAggregateSupport.INSTANCE;
    }

    @Override
    public ArraySupport getArraySupport() {
        return ARRAY_SUPPORT;
    }

    private static final ArraySupport ARRAY_SUPPORT = ArraySupport.builder()
            .capabilities(ArraySupport.Capability.STANDARD_ARRAY)
            .build();

    // Hibernate 7's `AggregateComponentSecondPass` rejects `@Struct` mappings unless this returns `true`.
    // MQL supports embedded documents as user-defined struct types via `MongoStructJdbcType`.
    @Override
    public boolean supportsUserDefinedTypes() {
        return true;
    }

    /**
     *
     *
     * <table>
     *     <caption>Supported HQL functions</caption>
     *     <thead>
     *         <tr>
     *             <th>Name</th>
     *             <th>Notes</th>
     *         </tr>
     *     </thead>
     *     <tbody>
     *         <tr>
     *             <td>
     *                 <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-array-constructor-functions">
     *                     {@code array}, {@code array_list}</a>
     *             </td>
     *             <td>
     *                 Is allowed only in a
     *                 <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-where-clause">
     *                     {@code where} clause</a>.
     *             </td>
     *         </tr>
     *         <tr>
     *             <td>
     *                 <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-array-contains-functions">
     *                     {@code array_contains}, {@code array_contains_nullable}</a>
     *             </td>
     *             <td>
     *                 Is allowed only in a
     *                 <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-where-clause">
     *                     {@code where} clause</a>.
     *                 <ul>
     *                     <li>
     *                         The first argument must be an
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-path-expressions">HQL path expression</a>
     *                         (see also
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-case-sensitivity">HQL identifiers</a>
     *                         ), and not an
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-literals">HQL literal</a>
     *                         or any other
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-expressions">HQL expression</a>,
     *                         despite Hibernate ORM seemingly not having such a limitation.
     *                     </li>
     *                     <li>
     *                         The second argument must not be an HQL path expression.
     *                         It is unclear if Hibernate ORM intended them to be supported.
     *                     </li>
     *                     <li>
     *                         Is allowed only in a
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-where-clause">
     *                             {@code where} clause</a>.
     *                     </li>
     *                 </ul>
     *             </td>
     *         </tr>
     *         <tr>
     *             <td>
     *                 <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-array-includes-functions">
     *                     {@code array_includes}, {@code array_includes_nullable}</a>
     *             </td>
     *             <td>
     *                 Is allowed only in a
     *                 <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-where-clause">
     *                     {@code where} clause</a>.
     *                 <ul>
     *                     <li>
     *                         The first argument must be an
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-path-expressions">HQL path expression</a>
     *                         (see also
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-case-sensitivity">HQL identifiers</a>
     *                         ), and not an
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-literals">HQL literal</a>
     *                         or any other
     *                         <a href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-expressions">HQL expression</a>,
     *                         despite Hibernate ORM seemingly not having such a limitation.
     *                     </li>
     *                     <li>
     *                         The second argument must not be an HQL path expression.
     *                         Also, it must be an array and not be a {@link Collection} when specified as
     *                         {@linkplain org.hibernate.query.SelectionQuery#setParameter(String, Object) query parameter}.
     *                     </li>
     *                 </ul>
     *             </td>
     *         </tr>
     *     </tbody>
     * </table>
     */
    @Override
    public void initializeFunctionRegistry(FunctionContributions functionContributions) {
        super.initializeFunctionRegistry(functionContributions);
        var functionRegistry = functionContributions.getFunctionRegistry();
        var typeConfiguration = functionContributions.getTypeConfiguration();
        functionRegistry.register("array", new MongoArrayConstructorFunction(false));
        functionRegistry.register("array_contains", new MongoArrayContainsFunction(false, typeConfiguration));
        functionRegistry.register("array_contains_nullable", new MongoArrayContainsFunction(true, typeConfiguration));
        functionRegistry.register("array_includes", new MongoArrayIncludesFunction(false, typeConfiguration));
        functionRegistry.register("array_includes_nullable", new MongoArrayIncludesFunction(true, typeConfiguration));
        functionRegistry.register("array_list", new MongoArrayConstructorFunction(true));
        functionRegistry.register(
                "character_length",
                new MongoExpressionUnaryFunction(
                        "character_length",
                        "$strLenCP",
                        typeConfiguration,
                        StandardBasicTypes.INTEGER,
                        FunctionParameterType.STRING));
        functionRegistry.register(
                "concat",
                new MongoExpressionVariadicFunction(
                        "concat",
                        "$concat",
                        typeConfiguration,
                        StandardBasicTypes.STRING,
                        Function.identity(),
                        FunctionParameterType.ANY,
                        input -> new AstUnaryOperatorExpression("$toString", input)));
        functionRegistry.register("extract", new MongoExtractFunction(typeConfiguration));
        functionRegistry.register(
                "format",
                new MongoExpressionNamedFunction(
                        "format",
                        "$dateToString",
                        typeConfiguration,
                        Map.of(
                                "timezone",
                                new AstLiteralExpression(new AstLiteral(
                                        new BsonString(ZoneId.systemDefault().getId())))),
                        StandardBasicTypes.STRING,
                        Function.identity(),
                        required("date", FunctionParameterType.TEMPORAL),
                        required("format", FunctionParameterType.STRING)));
        functionRegistry.register(
                "locate",
                new MongoExpressionPositionalFunction(
                        "locate",
                        "$indexOfCP",
                        typeConfiguration,
                        StandardBasicTypes.INTEGER,
                        FunctionParameterDefinition::addOne,
                        swap(0, 1),
                        required(FunctionParameterType.STRING),
                        required(FunctionParameterType.STRING),
                        orMissing(FunctionParameterType.INTEGER)
                                .map(FunctionParameterDefinition::subtractOne)
                                .map(FunctionParameterDefinition::atLeastZero)));
        functionRegistry.register(
                "lower",
                new MongoExpressionUnaryFunction(
                        "lower",
                        "$toLower",
                        typeConfiguration,
                        StandardBasicTypes.STRING,
                        FunctionParameterType.STRING));
        functionRegistry.register("lpad", new MongoPadFunction(typeConfiguration, true));
        functionRegistry.register("repeat", new MongoRepeatFunction(typeConfiguration));
        functionRegistry.register(
                "replace",
                new MongoExpressionNamedFunction(
                        "replace",
                        "$replaceAll",
                        typeConfiguration,
                        StandardBasicTypes.STRING,
                        required("input", FunctionParameterType.STRING),
                        required("find", FunctionParameterType.STRING),
                        required("replacement", FunctionParameterType.STRING)));
        functionRegistry.register("rpad", new MongoPadFunction(typeConfiguration, false));
        functionRegistry.register("substring", new MongoSubstringFunction(typeConfiguration));
        functionRegistry.register("trim", new MongoTrimFunction(typeConfiguration));
        functionRegistry.register("unnest", new MongoUnnestFunction());
        functionRegistry.register(
                "upper",
                new MongoExpressionUnaryFunction(
                        "upper",
                        "$toUpper",
                        typeConfiguration,
                        StandardBasicTypes.STRING,
                        FunctionParameterType.STRING));
        functionRegistry.registerAlternateKey("char_length", "character_length");
        functionRegistry.registerAlternateKey("length", "character_length");
    }

    /**
     * @mongoCme The {@link MutationOperation} returned from this method is created once per entity at SessionFactory
     *     build time and shared across sessions, so it must be thread-safe.
     */
    @Override
    public MutationOperation createOptionalTableUpdateOperation(OptionalTableUpdateOperationRequest request) {
        var optionalTableUpdate = request.update();
        var mutationTarget = request.mutationTarget();
        // This runs at SessionFactory build time for every entity, so nothing here may throw:
        // rejections are deferred to performMutation, which runs only when upsert is called.
        // In particular, an optional (e.g. @SecondaryTable) mutating table would otherwise make
        // visitOptionalTableUpdate throw eagerly during this same boot-time call.
        if (optionalTableUpdate.getMutatingTable().getTableMapping().isOptional()) {
            return rejectingUpsertOperation(
                    mutationTarget,
                    optionalTableUpdate,
                    "TODO-HIBERNATE-69 https://jira.mongodb.org/browse/HIBERNATE-69");
        }
        if (optionalTableUpdate.getValueBindings().isEmpty()) {
            // TableMergeBuilder always builds an OptionalTableUpdate, so an entity whose only persistent
            // attribute is its identifier reaches this boot-time translation with nothing to write.
            return rejectingUpsertOperation(
                    mutationTarget,
                    optionalTableUpdate,
                    format(
                            "%s does not support upserting an entity whose only persistent attribute is its identifier",
                            MONGO_DBMS_NAME));
        }
        if (optionalTableUpdate.getNumberOfOptimisticLockBindings() > 0) {
            return rejectingUpsertOperation(
                    mutationTarget,
                    optionalTableUpdate,
                    "TODO-HIBERNATE-216 https://jira.mongodb.org/browse/HIBERNATE-216");
        }
        for (var valueBinding : optionalTableUpdate.getValueBindings()) {
            if (!valueBinding.isAttributeInsertable()) {
                return rejectingUpsertOperation(
                        mutationTarget,
                        optionalTableUpdate,
                        format(
                                "%s does not support upserting a column that is updatable but not insertable: [%s]",
                                MONGO_DBMS_NAME,
                                valueBinding.getColumnReference().getColumnExpression()));
            }
        }
        return MongoTranslatorFactory.INSTANCE
                .buildUpsertModelMutationTranslator(optionalTableUpdate, request.sessionFactory())
                .translate(null, QueryOptions.NONE);
    }

    private static MutationOperation rejectingUpsertOperation(
            MutationTarget mutationTarget, OptionalTableUpdate optionalTableUpdate, String message) {
        return new RejectingUpsertOperation(mutationTarget, optionalTableUpdate, message);
    }

    private static final class RejectingUpsertOperation implements SelfExecutingUpdateOperation {
        private final MutationTarget mutationTarget;
        private final TableMapping tableMapping;
        private final String message;
        private final List<JdbcValueDescriptor> jdbcValueDescriptors;

        RejectingUpsertOperation(
                MutationTarget mutationTarget, OptionalTableUpdate optionalTableUpdate, String message) {
            this.mutationTarget = mutationTarget;
            this.tableMapping = optionalTableUpdate.getMutatingTable().getTableMapping();
            this.message = message;
            var parameters = optionalTableUpdate.getParameters();
            this.jdbcValueDescriptors = new ArrayList<>(parameters.size());
            for (var parameter : parameters) {
                jdbcValueDescriptors.add(new ParameterValueDescriptor(parameter, jdbcValueDescriptors.size() + 1));
            }
        }

        @Override
        public MutationType getMutationType() {
            return MutationType.UPDATE;
        }

        @Override
        public MutationTarget getMutationTarget() {
            return mutationTarget;
        }

        @Override
        public TableMapping getTableDetails() {
            return tableMapping;
        }

        private record ParameterValueDescriptor(ColumnValueParameter parameter, int jdbcPosition)
                implements JdbcValueDescriptor {

            @Override
            public String getColumnName() {
                return parameter.getColumnReference().getColumnExpression();
            }

            @Override
            public ParameterUsage getUsage() {
                return parameter.getUsage();
            }

            @Override
            public int getJdbcPosition() {
                return jdbcPosition;
            }

            @Override
            public JdbcMapping getJdbcMapping() {
                return parameter.getJdbcMapping();
            }
        }

        @Override
        public JdbcValueDescriptor findValueDescriptor(String columnName, ParameterUsage usage) {
            for (var descriptor : jdbcValueDescriptors) {
                if (descriptor.getColumnName().equals(columnName) && descriptor.getUsage() == usage) {
                    return descriptor;
                }
            }
            throw new UnknownParameterException(
                    getMutationType(), getMutationTarget(), tableMapping.getTableName(), columnName, usage);
        }

        @Override
        public void performMutation(
                JdbcValueBindings jdbcValueBindings,
                ValuesAnalysis valuesAnalysis,
                SharedSessionContractImplementor session) {
            throw new FeatureNotSupportedException(message);
        }
    }

    @Override
    public TemporalFormatSupport getTemporalFormatSupport() {
        return (appender, format) -> {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-88 https://jira.mongodb.org/browse/HIBERNATE-88");
        };
    }

    /** @mongoCme The {@link SQLExceptionConversionDelegate} returned from this method must be thread-safe. */
    @Override
    public SQLExceptionConversionDelegate buildSQLExceptionConversionDelegate() {
        return (sqlException, exceptionMessage, mql) -> new JDBCException(exceptionMessage, sqlException, mql);
    }

    @Override
    public Exporter<Table> getTableExporter() {
        return new Exporter<>() {
            @Override
            public String[] getSqlCreateStrings(
                    Table exportable, Metadata metadata, SqlStringGenerationContext context) {
                return new String[] {
                    new BsonDocument("create", new BsonString(context.format(exportable.getQualifiedTableName())))
                            .toJson(MongoConstants.EXTENDED_JSON_WRITER_SETTINGS)
                };
            }

            @Override
            public String[] getSqlDropStrings(Table exportable, Metadata metadata, SqlStringGenerationContext context) {
                return new String[] {
                    new BsonDocument("drop", new BsonString(context.format(exportable.getQualifiedTableName())))
                            .toJson(MongoConstants.EXTENDED_JSON_WRITER_SETTINGS)
                };
            }
        };
    }

    @Override
    public Exporter<Index> getIndexExporter() {
        return new MongoIndexExporter<>(false) {

            @Override
            protected Table tableForExportable(Index exportable) {
                return exportable.getTable();
            }

            @Override
            protected String indexNameForExportable(Index exportable) {
                return exportable.getName();
            }

            @Override
            protected Stream<IndexEntry> indexEntriesForExportable(Index exportable) {
                return exportable.getSelectables().stream().map(selectable -> {
                    if (selectable.isFormula()) {
                        throw new FeatureNotSupportedException(
                                "Index %s on %s uses a formula column, which is not supported"
                                        .formatted(
                                                exportable.getName(),
                                                exportable.getTable().getName()));
                    }
                    return new IndexEntry(
                            selectable.getText(),
                            exportable.getSelectableOrderMap().getOrDefault(selectable, ""));
                });
            }

            @Override
            protected String optionsForExportable(Index exportable) {
                return exportable.getOptions();
            }
        };
    }

    @Override
    public UniqueDelegate getUniqueDelegate() {
        return MONGO_UNIQUE_DELEGATE;
    }

    private static final UniqueDelegate MONGO_UNIQUE_DELEGATE = new UniqueDelegate() {
        private final Exporter<UniqueKey> exporter = new MongoIndexExporter<>(true) {
            @Override
            protected Table tableForExportable(UniqueKey exportable) {
                return exportable.getTable();
            }

            @Override
            protected String indexNameForExportable(UniqueKey exportable) {
                return exportable.getName();
            }

            @Override
            protected Stream<IndexEntry> indexEntriesForExportable(UniqueKey exportable) {
                return exportable.getColumns().stream()
                        .map(column -> new IndexEntry(
                                column.getName(), exportable.getColumnOrderMap().getOrDefault(column, "")));
            }

            @Override
            protected String optionsForExportable(UniqueKey exportable) {
                return exportable.getOptions();
            }
        };

        @Override
        public String getColumnDefinitionUniquenessFragment(Column column, SqlStringGenerationContext context) {
            return "";
        }

        @Override
        public String getTableCreationUniqueConstraintsFragment(Table table, SqlStringGenerationContext context) {
            return "";
        }

        @Override
        public String getAlterTableToAddUniqueKeyCommand(
                UniqueKey uniqueKey, Metadata metadata, SqlStringGenerationContext context) {
            return String.join(";", exporter.getSqlCreateStrings(uniqueKey, metadata, context));
        }

        @Override
        public String getAlterTableToDropUniqueKeyCommand(
                UniqueKey uniqueKey, Metadata metadata, SqlStringGenerationContext context) {
            return String.join(";", exporter.getSqlDropStrings(uniqueKey, metadata, context));
        }
    };
}
