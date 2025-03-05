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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import org.jspecify.annotations.Nullable;

interface PreparedStatementAdapter extends StatementAdapter, PreparedStatement {
    @Override
    default ResultSet executeQuery() throws SQLException {
        throw new SQLFeatureNotSupportedException("executeQuery not implemented");
    }

    @Override
    default int executeUpdate() throws SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate not implemented");
    }

    @Override
    default void setNull(int parameterIndex, int sqlType) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNull not implemented");
    }

    @Override
    default void setBoolean(int parameterIndex, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBoolean not implemented");
    }

    @Override
    default void setByte(int parameterIndex, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setByte not implemented");
    }

    @Override
    default void setShort(int parameterIndex, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setShort not implemented");
    }

    @Override
    default void setInt(int parameterIndex, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setInt not implemented");
    }

    @Override
    default void setLong(int parameterIndex, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setLong not implemented");
    }

    @Override
    default void setFloat(int parameterIndex, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setFloat not implemented");
    }

    @Override
    default void setDouble(int parameterIndex, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setDouble not implemented");
    }

    @Override
    default void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBigDecimal not implemented");
    }

    @Override
    default void setString(int parameterIndex, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setString not implemented");
    }

    @Override
    default void setBytes(int parameterIndex, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBytes not implemented");
    }

    @Override
    default void setDate(int parameterIndex, Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setDate not implemented");
    }

    @Override
    default void setTime(int parameterIndex, Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setTime not implemented");
    }

    @Override
    default void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setTimestamp not implemented");
    }

    @Override
    default void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setAsciiStream not implemented");
    }

    @Override
    @SuppressWarnings("deprecation")
    default void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setUnicodeStream not implemented");
    }

    @Override
    default void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBinaryStream not implemented");
    }

    @Override
    default void clearParameters() throws SQLException {
        throw new SQLFeatureNotSupportedException("clearParameters not implemented");
    }

    @Override
    default void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        throw new SQLFeatureNotSupportedException("setObject not implemented");
    }

    @Override
    default void setObject(int parameterIndex, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setObject not implemented");
    }

    @Override
    default boolean execute() throws SQLException {
        throw new SQLFeatureNotSupportedException("execute not implemented");
    }

    @Override
    default void addBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("addBatch not implemented");
    }

    @Override
    default void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setCharacterStream not implemented");
    }

    @Override
    default void setRef(int parameterIndex, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setRef not implemented");
    }

    @Override
    default void setBlob(int parameterIndex, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBlob not implemented");
    }

    @Override
    default void setClob(int parameterIndex, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setClob not implemented");
    }

    @Override
    default void setArray(int parameterIndex, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setArray not implemented");
    }

    @Override
    default @Nullable ResultSetMetaData getMetaData() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMetaData not implemented");
    }

    @Override
    default void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("setDate not implemented");
    }

    @Override
    default void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("setTime not implemented");
    }

    @Override
    default void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("setTimestamp not implemented");
    }

    @Override
    default void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNull not implemented");
    }

    @Override
    default void setURL(int parameterIndex, URL x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setURL not implemented");
    }

    @Override
    default ParameterMetaData getParameterMetaData() throws SQLException {
        throw new SQLFeatureNotSupportedException("getParameterMetaData not implemented");
    }

    @Override
    default void setRowId(int parameterIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setRowId not implemented");
    }

    @Override
    default void setNString(int parameterIndex, String value) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNString not implemented");
    }

    @Override
    default void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNCharacterStream not implemented");
    }

    @Override
    default void setNClob(int parameterIndex, NClob value) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNClob not implemented");
    }

    @Override
    default void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setClob not implemented");
    }

    @Override
    default void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBlob not implemented");
    }

    @Override
    default void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNClob not implemented");
    }

    @Override
    default void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("setSQLXML not implemented");
    }

    @Override
    default void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException("setObject not implemented");
    }

    @Override
    default void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setAsciiStream not implemented");
    }

    @Override
    default void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBinaryStream not implemented");
    }

    @Override
    default void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("setCharacterStream not implemented");
    }

    @Override
    default void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setAsciiStream not implemented");
    }

    @Override
    default void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBinaryStream not implemented");
    }

    @Override
    default void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("setCharacterStream not implemented");
    }

    @Override
    default void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNCharacterStream not implemented");
    }

    @Override
    default void setClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("setClob not implemented");
    }

    @Override
    default void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("setBlob not implemented");
    }

    @Override
    default void setNClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNClob not implemented");
    }
}
