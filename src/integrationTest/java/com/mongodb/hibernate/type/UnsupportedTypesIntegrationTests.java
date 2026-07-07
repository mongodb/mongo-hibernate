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

package com.mongodb.hibernate.type;

import static com.mongodb.hibernate.type.UnsupportedTypeAssertions.assertNotSupported;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.types.BSONTimestamp;
import org.bson.types.Binary;
import org.bson.types.Code;
import org.bson.types.CodeWScope;
import org.bson.types.CodeWithScope;
import org.bson.types.Decimal128;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.Symbol;
import org.junit.jupiter.api.Test;

class UnsupportedTypesIntegrationTests {

    @Test
    void uuidBasicAttributeNotSupported() {
        assertNotSupported(UuidItem.class);
    }

    @Test
    void bsonTimestampBasicAttributeNotSupported() {
        assertNotSupported(BsonTimestampItem.class);
    }

    @Test
    void binaryBasicAttributeNotSupported() {
        assertNotSupported(BinaryItem.class);
    }

    @Test
    void codeBasicAttributeNotSupported() {
        assertNotSupported(CodeItem.class);
    }

    @Test
    void codeWithScopeBasicAttributeNotSupported() {
        assertNotSupported(CodeWithScopeItem.class);
    }

    @Test
    void codeWScopeBasicAttributeNotSupported() {
        assertNotSupported(CodeWScopeItem.class);
    }

    @Test
    void minKeyBasicAttributeNotSupported() {
        assertNotSupported(MinKeyItem.class);
    }

    @Test
    void maxKeyBasicAttributeNotSupported() {
        assertNotSupported(MaxKeyItem.class);
    }

    @Test
    void symbolBasicAttributeNotSupported() {
        assertNotSupported(SymbolItem.class);
    }

    @Test
    void decimal128BasicAttributeNotSupported() {
        assertNotSupported(Decimal128Item.class);
    }

    @Test
    void documentBasicAttributeNotSupported() {
        assertNotSupported(DocumentItem.class);
    }

    @Test
    void bsonDocumentBasicAttributeNotSupported() {
        assertNotSupported(BsonDocumentItem.class);
    }

    @Test
    void rawBsonDocumentBasicAttributeNotSupported() {
        assertNotSupported(RawBsonDocumentItem.class);
    }

    @Test
    void bsonDocumentWrapperBasicAttributeNotSupported() {
        assertNotSupported(BsonDocumentWrapperItem.class);
    }

    @Entity
    static class UuidItem {
        @Id
        int id;

        UUID v;
    }

    @Entity
    static class BsonTimestampItem {
        @Id
        int id;

        BSONTimestamp v;
    }

    @Entity
    static class BinaryItem {
        @Id
        int id;

        Binary v;
    }

    @Entity
    static class CodeItem {
        @Id
        int id;

        Code v;
    }

    @Entity
    static class CodeWithScopeItem {
        @Id
        int id;

        CodeWithScope v;
    }

    @Entity
    static class CodeWScopeItem {
        @Id
        int id;

        CodeWScope v;
    }

    @Entity
    static class MinKeyItem {
        @Id
        int id;

        MinKey v;
    }

    @Entity
    static class MaxKeyItem {
        @Id
        int id;

        MaxKey v;
    }

    @Entity
    static class SymbolItem {
        @Id
        int id;

        Symbol v;
    }

    @Entity
    static class Decimal128Item {
        @Id
        int id;

        Decimal128 v;
    }

    @Entity
    static class DocumentItem {
        @Id
        int id;

        Document v;
    }

    @Entity
    static class BsonDocumentItem {
        @Id
        int id;

        BsonDocument v;
    }

    @Entity
    static class RawBsonDocumentItem {
        @Id
        int id;

        RawBsonDocument v;
    }

    @Entity
    static class BsonDocumentWrapperItem {
        @Id
        int id;

        @SuppressWarnings("rawtypes")
        BsonDocumentWrapper v;
    }
}
