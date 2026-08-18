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

package com.mongodb.hibernate.query.select;

import java.math.BigDecimal;
import java.time.Instant;
import org.bson.types.ObjectId;

/**
 * Holds one constant per supported scalar type, referenced by fully-qualified name in HQL. A static field reference is
 * what produces a literal of an arbitrary type; numeric HQL literals such as {@code 1L} take a different translator
 * path.
 */
public final class QueryLiteralConstants {

    public static final String STRING = "War & Peace";
    public static final Character CHARACTER = 'c';
    public static final int INT = 42;
    public static final long LONG = 43L;
    public static final double DOUBLE = 4.5;
    public static final boolean BOOLEAN = true;
    public static final BigDecimal BIG_DECIMAL = new BigDecimal("4.25");
    public static final ObjectId OBJECT_ID = new ObjectId("000000000000000000000001");
    public static final Instant INSTANT = Instant.parse("2025-01-04T10:05:01Z");

    private QueryLiteralConstants() {}
}
