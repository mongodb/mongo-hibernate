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
import java.util.Objects;
import java.util.TreeMap;
import org.hibernate.metamodel.model.domain.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.produce.function.ArgumentTypesValidator;
import org.hibernate.query.sqm.produce.function.FunctionParameterType;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionArgumentTypeResolvers;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.spi.SqlAstNode;
import org.hibernate.sql.ast.spi.query.expression.TrimSpecification;
import org.hibernate.sql.ast.spi.translation.SqlAstTranslator;
import org.hibernate.sql.spi.SqlAppender;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.spi.TypeConfiguration;

public final class MongoTrimFunction extends AbstractSqmSelfRenderingFunctionDescriptor implements ExpressionFunction {

    public MongoTrimFunction(TypeConfiguration typeConfiguration) {
        super(
                "trim",
                new ArgumentTypesValidator(
                        StandardArgumentsValidators.exactly(3),
                        FunctionParameterType.TRIM_SPEC,
                        FunctionParameterType.STRING,
                        FunctionParameterType.STRING),
                StandardFunctionReturnTypeResolvers.invariant(Objects.requireNonNull(
                        typeConfiguration.getBasicTypeRegistry().resolve(StandardBasicTypes.STRING))),
                StandardFunctionArgumentTypeResolvers.byArgument(
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.TRIM_SPEC),
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.STRING),
                        StandardFunctionArgumentTypeResolvers.impliedOrInvariant(
                                typeConfiguration, FunctionParameterType.STRING)));
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        var namedArguments = new TreeMap<String, AstExpression>();
        namedArguments.put("chars", translator.acceptAndYield(arguments.get(1), EXPRESSION));
        namedArguments.put("input", translator.acceptAndYield(arguments.get(2), EXPRESSION));

        translator.yield(
                EXPRESSION,
                new AstNamedOperatorExpression(
                        switch (((TrimSpecification) arguments.get(0)).getSpecification()) {
                            case LEADING -> "$ltrim";
                            case TRAILING -> "$rtrim";
                            case BOTH -> "$trim";
                        },
                        namedArguments));
    }
}
