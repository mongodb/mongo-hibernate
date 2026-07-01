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

package com.mongodb.hibernate.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when {@code spring.jpa.database-platform} is {@code MongoDB} (the MongoDB dialect short name). Gates the
 * MongoDB JPA auto-configurations so they activate only when the application has declared MongoDB as its JPA platform,
 * leaving a SQL-backed application untouched even when this module is on its classpath.
 */
final class OnMongoDatabasePlatformCondition extends SpringBootCondition {

    // The MongoDB dialect short name (MongoConstants.MONGO_DIALECT_SHORT_NAME in the core module). Duplicated here
    // because that constant is in an unexported internal package.
    private static final String MONGO_DATABASE_PLATFORM = "MongoDB";

    private static final String DATABASE_PLATFORM_PROPERTY = "spring.jpa.database-platform";

    OnMongoDatabasePlatformCondition() {}

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var message = ConditionMessage.forCondition("MongoDB JPA platform");
        var platform = context.getEnvironment().getProperty(DATABASE_PLATFORM_PROPERTY);
        return MONGO_DATABASE_PLATFORM.equals(platform)
                ? ConditionOutcome.match(message.foundExactly(DATABASE_PLATFORM_PROPERTY + "=" + MONGO_DATABASE_PLATFORM))
                : ConditionOutcome.noMatch(
                        message.didNotFind(DATABASE_PLATFORM_PROPERTY + "=" + MONGO_DATABASE_PLATFORM).atAll());
    }
}
