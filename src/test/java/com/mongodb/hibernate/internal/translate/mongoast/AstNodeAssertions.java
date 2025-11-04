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

package com.mongodb.hibernate.internal.translate.mongoast;

import static com.mongodb.hibernate.internal.MongoConstants.EXTENDED_JSON_WRITER_SETTINGS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import org.bson.json.JsonWriter;

public final class AstNodeAssertions {

    private AstNodeAssertions() {}

    public static void assertRendering(String expectedCanonicalExtendedJson, AstNode node) {
        doAssertRendering(expectedCanonicalExtendedJson, node, AstNodeKind.DOCUMENT);
    }

    public static void assertElementRendering(String expectedCanonicalExtendedJson, AstNode node) {
        doAssertRendering(expectedCanonicalExtendedJson, node, AstNodeKind.ELEMENT);
    }

    public static void assertValueRendering(String expectedCanonicalExtendedJson, AstValue node) {
        doAssertRendering(expectedCanonicalExtendedJson, node, AstNodeKind.VALUE);
    }

    private static void doAssertRendering(String expectedJson, AstNode node, AstNodeKind nodeKind) {
        try (var stringWriter = new StringWriter();
                var jsonWriter = new JsonWriter(stringWriter, EXTENDED_JSON_WRITER_SETTINGS)) {
            if (nodeKind != AstNodeKind.DOCUMENT) {
                jsonWriter.writeStartDocument();
            }
            var ancillaryFieldName = "";
            if (nodeKind == AstNodeKind.VALUE) {
                jsonWriter.writeName(ancillaryFieldName);
            }
            node.render(jsonWriter);
            if (nodeKind != AstNodeKind.DOCUMENT) {
                jsonWriter.writeEndDocument();
            }
            jsonWriter.flush();
            var actualJson = stringWriter.toString();
            assertEquals(expectedJson, actualJson);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private enum AstNodeKind {
        DOCUMENT,
        /** A key/value pair, a.k.a., field (a name and a value). */
        ELEMENT,
        VALUE
    }
}
