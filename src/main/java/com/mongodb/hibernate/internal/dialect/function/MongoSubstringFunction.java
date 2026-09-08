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

import static com.mongodb.hibernate.internal.dialect.function.FunctionParameterDefinition.atLeastZero;
import static com.mongodb.hibernate.internal.dialect.function.FunctionParameterDefinition.subtractOne;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.EXPRESSION;

import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstComparisonExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLetBindingExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstPositionalOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstVariableExpression;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.bson.BsonInt32;
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

public final class MongoSubstringFunction extends AbstractSqmSelfRenderingFunctionDescriptor
        implements ExpressionFunction {

    public MongoSubstringFunction(TypeConfiguration typeConfiguration) {
        super(
                "substring",
                new ArgumentTypesValidator(
                        StandardArgumentsValidators.between(2, 3),
                        FunctionParameterType.STRING,
                        FunctionParameterType.INTEGER,
                        FunctionParameterType.INTEGER),
                StandardFunctionReturnTypeResolvers.invariant(Objects.requireNonNull(
                        typeConfiguration.getBasicTypeRegistry().resolve(StandardBasicTypes.STRING))),
                StandardFunctionArgumentTypeResolvers.byArgument(
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.STRING),
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.INTEGER),
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
        var vars = new TreeMap<>(Map.of(
                "str",
                translator.acceptAndYield(arguments.get(0), EXPRESSION),
                "start",
                subtractOne(translator.acceptAndYield(arguments.get(1), EXPRESSION))));
        AstExpression length;
        if (arguments.size() == 3) {
            vars.put("len", translator.acceptAndYield(arguments.get(2), EXPRESSION));
            length = atLeastZero(new AstBinaryOperatorExpression(
                    AstArithmeticExpressionOperator.ADD,
                    new AstVariableExpression("len"),
                    new AstBinaryOperatorExpression(
                            AstArithmeticExpressionOperator.SUBTRACT,
                            new AstVariableExpression("start"),
                            new AstVariableExpression("adjustedStart"))));
        } else {
            length = new AstLiteralExpression(new AstLiteral(new BsonInt32(Integer.MAX_VALUE)));
        }

        translator.yield(
                EXPRESSION,
                new AstLetBindingExpression(
                        new AstLetBindingExpression(
                                new AstPositionalOperatorExpression(
                                        "$substrCP",
                                        List.of(
                                                new AstVariableExpression("str"),
                                                new AstVariableExpression("adjustedStart"),
                                                length)),
                                new TreeMap<>(Map.of(
                                        "adjustedStart",
                                        new AstPositionalOperatorExpression(
                                                "$cond",
                                                List.of(
                                                        new AstBinaryOperatorExpression(
                                                                AstComparisonExpressionOperator.LT,
                                                                new AstVariableExpression("start"),
                                                                new AstLiteralExpression(
                                                                        new AstLiteral(new BsonInt32(0)))),
                                                        new AstLiteralExpression(new AstLiteral(new BsonInt32(0))),
                                                        new AstVariableExpression("start")))))),
                        vars));
    }
}
