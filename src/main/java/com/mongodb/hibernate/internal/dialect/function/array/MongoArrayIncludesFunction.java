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

import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.dialect.function.array.MongoArrayContainsFunction.checkNotHqlPathExpression;
import static com.mongodb.hibernate.internal.dialect.function.array.MongoArrayContainsFunction.getArgumentAsExpression;
import static com.mongodb.hibernate.internal.dialect.function.array.MongoArrayContainsFunction.haystackFieldPath;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FILTER;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.VALUE;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.translate.AbstractMqlTranslator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstAllFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstTypeFilterOperation;
import java.util.List;
import org.bson.BsonType;
import org.hibernate.dialect.function.array.AbstractArrayIncludesFunction;
import org.hibernate.query.ReturnableType;
import org.hibernate.query.sqm.produce.function.FunctionArgumentException;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Implements <a
 * href="https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#hql-array-includes-functions">
 * {@code array_includes}, {@code array_includes_nullable}</a>.
 *
 * <p>See <a href="https://www.mongodb.com/docs/manual/tutorial/query-arrays/">Query an Array</a>.
 *
 * @mongoCme Must be thread-safe.
 */
public final class MongoArrayIncludesFunction extends AbstractArrayIncludesFunction {
    public MongoArrayIncludesFunction(boolean nullable, TypeConfiguration typeConfiguration) {
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
        if (needleExpression instanceof SqlTuple) {
            // Hibernate ORM represents a `Collection` set as a query parameter as an `SqlTuple`
            // with `SqmParameterInterpretation` for each of the elements in the `Collection`. That is,
            // despite there being a single query parameter, Hibernate ORM incorrectly presents it as multiple
            // parameters, which is why we do not support such situations.
            //
            // The error wording is taken partially from
            // `org.hibernate.dialect.function.array.ArrayIncludesArgumentValidator`.
            throw new FunctionArgumentException(format(
                    "Parameter %d of function '%s()' requires an array, but argument is a collection",
                    needleParameterIndex, functionName));
        }
        checkNotHqlPathExpression(functionName, needleParameterIndex, needleExpression);
        var needleValue = translator.acceptAndYield(needleExpression, VALUE);
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
                                new AstFieldOperationFilter(fieldPath, new AstAllFilterOperation(needleValue)))));
    }
}
