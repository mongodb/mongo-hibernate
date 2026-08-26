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

import static com.mongodb.hibernate.internal.translate.mongoast.AstMapChildrenAssertions.assertMapsChildren;
import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertValueRendering;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class AstParameterMarkerTests {

    // Captures so that each call yields a distinct instance: a non-capturing lambda may be shared by the JVM.
    private static JdbcParameterBinder binder() {
        var distinct = new Object();
        return (statement, startPosition, jdbcParameterBindings, executionContext) -> distinct.hashCode();
    }

    @Test
    void testRender() {
        var binder = binder();
        var expr = new AstParameterMarker(binder, 0);
        assertValueRendering("""
                             {"": ?}""", List.of(binder), expr);
    }

    @Test
    void testMapChildren() {
        assertMapsChildren(new AstParameterMarker(binder(), 0));
    }

    @Nested
    class Equality {
        @Test
        void testEqualWhenParameterIdMatchesDespiteDifferentBinders() {
            var marker = new AstParameterMarker(binder(), 1);
            var other = new AstParameterMarker(binder(), 1);
            assertThat(marker).isEqualTo(other).hasSameHashCodeAs(other);
        }

        @Test
        void testNotEqualWhenParameterIdDiffers() {
            assertThat(new AstParameterMarker(binder(), 1)).isNotEqualTo(new AstParameterMarker(binder(), 2));
        }

        @Test
        void testEqualWithoutParameterIdWhenBinderIsSame() {
            var binder = binder();
            var marker = new AstParameterMarker(binder, null);
            var other = new AstParameterMarker(binder, null);
            assertThat(marker).isEqualTo(other).hasSameHashCodeAs(other);
        }

        @Test
        void testNotEqualWithoutParameterIdWhenBinderDiffers() {
            assertThat(new AstParameterMarker(binder(), null)).isNotEqualTo(new AstParameterMarker(binder(), null));
        }

        @Test
        void testNotEqualWhenOnlyOneHasParameterId() {
            var binder = binder();
            assertThat(new AstParameterMarker(binder, 1)).isNotEqualTo(new AstParameterMarker(binder, null));
        }

        @Test
        void testEqualToItselfWithoutParameterId() {
            var marker = new AstParameterMarker(binder(), null);
            assertThat(marker).isEqualTo(marker);
        }
    }
}
