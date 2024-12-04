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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * A {@link java.sql.DatabaseMetaData} implementation class that throws exceptions for all its API methods.
 *
 * @see MongoDatabaseMetaData
 */
abstract class DatabaseMetaDataAdapter implements DatabaseMetaData {

    @Override
    public <T> T unwrap(Class<T> unwrapType) throws SQLException {
        throw new SQLFeatureNotSupportedException("unwrap not implemented");
    }

    @Override
    public boolean isWrapperFor(Class<?> unwrapType) throws SQLException {
        throw new SQLFeatureNotSupportedException("isWrapperFor not implemented");
    }

    // ----------------------------------------------------------------------
    // First, a variety of minor information about the target database.

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        throw new SQLFeatureNotSupportedException("allProceduresAreCallable not implemented");
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        throw new SQLFeatureNotSupportedException("allTablesAreSelectable not implemented");
    }

    @Override
    public String getURL() throws SQLException {
        throw new SQLFeatureNotSupportedException("getURL not implemented");
    }

    @Override
    public String getUserName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getUserName not implemented");
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        throw new SQLFeatureNotSupportedException("isReadOnly not implemented");
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedHigh not implemented");
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedLow not implemented");
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedAtStart not implemented");
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedAtEnd not implemented");
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseProductName not implemented");
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseProductVersion not implemented");
    }

    @Override
    public String getDriverName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDriverName not implemented");
    }

    @Override
    public String getDriverVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDriverVersion not implemented");
    }

    @Override
    public int getDriverMajorVersion() {
        throw new RuntimeException("getDriverMajorVersion not implemented");
    }

    @Override
    public int getDriverMinorVersion() {
        throw new RuntimeException("getDriverMinorVersion not implemented");
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        throw new SQLFeatureNotSupportedException("usesLocalFiles not implemented");
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        throw new SQLFeatureNotSupportedException("usesLocalFilePerTable not implemented");
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMixedCaseIdentifiers not implemented");
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesUpperCaseIdentifiers not implemented");
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesLowerCaseIdentifiers not implemented");
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesMixedCaseIdentifiers not implemented");
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMixedCaseQuotedIdentifiers not implemented");
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesUpperCaseQuotedIdentifiers not implemented");
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesLowerCaseQuotedIdentifiers not implemented");
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesMixedCaseQuotedIdentifiers not implemented");
    }

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        throw new SQLFeatureNotSupportedException("getIdentifierQuoteString not implemented");
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSQLKeywords not implemented");
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        throw new SQLFeatureNotSupportedException("getNumericFunctions not implemented");
    }

    @Override
    public String getStringFunctions() throws SQLException {
        throw new SQLFeatureNotSupportedException("getStringFunctions not implemented");
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSystemFunctions not implemented");
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTimeDateFunctions not implemented");
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSearchStringEscape not implemented");
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        throw new SQLFeatureNotSupportedException("getExtraNameCharacters not implemented");
    }

    // --------------------------------------------------------------------
    // Functions describing which features are supported.

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsAlterTableWithAddColumn not implemented");
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsAlterTableWithDropColumn not implemented");
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsColumnAliasing not implemented");
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullPlusNonNullIsNull not implemented");
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsConvert not implemented");
    }

    @Override
    public boolean supportsConvert(int fromType, int toType) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsConvert not implemented");
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsTableCorrelationNames not implemented");
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsDifferentTableCorrelationNames not implemented");
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsExpressionsInOrderBy not implemented");
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOrderByUnrelated not implemented");
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupBy not implemented");
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupByUnrelated not implemented");
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupByBeyondSelect not implemented");
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsLikeEscapeClause not implemented");
    }

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleResultSets not implemented");
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleTransactions not implemented");
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsNonNullableColumns not implemented");
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMinimumSQLGrammar not implemented");
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCoreSQLGrammar not implemented");
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsExtendedSQLGrammar not implemented");
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92EntryLevelSQL not implemented");
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92IntermediateSQL not implemented");
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92FullSQL not implemented");
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsIntegrityEnhancementFacility not implemented");
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOuterJoins not implemented");
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsFullOuterJoins not implemented");
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsLimitedOuterJoins not implemented");
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSchemaTerm not implemented");
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureTerm not implemented");
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogTerm not implemented");
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        throw new SQLFeatureNotSupportedException("isCatalogAtStart not implemented");
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogSeparator not implemented");
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInDataManipulation not implemented");
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInProcedureCalls not implemented");
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInTableDefinitions not implemented");
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInIndexDefinitions not implemented");
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInPrivilegeDefinitions not implemented");
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInDataManipulation not implemented");
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInProcedureCalls not implemented");
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInTableDefinitions not implemented");
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInIndexDefinitions not implemented");
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInPrivilegeDefinitions not implemented");
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsPositionedDelete not implemented");
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsPositionedUpdate not implemented");
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSelectForUpdate not implemented");
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsStoredProcedures not implemented");
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInComparisons not implemented");
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInExists not implemented");
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInIns not implemented");
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInQuantifieds not implemented");
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCorrelatedSubqueries not implemented");
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsUnion not implemented");
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsUnionAll not implemented");
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenCursorsAcrossCommit not implemented");
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenCursorsAcrossRollback not implemented");
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenStatementsAcrossCommit not implemented");
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenStatementsAcrossRollback not implemented");
    }

    // ----------------------------------------------------------------------
    // The following group of methods exposes various limitations
    // based on the target database with the current driver.
    // Unless otherwise specified, a result of zero means there is no
    // limit, or the limit is not known.

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxBinaryLiteralLength not implemented");
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCharLiteralLength not implemented");
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnNameLength not implemented");
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInGroupBy not implemented");
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInIndex not implemented");
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInOrderBy not implemented");
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInSelect not implemented");
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInTable not implemented");
    }

    @Override
    public int getMaxConnections() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxConnections not implemented");
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCursorNameLength not implemented");
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxIndexLength not implemented");
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxSchemaNameLength not implemented");
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxProcedureNameLength not implemented");
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCatalogNameLength not implemented");
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxRowSize not implemented");
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        throw new SQLFeatureNotSupportedException("doesMaxRowSizeIncludeBlobs not implemented");
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxStatementLength not implemented");
    }

    @Override
    public int getMaxStatements() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxStatements not implemented");
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxTableNameLength not implemented");
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxTablesInSelect not implemented");
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxUserNameLength not implemented");
    }

    // ----------------------------------------------------------------------

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDefaultTransactionIsolation not implemented");
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsTransactions not implemented");
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsTransactionIsolationLevel not implemented");
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        throw new SQLFeatureNotSupportedException(
                "supportsDataDefinitionAndDataManipulationTransactions not implemented");
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsDataManipulationTransactionsOnly not implemented");
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        throw new SQLFeatureNotSupportedException("dataDefinitionCausesTransactionCommit not implemented");
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        throw new SQLFeatureNotSupportedException("dataDefinitionIgnoredInTransactions not implemented");
    }

    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedures not implemented");
    }

    @Override
    public ResultSet getProcedureColumns(
            String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureColumns not implemented");
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getTables not implemented");
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSchemas not implemented");
    }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogs not implemented");
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTableTypes not implemented");
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumns not implemented");
    }

    @Override
    public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnPrivileges not implemented");
    }

    @Override
    public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getTablePrivileges not implemented");
    }

    @Override
    public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getBestRowIdentifier not implemented");
    }

    @Override
    public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getVersionColumns not implemented");
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getPrimaryKeys not implemented");
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getImportedKeys not implemented");
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getExportedKeys not implemented");
    }

    @Override
    public ResultSet getCrossReference(
            String parentCatalog,
            String parentSchema,
            String parentTable,
            String foreignCatalog,
            String foreignSchema,
            String foreignTable)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getCrossReference not implemented");
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTypeInfo not implemented");
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getIndexInfo not implemented");
    }

    @Override
    public boolean supportsResultSetType(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetType not implemented");
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetConcurrency not implemented");
    }

    @Override
    public boolean ownUpdatesAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("ownUpdatesAreVisible not implemented");
    }

    @Override
    public boolean ownDeletesAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("ownDeletesAreVisible not implemented");
    }

    @Override
    public boolean ownInsertsAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("ownInsertsAreVisible not implemented");
    }

    @Override
    public boolean othersUpdatesAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("othersUpdatesAreVisible not implemented");
    }

    @Override
    public boolean othersDeletesAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("othersDeletesAreVisible not implemented");
    }

    @Override
    public boolean othersInsertsAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("othersInsertsAreVisible not implemented");
    }

    @Override
    public boolean updatesAreDetected(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("updatesAreDetected not implemented");
    }

    @Override
    public boolean deletesAreDetected(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("deletesAreDetected not implemented");
    }

    @Override
    public boolean insertsAreDetected(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("insertsAreDetected not implemented");
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsBatchUpdates not implemented");
    }

    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getUDTs not implemented");
    }

    @Override
    public Connection getConnection() throws SQLException {
        throw new SQLFeatureNotSupportedException("getConnection not implemented");
    }

    // ------------------- JDBC 3.0 -------------------------

    @Override
    public boolean supportsSavepoints() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSavepoints not implemented");
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsNamedParameters not implemented");
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleOpenResults not implemented");
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsGetGeneratedKeys not implemented");
    }

    @Override
    public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTypes not implemented");
    }

    @Override
    public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTables not implemented");
    }

    @Override
    public ResultSet getAttributes(
            String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getAttributes not implemented");
    }

    @Override
    public boolean supportsResultSetHoldability(int holdability) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetHoldability not implemented");
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetHoldability not implemented");
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseMajorVersion not implemented");
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseMinorVersion not implemented");
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getJDBCMajorVersion not implemented");
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getJDBCMinorVersion not implemented");
    }

    @Override
    public int getSQLStateType() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSQLStateType not implemented");
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        throw new SQLFeatureNotSupportedException("locatorsUpdateCopy not implemented");
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsStatementPooling not implemented");
    }

    // ------------------------- JDBC 4.0 -----------------------------------

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        throw new SQLFeatureNotSupportedException("getRowIdLifetime not implemented");
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSchemas not implemented");
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsStoredFunctionsUsingCallSyntax not implemented");
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        throw new SQLFeatureNotSupportedException("autoCommitFailureClosesAllResultSets not implemented");
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfoProperties not implemented");
    }

    @Override
    public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getFunctions not implemented");
    }

    @Override
    public ResultSet getFunctionColumns(
            String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getFunctionColumns not implemented");
    }

    // --------------------------JDBC 4.1 -----------------------------

    @Override
    public ResultSet getPseudoColumns(
            String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getPseudoColumns not implemented");
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        throw new SQLFeatureNotSupportedException("generatedKeyAlwaysReturned not implemented");
    }

    // --------------------------JDBC 4.2 -----------------------------

    // JDBC 4.3
}
