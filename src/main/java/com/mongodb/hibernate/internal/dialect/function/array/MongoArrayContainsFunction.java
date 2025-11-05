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

package com.mongodb.hibernate.internal.dialect.function.array;

import static com.mongodb.hibernate.internal.MongoAssertions.assertInstanceOf;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FIELD_PATH;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FILTER;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.VALUE;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.EQ;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstTypeFilterOperation;
import java.util.List;
import org.bson.BsonType;
import org.hibernate.dialect.function.array.AbstractArrayContainsFunction;
import org.hibernate.query.ReturnableType;
import org.hibernate.query.sqm.produce.function.FunctionArgumentException;
import org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation;
import org.hibernate.query.sqm.sql.internal.SqmPathInterpretation;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.FunctionExpression;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.type.BasicPluralType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Implements <a
 * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-array-contains-functions">
 * {@code array_contains}, {@code array_contains_nullable}</a>.
 *
 * <p>See <a href="https://www.mongodb.com/docs/manual/tutorial/query-arrays/">Query an Array</a>.
 *
 * @mongoCme Must be thread-safe.
 */
public final class MongoArrayContainsFunction extends AbstractArrayContainsFunction {
    public MongoArrayContainsFunction(boolean nullable, TypeConfiguration typeConfiguration) {
        super(nullable, typeConfiguration);
    }

    @Override
    public void render(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> arguments,
            ReturnableType<?> returnType,
            SqlAstTranslator<?> walker) {
        var translator = AbstractMqlTranslator.cast(walker);
        assertTrue(arguments.size() == 2);
        var functionName = getName();
        var fieldPath = haystackFieldPath(translator, functionName, arguments);
        var needleParameterIndex = 1;
        var needleExpression = getArgumentAsExpression(arguments, needleParameterIndex);
        if (needleExpression instanceof SqlTuple
                || needleExpression.getExpressionType() instanceof BasicPluralType
                || (needleExpression instanceof FunctionExpression functionExpression
                        && MongoArrayConstructorFunction.NAMES.contains(functionExpression.getFunctionName()))) {
            // In Hibernate ORM 6.5, the function allowed a plural second argument, see
            // https://docs.jboss.org/hibernate/orm/6.5/userguide/html_single/Hibernate_User_Guide.html#hql-array-contains-functions.
            // This was changed in Hibernate ORM 6.6, see
            // https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-array-contains-functions.
            // To allow for a graceful transition, the function still accepts a plural second argument with a warning,
            // see
            // https://docs.jboss.org/hibernate/orm/6.6/migration-guide/migration-guide.html#array-contains-array-deprecation.
            // We, however, are free to require the argument to be singular,
            // since there was no version of our product having the old behavior.
            //
            // The error wording is taken partially from
            // `org.hibernate.dialect.function.array.ArrayContainsArgumentValidator`.
            throw new FunctionArgumentException(format(
                    "Parameter %d of function '%s()' requires a singular value, but argument is plural",
                    needleParameterIndex, functionName));
        }
        checkNotHqlPathExpression(functionName, needleParameterIndex, needleExpression);
        var needleValue = translator.acceptAndYield(needleExpression, VALUE);
        // Unfortunately, `$eq` also matches non-array BSON values, see
        // https://www.mongodb.com/docs/manual/reference/operator/query/eq/#array-element-equals-a-value,
        // so ideally we should have used
        // https://www.mongodb.com/docs/manual/reference/operator/query/elemMatch/ instead.
        // However, since Hibernate ORM does not allow the first argument to be an HQL path expression
        // referring to a non-plural persistent attribute, the current approach should be fine.
        translator.yield(
                FILTER,
                new AstLogicalFilter(
                        AstLogicalFilterOperator.AND,
                        List.of(
                                new AstFieldOperationFilter(
                                        // This is how we check that the field exists and is not BSON `Null`.
                                        // Explicitly checking that the field `$exists`
                                        // and the type is not BSON `Null` does not work,
                                        // because `$type` somehow checks the type of both the field
                                        // and the array elements in the field value.
                                        fieldPath, new AstTypeFilterOperation(List.of(BsonType.ARRAY))),
                                new AstFieldOperationFilter(
                                        fieldPath, new AstComparisonFilterOperation(EQ, needleValue)))));
    }

    static Expression getArgumentAsExpression(List<? extends SqlAstNode> arguments, int index) {
        return assertInstanceOf(arguments.get(index), Expression.class);
    }

    static String haystackFieldPath(
            AbstractMqlTranslator<?> translator, String functionName, List<? extends SqlAstNode> arguments) {
        var haystackParameterIndex = 0;
        var haystackExpression = getArgumentAsExpression(arguments, haystackParameterIndex);
        if (haystackExpression instanceof Literal
                || haystackExpression.getExpressionType() instanceof BasicPluralType
                || haystackExpression instanceof SqmParameterInterpretation) {
            // We do not support anything but an HQL path expression, see
            // https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-path-expressions,
            // as the first argument, due to a lacking MQL equivalent.
            //
            // The error wording is taken partially from
            // `org.hibernate.dialect.function.array.ArrayContainsArgumentValidator`.
            throw new FeatureNotSupportedException(format(
                    "Parameter %d of function '%s()' requires an HQL path expression",
                    haystackParameterIndex, functionName));
        }
        return translator.acceptAndYield(haystackExpression, FIELD_PATH);
    }

    static void checkNotHqlPathExpression(String functionName, int parameterIndex, Expression parameterExpression)
            throws FunctionArgumentException {
        if (parameterExpression instanceof SqmPathInterpretation) {
            // The error wording is taken partially from
            // `org.hibernate.dialect.function.array.ArrayIncludesArgumentValidator`.
            throw new FunctionArgumentException(format(
                    "Parameter %d of function '%s()' requires a value that is not an HQL path expression",
                    parameterIndex, functionName));
        }
    }
}
