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

import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayConstructorFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayContainsFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoArrayIncludesFunction;
import com.mongodb.hibernate.internal.translate.MongoTranslatorFactory;
import com.mongodb.hibernate.internal.type.MongoArrayJdbcType;
import com.mongodb.hibernate.internal.type.MongoStructJdbcType;
import com.mongodb.hibernate.internal.type.MqlType;
import com.mongodb.hibernate.internal.type.ObjectIdJavaType;
import com.mongodb.hibernate.internal.type.ObjectIdJdbcType;
import com.mongodb.hibernate.jdbc.MongoConnectionProvider;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.aggregate.AggregateSupport;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;
import org.jspecify.annotations.Nullable;

/**
 * A MongoDB {@link Dialect} for {@linkplain #getMinimumSupportedVersion() version 6.0 and above}. Must be used together
 * with {@link MongoConnectionProvider}.
 *
 * <p>Usually Hibernate dialect represents some SQL RDBMS and speaks SQL with vendor-specific difference. MongoDB is a
 * document DB and speaks <i>MQL</i> (MongoDB Query Language), but it is still possible to integrate with Hibernate by
 * creating a JDBC adaptor on top of <a href="https://www.mongodb.com/docs/drivers/java/sync/current/">MongoDB Java
 * Driver</a>.
 *
 * <p>For the documentation on the supported <a
 * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-exp-functions">HQL
 * functions</a> see {@link #initializeFunctionRegistry(FunctionContributions)}.
 */
public final class MongoDialect extends Dialect {
    private static final DatabaseVersion MINIMUM_VERSION = DatabaseVersion.make(6);

    /**
     * Constructs a {@link MongoDialect} using the given {@link DialectResolutionInfo}.
     *
     * <p>This constructor is used by Hibernate to initialize the dialect with database version and connection details.
     *
     * @param info the dialect resolution information provided by Hibernate
     */
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
    }

    private void contributeObjectIdType(TypeContributions typeContributions) {
        typeContributions.contributeJavaType(ObjectIdJavaType.INSTANCE);
        typeContributions.contributeJdbcType(ObjectIdJdbcType.INSTANCE);
        var objectIdTypeCode = MqlType.OBJECT_ID.getVendorTypeNumber();
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
     *                         Also, it must be an array and not be a {@link java.util.Collection} when specified as
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
}
