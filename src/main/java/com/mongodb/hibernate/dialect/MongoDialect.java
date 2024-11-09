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

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.jspecify.annotations.NullMarked;

/**
 * A MongoDB {@link Dialect} for {@linkplain #getMinimumSupportedVersion() version 6.0 and above}.
 *
 * <p>Usually Hibernate dialect represents some SQL RDBMS and speaks SQL with vendor-specific difference. MongoDB is a
 * document DB and speaks <i>MQL</i> (MongoDB Query Language), but it is still possible to integrate with Hibernate by
 * creating a JDBC adaptor on top of MongoDB's Java Driver.
 */
@NullMarked
public final class MongoDialect extends Dialect {
    public static final int MINIMUM_MONGODB_MAJOR_VERSION_SUPPORTED = 6;
    public static final int MINIMUM_MONGODB_MINOR_VERSION_SUPPORTED = 0;

    private static final DatabaseVersion MINIMUM_VERSION =
            DatabaseVersion.make(MINIMUM_MONGODB_MAJOR_VERSION_SUPPORTED, MINIMUM_MONGODB_MINOR_VERSION_SUPPORTED);

    /** Default constructor used when no version info is available. */
    public MongoDialect() {
        super((DatabaseVersion) null);
    }

    /**
     * Constructor used when MongoDB meta data is available.
     *
     * @param info MongoDB meta data
     */
    public MongoDialect(DialectResolutionInfo info) {
        super(info);
    }

    @Override
    protected DatabaseVersion getMinimumSupportedVersion() {
        return MINIMUM_VERSION;
    }
}