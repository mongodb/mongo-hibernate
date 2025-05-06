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

package com.mongodb.hibernate.internal.translate;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_AGGREGATE;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_MUTATION;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_NAME;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FIELD_PATH;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FIELD_VALUE;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FILTER;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.PROJECT_STAGE_SPECIFICATIONS;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.SORT_FIELDS;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.TUPLE;
import static com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue.FALSE;
import static com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue.TRUE;
import static com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder.ASC;
import static com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder.DESC;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.EQ;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.GT;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.GTE;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.LT;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.LTE;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.NE;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator.AND;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator.NOR;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator.OR;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.extension.service.StandardServiceRegistryScopedState;
import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralValue;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstParameterMarker;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstDeleteCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstAggregateCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstMatchStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageIncludeSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortField;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstStage;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilterFieldPath;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.BsonBoolean;
import org.bson.BsonDecimal128;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriter;
import org.bson.json.JsonWriterSettings;
import org.bson.types.Decimal128;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.internal.SqlFragmentPredicate;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.query.sqm.sql.internal.BasicValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation;
import org.hibernate.query.sqm.tree.expression.Conversion;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.delete.DeleteStatement;
import org.hibernate.sql.ast.tree.expression.AggregateColumnWriteExpression;
import org.hibernate.sql.ast.tree.expression.Any;
import org.hibernate.sql.ast.tree.expression.BinaryArithmeticExpression;
import org.hibernate.sql.ast.tree.expression.CaseSearchedExpression;
import org.hibernate.sql.ast.tree.expression.CaseSimpleExpression;
import org.hibernate.sql.ast.tree.expression.CastTarget;
import org.hibernate.sql.ast.tree.expression.Collation;
import org.hibernate.sql.ast.tree.expression.ColumnReference;
import org.hibernate.sql.ast.tree.expression.Distinct;
import org.hibernate.sql.ast.tree.expression.Duration;
import org.hibernate.sql.ast.tree.expression.DurationUnit;
import org.hibernate.sql.ast.tree.expression.EmbeddableTypeLiteral;
import org.hibernate.sql.ast.tree.expression.EntityTypeLiteral;
import org.hibernate.sql.ast.tree.expression.Every;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.ExtractUnit;
import org.hibernate.sql.ast.tree.expression.Format;
import org.hibernate.sql.ast.tree.expression.JdbcLiteral;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
import org.hibernate.sql.ast.tree.expression.Literal;
import org.hibernate.sql.ast.tree.expression.ModifiedSubQueryExpression;
import org.hibernate.sql.ast.tree.expression.NestedColumnReference;
import org.hibernate.sql.ast.tree.expression.Over;
import org.hibernate.sql.ast.tree.expression.Overflow;
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.sql.ast.tree.expression.SelfRenderingExpression;
import org.hibernate.sql.ast.tree.expression.SqlSelectionExpression;
import org.hibernate.sql.ast.tree.expression.SqlTuple;
import org.hibernate.sql.ast.tree.expression.SqlTupleContainer;
import org.hibernate.sql.ast.tree.expression.Star;
import org.hibernate.sql.ast.tree.expression.Summarization;
import org.hibernate.sql.ast.tree.expression.TrimSpecification;
import org.hibernate.sql.ast.tree.expression.UnaryOperation;
import org.hibernate.sql.ast.tree.expression.UnparsedNumericLiteral;
import org.hibernate.sql.ast.tree.from.FromClause;
import org.hibernate.sql.ast.tree.from.FunctionTableReference;
import org.hibernate.sql.ast.tree.from.NamedTableReference;
import org.hibernate.sql.ast.tree.from.QueryPartTableReference;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;
import org.hibernate.sql.ast.tree.from.ValuesTableReference;
import org.hibernate.sql.ast.tree.insert.InsertSelectStatement;
import org.hibernate.sql.ast.tree.predicate.BetweenPredicate;
import org.hibernate.sql.ast.tree.predicate.BooleanExpressionPredicate;
import org.hibernate.sql.ast.tree.predicate.ComparisonPredicate;
import org.hibernate.sql.ast.tree.predicate.ExistsPredicate;
import org.hibernate.sql.ast.tree.predicate.FilterPredicate;
import org.hibernate.sql.ast.tree.predicate.GroupedPredicate;
import org.hibernate.sql.ast.tree.predicate.InArrayPredicate;
import org.hibernate.sql.ast.tree.predicate.InListPredicate;
import org.hibernate.sql.ast.tree.predicate.InSubQueryPredicate;
import org.hibernate.sql.ast.tree.predicate.Junction;
import org.hibernate.sql.ast.tree.predicate.LikePredicate;
import org.hibernate.sql.ast.tree.predicate.NegatedPredicate;
import org.hibernate.sql.ast.tree.predicate.NullnessPredicate;
import org.hibernate.sql.ast.tree.predicate.Predicate;
import org.hibernate.sql.ast.tree.predicate.SelfRenderingPredicate;
import org.hibernate.sql.ast.tree.predicate.ThruthnessPredicate;
import org.hibernate.sql.ast.tree.select.QueryGroup;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.ast.tree.select.SelectStatement;
import org.hibernate.sql.ast.tree.select.SortSpecification;
import org.hibernate.sql.ast.tree.update.Assignment;
import org.hibernate.sql.ast.tree.update.UpdateStatement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.model.MutationOperation;
import org.hibernate.sql.model.ast.AbstractRestrictedTableMutation;
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.internal.TableDeleteCustomSql;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateCustomSql;
import org.hibernate.sql.model.internal.TableUpdateStandard;
import org.jspecify.annotations.Nullable;

