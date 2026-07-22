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
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstPositionalOperatorExpression;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.spi.TypeConfiguration;

/** Defines a HQL function that maps to a Mongo operator where the parameters are included as an array */
public final class MongoExpressionPositionalFunction extends AbstractSqmSelfRenderingFunctionDescriptor
        implements ExpressionFunction {
    public static final ArgumentModifier NONE = (arguments) -> {};
    private final String mongoOperator;
    private final ArgumentModifier modifier;
    private final FunctionParameterDefinition<Void>[] parameters;
    private final Function<? super AstExpression, ? extends AstExpression> outputMapper;

    /**
     * Create a new function definition
     *
     * @param hqlName the name for the function in HQL
     * @param mongoOperator the operator in Mongo, including the leading <code>$</code>
     * @param typeConfiguration the type information of the Hibernate context
     * @param returnType the type that this function will return
     * @param parameters a list of parameters accepted by the function; this can include required or optional arguments,
     *     but the required arguments must be first, the ones with default parameters second, and ones that can be
     *     missing last
     */
    @SafeVarargs
    public MongoExpressionPositionalFunction(
            String hqlName,
            String mongoOperator,
            TypeConfiguration typeConfiguration,
            BasicTypeReference<?> returnType,
            FunctionParameterDefinition<Void>... parameters) {
        this(hqlName, mongoOperator, typeConfiguration, returnType, Function.identity(), NONE, parameters);
    }
    /**
     * Create a new function definition
     *
     * @param hqlName the name for the function in HQL
     * @param mongoOperator the operator in Mongo, including the leading <code>$</code>
     * @param typeConfiguration the type information of the Hibernate context
     * @param returnType the type that this function will return
     * @param modifier modifies the argument list before generating MQL; this happens after all argument pre-processing
     *     is complete
     * @param outputMapper allows wrapping the generated output with additional operations; this is intended to provide
     *     a mechanism that
     * @param parameters a list of parameters accepted by the function; this can include required or optional arguments,
     *     but the required arguments must be first, the ones with default parameters second, and ones that can be
     *     missing last
     * @see FunctionParameterDefinition#addOne(AstExpression)
     * @see FunctionParameterDefinition#subtractOne(AstExpression)
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public MongoExpressionPositionalFunction(
            String hqlName,
            String mongoOperator,
            TypeConfiguration typeConfiguration,
            BasicTypeReference<?> returnType,
            Function<? super AstExpression, ? extends AstExpression> outputMapper,
            ArgumentModifier modifier,
            FunctionParameterDefinition<Void>... parameters) {
        super(
                hqlName,
                FunctionParameterDefinition.argumentsValidatorFromParameters(
                        new FunctionParameterDefinition.PositionalCheck(), parameters),
                StandardFunctionReturnTypeResolvers.invariant(Objects.requireNonNull(
                        typeConfiguration.getBasicTypeRegistry().resolve(returnType))),
                FunctionParameterDefinition.typeResolverFromParameters(typeConfiguration, parameters));
        this.mongoOperator = mongoOperator;
        this.modifier = modifier;
        this.outputMapper = outputMapper;
        this.parameters = parameters;
    }

    /**
     * Applies a series of modifications in sequence
     *
     * @param modifiers the modifications to apply
     * @return an modifier that applies all modifications
     */
    public static ArgumentModifier allModifications(ArgumentModifier... modifiers) {
        return arguments -> {
            for (final var modifier : modifiers) {
                modifier.mutate(arguments);
            }
        };
    }

    /**
     * Changes a length parameter into an end position
     *
     * @param start the position of the start argument
     * @param length the position of the length argument
     * @return an argument modifier that replaces the length argument with an end position by adding the start argument
     *     to that position; the start argument is unmodified
     */
    public static ArgumentModifier offsetToEnd(int start, int length) {
        return arguments -> {
            if (start < arguments.size() && length < arguments.size()) {
                final var end = new AstBinaryOperatorExpression(
                        AstArithmeticExpressionOperator.ADD, arguments.get(start), arguments.get(length));
                arguments.set(length, end);
            }
        };
    }

    /**
     * Swaps two arguments
     *
     * @param a the index of the first argument
     * @param b the index of the second argument
     * @return a manipulator that swaps two arguments
     */
    public static ArgumentModifier swap(int a, int b) {
        if (a == b) {
            return NONE;
        }
        return (arguments) -> Collections.swap(arguments, a, b);
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        var translatedArguments = new ArrayList<AstExpression>();
        FunctionParameterDefinition.processArguments(
                parameters, arguments, walker, (name, node) -> translatedArguments.add(node));
        modifier.mutate(translatedArguments);
        translator.yield(
                EXPRESSION,
                outputMapper.apply(new AstPositionalOperatorExpression(mongoOperator, translatedArguments)));
    }

    /** Perform arbitrary manipulation of arguments */
    public interface ArgumentModifier {
        /**
         * Manipulates the argument list
         *
         * @param arguments the arguments to modify
         */
        void mutate(List<AstExpression> arguments);
    }
}
