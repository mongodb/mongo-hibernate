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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import org.bson.json.JsonWriter;

public final class AstNodeAssertions {

    private AstNodeAssertions() {}

    public static void assertRender(String expectedJson, AstNode node) {
        doAssertRender(expectedJson, node, false);
    }

    public static void assertNameValueRender(String expectedJson, AstNode node) {
        doAssertRender(expectedJson, node, true);
    }

    private static void doAssertRender(String expectedJson, AstNode node, boolean isNameValue) {
        try (var stringWriter = new StringWriter();
                var jsonWriter = new JsonWriter(stringWriter)) {
            if (isNameValue) {
                jsonWriter.writeStartDocument();
            }
            node.render(jsonWriter);
            if (isNameValue) {
                jsonWriter.writeEndDocument();
            }
            jsonWriter.flush();
            var actualJson = stringWriter.toString();
            assertEquals(expectedJson, actualJson);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
