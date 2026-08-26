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

import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNodeRewriter;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * Renders {@code { $expr: expression }} as a MongoDB aggregation expression predicate in a {@code $match} stage. Used
 * for comparisons that the compact query form cannot express, including non-equijoin {@code ON} conditions inside the
 * {@code $lookup} pipeline form.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstExprFilter(AstExpression expression) implements AstFilter {
    @Override
    public AstFilter mapChildren(AstNodeRewriter rewriter) {
        return new AstExprFilter(rewriter.rewrite(expression));
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        writer.writeName("$expr");
        expression.render(writer, binderConsumer);
        writer.writeEndDocument();
    }
}
