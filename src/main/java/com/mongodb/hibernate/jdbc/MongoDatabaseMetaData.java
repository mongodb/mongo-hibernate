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

package com.mongodb.hibernate.jdbc;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;

import com.mongodb.hibernate.BuildConfig;
import com.mongodb.hibernate.internal.VisibleForTesting;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.StringTokenizer;

/**
 * MongoDB Dialect's JDBC {@link java.sql.DatabaseMetaData} implementation class.
 *
 * <p>It only focuses on API methods MongoDB Dialect will support. All the other methods are implemented by throwing
 * exceptions in its parent {@link DatabaseMetaDataAdapter adapter interface}.
 */
final class MongoDatabaseMetaData implements DatabaseMetaDataAdapter {

    public static final String MONGO_DATABASE_PRODUCT_NAME = "MongoDB";
    public static final String MONGO_JDBC_DRIVER_NAME = "MongoDB Java Driver JDBC Adapter";

    @VisibleForTesting(otherwise = PRIVATE)
    record VersionNumPair(int majorVersion, int minVersion) {}

    private final Connection connection;

    private final String databaseVersionText;
    private final int databaseMajorVersion;
    private final int databaseMinorVersion;

    private final VersionNumPair versionNumPair;

    MongoDatabaseMetaData(
            Connection connection, String databaseVersionText, int databaseMajorVersion, int databaseMinorVersion) {
        this.connection = connection;
        this.databaseVersionText = databaseVersionText;
        this.databaseMajorVersion = databaseMajorVersion;
        this.databaseMinorVersion = databaseMinorVersion;
        versionNumPair = parseVersionNumPair(assertNotNull(BuildConfig.VERSION));
    }

    @Override
    public String getDatabaseProductName() {
        return MONGO_DATABASE_PRODUCT_NAME;
    }

    @Override
    public String getDatabaseProductVersion() {
        return databaseVersionText;
    }

    @Override
    public String getDriverName() {
        return MONGO_JDBC_DRIVER_NAME;
    }

    @Override
    public String getDriverVersion() {
        return BuildConfig.VERSION;
    }

    @Override
    public int getDriverMajorVersion() {
        return versionNumPair.majorVersion;
    }

    @Override
    public int getDriverMinorVersion() {
        return versionNumPair.minVersion;
    }

    @Override
    public String getSQLKeywords() {
        return "";
    }

    @Override
    public boolean isCatalogAtStart() {
        return true;
    }

    @Override
    public String getCatalogSeparator() {
        return ".";
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() {
        return false;
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() {
        return false;
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() {
        return false;
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() {
        return false;
    }

    @Override
    public boolean supportsResultSetType(int type) {
        return type == ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public boolean supportsBatchUpdates() {
        return true;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public boolean supportsNamedParameters() {
        return false;
    }

    @Override
    public boolean supportsGetGeneratedKeys() {
        return false;
    }

    @Override
    public int getDatabaseMajorVersion() {
        return databaseMajorVersion;
    }

    @Override
    public int getDatabaseMinorVersion() {
        return databaseMinorVersion;
    }

    @Override
    public int getJDBCMajorVersion() {
        return 4;
    }

    @Override
    public int getJDBCMinorVersion() {
        return 3;
    }

    @Override
    public int getSQLStateType() {
        return DatabaseMetaData.sqlStateSQL;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    @VisibleForTesting(otherwise = PRIVATE)
    static VersionNumPair parseVersionNumPair(String versionText) {
        int majorVersion = 0;
        int minVersion = 0;
        var tokenizer = new StringTokenizer(versionText, ".");
        if (tokenizer.hasMoreTokens()) {
            try {
                majorVersion = Integer.parseInt(tokenizer.nextToken());
                if (tokenizer.hasMoreTokens()) {
                    minVersion = Integer.parseInt(tokenizer.nextToken());
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new VersionNumPair(majorVersion, minVersion);
    }
}
