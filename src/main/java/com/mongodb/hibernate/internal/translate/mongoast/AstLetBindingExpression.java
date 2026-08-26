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

package com.mongodb.hibernate.internal.translate.mongoast;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;

/** Define an expression with locally bound variables */
public record AstLetBindingExpression(AstExpression in, SortedMap<String, AstExpression> vars)
        implements AstExpression {

    @Override
    public StructuralKey structuralKey() {
        return new StructuralKey("LetBinding", List.of(in, vars));
    }

    @Override
    public AstExpression mapChildren(AstNodeRewriter rewriter) {
        var newVars = new TreeMap<String, AstExpression>();
        vars.forEach((name, value) -> newVars.put(name, rewriter.rewrite(value)));
        return new AstLetBindingExpression(rewriter.rewrite(in), newVars);
    }

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeStartDocument();
        writer.writeName("$let");
        {
            writer.writeStartDocument();
            writer.writeStartDocument("vars");
            for (var v : vars.entrySet()) {
                writer.writeName(v.getKey());
                v.getValue().render(writer, binderConsumer);
            }
            writer.writeEndDocument();
            writer.writeName("in");
            in.render(writer, binderConsumer);
            writer.writeEndDocument();
        }
        writer.writeEndDocument();
    }
}
