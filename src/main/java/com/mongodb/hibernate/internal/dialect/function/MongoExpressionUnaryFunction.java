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
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.spi.TypeConfiguration;

/** Defines a HQL function that maps to a Mongo operator where a single parameters is included as the attribute value */
public final class MongoExpressionUnaryFunction extends AbstractSqmSelfRenderingFunctionDescriptor
        implements ExpressionFunction {

    private final Function<? super AstExpression, ? extends AstExpression> outputMapper;
    private final String mongoOperator;
    /**
     * Create a new function definition
     *
     * @param hqlName the name for the function in HQL
     * @param mongoOperator the operator in Mongo, including the leading <code>$</code>
     * @param typeConfiguration the type information of the Hibernate context
     * @param returnType the type that this function will return
     * @param parameterType the type of the single argument to this function
     */
    public MongoExpressionUnaryFunction(
            String hqlName,
            String mongoOperator,
            TypeConfiguration typeConfiguration,
            BasicTypeReference<?> returnType,
            FunctionParameterType parameterType) {
        this(hqlName, mongoOperator, typeConfiguration, returnType, Function.identity(), parameterType);
    }
    /**
     * Create a new function definition
     *
     * @param hqlName the name for the function in HQL
     * @param mongoOperator the operator in Mongo, including the leading <code>$</code>
     * @param typeConfiguration the type information of the Hibernate context
     * @param returnType the type that this function will return
     * @param outputMapper allows wrapping the generated output with additional operations
     * @param parameterType the type of the single argument to this function
     * @see FunctionParameterDefinition#addOne(AstExpression)
     * @see FunctionParameterDefinition#subtractOne(AstExpression)
     */
    public MongoExpressionUnaryFunction(
            String hqlName,
            String mongoOperator,
            TypeConfiguration typeConfiguration,
            BasicTypeReference<?> returnType,
            Function<? super AstExpression, ? extends AstExpression> outputMapper,
            FunctionParameterType parameterType) {
        super(
                hqlName,
                new ArgumentTypesValidator(StandardArgumentsValidators.exactly(1), parameterType),
                StandardFunctionReturnTypeResolvers.invariant(Objects.requireNonNull(
                        typeConfiguration.getBasicTypeRegistry().resolve(returnType))),
                StandardFunctionArgumentTypeResolvers.impliedOrInvariant(typeConfiguration, parameterType));
        this.mongoOperator = mongoOperator;
        this.outputMapper = outputMapper;
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("Only one argument can be supplied");
        }
        translator.yield(
                EXPRESSION,
                outputMapper.apply(new AstUnaryOperatorExpression(
                        mongoOperator, translator.acceptAndYield(arguments.get(0), EXPRESSION))));
    }
}
