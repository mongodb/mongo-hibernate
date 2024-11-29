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

package com.mongodb.hibernate.translate.mongoast;

import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.codecs.BsonValueCodec;
import org.bson.codecs.EncoderContext;

/**
 * Represents a literal Bson value {@link AstNode} type, whose rendering is based on some {@link BsonValueCodec}.
 *
 * @param literalValue some {@link BsonValue} instance to be rendered; never null
 * @see AstPlaceholder
 */
public record AstLiteralValue(BsonValue literalValue) implements AstValue {

    private static final BsonValueCodec BSON_VALUE_CODEC = new BsonValueCodec();
    private static final EncoderContext DEFAULT_CONTEXT =
            EncoderContext.builder().build();

    @Override
    public void render(final BsonWriter writer) {
        BSON_VALUE_CODEC.encode(writer, literalValue, DEFAULT_CONTEXT);
    }
}
