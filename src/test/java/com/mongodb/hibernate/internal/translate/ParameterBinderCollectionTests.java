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

import static com.mongodb.hibernate.internal.translate.AbstractMqlTranslator.renderMongoAstNode;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstParameterMarker;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.junit.jupiter.api.Test;

class ParameterBinderCollectionTests {

    private static JdbcParameterBinder binder() {
        return (statement, startPosition, jdbcParameterBindings, executionContext) -> {};
    }

    private static List<JdbcParameterBinder> render(AstDocument document) {
        var parameterBinders = new ArrayList<JdbcParameterBinder>();
        renderMongoAstNode(document, parameterBinders::add);
        return parameterBinders;
    }

    @Test
    void testBindersFollowRenderingOrderRatherThanConstructionOrder() {
        var first = binder();
        var second = binder();

        var document = new AstDocument(List.of(
                new AstElement("a", new AstParameterMarker(second)),
                new AstElement("b", new AstParameterMarker(first))));

        assertEquals(List.of(second, first), render(document));
    }

    @Test
    void testMarkerRenderedTwiceContributesTwoBinders() {
        var shared = binder();
        var marker = new AstParameterMarker(shared);

        var document = new AstDocument(List.of(new AstElement("a", marker), new AstElement("b", marker)));

        assertEquals(List.of(shared, shared), render(document));
    }
}
