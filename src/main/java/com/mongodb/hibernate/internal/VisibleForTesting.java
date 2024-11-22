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

package com.mongodb.hibernate.internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Denotes that the annotated program element is made more accessible than otherwise necessary for the purpose of
 * testing. The annotated program element must be used as if it had the {@linkplain #otherwise() intended} access
 * modifier for any purpose other than testing.
 *
 * <p>This class is not part of the public API and may be removed or changed at any time
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD})
public @interface VisibleForTesting {
    VisibleForTesting.AccessModifier otherwise();

    public static enum AccessModifier {
        PRIVATE,
        PACKAGE,
        PROTECTED;

        private AccessModifier() {}
    }
}
