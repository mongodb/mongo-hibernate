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

package com.mongodb.hibernate.internal.translate;

import static com.mongodb.hibernate.internal.translate.mongoast.AstNodeAssertions.assertValueRendering;

import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstParameterMarker;
import java.util.List;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.junit.jupiter.api.Test;

class ParameterBinderCollectionTests {

    private static JdbcParameterBinder binder() {
        return (statement, startPosition, jdbcParameterBindings, executionContext) -> {};
    }

    @Test
    void testBindersFollowRenderingOrderRatherThanConstructionOrder() {
        var first = binder();
        var second = binder();

        var document = new AstDocument(List.of(
                new AstElement("a", new AstParameterMarker(second, 0)),
                new AstElement("b", new AstParameterMarker(first, 1))));
        assertValueRendering("""
                {"": {"a": ?, "b": ?}}""", List.of(second, first), document);
    }

    @Test
    void testMarkerRenderedTwiceContributesTwoBinders() {
        var shared = binder();
        var marker = new AstParameterMarker(shared, 0);

        var document = new AstDocument(List.of(new AstElement("a", marker), new AstElement("b", marker)));
        assertValueRendering("""
                {"": {"a": ?, "b": ?}}""", List.of(shared, shared), document);
    }
}
