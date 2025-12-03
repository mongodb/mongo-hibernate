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

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

interface DatabaseMetaDataAdapter extends DatabaseMetaData {

    @Override
    default <T> T unwrap(Class<T> unwrapType) throws SQLException {
        throw new SQLFeatureNotSupportedException("unwrap not implemented");
    }

    @Override
    default boolean isWrapperFor(Class<?> unwrapType) throws SQLException {
        throw new SQLFeatureNotSupportedException("isWrapperFor not implemented");
    }

    @Override
    default boolean allProceduresAreCallable() throws SQLException {
        throw new SQLFeatureNotSupportedException("allProceduresAreCallable not implemented");
    }

    @Override
    default boolean allTablesAreSelectable() throws SQLException {
        throw new SQLFeatureNotSupportedException("allTablesAreSelectable not implemented");
    }

    @Override
    default String getURL() throws SQLException {
        throw new SQLFeatureNotSupportedException("getURL not implemented");
    }

    @Override
    default String getUserName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getUserName not implemented");
    }

    @Override
    default boolean isReadOnly() throws SQLException {
        throw new SQLFeatureNotSupportedException("isReadOnly not implemented");
    }

    @Override
    default boolean nullsAreSortedHigh() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedHigh not implemented");
    }

    @Override
    default boolean nullsAreSortedLow() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedLow not implemented");
    }

    @Override
    default boolean nullsAreSortedAtStart() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedAtStart not implemented");
    }

    @Override
    default boolean nullsAreSortedAtEnd() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedAtEnd not implemented");
    }

    @Override
    default String getDatabaseProductName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseProductName not implemented");
    }

    @Override
    default String getDatabaseProductVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseProductVersion not implemented");
    }

    @Override
    default String getDriverName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDriverName not implemented");
    }

    @Override
    default String getDriverVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDriverVersion not implemented");
    }

    @Override
    default int getDriverMajorVersion() {
        throw new FeatureNotSupportedException("getDriverMajorVersion not implemented");
    }

    @Override
    default int getDriverMinorVersion() {
        throw new FeatureNotSupportedException("getDriverMinorVersion not implemented");
    }

    @Override
    default boolean usesLocalFiles() throws SQLException {
        throw new SQLFeatureNotSupportedException("usesLocalFiles not implemented");
    }

    @Override
    default boolean usesLocalFilePerTable() throws SQLException {
        throw new SQLFeatureNotSupportedException("usesLocalFilePerTable not implemented");
    }

    @Override
    default boolean supportsMixedCaseIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMixedCaseIdentifiers not implemented");
    }

    @Override
    default boolean storesUpperCaseIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesUpperCaseIdentifiers not implemented");
    }

    @Override
    default boolean storesLowerCaseIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesLowerCaseIdentifiers not implemented");
    }

    @Override
    default boolean storesMixedCaseIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesMixedCaseIdentifiers not implemented");
    }

    @Override
    default boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMixedCaseQuotedIdentifiers not implemented");
    }

    @Override
    default boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesUpperCaseQuotedIdentifiers not implemented");
    }

    @Override
    default boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesLowerCaseQuotedIdentifiers not implemented");
    }

    @Override
    default boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        throw new SQLFeatureNotSupportedException("storesMixedCaseQuotedIdentifiers not implemented");
    }

    @Override
    default String getIdentifierQuoteString() throws SQLException {
        throw new SQLFeatureNotSupportedException("getIdentifierQuoteString not implemented");
    }

    @Override
    default String getSQLKeywords() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSQLKeywords not implemented");
    }

    @Override
    default String getNumericFunctions() throws SQLException {
        throw new SQLFeatureNotSupportedException("getNumericFunctions not implemented");
    }

    @Override
    default String getStringFunctions() throws SQLException {
        throw new SQLFeatureNotSupportedException("getStringFunctions not implemented");
    }

    @Override
    default String getSystemFunctions() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSystemFunctions not implemented");
    }

    @Override
    default String getTimeDateFunctions() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTimeDateFunctions not implemented");
    }

    @Override
    default String getSearchStringEscape() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSearchStringEscape not implemented");
    }

    @Override
    default String getExtraNameCharacters() throws SQLException {
        throw new SQLFeatureNotSupportedException("getExtraNameCharacters not implemented");
    }

    @Override
    default boolean supportsAlterTableWithAddColumn() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsAlterTableWithAddColumn not implemented");
    }

    @Override
    default boolean supportsAlterTableWithDropColumn() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsAlterTableWithDropColumn not implemented");
    }

    @Override
    default boolean supportsColumnAliasing() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsColumnAliasing not implemented");
    }

    @Override
    default boolean nullPlusNonNullIsNull() throws SQLException {
        throw new SQLFeatureNotSupportedException("nullPlusNonNullIsNull not implemented");
    }

    @Override
    default boolean supportsConvert() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsConvert not implemented");
    }

    @Override
    default boolean supportsConvert(int fromType, int toType) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsConvert not implemented");
    }

    @Override
    default boolean supportsTableCorrelationNames() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsTableCorrelationNames not implemented");
    }

    @Override
    default boolean supportsDifferentTableCorrelationNames() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsDifferentTableCorrelationNames not implemented");
    }

    @Override
    default boolean supportsExpressionsInOrderBy() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsExpressionsInOrderBy not implemented");
    }

    @Override
    default boolean supportsOrderByUnrelated() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOrderByUnrelated not implemented");
    }

    @Override
    default boolean supportsGroupBy() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupBy not implemented");
    }

    @Override
    default boolean supportsGroupByUnrelated() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupByUnrelated not implemented");
    }

    @Override
    default boolean supportsGroupByBeyondSelect() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupByBeyondSelect not implemented");
    }

    @Override
    default boolean supportsLikeEscapeClause() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsLikeEscapeClause not implemented");
    }

    @Override
    default boolean supportsMultipleResultSets() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleResultSets not implemented");
    }

    @Override
    default boolean supportsMultipleTransactions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleTransactions not implemented");
    }

    @Override
    default boolean supportsNonNullableColumns() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsNonNullableColumns not implemented");
    }

    @Override
    default boolean supportsMinimumSQLGrammar() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMinimumSQLGrammar not implemented");
    }

    @Override
    default boolean supportsCoreSQLGrammar() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCoreSQLGrammar not implemented");
    }

    @Override
    default boolean supportsExtendedSQLGrammar() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsExtendedSQLGrammar not implemented");
    }

    @Override
    default boolean supportsANSI92EntryLevelSQL() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92EntryLevelSQL not implemented");
    }

    @Override
    default boolean supportsANSI92IntermediateSQL() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92IntermediateSQL not implemented");
    }

    @Override
    default boolean supportsANSI92FullSQL() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92FullSQL not implemented");
    }

    @Override
    default boolean supportsIntegrityEnhancementFacility() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsIntegrityEnhancementFacility not implemented");
    }

    @Override
    default boolean supportsOuterJoins() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOuterJoins not implemented");
    }

    @Override
    default boolean supportsFullOuterJoins() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsFullOuterJoins not implemented");
    }

    @Override
    default boolean supportsLimitedOuterJoins() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsLimitedOuterJoins not implemented");
    }

    @Override
    default String getSchemaTerm() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSchemaTerm not implemented");
    }

    @Override
    default String getProcedureTerm() throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureTerm not implemented");
    }

    @Override
    default String getCatalogTerm() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogTerm not implemented");
    }

    @Override
    default boolean isCatalogAtStart() throws SQLException {
        throw new SQLFeatureNotSupportedException("isCatalogAtStart not implemented");
    }

    @Override
    default String getCatalogSeparator() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogSeparator not implemented");
    }

    @Override
    default boolean supportsSchemasInDataManipulation() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInDataManipulation not implemented");
    }

    @Override
    default boolean supportsSchemasInProcedureCalls() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInProcedureCalls not implemented");
    }

    @Override
    default boolean supportsSchemasInTableDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInTableDefinitions not implemented");
    }

    @Override
    default boolean supportsSchemasInIndexDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInIndexDefinitions not implemented");
    }

    @Override
    default boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInPrivilegeDefinitions not implemented");
    }

    @Override
    default boolean supportsCatalogsInDataManipulation() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInDataManipulation not implemented");
    }

    @Override
    default boolean supportsCatalogsInProcedureCalls() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInProcedureCalls not implemented");
    }

    @Override
    default boolean supportsCatalogsInTableDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInTableDefinitions not implemented");
    }

    @Override
    default boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInIndexDefinitions not implemented");
    }

    @Override
    default boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInPrivilegeDefinitions not implemented");
    }

    @Override
    default boolean supportsPositionedDelete() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsPositionedDelete not implemented");
    }

    @Override
    default boolean supportsPositionedUpdate() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsPositionedUpdate not implemented");
    }

    @Override
    default boolean supportsSelectForUpdate() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSelectForUpdate not implemented");
    }

    @Override
    default boolean supportsStoredProcedures() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsStoredProcedures not implemented");
    }

    @Override
    default boolean supportsSubqueriesInComparisons() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInComparisons not implemented");
    }

    @Override
    default boolean supportsSubqueriesInExists() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInExists not implemented");
    }

    @Override
    default boolean supportsSubqueriesInIns() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInIns not implemented");
    }

    @Override
    default boolean supportsSubqueriesInQuantifieds() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInQuantifieds not implemented");
    }

    @Override
    default boolean supportsCorrelatedSubqueries() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsCorrelatedSubqueries not implemented");
    }

    @Override
    default boolean supportsUnion() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsUnion not implemented");
    }

    @Override
    default boolean supportsUnionAll() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsUnionAll not implemented");
    }

    @Override
    default boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenCursorsAcrossCommit not implemented");
    }

    @Override
    default boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenCursorsAcrossRollback not implemented");
    }

    @Override
    default boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenStatementsAcrossCommit not implemented");
    }

    @Override
    default boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenStatementsAcrossRollback not implemented");
    }

    @Override
    default int getMaxBinaryLiteralLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxBinaryLiteralLength not implemented");
    }

    @Override
    default int getMaxCharLiteralLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCharLiteralLength not implemented");
    }

    @Override
    default int getMaxColumnNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnNameLength not implemented");
    }

    @Override
    default int getMaxColumnsInGroupBy() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInGroupBy not implemented");
    }

    @Override
    default int getMaxColumnsInIndex() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInIndex not implemented");
    }

    @Override
    default int getMaxColumnsInOrderBy() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInOrderBy not implemented");
    }

    @Override
    default int getMaxColumnsInSelect() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInSelect not implemented");
    }

    @Override
    default int getMaxColumnsInTable() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInTable not implemented");
    }

    @Override
    default int getMaxConnections() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxConnections not implemented");
    }

    @Override
    default int getMaxCursorNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCursorNameLength not implemented");
    }

    @Override
    default int getMaxIndexLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxIndexLength not implemented");
    }

    @Override
    default int getMaxSchemaNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxSchemaNameLength not implemented");
    }

    @Override
    default int getMaxProcedureNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxProcedureNameLength not implemented");
    }

    @Override
    default int getMaxCatalogNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCatalogNameLength not implemented");
    }

    @Override
    default int getMaxRowSize() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxRowSize not implemented");
    }

    @Override
    default boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        throw new SQLFeatureNotSupportedException("doesMaxRowSizeIncludeBlobs not implemented");
    }

    @Override
    default int getMaxStatementLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxStatementLength not implemented");
    }

    @Override
    default int getMaxStatements() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxStatements not implemented");
    }

    @Override
    default int getMaxTableNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxTableNameLength not implemented");
    }

    @Override
    default int getMaxTablesInSelect() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxTablesInSelect not implemented");
    }

    @Override
    default int getMaxUserNameLength() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxUserNameLength not implemented");
    }

    @Override
    default int getDefaultTransactionIsolation() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDefaultTransactionIsolation not implemented");
    }

    @Override
    default boolean supportsTransactions() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsTransactions not implemented");
    }

    @Override
    default boolean supportsTransactionIsolationLevel(int level) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsTransactionIsolationLevel not implemented");
    }

    @Override
    default boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        throw new SQLFeatureNotSupportedException(
                "supportsDataDefinitionAndDataManipulationTransactions not implemented");
    }

    @Override
    default boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsDataManipulationTransactionsOnly not implemented");
    }

    @Override
    default boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        throw new SQLFeatureNotSupportedException("dataDefinitionCausesTransactionCommit not implemented");
    }

    @Override
    default boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        throw new SQLFeatureNotSupportedException("dataDefinitionIgnoredInTransactions not implemented");
    }

    @Override
    default ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedures not implemented");
    }

    @Override
    default ResultSet getProcedureColumns(
            String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureColumns not implemented");
    }

    @Override
    default ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getTables not implemented");
    }

    @Override
    default ResultSet getSchemas() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSchemas not implemented");
    }

    @Override
    default ResultSet getCatalogs() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogs not implemented");
    }

    @Override
    default ResultSet getTableTypes() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTableTypes not implemented");
    }

    @Override
    default ResultSet getColumns(
            String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumns not implemented");
    }

    @Override
    default ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnPrivileges not implemented");
    }

    @Override
    default ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getTablePrivileges not implemented");
    }

    @Override
    default ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getBestRowIdentifier not implemented");
    }

    @Override
    default ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getVersionColumns not implemented");
    }

    @Override
    default ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getPrimaryKeys not implemented");
    }

    @Override
    default ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getImportedKeys not implemented");
    }

    @Override
    default ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        throw new SQLFeatureNotSupportedException("getExportedKeys not implemented");
    }

    @Override
    default ResultSet getCrossReference(
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
    default ResultSet getTypeInfo() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTypeInfo not implemented");
    }

    @Override
    default ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getIndexInfo not implemented");
    }

    @Override
    default boolean supportsResultSetType(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetType not implemented");
    }

    @Override
    default boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetConcurrency not implemented");
    }

    @Override
    default boolean ownUpdatesAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("ownUpdatesAreVisible not implemented");
    }

    @Override
    default boolean ownDeletesAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("ownDeletesAreVisible not implemented");
    }

    @Override
    default boolean ownInsertsAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("ownInsertsAreVisible not implemented");
    }

    @Override
    default boolean othersUpdatesAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("othersUpdatesAreVisible not implemented");
    }

    @Override
    default boolean othersDeletesAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("othersDeletesAreVisible not implemented");
    }

    @Override
    default boolean othersInsertsAreVisible(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("othersInsertsAreVisible not implemented");
    }

    @Override
    default boolean updatesAreDetected(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("updatesAreDetected not implemented");
    }

    @Override
    default boolean deletesAreDetected(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("deletesAreDetected not implemented");
    }

    @Override
    default boolean insertsAreDetected(int type) throws SQLException {
        throw new SQLFeatureNotSupportedException("insertsAreDetected not implemented");
    }

    @Override
    default boolean supportsBatchUpdates() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsBatchUpdates not implemented");
    }

    @Override
    default ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getUDTs not implemented");
    }

    @Override
    default Connection getConnection() throws SQLException {
        throw new SQLFeatureNotSupportedException("getConnection not implemented");
    }

    @Override
    default boolean supportsSavepoints() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsSavepoints not implemented");
    }

    @Override
    default boolean supportsNamedParameters() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsNamedParameters not implemented");
    }

    @Override
    default boolean supportsMultipleOpenResults() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleOpenResults not implemented");
    }

    @Override
    default boolean supportsGetGeneratedKeys() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsGetGeneratedKeys not implemented");
    }

    @Override
    default ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTypes not implemented");
    }

    @Override
    default ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTables not implemented");
    }

    @Override
    default ResultSet getAttributes(
            String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getAttributes not implemented");
    }

    @Override
    default boolean supportsResultSetHoldability(int holdability) throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetHoldability not implemented");
    }

    @Override
    default int getResultSetHoldability() throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetHoldability not implemented");
    }

    @Override
    default int getDatabaseMajorVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseMajorVersion not implemented");
    }

    @Override
    default int getDatabaseMinorVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseMinorVersion not implemented");
    }

    @Override
    default int getJDBCMajorVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getJDBCMajorVersion not implemented");
    }

    @Override
    default int getJDBCMinorVersion() throws SQLException {
        throw new SQLFeatureNotSupportedException("getJDBCMinorVersion not implemented");
    }

    @Override
    default int getSQLStateType() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSQLStateType not implemented");
    }

    @Override
    default boolean locatorsUpdateCopy() throws SQLException {
        throw new SQLFeatureNotSupportedException("locatorsUpdateCopy not implemented");
    }

    @Override
    default boolean supportsStatementPooling() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsStatementPooling not implemented");
    }

    @Override
    default RowIdLifetime getRowIdLifetime() throws SQLException {
        throw new SQLFeatureNotSupportedException("getRowIdLifetime not implemented");
    }

    @Override
    default ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSchemas not implemented");
    }

    @Override
    default boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        throw new SQLFeatureNotSupportedException("supportsStoredFunctionsUsingCallSyntax not implemented");
    }

    @Override
    default boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        throw new SQLFeatureNotSupportedException("autoCommitFailureClosesAllResultSets not implemented");
    }

    @Override
    default ResultSet getClientInfoProperties() throws SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfoProperties not implemented");
    }

    @Override
    default ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getFunctions not implemented");
    }

    @Override
    default ResultSet getFunctionColumns(
            String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getFunctionColumns not implemented");
    }

    @Override
    default ResultSet getPseudoColumns(
            String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("getPseudoColumns not implemented");
    }

    @Override
    default boolean generatedKeyAlwaysReturned() throws SQLException {
        throw new SQLFeatureNotSupportedException("generatedKeyAlwaysReturned not implemented");
    }
}
