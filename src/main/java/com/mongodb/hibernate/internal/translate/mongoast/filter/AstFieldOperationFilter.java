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

package com.mongodb.hibernate.internal.translate.mongoast.filter;

import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * See <a href="https://www.mongodb.com/docs/manual/reference/glossary/#std-term-query-predicate">query predicate</a>,
 * <a href="https://www.mongodb.com/docs/manual/tutorial/query-documents/">Query Documents</a>.
 *
 * @see AstEmptyFilter
 * @hidden
 */
public record AstFieldOperationFilter(String fieldPath, AstFilterOperation filterOperation) implements AstFilter {
    @Override
    public AstFilter mapChildren(AstNodeRewriter rewriter) {
        return new AstFieldOperationFilter(fieldPath, rewriter.rewrite(filterOperation));
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        {
            writer.writeName(fieldPath);
            filterOperation.render(writer, binderConsumer);
        }
        writer.writeEndDocument();
    }
}
