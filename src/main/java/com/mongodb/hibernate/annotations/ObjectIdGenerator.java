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

package com.mongodb.hibernate.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.ValueGenerationType;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

/**
 * Specifies that the value of an annotated persistent attribute, including an entity identifier, is generated using
 * {@link org.bson.codecs.ObjectIdGenerator} {@linkplain BeforeExecutionGenerator before} {@linkplain EventType#INSERT
 * inserting}. If the value is explicitly assigned, then the assigned value is used instead of generating a different
 * one.
 */
@IdGeneratorType(com.mongodb.hibernate.internal.id.objectid.ObjectIdGenerator.class)
@ValueGenerationType(generatedBy = com.mongodb.hibernate.internal.id.objectid.ObjectIdGenerator.class)
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface ObjectIdGenerator {}