abstract class AbstractMqlTranslator<T extends JdbcOperation> implements SqlAstTranslator<T> {
    private static final JsonWriterSettings JSON_WRITER_SETTINGS =
            JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    private final SessionFactoryImplementor sessionFactory;

    private final AstVisitorValueHolder astVisitorValueHolder = new AstVisitorValueHolder();

    private final List<JdbcParameterBinder> parameterBinders = new ArrayList<>();

    private final Set<String> affectedTableNames = new HashSet<>();

    AbstractMqlTranslator(SessionFactoryImplementor sessionFactory) {
        this.sessionFactory = sessionFactory;
        assertNotNull(sessionFactory
                .getServiceRegistry()
                .requireService(StandardServiceRegistryScopedState.class)
                .getConfiguration());
    }

    @Override
    public SessionFactoryImplementor getSessionFactory() {
        return sessionFactory;
    }

    @Override
    public void render(SqlAstNode sqlAstNode, SqlAstNodeRenderingMode sqlAstNodeRenderingMode) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public boolean supportsFilterClause() {
        throw new FeatureNotSupportedException();
    }

    @Override
    public QueryPart getCurrentQueryPart() {
        throw new FeatureNotSupportedException();
    }

    @Override
    public Stack<Clause> getCurrentClauseStack() {
        throw new FeatureNotSupportedException();
    }

    @Override
    public Set<String> getAffectedTableNames() {
        return affectedTableNames;
    }

    List<JdbcParameterBinder> getParameterBinders() {
        return parameterBinders;
    }

    @SuppressWarnings("overloads")
    <R extends AstCommand> R acceptAndYield(Statement statement, AstVisitorValueDescriptor<R> resultDescriptor) {
        return astVisitorValueHolder.execute(resultDescriptor, () -> statement.accept(this));
    }

