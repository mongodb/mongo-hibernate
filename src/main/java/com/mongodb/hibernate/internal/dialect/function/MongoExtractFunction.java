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

import static com.mongodb.hibernate.internal.dialect.function.FunctionParameterDefinition.addOne;
import static com.mongodb.hibernate.internal.dialect.function.FunctionParameterDefinition.ceilingDivideAsInt;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.EXPRESSION;
import static org.hibernate.query.sqm.produce.function.FunctionParameterType.TEMPORAL;
import static org.hibernate.query.sqm.produce.function.FunctionParameterType.TEMPORAL_UNIT;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLetBindingExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNamedOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstVariableExpression;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.ArgumentTypesValidator;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionArgumentTypeResolvers;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.ExtractUnit;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Defines an operator for a function as used by {@link MongoExpressionPositionalFunction},
 * {@link MongoExpressionNamedFunction}, and {@link MongoExpressionUnaryFunction} that can either provide a fixed
 * operator name or generate an appropriate operator based on a parameter
 */
public final class MongoExtractFunction extends AbstractSqmSelfRenderingFunctionDescriptor
        implements ExpressionFunction {

    public MongoExtractFunction(TypeConfiguration typeConfiguration) {
        super(
                "extract",
                new ArgumentTypesValidator(StandardArgumentsValidators.exactly(2), TEMPORAL_UNIT, TEMPORAL),
                StandardFunctionReturnTypeResolvers.useArgType(1),
                StandardFunctionArgumentTypeResolvers.invariant(typeConfiguration, TEMPORAL_UNIT, TEMPORAL));
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> sqlAstArguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);

        var input = translator.acceptAndYield(sqlAstArguments.get(1), EXPRESSION);
        var unit = ((ExtractUnit) sqlAstArguments.get(0)).getUnit();
        translator.yield(
                EXPRESSION,
                switch (unit) {
                    case DAY, DAY_OF_MONTH -> dateOperator("$dayOfMonth", input);
                    case DAY_OF_WEEK -> dateOperator("$dayOfWeek", input);
                    case DAY_OF_YEAR -> dateOperator("$dayOfYear", input);
                    case EPOCH ->
                        new AstUnaryOperatorExpression(
                                "$toLong",
                                new AstBinaryOperatorExpression(
                                        AstArithmeticExpressionOperator.DIVIDE,
                                        new AstUnaryOperatorExpression("$toLong", input),
                                        new AstLiteralExpression(new AstLiteral(new BsonInt32(1000)))));
                    case HOUR -> dateOperator("$hour", input);
                    case MINUTE -> dateOperator("$minute", input);
                    case MONTH -> dateOperator("$month", input);
                    case NANOSECOND ->
                        new AstLetBindingExpression(
                                new AstUnaryOperatorExpression(
                                        "$toLong",
                                        new AstBinaryOperatorExpression(
                                                AstArithmeticExpressionOperator.ADD,
                                                new AstBinaryOperatorExpression(
                                                        AstArithmeticExpressionOperator.MULTIPLY,
                                                        dateOperator("$millisecond", new AstVariableExpression("time")),
                                                        new AstLiteralExpression(
                                                                new AstLiteral(new BsonInt32(1_000_000)))),
                                                new AstBinaryOperatorExpression(
                                                        AstArithmeticExpressionOperator.MULTIPLY,
                                                        dateOperator("$second", new AstVariableExpression("time")),
                                                        new AstLiteralExpression(
                                                                new AstLiteral(new BsonInt32(1_000_000_000)))))),
                                new TreeMap<>(Map.of("time", input)));
                    case QUARTER -> ceilingDivideAsInt(dateOperator("$month", input), 3);
                    case SECOND ->
                        new AstLetBindingExpression(
                                new AstBinaryOperatorExpression(
                                        AstArithmeticExpressionOperator.ADD,
                                        dateOperator("$second", new AstVariableExpression("time")),
                                        new AstBinaryOperatorExpression(
                                                AstArithmeticExpressionOperator.DIVIDE,
                                                dateOperator("$millisecond", new AstVariableExpression("time")),
                                                new AstLiteralExpression(new AstLiteral(new BsonInt32(1000))))),
                                new TreeMap<>(Map.of("time", input)));
                    case WEEK_OF_YEAR -> sundayBasedWeek(input, "$dayOfYear");
                    case WEEK -> dateOperator("$isoWeek", input);
                    case WEEK_OF_MONTH -> sundayBasedWeek(input, "$dayOfMonth");
                    case YEAR -> dateOperator("$year", input);
                    default -> throw new FeatureNotSupportedException("Time unit %s not supported".formatted(unit));
                });
    }

    /**
     * Hibernate defines {@link org.hibernate.query.common.TemporalUnit#WEEK_OF_YEAR} nd {}as a 1-origin count whose
     * weeks start on Sunday, which {@code ExtractFunction} computes as {@code ceiling((dayOfYear - dayOfWeek)/7.0 + 1)}
     * with Sunday as day one. MongoDB's {@code $week} is a different definition, 0-origin and without a week-year, and
     * no constant adjustment relates the two: {@code $week + 1} agrees with the rule except in a year that begins on a
     * Sunday, where {@code $week} has no week 0 and so already starts at 1. Transliterating the rule avoids that
     * special case.
     */
    private static AstExpression sundayBasedWeek(AstExpression input, String operator) {
        var time = new AstVariableExpression("time");
        return new AstLetBindingExpression(
                addOne(ceilingDivideAsInt(
                        new AstBinaryOperatorExpression(
                                AstArithmeticExpressionOperator.SUBTRACT,
                                dateOperator(operator, time),
                                dateOperator("$dayOfWeek", time)),
                        7)),
                new TreeMap<>(Map.of("time", input)));
    }

    private static AstExpression dateOperator(String operator, AstExpression date) {
        return new AstNamedOperatorExpression(
                operator,
                new TreeMap<>(Map.of(
                        "date",
                        date,
                        "timezone",
                        new AstLiteralExpression(new AstLiteral(
                                new BsonString(ZoneId.systemDefault().getId()))))));
    }
}
