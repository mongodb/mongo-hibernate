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

import com.mongodb.hibernate.internal.translate.MongoTranslatorFactory;
import com.mongodb.hibernate.jdbc.MongoConnectionProvider;
import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;

/**
 * A MongoDB {@link Dialect} for {@linkplain #getMinimumSupportedVersion() version 6.0 and above}. Must be used together
 * with {@link MongoConnectionProvider}.
 *
 * <p>Usually Hibernate dialect represents some SQL RDBMS and speaks SQL with vendor-specific difference. MongoDB is a
 * document DB and speaks <i>MQL</i> (MongoDB Query Language), but it is still possible to integrate with Hibernate by
 * creating a JDBC adaptor on top of <a href="https://www.mongodb.com/docs/drivers/java/sync/current/">MongoDB Java
 * Driver</a>.
 */
public final class MongoDialect extends Dialect {
    private static final DatabaseVersion MINIMUM_VERSION = DatabaseVersion.make(6);

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
}
