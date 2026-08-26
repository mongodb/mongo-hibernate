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

package com.mongodb.hibernate.internal.translate.mongoast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Asserts that an expression describes its structure exactly, and that every component it names is read from the
 * expression rather than fixed.
 *
 * <p>Turning a key into a number is {@link VNRegistry}'s business and is tested there; what each expression puts in its
 * key is tested here.
 */
public final class AstStructuralKeyAssertions {

    private AstStructuralKeyAssertions() {}

    /**
     * Asserts that {@code subject} describes itself as {@code expected}, and that each of {@code variants} -- each
     * differing from {@code subject} in one component -- describes itself differently, both from {@code subject} and
     * from the other variants.
     *
     * <p>The variants are what distinguish a component that is read from one that merely happens to match: a key naming
     * a component but ignoring the field it comes from still equals {@code expected} for one chosen subject.
     */
    public static void assertStructuralKey(AstExpression subject, StructuralKey expected, AstExpression... variants) {
        assertEquals(expected, subject.structuralKey(), "the expression must describe its structure exactly");

        StructuralKey subjectKey = subject.structuralKey();
        for (AstExpression variant : variants) {
            assertNotEquals(
                    subjectKey,
                    variant.structuralKey(),
                    () -> "a differing component must change the key, but " + variant + " described itself the same as "
                            + subject);
        }
        for (int i = 0; i < variants.length; i++) {
            for (int j = i + 1; j < variants.length; j++) {
                AstExpression left = variants[i];
                AstExpression right = variants[j];
                assertNotEquals(
                        left.structuralKey(),
                        right.structuralKey(),
                        () -> "variants differing in different components must not describe themselves alike: " + left
                                + " and " + right);
            }
        }
    }
}
