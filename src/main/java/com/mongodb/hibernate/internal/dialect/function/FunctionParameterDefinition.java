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

import com.mongodb.hibernate.internal.MongoAssertions;
import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstPositionalOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import org.hibernate.query.sqm.produce.function.ArgumentTypesValidator;
import org.hibernate.query.sqm.produce.function.ArgumentsValidator;
import org.hibernate.query.sqm.produce.function.FunctionArgumentTypeResolver;
import org.hibernate.query.sqm.produce.function.FunctionParameterType;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionArgumentTypeResolvers;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Defines a parameter to a {@link MongoExpressionPositionalFunction} or {@link MongoExpressionNamedFunction}
 *
 * @param <N> the name for this function; normally {@link String} for {@link MongoExpressionNamedFunction} and
 *     {@link Void} for {@link MongoExpressionPositionalFunction}
 */
public abstract sealed class FunctionParameterDefinition<N>
        permits FunctionParameterDefinition.Default,
                FunctionParameterDefinition.Mapped,
                FunctionParameterDefinition.Missing,
                FunctionParameterDefinition.Required {
    /**
     * Utility function that transforms an operation into one that returns one more than its original output
     *
     * <p>This is provided to help adjust indices as Mongo uses zero-based string indices and SQL/HQL uses one-based
     * string indices
     *
     * @param input the expression to transform
     * @return an expression that returns one more than the provided expression
     */
    public static AstExpression addOne(AstExpression input) {
        return new AstBinaryOperatorExpression(
                AstArithmeticExpressionOperator.ADD, input, new AstLiteralExpression(new AstLiteral(new BsonInt32(1))));
    }

    /**
     * Utility function that divides a value and applies another operation after and converts the result to an integer
     *
     * @param input the value to divide
     * @param divisor the divisor to use in the calculation
     * @param operator the operator to apply after division (typically, <code>$floor</code> or <code>$ceil</code>
     * @return a function that will apply the additional operations to the tree provided
     */
    public static AstExpression divideAndSomethingAsInt(AstExpression input, int divisor, String operator) {
        return new AstUnaryOperatorExpression(
                "$toInt",
                new AstUnaryOperatorExpression(
                        operator,
                        new AstBinaryOperatorExpression(
                                AstArithmeticExpressionOperator.DIVIDE,
                                input,
                                new AstLiteralExpression(new AstLiteral(new BsonInt32(divisor))))));
    }

    /**
     * Utility function that transforms an operation into one that returns one less than its original output
     *
     * <p>This is provided to help adjust indices as Mongo uses zero-based string indices and SQL/HQL uses one-based
     * string indices
     *
     * @param input the expression to transform
     * @return an expression that returns one less than the provided expression
     */
    public static AstExpression subtractOne(AstExpression input) {
        return new AstBinaryOperatorExpression(
                AstArithmeticExpressionOperator.SUBTRACT,
                input,
                new AstLiteralExpression(new AstLiteral(new BsonInt32(1))));
    }

    /**
     * Define a required parameter that has a particular type
     *
     * @param type the type of the parameter
     * @return a parameter definition
     * @see MongoExpressionPositionalFunction
     */
    @SuppressWarnings("NullAway")
    public static FunctionParameterDefinition<Void> required(FunctionParameterType type) {
        return new Required<>(null, type);
    }

    /**
     * Define a required parameter that has a particular type
     *
     * @param name the property name that should be emitted in the MQL
     * @param type the type of the parameter
     * @return a parameter definition
     * @see MongoExpressionNamedFunction
     */
    public static FunctionParameterDefinition<String> required(String name, FunctionParameterType type) {
        return new Required<>(name, type);
    }

    /**
     * Define an optional parameter that will have a default value inserted if not provided in the HQL
     *
     * <p>Note that the type of this parameter is inferred to match the type of the default value provided.
     *
     * @param defaultValue the default value to insert if a matching argument is not supplied in the HQL
     * @return a parameter definition
     * @see MongoExpressionPositionalFunction
     */
    @SuppressWarnings("NullAway")
    public static FunctionParameterDefinition<Void> orDefault(BsonValue defaultValue) {
        return new Default<>(null, defaultValue);
    }

    /**
     * Define an optional parameter that will be omitted if not provided in the HQL
     *
     * @param type the type of the parameter
     * @return a parameter definition
     * @see MongoExpressionNamedFunction
     */
    @SuppressWarnings("NullAway")
    public static FunctionParameterDefinition<Void> orMissing(FunctionParameterType type) {
        return new Missing<>(null, type);
    }

    /**
     * Define an optional parameter that will be omitted if not provided in the HQL
     *
     * @param name the property name that should be emitted in the MQL
     * @param type the type of the parameter
     * @return a parameter definition
     * @see MongoExpressionNamedFunction
     */
    public static FunctionParameterDefinition<String> orMissing(String name, FunctionParameterType type) {
        return new Missing<>(name, type);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <N> ArgumentsValidator argumentsValidatorFromParameters(
            RelativePositionChecker position, FunctionParameterDefinition<N>... parameters) {
        var types = new FunctionParameterType[parameters.length];
        for (var i = 0; i < parameters.length; i++) {
            parameters[i].check(position);
            types[i] = parameters[i].type();
        }
        return new ArgumentTypesValidator(StandardArgumentsValidators.between(position.min(), position.max()), types);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <N> FunctionArgumentTypeResolver typeResolverFromParameters(
            TypeConfiguration typeConfiguration, FunctionParameterDefinition<N>... parameters) {
        return StandardFunctionArgumentTypeResolvers.byArgument(Stream.of(parameters)
                .map(parameter ->
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(typeConfiguration, parameter.type()))
                .toArray(FunctionArgumentTypeResolver[]::new));
    }

    static <N> void processArguments(
            FunctionParameterDefinition<N>[] parameters,
            List<? extends SqlAstNode> arguments,
            SqlAstTranslator<?> walker,
            ArgumentCollector<N> collector) {
        var translator = AbstractMqlTranslator.cast(walker);
        for (var index = 0; index < parameters.length; index++) {
            var parameter = parameters[index];
            if (index < arguments.size()) {
                collector.append(
                        parameter.name(),
                        parameter.asNode(translator.acceptAndYield(arguments.get(index), EXPRESSION)));
            } else {
                parameter.asNode().ifPresent(expression -> collector.append(parameter.name(), expression));
            }
        }
    }

    abstract N name();

    abstract FunctionParameterType type();

    abstract Optional<AstExpression> asNode();

    abstract AstExpression asNode(AstExpression expression);

    abstract void check(RelativePositionChecker position);

    /**
     * Transforms the user-provided value before passing it into the function
     *
     * <p>This is provided to allow adjusting the input value in the case that the MQL operator does not exactly match
     * the semantics of the SQL/HQL function. In particular, this is meant to help adjust string indices as MQL uses
     * zero-based strings and SQL uses one-based strings.
     *
     * <p>Default values are <em>not</em> transformed; they are left as originally provided
     *
     * @param mapper the transformation to apply to the value
     * @return a parameter definition that will transform the input
     */
    public final FunctionParameterDefinition<N> map(Function<? super AstExpression, ? extends AstExpression> mapper) {
        return new Mapped<>(this, mapper);
    }

    /**
     * Ensures that an input value, which must be a number, is greater than zero
     *
     * @param input the expression to clamp
     * @return an expression which returns the original value or zero if the original value is less than zero
     */
    public static AstExpression atLeastZero(AstExpression input) {
        // All variables are bound, so we don't need to worry about aliasing
        return new AstPositionalOperatorExpression(
                "$max", List.of(input, new AstLiteralExpression(new AstLiteral(new BsonInt32(0)))));
    }

    interface RelativePositionChecker {
        void incrementRequired();

        void incrementDefault();

        void incrementMissing();

        int max();

        int min();
    }

    interface ArgumentCollector<N> {
        void append(N name, AstExpression node);
    }

    static final class PositionalCheck implements RelativePositionChecker {
        private int required;
        private int withDefault;
        private int missing;

        @Override
        public void incrementRequired() {
            MongoAssertions.assertFalse(withDefault > 0 || missing > 0);
            required++;
        }

        @Override
        public void incrementDefault() {
            MongoAssertions.assertFalse(missing > 0);
            withDefault++;
        }

        @Override
        public void incrementMissing() {
            missing++;
        }

        @Override
        public int max() {
            return required + withDefault + missing;
        }

        @Override
        public int min() {
            return required;
        }
    }

    static final class NamedCheck implements RelativePositionChecker {
        private int required;
        private int other;

        @Override
        public void incrementRequired() {
            MongoAssertions.assertFalse(other > 0);
            required++;
        }

        @Override
        public void incrementDefault() {
            other++;
        }

        @Override
        public void incrementMissing() {
            other++;
        }

        @Override
        public int max() {
            return required + other;
        }

        @Override
        public int min() {
            return required;
        }
    }

    static final class Default<N> extends FunctionParameterDefinition<N> {

        private final N name;
        private final BsonValue defaultValue;

        Default(N name, BsonValue defaultValue) {
            super();
            this.name = name;
            this.defaultValue = defaultValue;
        }

        @Override
        N name() {
            return name;
        }

        @Override
        FunctionParameterType type() {
            return switch (defaultValue.getBsonType()) {
                case DOUBLE, DECIMAL128 -> FunctionParameterType.NUMERIC;
                case STRING -> FunctionParameterType.STRING;
                case BINARY -> FunctionParameterType.BINARY;
                case BOOLEAN -> FunctionParameterType.BOOLEAN;
                case DATE_TIME, TIMESTAMP -> FunctionParameterType.TEMPORAL;
                case INT32, INT64 -> FunctionParameterType.INTEGER;
                default ->
                    throw new IllegalArgumentException(
                            "No SQL Type corresponding to BSON type %s".formatted(defaultValue.getBsonType()));
            };
        }

        @Override
        Optional<AstExpression> asNode() {
            return Optional.of(new AstLiteralExpression(new AstLiteral(defaultValue)));
        }

        @Override
        AstExpression asNode(AstExpression expression) {
            return expression;
        }

        @Override
        void check(RelativePositionChecker position) {
            position.incrementDefault();
        }
    }

    static final class Required<N> extends FunctionParameterDefinition<N> {

        private final N name;
        private final FunctionParameterType type;

        public Required(N name, FunctionParameterType type) {
            super();

            this.name = name;
            this.type = type;
        }

        @Override
        N name() {
            return name;
        }

        @Override
        FunctionParameterType type() {
            return type;
        }

        @Override
        Optional<AstExpression> asNode() {
            throw new IllegalStateException("Hibernate did not supply required value");
        }

        @Override
        AstExpression asNode(AstExpression expression) {
            return expression;
        }

        @Override
        void check(RelativePositionChecker position) {
            position.incrementRequired();
        }
    }

    static final class Mapped<N> extends FunctionParameterDefinition<N> {
        private final Function<? super AstExpression, ? extends AstExpression> mapper;
        private final FunctionParameterDefinition<N> inner;

        Mapped(FunctionParameterDefinition<N> inner, Function<? super AstExpression, ? extends AstExpression> mapper) {
            super();
            this.inner = inner;
            this.mapper = mapper;
        }

        @Override
        N name() {
            return inner.name();
        }

        @Override
        FunctionParameterType type() {
            return inner.type();
        }

        @Override
        Optional<AstExpression> asNode() {
            return inner.asNode();
        }

        @Override
        AstExpression asNode(AstExpression expression) {
            return mapper.apply(inner.asNode(expression));
        }

        @Override
        void check(RelativePositionChecker position) {
            inner.check(position);
        }
    }

    static final class Missing<N> extends FunctionParameterDefinition<N> {
        private final N name;
        private final FunctionParameterType type;

        public Missing(N name, FunctionParameterType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        N name() {
            return name;
        }

        @Override
        FunctionParameterType type() {
            return type;
        }

        @Override
        Optional<AstExpression> asNode() {
            return Optional.empty();
        }

        @Override
        AstExpression asNode(AstExpression expression) {
            return expression;
        }

        @Override
        void check(RelativePositionChecker position) {
            position.incrementMissing();
        }
    }
}
