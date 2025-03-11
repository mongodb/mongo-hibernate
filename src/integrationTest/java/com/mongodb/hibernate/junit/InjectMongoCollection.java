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

package com.mongodb.hibernate.junit;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.mongodb.client.MongoCollection;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * The annotated field must be static and of the {@link MongoCollection}{@code <BsonDocument>} type. It is injected
 * {@linkplain BeforeAllCallback#beforeAll(ExtensionContext) before all} tests, and is
 * {@linkplain MongoCollection#drop() dropped} {@linkplain BeforeEachCallback#beforeEach(ExtensionContext) before each}
 * test.
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface InjectMongoCollection {
    String value();
}
