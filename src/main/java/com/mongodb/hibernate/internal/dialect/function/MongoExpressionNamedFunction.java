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
import com.mongodb.hibernate.internal.translate.mongoast.AstNamedOperatorExpression;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.spi.SqlAstNode;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.type.BasicTypeReference;
import org.hibernate.type.spi.TypeConfiguration;

/** Defines a HQL function that maps to a Mongo operator where the parameters are included as a document */
public final class MongoExpressionNamedFunction extends AbstractSqmSelfRenderingFunctionDescriptor
        implements ExpressionFunction {

    private final String mongoOperator;
    private final FunctionParameterDefinition<String>[] parameters;
    private final Map<String, AstExpression> supplementalArguments;
    private final Function<? super AstExpression, ? extends AstExpression> outputMapper;

    /**
     * Create a new function definition
     *
     * @param hqlName the name for the function in HQL
     * @param mongoOperator the operator in Mongo, including the leading <code>$</code>
     * @param typeConfiguration the type information of the Hibernate context
     * @param returnType the type that this function will return
     * @param parameters a list of parameters accepted by the function; this can include required or optional arguments,
     *     but the required arguments must be first
     */
    @SafeVarargs
    public MongoExpressionNamedFunction(
            String hqlName,
            String mongoOperator,
            TypeConfiguration typeConfiguration,
            BasicTypeReference<?> returnType,
            FunctionParameterDefinition<String>... parameters) {
        this(hqlName, mongoOperator, typeConfiguration, Map.of(), returnType, Function.identity(), parameters);
    }

    /**
     * Create a new function definition
     *
     * @param hqlName the name for the function in HQL
     * @param mongoOperator the operator in Mongo, including the leading <code>$</code>
     * @param typeConfiguration the type information of the Hibernate context
     * @param returnType the type that this function will return
     * @param outputMapper allows wrapping the generated output with additional operations
     * @param parameters a list of parameters accepted by the function; this can include required or optional arguments,
     *     but the required arguments must be first
     * @see FunctionParameterDefinition#addOne(AstExpression)
     * @see FunctionParameterDefinition#subtractOne(AstExpression)
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public MongoExpressionNamedFunction(
            String hqlName,
            String mongoOperator,
            TypeConfiguration typeConfiguration,
            Map<String, AstExpression> supplementalArguments,
            BasicTypeReference<?> returnType,
            Function<? super AstExpression, ? extends AstExpression> outputMapper,
            FunctionParameterDefinition<String>... parameters) {
        super(
                hqlName,
                FunctionParameterDefinition.argumentsValidatorFromParameters(
                        new FunctionParameterDefinition.NamedCheck(), parameters),
                StandardFunctionReturnTypeResolvers.invariant(Objects.requireNonNull(
                        typeConfiguration.getBasicTypeRegistry().resolve(returnType))),
                FunctionParameterDefinition.typeResolverFromParameters(typeConfiguration, parameters));
        this.mongoOperator = mongoOperator;
        this.supplementalArguments = supplementalArguments;
        this.outputMapper = outputMapper;
        this.parameters = parameters;
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        var namedArguments = new TreeMap<>(supplementalArguments);
        FunctionParameterDefinition.processArguments(parameters, arguments, walker, namedArguments::put);
        translator.yield(EXPRESSION, outputMapper.apply(new AstNamedOperatorExpression(mongoOperator, namedArguments)));
    }
}
