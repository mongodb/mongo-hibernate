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

import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/**
 * One branch of a {@link AstSwitchExpression}, rendered as {@code {"case": <caseExpression>, "then":
 * <thenExpression>}}. {@code caseExpression} is a boolean aggregation expression; {@code thenExpression} is the value
 * produced when it is {@code true}.
 *
 * @see AstSwitchExpression
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstSwitchCase(AstExpression caseExpression, AstExpression thenExpression) implements AstNode {
    @Override
    public AstSwitchCase mapChildren(AstNodeRewriter rewriter) {
        return new AstSwitchCase(rewriter.rewrite(caseExpression), rewriter.rewrite(thenExpression));
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        writer.writeName("case");
        caseExpression.render(writer, binderConsumer);
        writer.writeName("then");
        thenExpression.render(writer, binderConsumer);
        writer.writeEndDocument();
    }
}
