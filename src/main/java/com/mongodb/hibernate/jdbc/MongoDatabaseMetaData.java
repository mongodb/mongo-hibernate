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
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_JDBC_DRIVER_NAME;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;

import com.mongodb.hibernate.internal.BuildConfig;
import com.mongodb.hibernate.internal.VisibleForTesting;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

final class MongoDatabaseMetaData implements DatabaseMetaDataAdapter {

    private final Connection connection;
    private final Version dbmsVersion;
    private final Version driverVersion;

    @VisibleForTesting(otherwise = PRIVATE)
    MongoDatabaseMetaData(
            Connection connection,
            String dbmsVersionText,
            int dbmsMajorVersion,
            int dbmsMinorVersion,
            String driverVersionText) {
        this.connection = connection;
        dbmsVersion = new Version(dbmsVersionText, dbmsMajorVersion, dbmsMinorVersion);
        driverVersion = Version.parse(driverVersionText);
    }

    MongoDatabaseMetaData(Connection connection, String dbmsVersionText, int dbmsMajorVersion, int dbmsMinorVersion) {
        this(connection, dbmsVersionText, dbmsMajorVersion, dbmsMinorVersion, assertNotNull(BuildConfig.VERSION));
    }

    @Override
    public String getDatabaseProductName() {
        return MONGO_DBMS_NAME;
    }

    @Override
    public String getDatabaseProductVersion() {
        return dbmsVersion.versionText;
    }

    @Override
    public String getDriverName() {
        return MONGO_JDBC_DRIVER_NAME;
    }

    @Override
    public String getDriverVersion() {
        return driverVersion.versionText;
    }

    @Override
    public int getDriverMajorVersion() {
        return driverVersion.major;
    }

    @Override
    public int getDriverMinorVersion() {
        return driverVersion.minor;
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
        return dbmsVersion.major;
    }

    @Override
    public int getDatabaseMinorVersion() {
        return dbmsVersion.minor;
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

    private record Version(String versionText, int major, int minor) {
        static Version parse(String versionText) {
            String[] parts = versionText.split("[-.]", 3);
            assertTrue(parts.length >= 2);
            try {
                return new Version(versionText, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                throw fail(e.toString());
            }
        }
    }
}
