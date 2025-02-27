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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.client.MongoCursor;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoResultSetTests {

    private static final List<String> FIELDS = List.of("id", "title", "publishYear");

    @Mock
    private MongoCursor<BsonDocument> mongoCursor;

    @AutoClose
    private MongoResultSet mongoResultSet;

    @BeforeEach
    void beforeEach() {
        mongoResultSet = new MongoResultSet(mongoCursor, FIELDS);
    }

    @Test
    void testColumnIndexUnderflow() {
        checkGetterMethods(0, this::assertThrowsOutOfRangeException);
    }

    @Test
    void testColumnIndexOverflow() {
        checkGetterMethods(FIELDS.size() + 1, this::assertThrowsOutOfRangeException);
    }

    @Test
    void testCheckClosed() throws SQLException {
        mongoResultSet.close();
        checkMethodsWithOpenPrecondition(this::assertThrowsClosedException);
    }

    private void checkMethodsWithOpenPrecondition(Consumer<Executable> asserter) {
        checkGetterMethods(1, asserter);
        assertAll(
                () -> asserter.accept(() -> mongoResultSet.next()),
                () -> asserter.accept(() -> mongoResultSet.wasNull()),
                () -> asserter.accept(() -> mongoResultSet.getMetaData()),
                () -> asserter.accept(() -> mongoResultSet.findColumn("id")),
                () -> asserter.accept(() -> mongoResultSet.isWrapperFor(MongoResultSet.class)));
    }

    private void checkGetterMethods(int columnIndex, Consumer<Executable> asserter) {
        assertAll(
                () -> asserter.accept(() -> mongoResultSet.getString(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getBoolean(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getByte(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getShort(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getInt(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getLong(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getFloat(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getDouble(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getBytes(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getDate(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getTime(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getTime(columnIndex, Calendar.getInstance())),
                () -> asserter.accept(() -> mongoResultSet.getTimestamp(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getTimestamp(columnIndex, Calendar.getInstance())),
                () -> asserter.accept(() -> mongoResultSet.getBigDecimal(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getArray(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getObject(columnIndex, UUID.class)));
    }

    private void assertThrowsOutOfRangeException(Executable executable) {
        var e = assertThrows(SQLException.class, executable);
        assertThat(e.getMessage())
                .matches(
                        "Invalid column index \\[\\d+]; cannot be under 1 or over the current number of fields \\[\\d+]");
    }

    private void assertThrowsClosedException(Executable executable) {
        var exception = assertThrows(SQLException.class, executable);
        assertEquals("MongoResultSet has been closed", exception.getMessage());
    }
}
