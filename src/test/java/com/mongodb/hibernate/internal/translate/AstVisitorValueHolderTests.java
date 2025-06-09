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

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MUTATION_RESULT;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.VALUE;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.AstParameterMarker;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import java.util.List;
import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AstVisitorValueHolderTests {

    private AstVisitorValueHolder astVisitorValueHolder;

    @BeforeEach
    void beforeEach() {
        astVisitorValueHolder = new AstVisitorValueHolder();
    }

    @Test
    void testSimpleUsage() {

        var value = new AstLiteralValue(new BsonString("field_value"));
        Runnable valueYielder = () -> astVisitorValueHolder.yield(VALUE, value);

        var valueGotten = astVisitorValueHolder.execute(VALUE, valueYielder);

        assertSame(value, valueGotten);
    }

    @Test
    void testRecursiveUsage() {

        Runnable tableInserter = () -> {
            Runnable fieldValueYielder = () -> {
                astVisitorValueHolder.yield(VALUE, AstParameterMarker.INSTANCE);
            };
            var fieldValue = astVisitorValueHolder.execute(VALUE, fieldValueYielder);
            AstElement astElement = new AstElement("province", fieldValue);
            astVisitorValueHolder.yield(
                    MUTATION_RESULT,
                    ModelMutationMqlTranslator.Result.create(
                            new AstInsertCommand("city", new AstDocument(List.of(astElement))), emptyList()));
        };

        astVisitorValueHolder.execute(MUTATION_RESULT, tableInserter);
    }

    @Test
    @DisplayName("Exception is thrown when holder is not empty when setting value")
    void testHolderNotEmptyWhenSetting() {

        Runnable valueYielder = () -> {
            astVisitorValueHolder.yield(VALUE, new AstLiteralValue(new BsonString("value1")));
            astVisitorValueHolder.yield(VALUE, new AstLiteralValue(new BsonString("value2")));
        };

        assertThrows(Error.class, () -> astVisitorValueHolder.execute(VALUE, valueYielder));
    }

    @Test
    @DisplayName("Exception is thrown when holder is expecting a descriptor different from that of actual data")
    void testHolderExpectingDifferentDescriptor() {

        Runnable valueYielder =
                () -> astVisitorValueHolder.yield(VALUE, new AstLiteralValue(new BsonString("some_value")));

        assertThrows(Error.class, () -> astVisitorValueHolder.execute(MUTATION_RESULT, valueYielder));
    }

    @Test
    @DisplayName("Exception is thrown when no value is yielded")
    void testHolderStillEmpty() {
        assertThrows(Error.class, () -> astVisitorValueHolder.execute(VALUE, () -> {}));
    }
}
