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

package com.mongodb.hibernate.internal.dialect.function;

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.EXPRESSION;

import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.AstLetBindingExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNamedOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstPositionalOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstVariableExpression;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.ArgumentTypesValidator;
import org.hibernate.query.sqm.produce.function.FunctionParameterType;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionArgumentTypeResolvers;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.spi.SqlAstNode;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.spi.TypeConfiguration;

public final class MongoRepeatFunction extends AbstractSqmSelfRenderingFunctionDescriptor
        implements ExpressionFunction {

    public MongoRepeatFunction(TypeConfiguration typeConfiguration) {
        super(
                "repeat",
                new ArgumentTypesValidator(
                        StandardArgumentsValidators.exactly(2),
                        FunctionParameterType.STRING,
                        FunctionParameterType.INTEGER),
                StandardFunctionReturnTypeResolvers.invariant(Objects.requireNonNull(
                        typeConfiguration.getBasicTypeRegistry().resolve(StandardBasicTypes.STRING))),
                StandardFunctionArgumentTypeResolvers.byArgument(
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.STRING),
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.INTEGER)));
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        // All parameters are processed in the binding so that variables won't shadow anything outside, so fresh names
        // aren't required
        translator.yield(
                EXPRESSION,
                new AstLetBindingExpression(
                        new AstNamedOperatorExpression(
                                "$reduce",
                                new TreeMap<>(Map.of(
                                        "initialValue", new AstLiteralExpression(new AstLiteral(new BsonString(""))),
                                        "in",
                                                new AstPositionalOperatorExpression(
                                                        "$concat",
                                                        List.of(
                                                                new AstVariableExpression("value"),
                                                                new AstVariableExpression("repeatStr"))),
                                        "input",
                                                new AstPositionalOperatorExpression(
                                                        "$range",
                                                        List.of(
                                                                new AstLiteralExpression(
                                                                        new AstLiteral(new BsonInt32(0))),
                                                                new AstVariableExpression("count")))))),
                        new TreeMap<>(Map.of(
                                "repeatStr",
                                translator.acceptAndYield(arguments.get(0), EXPRESSION),
                                "count",
                                translator.acceptAndYield(arguments.get(1), EXPRESSION)))));
    }
}
