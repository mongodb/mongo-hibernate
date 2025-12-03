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

package com.mongodb.hibernate.dialect;

import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.Sealed;
import com.mongodb.hibernate.internal.dialect.MongoAggregateSupport;
import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayConstructorFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayContainsFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayIncludesFunction;
import com.mongodb.hibernate.internal.translate.MongoTranslatorFactory;
import com.mongodb.hibernate.internal.type.MongoArrayJdbcType;
import com.mongodb.hibernate.internal.type.MongoStructJdbcType;
import com.mongodb.hibernate.internal.type.ObjectIdJavaType;
import com.mongodb.hibernate.internal.type.ObjectIdJdbcType;
import com.mongodb.hibernate.jdbc.MongoConnectionProvider;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collection;
import org.bson.types.ObjectId;
import org.hibernate.JDBCException;
import org.hibernate.annotations.Struct;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.aggregate.AggregateSupport;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.mutation.JdbcValueBindings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.exception.spi.SQLExceptionConversionDelegate;
import org.hibernate.persister.entity.mutation.EntityMutationTarget;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.model.MutationOperation;
import org.hibernate.sql.model.ValuesAnalysis;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.jdbc.OptionalTableUpdateOperation;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.WrapperArrayHandling;
import org.hibernate.type.descriptor.jdbc.TimestampUtcAsInstantJdbcType;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.jspecify.annotations.Nullable;

/**
 * A MongoDB {@link Dialect} for {@linkplain #getMinimumSupportedVersion() version 7.0 and above}. Must be used together
 * with {@link MongoConnectionProvider}.
 *
 * <p>Usually Hibernate dialect represents some SQL RDBMS and speaks SQL with vendor-specific difference. MongoDB is a
 * document DB and speaks <i>MQL</i> (MongoDB Query Language), but it is still possible to integrate with Hibernate by
 * creating a JDBC adaptor on top of <a href="https://www.mongodb.com/docs/drivers/java/sync/current/">MongoDB Java
 * Driver</a>.
 *
 * <table>
 *     <caption>Default type mapping</caption>
 *     <thead>
 *         <tr>
 *             <th>Java type</th>
 *             <th><a href="https://www.mongodb.com/docs/manual/reference/bson-types/">BSON type</a></th>
 *         </tr>
 *     </thead>
 *     <tbody>
 *         <tr>
 *             <td><a href="https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.1">null type</a></td>
 *             <td>BSON {@code Null}</td>
 *         </tr>
 *         <tr>
 *             <td>{@code byte[]}</td>
 *             <td>BSON {@code Binary data} with subtype 0</td>
 *         </tr>
 *         <tr>
 *             <td>{@code char}, {@link Character}, {@link String}, {@code char[]}</td>
 *             <td>BSON {@code String}</td>
 *         </tr>
 *         <tr>
 *             <td>{@code int}, {@link Integer}</td>
 *             <td>BSON {@code 32-bit integer}</td>
 *         </tr>
 *         <tr>
 *             <td>{@code long}, {@link Long}</td>
 *             <td>BSON {@code 64-bit integer}</td>
 *         </tr>
 *         <tr>
 *             <td>{@code double}, {@link Double}</td>
 *             <td>BSON {@code Double}</td>
 *         </tr>
 *         <tr>
 *             <td>{@code boolean}, {@link Boolean}</td>
 *             <td>BSON {@code Boolean}</td>
 *         </tr>
 *         <tr>
 *             <td>{@link BigDecimal}</td>
 *             <td>BSON {@code Decimal128}</td>
 *         </tr>
 *         <tr>
 *             <td>{@link ObjectId}</td>
 *             <td>BSON {@code ObjectId}</td>
 *         </tr>
 *         <tr>
 *             <td>{@link Instant}</td>
 *             <td>BSON {@code Date}</td>
 *         </tr>
 *         <tr>
 *             <td>{@link Struct} <a href="https://docs.hibernate.org/orm/6.6/userguide/html_single/#embeddable-mapping-aggregate">aggregate embeddable</a></td>
 *             <td>
 *                 BSON {@code Object}
 *
 *                 <p>Field values are mapped as per this table.
 *             </td>
 *         </tr>
 *         <tr>
 *             <td>
 *                 array, {@link Collection} (or a subtype supported by Hibernate ORM),
 *                 except for {@code byte[]}, {@code char[]}
 *
 *                 <p>Note that {@link Character}{@code []} requires setting {@value AvailableSettings#WRAPPER_ARRAY_HANDLING} to {@link WrapperArrayHandling#ALLOW}.
 *             </td>
 *             <td>
 *                 BSON {@code Array}
 *
 *                 <p>Array elements are mapped as per this table.
 *             </td>
 *         </tr>
 *     </tbody>
 * </table>
 *
 * <p>For the documentation on the supported <a
 * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-exp-functions">HQL
 * functions</a> see {@link #initializeFunctionRegistry(FunctionContributions)}.
 */
