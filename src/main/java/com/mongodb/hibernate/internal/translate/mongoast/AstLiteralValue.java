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

package com.mongodb.hibernate.internal.translate.mongoast;

import org.bson.BsonBoolean;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.codecs.BsonValueCodec;
import org.bson.codecs.EncoderContext;

public record AstLiteralValue(BsonValue literalValue) implements AstValue {

    private static final BsonValueCodec BSON_VALUE_CODEC = new BsonValueCodec();
    private static final EncoderContext DEFAULT_CONTEXT =
            EncoderContext.builder().build();

    public static final AstLiteralValue TRUE = new AstLiteralValue(BsonBoolean.TRUE);
    public static final AstLiteralValue FALSE = new AstLiteralValue(BsonBoolean.FALSE);
    public static final AstLiteralValue NULL = new AstLiteralValue(BsonNull.VALUE);

    @Override
    public void render(BsonWriter writer) {
        BSON_VALUE_CODEC.encode(writer, literalValue, DEFAULT_CONTEXT);
    }
}
