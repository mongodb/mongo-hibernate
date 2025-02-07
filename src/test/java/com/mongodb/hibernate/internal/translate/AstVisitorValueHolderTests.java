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

package com.mongodb.hibernate.internal.translate;

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_MUTATION;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FIELD_VALUE;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.AstPlaceholder;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import java.util.List;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AstVisitorValueHolderTests {

    private AstVisitorValueHolder astVisitorValueHolder;

    @BeforeEach
    void setUp() {
        astVisitorValueHolder = new AstVisitorValueHolder();
    }

    @Test
    void testSimpleUsage() {
        // given

        var valueSet = new AstLiteralValue(new BsonString("field_value"));
        Runnable valueSetter = () -> astVisitorValueHolder.yield(FIELD_VALUE, valueSet);

        // when
        var valueGotten = astVisitorValueHolder.execute(FIELD_VALUE, valueSetter);

        // then
        assertSame(valueSet, valueGotten);
    }

    @Test
    void testRecursiveUsage() {
        // given
        Runnable tableInserter = () -> {
            Runnable fieldValueSetter = () -> {
                astVisitorValueHolder.yield(FIELD_VALUE, AstPlaceholder.INSTANCE);
            };
            var fieldValue = astVisitorValueHolder.execute(FIELD_VALUE, fieldValueSetter);
            AstElement astElement = new AstElement("province", fieldValue);
            astVisitorValueHolder.yield(COLLECTION_MUTATION, new AstInsertCommand("city", List.of(astElement)));
        };

        // when && then
        astVisitorValueHolder.execute(COLLECTION_MUTATION, tableInserter);
    }

    @Test
    @DisplayName("Exception is thrown when holder is not empty when setting data")
    void testHolderNotEmptyWhenSetting() {
        // given
        Runnable valueSetter = () -> {
            astVisitorValueHolder.yield(FIELD_VALUE, new AstLiteralValue(new BsonString("value1")));
            astVisitorValueHolder.yield(FIELD_VALUE, new AstLiteralValue(new BsonString("value2")));
        };
        // when && then
        assertThrows(Error.class, () -> astVisitorValueHolder.execute(FIELD_VALUE, valueSetter));
    }

    @Test
    @DisplayName("Exception is thrown when holder is expecting a type different from that of real data")
    void testHolderExpectingDifferentType() {
        // given
        Runnable valueSetter =
                () -> astVisitorValueHolder.yield(FIELD_VALUE, new AstLiteralValue(new BsonString("some_value")));

        // when && then
        assertThrows(Error.class, () -> astVisitorValueHolder.execute(COLLECTION_MUTATION, valueSetter));
    }

    @Test
    @DisplayName("Exception is thrown when getting value from an empty holder")
    void testHolderStillEmpty() {
        assertThrows(Error.class, () -> astVisitorValueHolder.execute(FIELD_VALUE, () -> {}));
    }
}
