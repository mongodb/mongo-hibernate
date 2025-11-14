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

package com.mongodb.hibernate.internal.id.objectid;

import static org.hibernate.generator.EventTypeSets.INSERT_ONLY;

import java.io.Serial;
import java.lang.reflect.Member;
import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.jspecify.annotations.Nullable;

/**
 * @see com.mongodb.hibernate.annotations.ObjectIdGenerator
 * @mongoCme Must be thread-safe.
 */
@SuppressWarnings("MissingSummary")
public final class ObjectIdGenerator implements BeforeExecutionGenerator {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final org.bson.codecs.ObjectIdGenerator GENERATOR = new org.bson.codecs.ObjectIdGenerator();

    /** @see Generator */
    public ObjectIdGenerator(
            com.mongodb.hibernate.annotations.ObjectIdGenerator config,
            Member annotatedMember,
            CustomIdGeneratorCreationContext context) {}

    /** @see Generator */
    public ObjectIdGenerator(
            com.mongodb.hibernate.annotations.ObjectIdGenerator config,
            Member annotatedMember,
            GeneratorCreationContext context) {}

    @Override
    public Object generate(
            SharedSessionContractImplementor session,
            Object owner,
            @Nullable Object currentValue,
            EventType eventType) {
        if (currentValue != null) {
            return currentValue;
        }
        return GENERATOR.generate();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return INSERT_ONLY;
    }

    @Override
    public boolean allowAssignedIdentifiers() {
        return true;
    }
}
