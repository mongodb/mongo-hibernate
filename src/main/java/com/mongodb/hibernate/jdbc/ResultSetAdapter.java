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
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;
import org.jspecify.annotations.Nullable;

interface ResultSetAdapter extends ResultSet {
    @Override
    default boolean next() throws SQLException {
        throw new SQLFeatureNotSupportedException("next not implemented");
    }

    @Override
    default void close() throws SQLException {
        throw new SQLFeatureNotSupportedException("close not implemented");
    }

    @Override
    default boolean wasNull() throws SQLException {
        throw new SQLFeatureNotSupportedException("wasNull not implemented");
    }

    @Override
    default @Nullable String getString(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getString not implemented");
    }

    @Override
    default boolean getBoolean(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBoolean not implemented");
    }

    @Override
    default byte getByte(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getByte not implemented");
    }

    @Override
    default short getShort(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getShort not implemented");
    }

    @Override
    default int getInt(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getInt not implemented");
    }

    @Override
    default long getLong(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getLong not implemented");
    }

    @Override
    default float getFloat(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getFloat not implemented");
    }

    @Override
    default double getDouble(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getDouble not implemented");
    }

    @Override
    @SuppressWarnings("deprecation")
    default @Nullable BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBigDecimal not implemented");
    }

    @Override
    default byte @Nullable [] getBytes(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBytes not implemented");
    }

    @Override
    default @Nullable Date getDate(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getDate not implemented");
    }

    @Override
    default @Nullable Time getTime(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTime not implemented");
    }

    @Override
    default @Nullable Timestamp getTimestamp(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTimestamp not implemented");
    }

    @Override
    default @Nullable InputStream getAsciiStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getAsciiStream not implemented");
    }

    @Override
    @SuppressWarnings("deprecation")
    default @Nullable InputStream getUnicodeStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getUnicodeStream not implemented");
    }

    @Override
    default @Nullable InputStream getBinaryStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBinaryStream not implemented");
    }

    @Override
    default @Nullable String getString(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getString not implemented");
    }

    @Override
    default boolean getBoolean(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBoolean not implemented");
    }

    @Override
    default byte getByte(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getByte not implemented");
    }

    @Override
    default short getShort(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getShort not implemented");
    }

    @Override
    default int getInt(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getInt not implemented");
    }

    @Override
    default long getLong(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getLong not implemented");
    }

    @Override
    default float getFloat(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getFloat not implemented");
    }

    @Override
    default double getDouble(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getDouble not implemented");
    }

    @Override
    @SuppressWarnings("deprecation")
    default BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBigDecimal not implemented");
    }

    @Override
    default byte[] getBytes(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBytes not implemented");
    }

    @Override
    default Date getDate(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getDate not implemented");
    }

    @Override
    default Time getTime(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTime not implemented");
    }

    @Override
    default Timestamp getTimestamp(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTimestamp not implemented");
    }

    @Override
    default InputStream getAsciiStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getAsciiStream not implemented");
    }

    @Override
    @SuppressWarnings("deprecation")
    default InputStream getUnicodeStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getUnicodeStream not implemented");
    }

    @Override
    default InputStream getBinaryStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBinaryStream not implemented");
    }

    @Override
    default SQLWarning getWarnings() throws SQLException {
        throw new SQLFeatureNotSupportedException("getWarnings not implemented");
    }

    @Override
    default void clearWarnings() throws SQLException {
        throw new SQLFeatureNotSupportedException("clearWarnings not implemented");
    }

    @Override
    default String getCursorName() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCursorName not implemented");
    }

    @Override
    default ResultSetMetaData getMetaData() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMetaData not implemented");
    }

    @Override
    default Object getObject(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getObject not implemented");
    }

    @Override
    default Object getObject(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getObject not implemented");
    }

    @Override
    default int findColumn(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("findColumn not implemented");
    }

    @Override
    default Reader getCharacterStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getCharacterStream not implemented");
    }

    @Override
    default Reader getCharacterStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getCharacterStream not implemented");
    }

    @Override
    default @Nullable BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBigDecimal not implemented");
    }

    @Override
    default BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBigDecimal not implemented");
    }

    @Override
    default boolean isBeforeFirst() throws SQLException {
        throw new SQLFeatureNotSupportedException("isBeforeFirst not implemented");
    }

    @Override
    default boolean isAfterLast() throws SQLException {
        throw new SQLFeatureNotSupportedException("isAfterLast not implemented");
    }

    @Override
    default boolean isFirst() throws SQLException {
        throw new SQLFeatureNotSupportedException("isFirst not implemented");
    }

    @Override
    default boolean isLast() throws SQLException {
        throw new SQLFeatureNotSupportedException("isLast not implemented");
    }

    @Override
    default void beforeFirst() throws SQLException {
        throw new SQLFeatureNotSupportedException("beforeFirst not implemented");
    }

    @Override
    default void afterLast() throws SQLException {
        throw new SQLFeatureNotSupportedException("afterLast not implemented");
    }

    @Override
    default boolean first() throws SQLException {
        throw new SQLFeatureNotSupportedException("first not implemented");
    }

    @Override
    default boolean last() throws SQLException {
        throw new SQLFeatureNotSupportedException("last not implemented");
    }

    @Override
    default int getRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("getRow not implemented");
    }

    @Override
    default boolean absolute(int row) throws SQLException {
        throw new SQLFeatureNotSupportedException("absolute not implemented");
    }

    @Override
    default boolean relative(int rows) throws SQLException {
        throw new SQLFeatureNotSupportedException("relative not implemented");
    }

    @Override
    default boolean previous() throws SQLException {
        throw new SQLFeatureNotSupportedException("previous not implemented");
    }

    @Override
    default void setFetchDirection(int direction) throws SQLException {
        throw new SQLFeatureNotSupportedException("setFetchDirection not implemented");
    }

    @Override
    default int getFetchDirection() throws SQLException {
        throw new SQLFeatureNotSupportedException("getFetchDirection not implemented");
    }

    @Override
    default void setFetchSize(int rows) throws SQLException {
        throw new SQLFeatureNotSupportedException("setFetchSize not implemented");
    }

    @Override
    default int getFetchSize() throws SQLException {
        throw new SQLFeatureNotSupportedException("getFetchSize not implemented");
    }

    @Override
    default int getType() throws SQLException {
        throw new SQLFeatureNotSupportedException("getType not implemented");
    }

    @Override
    default int getConcurrency() throws SQLException {
        throw new SQLFeatureNotSupportedException("getConcurrency not implemented");
    }

    @Override
    default boolean rowUpdated() throws SQLException {
        throw new SQLFeatureNotSupportedException("rowUpdated not implemented");
    }

    @Override
    default boolean rowInserted() throws SQLException {
        throw new SQLFeatureNotSupportedException("rowInserted not implemented");
    }

    @Override
    default boolean rowDeleted() throws SQLException {
        throw new SQLFeatureNotSupportedException("rowDeleted not implemented");
    }

    @Override
    default void updateNull(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNull not implemented");
    }

    @Override
    default void updateBoolean(int columnIndex, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBoolean not implemented");
    }

    @Override
    default void updateByte(int columnIndex, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateByte not implemented");
    }

    @Override
    default void updateShort(int columnIndex, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateShort not implemented");
    }

    @Override
    default void updateInt(int columnIndex, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateInt not implemented");
    }

    @Override
    default void updateLong(int columnIndex, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateLong not implemented");
    }

    @Override
    default void updateFloat(int columnIndex, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateFloat not implemented");
    }

    @Override
    default void updateDouble(int columnIndex, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateDouble not implemented");
    }

    @Override
    default void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBigDecimal not implemented");
    }

    @Override
    default void updateString(int columnIndex, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateString not implemented");
    }

    @Override
    default void updateBytes(int columnIndex, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBytes not implemented");
    }

    @Override
    default void updateDate(int columnIndex, Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateDate not implemented");
    }

    @Override
    default void updateTime(int columnIndex, Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateTime not implemented");
    }

    @Override
    default void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateTimestamp not implemented");
    }

    @Override
    default void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream not implemented");
    }

    @Override
    default void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream not implemented");
    }

    @Override
    default void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream not implemented");
    }

    @Override
    default void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateObject not implemented");
    }

    @Override
    default void updateObject(int columnIndex, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateObject not implemented");
    }

    @Override
    default void updateNull(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNull not implemented");
    }

    @Override
    default void updateBoolean(String columnLabel, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBoolean not implemented");
    }

    @Override
    default void updateByte(String columnLabel, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateByte not implemented");
    }

    @Override
    default void updateShort(String columnLabel, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateShort not implemented");
    }

    @Override
    default void updateInt(String columnLabel, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateInt not implemented");
    }

    @Override
    default void updateLong(String columnLabel, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateLong not implemented");
    }

    @Override
    default void updateFloat(String columnLabel, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateFloat not implemented");
    }

    @Override
    default void updateDouble(String columnLabel, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateDouble not implemented");
    }

    @Override
    default void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBigDecimal not implemented");
    }

    @Override
    default void updateString(String columnLabel, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateString not implemented");
    }

    @Override
    default void updateBytes(String columnLabel, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBytes not implemented");
    }

    @Override
    default void updateDate(String columnLabel, Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateDate not implemented");
    }

    @Override
    default void updateTime(String columnLabel, Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateTime not implemented");
    }

    @Override
    default void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateTimestamp not implemented");
    }

    @Override
    default void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream not implemented");
    }

    @Override
    default void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream not implemented");
    }

    @Override
    default void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream not implemented");
    }

    @Override
    default void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateObject not implemented");
    }

    @Override
    default void updateObject(String columnLabel, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateObject not implemented");
    }

    @Override
    default void insertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("insertRow not implemented");
    }

    @Override
    default void updateRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("updateRow not implemented");
    }

    @Override
    default void deleteRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("deleteRow not implemented");
    }

    @Override
    default void refreshRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("refreshRow not implemented");
    }

    @Override
    default void cancelRowUpdates() throws SQLException {
        throw new SQLFeatureNotSupportedException("cancelRowUpdates not implemented");
    }

    @Override
    default void moveToInsertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("moveToInsertRow not implemented");
    }

    @Override
    default void moveToCurrentRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("moveToCurrentRow not implemented");
    }

    @Override
    default Statement getStatement() throws SQLException {
        throw new SQLFeatureNotSupportedException("getStatement not implemented");
    }

    @Override
    default Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("getObject not implemented");
    }

    @Override
    default Ref getRef(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getRef not implemented");
    }

    @Override
    default Blob getBlob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBlob not implemented");
    }

    @Override
    default Clob getClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getClob not implemented");
    }

    @Override
    default @Nullable Array getArray(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getArray not implemented");
    }

    @Override
    default Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("getObject not implemented");
    }

    @Override
    default Ref getRef(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getRef not implemented");
    }

    @Override
    default Blob getBlob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getBlob not implemented");
    }

    @Override
    default Clob getClob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getClob not implemented");
    }

    @Override
    default Array getArray(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getArray not implemented");
    }

    @Override
    default Date getDate(int columnIndex, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("getDate not implemented");
    }

    @Override
    default Date getDate(String columnLabel, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("getDate not implemented");
    }

    @Override
    default @Nullable Time getTime(int columnIndex, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTime not implemented");
    }

    @Override
    default @Nullable Time getTime(String columnLabel, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTime not implemented");
    }

    @Override
    default @Nullable Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTimestamp not implemented");
    }

    @Override
    default @Nullable Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        throw new SQLFeatureNotSupportedException("getTimestamp not implemented");
    }

    @Override
    default URL getURL(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getURL not implemented");
    }

    @Override
    default URL getURL(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getURL not implemented");
    }

    @Override
    default void updateRef(int columnIndex, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateRef not implemented");
    }

    @Override
    default void updateRef(String columnLabel, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateRef not implemented");
    }

    @Override
    default void updateBlob(int columnIndex, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob not implemented");
    }

    @Override
    default void updateBlob(String columnLabel, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob not implemented");
    }

    @Override
    default void updateClob(int columnIndex, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateClob not implemented");
    }

    @Override
    default void updateClob(String columnLabel, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateClob not implemented");
    }

    @Override
    default void updateArray(int columnIndex, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateArray not implemented");
    }

    @Override
    default void updateArray(String columnLabel, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateArray not implemented");
    }

    @Override
    default RowId getRowId(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getRowId not implemented");
    }

    @Override
    default RowId getRowId(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getRowId not implemented");
    }

    @Override
    default void updateRowId(int columnIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateRowId not implemented");
    }

    @Override
    default void updateRowId(String columnLabel, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateRowId not implemented");
    }

    @Override
    default int getHoldability() throws SQLException {
        throw new SQLFeatureNotSupportedException("getHoldability not implemented");
    }

    @Override
    default boolean isClosed() throws SQLException {
        throw new SQLFeatureNotSupportedException("isClosed not implemented");
    }

    @Override
    default void updateNString(int columnIndex, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNString not implemented");
    }

    @Override
    default void updateNString(String columnLabel, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNString not implemented");
    }

    @Override
    default void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob not implemented");
    }

    @Override
    default void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob not implemented");
    }

    @Override
    default NClob getNClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getNClob not implemented");
    }

    @Override
    default NClob getNClob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getNClob not implemented");
    }

    @Override
    default SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSQLXML not implemented");
    }

    @Override
    default SQLXML getSQLXML(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getSQLXML not implemented");
    }

    @Override
    default void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateSQLXML not implemented");
    }

    @Override
    default void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateSQLXML not implemented");
    }

    @Override
    default String getNString(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getNString not implemented");
    }

    @Override
    default String getNString(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getNString not implemented");
    }

    @Override
    default Reader getNCharacterStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("getNCharacterStream not implemented");
    }

    @Override
    default Reader getNCharacterStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("getNCharacterStream not implemented");
    }

    @Override
    default void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNCharacterStream not implemented");
    }

    @Override
    default void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNCharacterStream not implemented");
    }

    @Override
    default void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream not implemented");
    }

    @Override
    default void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream not implemented");
    }

    @Override
    default void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream not implemented");
    }

    @Override
    default void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream not implemented");
    }

    @Override
    default void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream not implemented");
    }

    @Override
    default void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream not implemented");
    }

    @Override
    default void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob not implemented");
    }

    @Override
    default void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob not implemented");
    }

    @Override
    default void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateClob not implemented");
    }

    @Override
    default void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateClob not implemented");
    }

    @Override
    default void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob not implemented");
    }

    @Override
    default void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob not implemented");
    }

    @Override
    default void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNCharacterStream not implemented");
    }

    @Override
    default void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNCharacterStream not implemented");
    }

    @Override
    default void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream not implemented");
    }

    @Override
    default void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream not implemented");
    }

    @Override
    default void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream not implemented");
    }

    @Override
    default void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream not implemented");
    }

    @Override
    default void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream not implemented");
    }

    @Override
    default void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream not implemented");
    }

    @Override
    default void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob not implemented");
    }

    @Override
    default void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob not implemented");
    }

    @Override
    default void updateClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateClob not implemented");
    }

    @Override
    default void updateClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateClob not implemented");
    }

    @Override
    default void updateNClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob not implemented");
    }

    @Override
    default void updateNClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob not implemented");
    }

    @Override
    default <T> @Nullable T getObject(int columnIndex, Class<T> type) throws SQLException {
        throw new SQLFeatureNotSupportedException("getObject not implemented");
    }

    @Override
    default <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        throw new SQLFeatureNotSupportedException("getObject not implemented");
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
