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

import static com.mongodb.hibernate.internal.translate.mongoast.AstMapChildrenAssertions.assertMapsChildren;

import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import java.util.List;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class AstDocumentUpdateTests {

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstDocumentUpdate(
                List.of(
                        new AstFieldUpdate("s", new AstLiteral(new BsonInt32(1))),
                        new AstFieldUpdate("t", new AstLiteral(new BsonInt32(2)))),
                List.of(
                        new AstFieldUpdate("i", new AstLiteral(new BsonInt32(3))),
                        new AstFieldUpdate("j", new AstLiteral(new BsonInt32(4))))));
    }
}
