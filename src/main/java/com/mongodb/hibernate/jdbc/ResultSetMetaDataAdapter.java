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

package com.mongodb.hibernate.jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

interface ResultSetMetaDataAdapter extends ResultSetMetaData {
    @Override
    default int getColumnCount() throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnCount not implemented");
    }

    @Override
    default boolean isAutoIncrement(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isAutoIncrement not implemented");
    }

    @Override
    default boolean isCaseSensitive(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isCaseSensitive not implemented");
    }

    @Override
    default boolean isSearchable(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isSearchable not implemented");
    }

    @Override
    default boolean isCurrency(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isCurrency not implemented");
    }

    @Override
    default int isNullable(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isNullable not implemented");
    }

    @Override
    default boolean isSigned(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isSigned not implemented");
    }

    @Override
    default int getColumnDisplaySize(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnDisplaySize not implemented");
    }

    @Override
    default String getColumnLabel(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnLabel not implemented");
    }

    @Override
    default String getColumnName(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnName not implemented");
    }

    @Override
    default String getSchemaName(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSchemaName not implemented");
    }

    @Override
    default int getPrecision(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getPrecision not implemented");
    }

    @Override
    default int getScale(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getScale not implemented");
    }

    @Override
    default String getTableName(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTableName not implemented");
    }

    @Override
    default String getCatalogName(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogName not implemented");
    }

    @Override
    default int getColumnType(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnType not implemented");
    }

    @Override
    default String getColumnTypeName(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnTypeName not implemented");
    }

    @Override
    default boolean isReadOnly(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isReadOnly not implemented");
    }

    @Override
    default boolean isWritable(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isWritable not implemented");
    }

    @Override
    default boolean isDefinitelyWritable(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("isDefinitelyWritable not implemented");
    }

    @Override
    default String getColumnClassName(int column) throws SQLException {
        throw new SQLFeatureNotSupportedException("getColumnClassName not implemented");
    }

    @Override
    default <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException("unwrap not implemented");
    }

    @Override
    default boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException("isWrapperFor not implemented");
    }
}
