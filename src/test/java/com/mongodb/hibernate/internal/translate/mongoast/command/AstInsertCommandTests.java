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

package com.mongodb.hibernate.internal.translate.mongoast.command;

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertRender;

import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.AstParameterMarker;
import java.util.List;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

class AstInsertCommandTests {

    @Test
    void testRendering() {
        // given
        var collection = "books";
        var elements = List.of(
                new AstElement("$title", new AstLiteralValue(new BsonString("War and Peace"))),
                new AstElement("$year", new AstLiteralValue(new BsonInt32(1867))),
                new AstElement("_id", AstParameterMarker.INSTANCE));
        var insertCommand = new AstInsertCommand(collection, new AstDocument(elements));

        // when && then
        var expectedJson =
                """
                {"insert": "books", "documents": [{"$title": "War and Peace", "$year": 1867, "_id": {"$undefined": true}}]}\
                """;
        assertRender(insertCommand, expectedJson);
    }
}
