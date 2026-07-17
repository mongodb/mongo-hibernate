/*
 * Copyright 2026-present MongoDB, Inc.
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

package com.mongodb.hibernate.boot;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.boot.ClassElementChecker;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClassElementCheckerTests {

    @Deprecated
    String pointless;

    @Test
    void testMethodForbidden() {
        Assertions.assertThrows(
                FeatureNotSupportedException.class,
                () -> ClassElementChecker.check(
                        ClassElementCheckerTests.class, ClassElementChecker.forbid(Test.class)));
    }

    @Test
    void testFieldForbidden() {
        Assertions.assertThrows(
                FeatureNotSupportedException.class,
                () -> ClassElementChecker.check(
                        ClassElementCheckerTests.class, ClassElementChecker.forbid(Deprecated.class)));
    }

    @Test
    void testOk() {
        ClassElementChecker.check(ClassElementCheckerTests.class, ClassElementChecker.forbid(Nullable.class));
    }
}