    @SuppressWarnings("overloads")
    <R> R acceptAndYield(SqlAstNode node, AstVisitorValueDescriptor<R> resultDescriptor) {
        return astVisitorValueHolder.execute(resultDescriptor, () -> node.accept(this));
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Table Mutation: insert

    @Override
    public void visitStandardTableInsert(TableInsertStandard tableInsert) {
        if (tableInsert.getNumberOfReturningColumns() > 0) {
            throw new FeatureNotSupportedException();
        }
        var astElements = new ArrayList<AstElement>(tableInsert.getNumberOfValueBindings());
        for (var columnValueBinding : tableInsert.getValueBindings()) {
            var fieldName = columnValueBinding.getColumnReference().getColumnExpression();
            var valueExpression = columnValueBinding.getValueExpression();
            if (valueExpression == null) {
                throw new FeatureNotSupportedException();
            }
            var fieldValue = acceptAndYield(valueExpression, FIELD_VALUE);
            astElements.add(new AstElement(fieldName, fieldValue));
        }
        astVisitorValueHolder.yield(
                COLLECTION_MUTATION,
                new AstInsertCommand(tableInsert.getMutatingTable().getTableName(), new AstDocument(astElements)));
    }

    @Override
    public void visitColumnWriteFragment(ColumnWriteFragment columnWriteFragment) {
        if (columnWriteFragment.getParameters().size() != 1) {
            throw new FeatureNotSupportedException();
        }
        columnWriteFragment.getParameters().iterator().next().accept(this);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Table Mutation: delete

    @Override
    public void visitStandardTableDelete(TableDeleteStandard tableDelete) {
        if (tableDelete.getWhereFragment() != null) {
            throw new FeatureNotSupportedException();
        }
        var keyFilter = getKeyFilter(tableDelete);
        astVisitorValueHolder.yield(
                COLLECTION_MUTATION,
                new AstDeleteCommand(tableDelete.getMutatingTable().getTableName(), keyFilter));
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Table Mutation: update

    @Override
    public void visitStandardTableUpdate(TableUpdateStandard tableUpdate) {
        if (tableUpdate.getNumberOfReturningColumns() > 0) {
            throw new FeatureNotSupportedException();
        }
        if (tableUpdate.getWhereFragment() != null) {
            throw new FeatureNotSupportedException();
        }
        var keyFilter = getKeyFilter(tableUpdate);
        var updates = new ArrayList<AstFieldUpdate>(tableUpdate.getNumberOfValueBindings());
        for (var valueBinding : tableUpdate.getValueBindings()) {
            var fieldName = valueBinding.getColumnReference().getColumnExpression();
            var fieldValue = acceptAndYield(valueBinding.getValueExpression(), FIELD_VALUE);
            updates.add(new AstFieldUpdate(fieldName, fieldValue));
        }
        astVisitorValueHolder.yield(
                COLLECTION_MUTATION,
                new AstUpdateCommand(tableUpdate.getMutatingTable().getTableName(), keyFilter, updates));
    }

    private AstFilter getKeyFilter(AbstractRestrictedTableMutation<? extends MutationOperation> tableMutation) {
        if (tableMutation.getNumberOfOptimisticLockBindings() > 0) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-51 https://jira.mongodb.org/browse/HIBERNATE-51");
        }

        if (tableMutation.getNumberOfKeyBindings() > 1) {
            throw new FeatureNotSupportedException(
                    format("%s does not support '%s' spanning multiple columns", MONGO_DBMS_NAME, ID_FIELD_NAME));
        }
        assertTrue(tableMutation.getNumberOfKeyBindings() == 1);
        var keyBinding = tableMutation.getKeyBindings().get(0);

        var astFilterFieldPath =
                new AstFilterFieldPath(keyBinding.getColumnReference().getColumnExpression());
        var fieldValue = acceptAndYield(keyBinding.getValueExpression(), FIELD_VALUE);
        return new AstFieldOperationFilter(astFilterFieldPath, new AstComparisonFilterOperation(EQ, fieldValue));
    }

    @Override
    public void visitParameter(JdbcParameter jdbcParameter) {
        parameterBinders.add(jdbcParameter.getParameterBinder());
        astVisitorValueHolder.yield(FIELD_VALUE, AstParameterMarker.INSTANCE);
    }

    @Override
    public void visitSelectStatement(SelectStatement selectStatement) {
        if (!selectStatement.getQueryPart().isRoot()) {
            throw new FeatureNotSupportedException("Subquery not supported");
        }
        if (!selectStatement.getCteStatements().isEmpty()
                || !selectStatement.getCteObjects().isEmpty()) {
            throw new FeatureNotSupportedException("CTE not supported");
        }
        selectStatement.getQueryPart().accept(this);
    }

    @Override
    public void visitQuerySpec(QuerySpec querySpec) {
        if (!querySpec.getGroupByClauseExpressions().isEmpty()) {
            throw new FeatureNotSupportedException("GroupBy not supported");
        }
        if (querySpec.hasOffsetOrFetchClause()) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-70 https://jira.mongodb.org/browse/HIBERNATE-70");
        }

        var collection = acceptAndYield(querySpec.getFromClause(), COLLECTION_NAME);

        var stages = new ArrayList<AstStage>(3);

        createMatchStage(querySpec).ifPresent(stages::add);
        createSortStage(querySpec).ifPresent(stages::add);
        stages.add(createProjectStage(querySpec.getSelectClause()));

        astVisitorValueHolder.yield(COLLECTION_AGGREGATE, new AstAggregateCommand(collection, stages));
    }

    private Optional<AstMatchStage> createMatchStage(QuerySpec querySpec) {
        var whereClauseRestrictions = querySpec.getWhereClauseRestrictions();
        if (whereClauseRestrictions != null && !whereClauseRestrictions.isEmpty()) {
            var filter = acceptAndYield(whereClauseRestrictions, FILTER);
            return Optional.of(new AstMatchStage(filter));
        } else {
            return Optional.empty();
        }
    }

    private Optional<AstSortStage> createSortStage(QuerySpec querySpec) {
        if (querySpec.hasSortSpecifications()) {
            var sortFields = new ArrayList<AstSortField>(
                    querySpec.getSortSpecifications().size());
            for (var sortSpecification : querySpec.getSortSpecifications()) {
                sortFields.addAll(acceptAndYield(sortSpecification, SORT_FIELDS));
            }
            return Optional.of(new AstSortStage(sortFields));
        }
        return Optional.empty();
    }

    private AstProjectStage createProjectStage(SelectClause selectClause) {
        var projectStageSpecifications = acceptAndYield(selectClause, PROJECT_STAGE_SPECIFICATIONS);
        return new AstProjectStage(projectStageSpecifications);
    }

    @Override
    public void visitFromClause(FromClause fromClause) {
        if (fromClause.getRoots().size() != 1) {
            throw new FeatureNotSupportedException();
        }
        var tableGroup = fromClause.getRoots().get(0);

        if (!(tableGroup.getModelPart() instanceof EntityPersister entityPersister)
                || entityPersister.getQuerySpaces().length != 1) {
            throw new FeatureNotSupportedException();
        }

        affectedTableNames.add(((String[]) entityPersister.getQuerySpaces())[0]);
        tableGroup.getPrimaryTableReference().accept(this);
    }

    @Override
    public void visitNamedTableReference(NamedTableReference namedTableReference) {
        astVisitorValueHolder.yield(COLLECTION_NAME, namedTableReference.getTableExpression());
    }

    @Override
    public void visitRelationalPredicate(ComparisonPredicate comparisonPredicate) {
        if (!isComparingFieldWithValue(comparisonPredicate)) {
            throw new FeatureNotSupportedException(
                    "Only the following comparisons are supported: field vs literal, field vs parameter");
        }

        var lhs = comparisonPredicate.getLeftHandExpression();
        var rhs = comparisonPredicate.getRightHandExpression();

        var isFieldOnLeftHandSide = isFieldPathExpression(lhs);
        if (!isFieldOnLeftHandSide) {
            assertTrue(isFieldPathExpression(rhs));
        }

        var fieldPath = acceptAndYield((isFieldOnLeftHandSide ? lhs : rhs), FIELD_PATH);
        var comparisonValue = acceptAndYield((isFieldOnLeftHandSide ? rhs : lhs), FIELD_VALUE);

        var operator = isFieldOnLeftHandSide
                ? comparisonPredicate.getOperator()
                : comparisonPredicate.getOperator().invert();
        var astComparisonFilterOperator = getAstComparisonFilterOperator(operator);

        var astFilterOperation = new AstComparisonFilterOperation(astComparisonFilterOperator, comparisonValue);
        var filter = new AstFieldOperationFilter(new AstFilterFieldPath(fieldPath), astFilterOperation);
        astVisitorValueHolder.yield(FILTER, filter);
    }

    @Override
    public void visitNegatedPredicate(NegatedPredicate negatedPredicate) {
        var filter = acceptAndYield(negatedPredicate.getPredicate(), FILTER);
        astVisitorValueHolder.yield(FILTER, new AstLogicalFilter(NOR, List.of(filter)));
    }

    @Override
    public void visitGroupedPredicate(GroupedPredicate groupedPredicate) {
        var filter = acceptAndYield(groupedPredicate.getSubPredicate(), FILTER);
        astVisitorValueHolder.yield(FILTER, filter);
    }

    @Override
    public void visitSelectClause(SelectClause selectClause) {
        if (selectClause.isDistinct()) {
            throw new FeatureNotSupportedException();
        }
        var projectStageSpecifications = new ArrayList<AstProjectStageSpecification>(
                selectClause.getSqlSelections().size());

        for (SqlSelection sqlSelection : selectClause.getSqlSelections()) {
            if (sqlSelection.isVirtual()) {
                continue;
            }
            if (!(sqlSelection.getExpression() instanceof ColumnReference columnReference)) {
                throw new FeatureNotSupportedException();
            }
            var field = acceptAndYield(columnReference, FIELD_PATH);
            projectStageSpecifications.add(new AstProjectStageIncludeSpecification(field));
        }
        astVisitorValueHolder.yield(PROJECT_STAGE_SPECIFICATIONS, projectStageSpecifications);
    }

    @Override
    public void visitColumnReference(ColumnReference columnReference) {
        if (columnReference.isColumnExpressionFormula()) {
            throw new FeatureNotSupportedException();
        }
        astVisitorValueHolder.yield(FIELD_PATH, columnReference.getColumnExpression());
    }

    @Override
    public void visitQueryLiteral(QueryLiteral<?> queryLiteral) {
        var bsonValue = toBsonValue(queryLiteral.getLiteralValue());
        astVisitorValueHolder.yield(FIELD_VALUE, new AstLiteralValue(bsonValue));
    }

    @Override
    public void visitJunction(Junction junction) {
        var subFilters = new ArrayList<AstFilter>(junction.getPredicates().size());
        for (Predicate predicate : junction.getPredicates()) {
            subFilters.add(acceptAndYield(predicate, FILTER));
        }
        var junctionFilter =
                switch (junction.getNature()) {
                    case DISJUNCTION -> new AstLogicalFilter(OR, subFilters);
                    case CONJUNCTION -> new AstLogicalFilter(AND, subFilters);
                };
        astVisitorValueHolder.yield(FILTER, junctionFilter);
    }

    @Override
    public <N extends Number> void visitUnparsedNumericLiteral(UnparsedNumericLiteral<N> unparsedNumericLiteral) {
        astVisitorValueHolder.yield(
                FIELD_VALUE, new AstLiteralValue(toBsonValue(unparsedNumericLiteral.getLiteralValue())));
    }

    @Override
    public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {
        if (!isFieldPathExpression(booleanExpressionPredicate.getExpression())) {
            throw new FeatureNotSupportedException("Expression not of field path not supported");
        }
        var fieldPath = acceptAndYield(booleanExpressionPredicate.getExpression(), FIELD_PATH);
        var astFilterOperation =
                new AstComparisonFilterOperation(EQ, booleanExpressionPredicate.isNegated() ? FALSE : TRUE);
        var filter = new AstFieldOperationFilter(new AstFilterFieldPath(fieldPath), astFilterOperation);
        astVisitorValueHolder.yield(FILTER, filter);
    }

    @Override
    public void visitSqlSelectionExpression(SqlSelectionExpression sqlSelectionExpression) {
        sqlSelectionExpression.getSelection().getExpression().accept(this);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // ORDER BY clause

    @Override
    public void visitSortSpecification(SortSpecification sortSpecification) {
        var nullPrecedence = sortSpecification.getNullPrecedence();
        if (nullPrecedence == null || nullPrecedence == NullPrecedence.NONE) {
            nullPrecedence = sessionFactory.getSessionFactoryOptions().getDefaultNullPrecedence();
        }
        if (nullPrecedence != null && nullPrecedence != NullPrecedence.NONE) {
            throw new FeatureNotSupportedException(
                    format("%s does not support null precedence: NULLS %s", MONGO_DBMS_NAME, nullPrecedence));
        }
        if (sortSpecification.isIgnoreCase()) {
            throw new FeatureNotSupportedException("Case-insensitive sorting not supported");
        }

        var astSortOrder =
                switch (sortSpecification.getSortOrder()) {
                    case ASCENDING -> ASC;
                    case DESCENDING -> DESC;
                };
        var sortExpression = sortSpecification.getSortExpression();
        var sqlTuple = SqlTupleContainer.getSqlTuple(sortExpression);
        if (sqlTuple == null) {
            var astSortField = createAstSortField(sortExpression, astSortOrder);
            astVisitorValueHolder.yield(SORT_FIELDS, List.of(astSortField));
        } else {
            var expressions = acceptAndYield(sqlTuple, TUPLE);
            var astSortFields = new ArrayList<AstSortField>(expressions.size());
            for (var expression : expressions) {
                astSortFields.add(createAstSortField(expression, astSortOrder));
            }
            astVisitorValueHolder.yield(SORT_FIELDS, astSortFields);
        }
    }

    private AstSortField createAstSortField(Expression sortExpression, AstSortOrder astSortOrder) {
        if (!isFieldPathExpression(sortExpression)) {
            throw new FeatureNotSupportedException(
                    format("%s does not support sort key not of field path type", MONGO_DBMS_NAME));
        }
        var fieldPath = acceptAndYield(sortExpression, FIELD_PATH);
        return new AstSortField(fieldPath, astSortOrder);
    }

    @Override
    public void visitTuple(SqlTuple sqlTuple) {
        var expressions = new ArrayList<Expression>(sqlTuple.getExpressions().size());
        for (var expression : sqlTuple.getExpressions()) {
            if (SqlTupleContainer.getSqlTuple(expression) != null) {
                expressions.addAll(acceptAndYield(expression, TUPLE));
            } else {
                expressions.add(expression);
            }
        }
        astVisitorValueHolder.yield(TUPLE, expressions);
    }

    @Override
    public void visitDeleteStatement(DeleteStatement deleteStatement) {
        throw new FeatureNotSupportedException("TODO-HIBERNATE-46 https://jira.mongodb.org/browse/HIBERNATE-46");
    }

    @Override
    public void visitUpdateStatement(UpdateStatement updateStatement) {
        throw new FeatureNotSupportedException("TODO-HIBERNATE-46 https://jira.mongodb.org/browse/HIBERNATE-46");
    }

    @Override
    public void visitInsertStatement(InsertSelectStatement insertSelectStatement) {
        throw new FeatureNotSupportedException("TODO-HIBERNATE-46 https://jira.mongodb.org/browse/HIBERNATE-46");
    }

    @Override
    public void visitAssignment(Assignment assignment) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitQueryGroup(QueryGroup queryGroup) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitOffsetFetchClause(QueryPart queryPart) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitSqlSelection(SqlSelection sqlSelection) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitTableGroup(TableGroup tableGroup) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitTableGroupJoin(TableGroupJoin tableGroupJoin) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitValuesTableReference(ValuesTableReference valuesTableReference) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitQueryPartTableReference(QueryPartTableReference queryPartTableReference) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitFunctionTableReference(FunctionTableReference functionTableReference) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitTableReferenceJoin(TableReferenceJoin tableReferenceJoin) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitNestedColumnReference(NestedColumnReference nestedColumnReference) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitAggregateColumnWriteExpression(AggregateColumnWriteExpression aggregateColumnWriteExpression) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitExtractUnit(ExtractUnit extractUnit) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitFormat(Format format) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitDistinct(Distinct distinct) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitOverflow(Overflow overflow) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitStar(Star star) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitTrimSpecification(TrimSpecification trimSpecification) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitCastTarget(CastTarget castTarget) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitBinaryArithmeticExpression(BinaryArithmeticExpression binaryArithmeticExpression) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitAny(Any any) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitEvery(Every every) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitSummarization(Summarization summarization) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitOver(Over<?> over) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitSelfRenderingExpression(SelfRenderingExpression selfRenderingExpression) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitEntityTypeLiteral(EntityTypeLiteral entityTypeLiteral) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitEmbeddableTypeLiteral(EmbeddableTypeLiteral embeddableTypeLiteral) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitCollation(Collation collation) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitJdbcLiteral(JdbcLiteral<?> jdbcLiteral) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitUnaryOperationExpression(UnaryOperation unaryOperation) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitModifiedSubQueryExpression(ModifiedSubQueryExpression modifiedSubQueryExpression) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitFilterPredicate(FilterPredicate filterPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitFilterFragmentPredicate(FilterPredicate.FilterFragmentPredicate filterFragmentPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitSqlFragmentPredicate(SqlFragmentPredicate sqlFragmentPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitInListPredicate(InListPredicate inListPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitInArrayPredicate(InArrayPredicate inArrayPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitExistsPredicate(ExistsPredicate existsPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitLikePredicate(LikePredicate likePredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitThruthnessPredicate(ThruthnessPredicate thruthnessPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitDurationUnit(DurationUnit durationUnit) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitDuration(Duration duration) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitConversion(Conversion conversion) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitCustomTableInsert(TableInsertCustomSql tableInsertCustomSql) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitCustomTableDelete(TableDeleteCustomSql tableDeleteCustomSql) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitOptionalTableUpdate(OptionalTableUpdate optionalTableUpdate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitCustomTableUpdate(TableUpdateCustomSql tableUpdateCustomSql) {
        throw new FeatureNotSupportedException();
    }

    static String renderMongoAstNode(AstNode rootAstNode) {
        try (var stringWriter = new StringWriter();
                var jsonWriter = new JsonWriter(stringWriter, JSON_WRITER_SETTINGS)) {
            rootAstNode.render(jsonWriter);
            jsonWriter.flush();
            return stringWriter.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void checkJdbcParameterBindingsSupportability(@Nullable JdbcParameterBindings jdbcParameterBindings) {
        if (jdbcParameterBindings != null) {
            for (var jdbcParameterBinding : jdbcParameterBindings.getBindings()) {
                if (jdbcParameterBinding.getBindValue() == null) {
                    throw new FeatureNotSupportedException(
                            "TODO-HIBERNATE-74 https://jira.mongodb.org/browse/HIBERNATE-74");
                }
            }
        }
    }

    static void checkQueryOptionsSupportability(QueryOptions queryOptions) {
        if (queryOptions.getTimeout() != null) {
            throw new FeatureNotSupportedException("'timeout' inQueryOptions not supported");
        }
        if (queryOptions.getFlushMode() != null) {
            throw new FeatureNotSupportedException("'flushMode' in QueryOptions not supported");
        }
        if (Boolean.TRUE.equals(queryOptions.isReadOnly())) {
            throw new FeatureNotSupportedException("'readOnly' in QueryOptions not supported");
        }
        if (queryOptions.getAppliedGraph() != null
                && queryOptions.getAppliedGraph().getGraph() != null) {
            throw new FeatureNotSupportedException("'appliedGraph' in QueryOptions not supported");
        }
        if (queryOptions.getTupleTransformer() != null) {
            throw new FeatureNotSupportedException("'tupleTransformer' in QueryOptions not supported");
        }
        if (queryOptions.getResultListTransformer() != null) {
            throw new FeatureNotSupportedException("'resultListTransformer' in QueryOptions not supported");
        }
        if (Boolean.TRUE.equals(queryOptions.isResultCachingEnabled())) {
            throw new FeatureNotSupportedException("'resultCaching' in QueryOptions not supported");
        }
        if (queryOptions.getDisabledFetchProfiles() != null
                && !queryOptions.getDisabledFetchProfiles().isEmpty()) {
            throw new FeatureNotSupportedException("'disabledFetchProfiles' in QueryOptions not supported");
        }
        if (queryOptions.getEnabledFetchProfiles() != null
                && !queryOptions.getEnabledFetchProfiles().isEmpty()) {
            throw new FeatureNotSupportedException("'enabledFetchProfiles' in QueryOptions not supported");
        }
        if (queryOptions.getLockOptions() != null
                && !queryOptions.getLockOptions().isEmpty()) {
            throw new FeatureNotSupportedException("'lockOptions' in QueryOptions not supported");
        }
        if (queryOptions.getDatabaseHints() != null
                && !queryOptions.getDatabaseHints().isEmpty()) {
            throw new FeatureNotSupportedException("'databaseHints' in QueryOptions not supported");
        }
        if (queryOptions.getFetchSize() != null) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-54 https://jira.mongodb.org/browse/HIBERNATE-54");
        }
        if (queryOptions.getLimit() != null && !queryOptions.getLimit().isEmpty()) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-70 https://jira.mongodb.org/browse/HIBERNATE-70");
        }
    }

    private static AstComparisonFilterOperator getAstComparisonFilterOperator(ComparisonOperator operator) {
        return switch (operator) {
            case EQUAL -> EQ;
            case NOT_EQUAL -> NE;
            case LESS_THAN -> LT;
            case LESS_THAN_OR_EQUAL -> LTE;
            case GREATER_THAN -> GT;
            case GREATER_THAN_OR_EQUAL -> GTE;
            default -> throw new FeatureNotSupportedException("Unsupported comparison operator: " + operator.name());
        };
    }

    private static boolean isFieldPathExpression(Expression expression) {
        return expression instanceof ColumnReference
                || expression instanceof BasicValuedPathInterpretation
                || expression instanceof SqlSelectionExpression;
    }

    private static boolean isValueExpression(Expression expression) {
        return expression instanceof Literal
                || expression instanceof JdbcParameter
                || expression instanceof SqmParameterInterpretation;
    }

    private static boolean isComparingFieldWithValue(ComparisonPredicate comparisonPredicate) {
        var lhs = comparisonPredicate.getLeftHandExpression();
        var rhs = comparisonPredicate.getRightHandExpression();
        return (isFieldPathExpression(lhs) && isValueExpression(rhs))
                || (isFieldPathExpression(rhs) && isValueExpression(lhs));
    }

    private static BsonValue toBsonValue(@Nullable Object queryLiteral) {
        if (queryLiteral == null) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-74 https://jira.mongodb.org/browse/HIBERNATE-74");
        }
        if (queryLiteral instanceof Boolean boolValue) {
            return BsonBoolean.valueOf(boolValue);
        }
        if (queryLiteral instanceof Integer intValue) {
            return new BsonInt32(intValue);
        }
        if (queryLiteral instanceof Long longValue) {
            return new BsonInt64(longValue);
        }
        if (queryLiteral instanceof Double doubleValue) {
            return new BsonDouble(doubleValue);
        }
        if (queryLiteral instanceof BigDecimal bigDecimalValue) {
            return new BsonDecimal128(new Decimal128(bigDecimalValue));
        }
        if (queryLiteral instanceof String stringValue) {
            return new BsonString(stringValue);
        }
        throw new FeatureNotSupportedException("Unsupported Java type: " + queryLiteral.getClass());
    }
}