@Sealed
public class MongoDialect extends Dialect {
    private static final DatabaseVersion MINIMUM_VERSION = DatabaseVersion.make(7);

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
    @Deprecated()
    public MongoDialect() {
        throw new RuntimeException(format(
                "Could not instantiate [%s], see the earlier exceptions to find out why",
                MongoDialect.class.getName()));
    }

    @Override
    protected DatabaseVersion getMinimumSupportedVersion() {
        return MINIMUM_VERSION;
    }

    @Override
    protected void checkVersion() {
        var version = getVersion();
        if (version == null) {
            return;
        }
        var minimumVersion = getMinimumSupportedVersion();
        if (version.isBefore(minimumVersion)) {
            throw new RuntimeException(format(
                    "The minimum supported version of %s is %s, but you are using %s",
                    MONGO_DBMS_NAME, minimumVersion, version));
        }
    }

    @Override
    public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
        return new MongoTranslatorFactory();
    }

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contribute(typeContributions, serviceRegistry);
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
                .addDescriptorIfAbsent(new DdlTypeImpl(
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

    @Override
    public AggregateSupport getAggregateSupport() {
        return MongoAggregateSupport.INSTANCE;
    }

    @Override
    public boolean supportsStandardArrays() {
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
        functionRegistry.register("array_list", new MongoArrayConstructorFunction(true));
        functionRegistry.register("array_contains", new MongoArrayContainsFunction(false, typeConfiguration));
        functionRegistry.register("array_contains_nullable", new MongoArrayContainsFunction(true, typeConfiguration));
        functionRegistry.register("array_includes", new MongoArrayIncludesFunction(false, typeConfiguration));
        functionRegistry.register("array_includes_nullable", new MongoArrayIncludesFunction(true, typeConfiguration));
    }

    @Override
    public MutationOperation createOptionalTableUpdateOperation(
            EntityMutationTarget mutationTarget,
            OptionalTableUpdate optionalTableUpdate,
            SessionFactoryImplementor factory) {
        return new OptionalTableUpdateOperation(mutationTarget, optionalTableUpdate, factory) {
            @Override
            public void performMutation(
                    JdbcValueBindings jdbcValueBindings,
                    ValuesAnalysis valuesAnalysis,
                    SharedSessionContractImplementor session) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-94 https://jira.mongodb.org/browse/HIBERNATE-94");
            }
        };
    }

    @Override
    public void appendDatetimeFormat(SqlAppender appender, String format) {
        throw new FeatureNotSupportedException("TODO-HIBERNATE-88 https://jira.mongodb.org/browse/HIBERNATE-88");
    }

    @Override
    public SQLExceptionConversionDelegate buildSQLExceptionConversionDelegate() {
        return (sqlException, exceptionMessage, mql) -> new JDBCException(exceptionMessage, sqlException, mql);
    }
}
