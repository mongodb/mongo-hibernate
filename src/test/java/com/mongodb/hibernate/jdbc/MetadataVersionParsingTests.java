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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MetadataVersionParsingTests {

    @ParameterizedTest
    @CsvSource({"3.2,3,2", "1.0.0-SNAPSHOT,1,0", "5.2.0-alpha,5,2", "2.1-beta,2,0"})
    void test(String versionText, int expectedMajorVersion, int expectedMinorVersion) {
        assertEquals(
                new MongoDatabaseMetaData.VersionNumPair(expectedMajorVersion, expectedMinorVersion),
                MongoDatabaseMetaData.parseVersionNumPair(versionText));
    }
}
