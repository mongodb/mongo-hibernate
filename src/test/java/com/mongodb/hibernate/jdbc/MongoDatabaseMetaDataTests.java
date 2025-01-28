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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

class MongoDatabaseMetaDataTests {
    @Mock
    private Connection connection;

    @ParameterizedTest
    @CsvSource({"3.2, 3, 2", "1.0.0-SNAPSHOT, 1, 0", "5.2.0-alpha, 5, 2", "2.1-beta, 2, 1", "1-0, 1, 0"})
    void constructor(String driverVersionText, int driverMajorVersion, int driverMinorVersion) {
        MongoDatabaseMetaData metadata = new MongoDatabaseMetaData(connection, "DBMS 1.2", 1, 2, driverVersionText);
        assertAll(
                () -> assertSame(connection, metadata.getConnection()),
                () -> assertEquals("DBMS 1.2", metadata.getDatabaseProductVersion()),
                () -> assertEquals(1, metadata.getDatabaseMajorVersion()),
                () -> assertEquals(2, metadata.getDatabaseMinorVersion()),
                () -> assertEquals(driverVersionText, metadata.getDriverVersion()),
                () -> assertEquals(driverMajorVersion, metadata.getDriverMajorVersion()),
                () -> assertEquals(driverMinorVersion, metadata.getDriverMinorVersion()));
    }

    @ParameterizedTest
    @CsvSource({"3", "alpha", "."})
    void constructorFails(String invalidDriverVersionText) {
        assertThrows(
                AssertionError.class,
                () -> new MongoDatabaseMetaData(connection, "DBMS 1.2", 1, 2, invalidDriverVersionText));
    }
}
