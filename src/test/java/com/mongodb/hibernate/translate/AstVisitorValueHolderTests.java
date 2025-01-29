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

package com.mongodb.hibernate.translate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.hibernate.internal.mongoast.AstElement;
import com.mongodb.hibernate.internal.mongoast.AstPlaceholder;
import com.mongodb.hibernate.internal.mongoast.command.AstInsertCommand;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AstVisitorValueHolderTests {

    private AstVisitorValueHolder astVisitorValueHolder;

    @BeforeEach
    void setUp() {
        astVisitorValueHolder = AstVisitorValueHolder.emptyHolder();
    }

    @Test
    void testSimpleUsage() {
        // given

        var valueSet = "field_value";
        Runnable valueSetter = () -> astVisitorValueHolder.setValue(TypeReference.FIELD_NAME, valueSet);

        // when
        var valueGotten = astVisitorValueHolder.getValue(TypeReference.FIELD_NAME, valueSetter);

        // then
        assertEquals(valueSet, valueGotten);
    }

    @Test
    void testBacktrack() {
        // given
        Runnable tableInserter = () -> {
            Runnable fieldNameSetter = () -> {
                astVisitorValueHolder.setValue(TypeReference.FIELD_NAME, "address");
            };
            var fieldName = astVisitorValueHolder.getValue(TypeReference.FIELD_NAME, fieldNameSetter);
            Runnable fielValueSetter = () -> {
                astVisitorValueHolder.setValue(TypeReference.FIELD_VALUE, AstPlaceholder.INSTANCE);
            };
            var fieldValue = astVisitorValueHolder.getValue(TypeReference.FIELD_VALUE, fielValueSetter);
            AstElement astElement = new AstElement(fieldName, fieldValue);
            astVisitorValueHolder.setValue(
                    TypeReference.COLLECTION_MUTATION, new AstInsertCommand("place", List.of(astElement)));
        };

        // when && then
        assertDoesNotThrow(() -> astVisitorValueHolder.getValue(TypeReference.COLLECTION_MUTATION, tableInserter));
    }

    @Nested
    class ValueSettingTests {

        @Test
        @DisplayName("Exception is thrown when holder is not empty when setting data")
        void testHolderNotEmptyWhenSetting() {
            // given
            Runnable valueSetter = () -> {
                astVisitorValueHolder.setValue(TypeReference.FIELD_NAME, "address");
                astVisitorValueHolder.setValue(TypeReference.FIELD_NAME, "country");
            };
            // when && then
            assertThrows(Error.class, () -> astVisitorValueHolder.getValue(TypeReference.FIELD_NAME, valueSetter));
        }

        @Test
        @DisplayName("Exception is thrown when holder is expecting a type different from that of real data")
        void testHolderExpectingDifferentType() {
            // given
            Runnable valueSetter = () -> astVisitorValueHolder.setValue(TypeReference.FIELD_NAME, "address");

            // when && then
            assertThrows(Error.class, () -> astVisitorValueHolder.getValue(TypeReference.FIELD_VALUE, valueSetter));
        }
    }

    @Nested
    class ValueGettingTests {

        @Test
        @DisplayName("Exception is thrown when getting value from an empty holder")
        void testHolderStillEmpty() {
            assertThrows(Error.class, () -> astVisitorValueHolder.getValue(TypeReference.FIELD_VALUE, () -> {}));
        }
    }

    @Test
    void test() {
        System.out.println(TypeReference.COLLECTION_MUTATION);
    }
}
