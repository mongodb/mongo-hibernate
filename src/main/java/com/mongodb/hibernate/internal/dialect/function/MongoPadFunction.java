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
import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstComparisonExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstLetBindingExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLogicalOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstLogicalOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNamedOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstPositionalOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
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

public final class MongoPadFunction extends AbstractSqmSelfRenderingFunctionDescriptor implements ExpressionFunction {

    private final boolean left;

    // We bind lpad and rpad because, while the query language defines the behavior for pad, it bases that on the
    // expectation of having lpad and rpad functions
    public MongoPadFunction(TypeConfiguration typeConfiguration, boolean left) {
        super(
                left ? "lpad" : "rpad",
                new ArgumentTypesValidator(
                        StandardArgumentsValidators.between(2, 3),
                        FunctionParameterType.STRING,
                        FunctionParameterType.INTEGER,
                        FunctionParameterType.STRING),
                StandardFunctionReturnTypeResolvers.invariant(Objects.requireNonNull(
                        typeConfiguration.getBasicTypeRegistry().resolve(StandardBasicTypes.STRING))),
                StandardFunctionArgumentTypeResolvers.byArgument(
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.STRING),
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.INTEGER),
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.STRING)));
        this.left = left;
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        var padding = arguments.size() < 3
                ? new AstLiteralExpression(new AstLiteral(new BsonString(" ")))
                : translator.acceptAndYield(arguments.get(2), EXPRESSION);
        // All parameters are processed in the binding so that variables won't shadow anything outside, so fresh names
        // aren't required
        var paddingLength = new AstBinaryOperatorExpression(
                AstArithmeticExpressionOperator.SUBTRACT,
                new AstVariableExpression("targetLen"),
                new AstUnaryOperatorExpression("$strLenCP", new AstVariableExpression("baseStr")));
        var repeats = new AstUnaryOperatorExpression(
                "$ceil",
                new AstBinaryOperatorExpression(
                        AstArithmeticExpressionOperator.DIVIDE,
                        paddingLength,
                        new AstUnaryOperatorExpression("$strLenCP", new AstVariableExpression("padding"))));
        var baseStr = new AstVariableExpression("baseStr");
        var combinedPadding = new AstPositionalOperatorExpression(
                "$substrCP",
                List.of(
                        new AstNamedOperatorExpression(
                                "$reduce",
                                new TreeMap<>(Map.of(
                                        "initialValue",
                                        new AstLiteralExpression(new AstLiteral(new BsonString(""))),
                                        "in",
                                        new AstPositionalOperatorExpression(
                                                "$concat",
                                                left
                                                        ? List.of(
                                                                new AstVariableExpression("padding"),
                                                                new AstVariableExpression("value"))
                                                        : List.of(
                                                                new AstVariableExpression("value"),
                                                                new AstVariableExpression("padding"))),
                                        "input",
                                        new AstPositionalOperatorExpression(
                                                "$range",
                                                List.of(
                                                        new AstLiteralExpression(new AstLiteral(new BsonInt32(0))),
                                                        repeats))))),
                        new AstLiteralExpression(new AstLiteral(new BsonInt32(0))),
                        paddingLength));

        translator.yield(
                EXPRESSION,
                new AstLetBindingExpression(
                        new AstPositionalOperatorExpression(
                                "$cond",
                                List.of(
                                        new AstBinaryOperatorExpression(
                                                AstComparisonExpressionOperator.LTE,
                                                new AstVariableExpression("targetLen"),
                                                new AstLiteralExpression(new AstLiteral(new BsonInt32(0)))),
                                        new AstLiteralExpression(new AstLiteral(new BsonString(""))),
                                        new AstPositionalOperatorExpression(
                                                "$cond",
                                                List.of(
                                                        new AstLogicalOperatorExpression(
                                                                AstLogicalOperator.OR,
                                                                List.of(
                                                                        new AstBinaryOperatorExpression(
                                                                                AstComparisonExpressionOperator.GTE,
                                                                                new AstUnaryOperatorExpression(
                                                                                        "$strLenCP", baseStr),
                                                                                new AstVariableExpression("targetLen")),
                                                                        new AstBinaryOperatorExpression(
                                                                                AstComparisonExpressionOperator.EQ,
                                                                                new AstUnaryOperatorExpression(
                                                                                        "$strLenCP",
                                                                                        new AstVariableExpression(
                                                                                                "padding")),
                                                                                new AstLiteralExpression(
                                                                                        new AstLiteral(
                                                                                                new BsonInt32(0)))))),
                                                        new AstPositionalOperatorExpression(
                                                                "$substrCP",
                                                                List.of(
                                                                        new AstVariableExpression("baseStr"),
                                                                        new AstLiteralExpression(
                                                                                new AstLiteral(new BsonInt32(0))),
                                                                        new AstVariableExpression("targetLen"))),
                                                        new AstPositionalOperatorExpression(
                                                                "$concat",
                                                                left
                                                                        ? List.of(combinedPadding, baseStr)
                                                                        : List.of(baseStr, combinedPadding)))))),
                        new TreeMap<>(Map.of(
                                "baseStr",
                                translator.acceptAndYield(arguments.get(0), EXPRESSION),
                                "targetLen",
                                translator.acceptAndYield(arguments.get(1), EXPRESSION),
                                "padding",
                                padding))));
    }
}
