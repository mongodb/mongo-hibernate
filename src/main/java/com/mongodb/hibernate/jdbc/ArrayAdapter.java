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

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;

interface ArrayAdapter extends Array {
    @Override
    default String getBaseTypeName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getBaseTypeName not implemented");
    }

    @Override
    default int getBaseType() throws SQLException {
        throw new SQLFeatureNotSupportedException("getBaseType not implemented");
    }

    @Override
    default Object getArray() throws SQLException {
        throw new SQLFeatureNotSupportedException("getArray not implemented");
    }

    @Override
    default Object getArray(Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("getArray not implemented");
    }

    @Override
    default Object getArray(long index, int count) throws SQLException {
        throw new SQLFeatureNotSupportedException("getArray not implemented");
    }

    @Override
    default Object getArray(long index, int count, Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("getArray not implemented");
    }

    @Override
    default ResultSet getResultSet() throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSet not implemented");
    }

    @Override
    default ResultSet getResultSet(Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSet not implemented");
    }

    @Override
    default ResultSet getResultSet(long index, int count) throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSet not implemented");
    }

    @Override
    default ResultSet getResultSet(long index, int count, Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSet not implemented");
    }

    @Override
    default void free() throws SQLException {
        throw new SQLFeatureNotSupportedException("free not implemented");
    }
}
