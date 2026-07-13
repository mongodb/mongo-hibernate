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

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;
import static com.mongodb.hibernate.internal.MongoAssertions.assertInstanceOf;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.internal.MongoConstants.EXTENDED_JSON_WRITER_SETTINGS;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_NAME;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FIELD_PATH;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FILTER;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MODEL_MUTATION_RESULT;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MUTATION_RESULT;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.PROJECT_STAGE_SPECIFICATIONS;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.SELECT_RESULT;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.SORT_FIELDS;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.TUPLE;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.VALUE;
import static com.mongodb.hibernate.internal.translate.mongoast.AstLiteral.FALSE;
import static com.mongodb.hibernate.internal.translate.mongoast.AstLiteral.TRUE;
import static com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder.ASC;
import static com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder.DESC;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.EQ;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.GT;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.GTE;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.LT;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.LTE;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator.NE;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstListComparisonFilterOperator.IN;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstListComparisonFilterOperator.NIN;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator.AND;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator.NOR;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator.OR;
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstRegularExpressionFilterOperation.quoteMeta;
import static java.lang.String.format;
import static org.hibernate.query.common.FetchClauseType.ROWS_ONLY;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.dialect.function.array.MongoUnnestFunction;
import com.mongodb.hibernate.internal.service.StandardServiceRegistryScopedState;
import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathValue;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstParameterMarker;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstDeleteCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstAggregateCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLimitStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLookupStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLookupStageWithPipeline;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstMatchStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageFieldPathSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageIncludeSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSkipStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortField;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortOrder;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstSortStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstUnwindStage;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstElemMatchFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstEmptyFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprComparisonFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstListComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstRegularExpressionFilterOperation;
import com.mongodb.hibernate.internal.type.ValueConversions;
import jakarta.persistence.criteria.Nulls;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.bson.json.JsonWriter;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.JoinedSubclassEntityPersister;
import org.hibernate.persister.entity.SingleTableEntityPersister;
import org.hibernate.persister.entity.UnionSubclassEntityPersister;
import org.hibernate.persister.internal.SqlFragmentPredicate;
import org.hibernate.query.spi.Limit;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.sqm.ComparisonOperator;
import org.hibernate.query.sqm.function.SelfRenderingFunctionSqlAstExpression;
import org.hibernate.query.sqm.sql.internal.BasicValuedPathInterpretation;
import org.hibernate.query.sqm.sql.internal.SqmParameterInterpretation;
import org.hibernate.query.sqm.sql.internal.SqmPathInterpretation;
import org.hibernate.query.sqm.tree.expression.Conversion;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.AbstractMutationStatement;
import org.hibernate.sql.ast.tree.AbstractUpdateOrDeleteStatement;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.cte.CteContainer;
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
import org.hibernate.sql.ast.tree.expression.FunctionExpression;
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
import org.hibernate.sql.ast.tree.from.PluralTableGroup;
import org.hibernate.sql.ast.tree.from.QueryPartTableReference;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupJoin;
import org.hibernate.sql.ast.tree.from.TableReferenceJoin;
import org.hibernate.sql.ast.tree.from.UnionTableReference;
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
import org.hibernate.sql.exec.internal.AbstractJdbcParameter;
import org.hibernate.sql.exec.spi.ExecutionContext;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.model.MutationOperation;
import org.hibernate.sql.model.ast.AbstractRestrictedTableMutation;
import org.hibernate.sql.model.ast.ColumnValueBinding;
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.internal.TableDeleteCustomSql;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateCustomSql;
import org.hibernate.sql.model.internal.TableUpdateStandard;
import org.hibernate.type.BasicType;
import org.jspecify.annotations.Nullable;

/**
 * @hidden
 * @mongoCme This class and its subclasses do not have to be thread-safe because they are
 *     {@linkplain SqlAstTranslatorFactory single-use}.
 */
@SuppressWarnings("MissingSummary")
public abstract class AbstractMqlTranslator<T extends JdbcOperation> implements SqlAstTranslator<T> {

    // '#' is blocked in mapped field names, so prefixing join aliases with it prevents $lookup from shadowing
    // a local field that happens to share the Hibernate-generated alias name (e.g. "o1_0").
    private static final String JOIN_ALIAS_PREFIX = "#";

    private final SessionFactoryImplementor sessionFactory;

    private final AstVisitorValueHolder astVisitorValueHolder = new AstVisitorValueHolder();

    private @Nullable String elemMatchInnerAlias;

    private final List<JdbcParameterBinder> parameterBinders = new ArrayList<>();

    private final Set<String> affectedTableNames = new HashSet<>();

    private final Set<String> joinedTableQualifiers = new HashSet<>();

    private @Nullable QueryOptionsLimit queryOptionsLimit;

    AbstractMqlTranslator(SessionFactoryImplementor sessionFactory) {
        this.sessionFactory = sessionFactory;
        assertNotNull(sessionFactory
                .getServiceRegistry()
                .requireService(StandardServiceRegistryScopedState.class)
                .getConfiguration());
    }

    public static AbstractMqlTranslator<?> cast(SqlAstTranslator<?> translator) {
        return assertInstanceOf(translator, AbstractMqlTranslator.class);
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
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <X> X getLiteralValue(Expression expression) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public Statement getSqlAst() {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void renderNamedSetReturningFunction(
            String functionName,
            java.util.List<? extends SqlAstNode> sqlAstArguments,
            org.hibernate.query.sqm.tuple.internal.AnonymousTupleTableGroupProducer tupleType,
            String tableIdentifierVariable,
            SqlAstNodeRenderingMode argumentRenderingMode) {
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
        throw fail();
    }

    @Override
    public void addAffectedTableName(String tableName) {
        throw fail();
    }

    @SuppressWarnings("overloads")
    <R> R acceptAndYield(Statement statement, AstVisitorValueDescriptor<R> resultDescriptor) {
        return astVisitorValueHolder.execute(resultDescriptor, () -> statement.accept(this));
    }

    @SuppressWarnings("overloads")
    public <R> R acceptAndYield(SqlAstNode node, AstVisitorValueDescriptor<R> resultDescriptor) {
        return astVisitorValueHolder.execute(resultDescriptor, () -> node.accept(this));
    }

    @SuppressWarnings("NamedLikeContextualKeyword")
    public <R> void yield(AstVisitorValueDescriptor<R> valueDescriptor, R value) {
        astVisitorValueHolder.yield(valueDescriptor, value);
    }

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
            var fieldValue = acceptAndYield(valueExpression, VALUE);
            astElements.add(new AstElement(fieldName, fieldValue));
        }
        astVisitorValueHolder.yield(
                MODEL_MUTATION_RESULT,
                ModelMutationMqlTranslator.Result.create(
                        new AstInsertCommand(
                                tableInsert.getMutatingTable().getTableName(), List.of(new AstDocument(astElements))),
                        parameterBinders));
    }

    @Override
    public void visitColumnWriteFragment(ColumnWriteFragment columnWriteFragment) {
        if (!columnWriteFragment.getFragment().equals("?")) {
            throw new FeatureNotSupportedException(
                    "@CurrentTimestamp(source=DB), @Generated, and @ColumnTransformer write expressions are not supported");
        }
        columnWriteFragment.getParameters().iterator().next().accept(this);
    }

    @Override
    public void visitStandardTableDelete(TableDeleteStandard tableDelete) {
        if (tableDelete.getWhereFragment() != null) {
            throw new FeatureNotSupportedException();
        }
        var keyFilter = createKeyFilter(tableDelete);
        astVisitorValueHolder.yield(
                MODEL_MUTATION_RESULT,
                ModelMutationMqlTranslator.Result.create(
                        new AstDeleteCommand(tableDelete.getMutatingTable().getTableName(), keyFilter),
                        parameterBinders));
    }

    @Override
    public void visitStandardTableUpdate(TableUpdateStandard tableUpdate) {
        if (tableUpdate.getNumberOfReturningColumns() > 0) {
            throw new FeatureNotSupportedException();
        }
        if (tableUpdate.getWhereFragment() != null) {
            throw new FeatureNotSupportedException();
        }
        var mutationResult = createMutationResult(
                tableUpdate.getValueBindings(),
                tableUpdate.getMutatingTable().getTableName(),
                createKeyFilter(tableUpdate));
        astVisitorValueHolder.yield(MODEL_MUTATION_RESULT, mutationResult);
    }

    private ModelMutationMqlTranslator.Result createMutationResult(
            List<ColumnValueBinding> valueBindings, String tableName, AstFilter keyFilter) {
        var astUpdateCommand = createAstUpdateCommand(valueBindings, tableName, keyFilter);
        return ModelMutationMqlTranslator.Result.create(astUpdateCommand, parameterBinders);
    }

    private AstFilter createKeyFilter(AbstractRestrictedTableMutation<? extends MutationOperation> tableMutation) {
        if (tableMutation.getNumberOfKeyBindings() > 1) {
            throw new FeatureNotSupportedException(
                    format("%s does not support primary key spanning multiple columns", MONGO_DBMS_NAME));
        }
        assertTrue(tableMutation.getNumberOfKeyBindings() == 1);

        var predicates = new ArrayList<AstFilter>(1 + tableMutation.getNumberOfOptimisticLockBindings());
        predicates.add(createEqualityFilter(tableMutation.getKeyBindings().get(0)));
        for (var lockBinding : tableMutation.getOptimisticLockBindings()) {
            predicates.add(createEqualityFilter(lockBinding));
        }
        return predicates.size() == 1 ? predicates.get(0) : new AstLogicalFilter(AND, predicates);
    }

    private AstFieldOperationFilter createEqualityFilter(ColumnValueBinding binding) {
        var fieldPath = acceptAndYield(binding.getColumnReference(), FIELD_PATH);
        var fieldValue = acceptAndYield(binding.getValueExpression(), VALUE);
        return new AstFieldOperationFilter(fieldPath, new AstComparisonFilterOperation(EQ, fieldValue));
    }

    @Override
    public void visitParameter(JdbcParameter jdbcParameter) {
        parameterBinders.add(jdbcParameter.getParameterBinder());
        astVisitorValueHolder.yield(VALUE, AstParameterMarker.INSTANCE);
    }

    @Override
    public void visitSelectStatement(SelectStatement selectStatement) {
        if (!selectStatement.getQueryPart().isRoot()) {
            throw new FeatureNotSupportedException("Subquery not supported");
        }
        checkCteContainerSupportability(selectStatement);
        selectStatement.getQueryPart().accept(this);
    }

    @Override
    public void visitQuerySpec(QuerySpec querySpec) {
        if (!querySpec.getGroupByClauseExpressions().isEmpty()) {
            throw new FeatureNotSupportedException("GroupBy is not supported");
        }

        var collection = acceptAndYield(querySpec.getFromClause(), COLLECTION_NAME);

        var stages = new ArrayList<AstStage>();

        var root = querySpec.getFromClause().getRoots().get(0);
        stages.addAll(buildJoinStages(root));

        createMatchStage(querySpec).ifPresent(stages::add);
        createSortStage(querySpec).ifPresent(stages::add);

        var skipLimitStagesAndJdbcParams =
                assertNotNull(queryOptionsLimit).createSkipLimitStagesAndJdbcParams(querySpec);
        stages.addAll(skipLimitStagesAndJdbcParams.stages());

        stages.add(createProjectStage(querySpec.getSelectClause()));

        astVisitorValueHolder.yield(
                SELECT_RESULT,
                new SelectMqlTranslator.Result(
                        new AstAggregateCommand(collection, stages),
                        parameterBinders,
                        affectedTableNames,
                        skipLimitStagesAndJdbcParams.offset(),
                        skipLimitStagesAndJdbcParams.limit()));
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

    @Override
    public void visitOffsetFetchClause(QueryPart queryPart) {
        fail();
    }

    private final class QueryOptionsLimit {
        private final @Nullable Limit limit;

        QueryOptionsLimit(@Nullable Limit limit) {
            this.limit = limit;
        }

        StagesAndJdbcParameters createSkipLimitStagesAndJdbcParams(QueryPart queryPart) {
            Expression skipExpression;
            Expression limitExpression;
            JdbcParameter offsetParameter = null;
            JdbcParameter limitParameter = null;
            if (queryPart.isRoot() && limit != null && !limit.isEmpty()) {
                var basicIntegerType = sessionFactory.getTypeConfiguration().getBasicTypeForJavaType(Integer.class);
                // We check if limit's firstRow/maxRows is set,
                // but ignore the actual values when creating OffsetJdbcParameter/LimitJdbcParameter.
                // Hibernate ORM reuses the translation result for the same HQL/SQL queries
                // with different values passed to setFirstResult/setMaxResults. Therefore, we cannot include the
                // values available when translating in the translation result. The only thing we pay attention to is
                // whether they are specified or not, because the translation results corresponding to
                // setFirstResult/setMaxResults being present
                // must be different from those with the limits being absent. Hibernate ORM also caches them separately.
                if (limit.getFirstRow() != null) {
                    offsetParameter = new OffsetJdbcParameter(basicIntegerType);
                }
                if (limit.getMaxRows() != null) {
                    limitParameter = new LimitJdbcParameter(basicIntegerType);
                }
                skipExpression = offsetParameter;
                limitExpression = limitParameter;
            } else {
                if (queryPart.getFetchClauseType() != ROWS_ONLY) {
                    throw new FeatureNotSupportedException(format(
                            "%s does not support '%s' fetch clause type",
                            MONGO_DBMS_NAME, queryPart.getFetchClauseType()));
                }
                skipExpression = queryPart.getOffsetClauseExpression();
                limitExpression = queryPart.getFetchClauseExpression();
            }
            var skipAndLimitStages = new ArrayList<AstStage>();
            if (skipExpression != null) {
                var skipValue = acceptAndYield(skipExpression, VALUE);
                skipAndLimitStages.add(new AstSkipStage(skipValue));
            }
            if (limitExpression != null) {
                var limitValue = acceptAndYield(limitExpression, VALUE);
                skipAndLimitStages.add(new AstLimitStage(limitValue));
            }
            return new StagesAndJdbcParameters(skipAndLimitStages, offsetParameter, limitParameter);
        }

        record StagesAndJdbcParameters(
                List<AstStage> stages,
                @Nullable JdbcParameter offset,
                @Nullable JdbcParameter limit) {}
    }

    void applyQueryOptions(QueryOptions queryOptions) {
        checkQueryOptionsSupportability(queryOptions);
        assertNull(queryOptionsLimit);
        queryOptionsLimit = new QueryOptionsLimit(queryOptions.getLimit());
    }

    private AstProjectStage createProjectStage(SelectClause selectClause) {
        var projectStageSpecifications = acceptAndYield(selectClause, PROJECT_STAGE_SPECIFICATIONS);
        return new AstProjectStage(projectStageSpecifications);
    }

    @Override
    public void visitFromClause(FromClause fromClause) {
        checkFromClauseSupportability(fromClause);
        var tableGroup = fromClause.getRoots().get(0);
        var primaryTableRef = assertInstanceOf(tableGroup.getPrimaryTableReference(), NamedTableReference.class);
        affectedTableNames.add(primaryTableRef.getTableExpression());
        primaryTableRef.accept(this);
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
        var comparisonValue = acceptAndYield((isFieldOnLeftHandSide ? rhs : lhs), VALUE);

        var operator = isFieldOnLeftHandSide
                ? comparisonPredicate.getOperator()
                : comparisonPredicate.getOperator().invert();
        var astComparisonFilterOperator = createAstComparisonFilterOperator(operator);

        var astFilterOperation = new AstComparisonFilterOperation(astComparisonFilterOperator, comparisonValue);
        var filter = new AstFieldOperationFilter(fieldPath, astFilterOperation);
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

        for (var sqlSelection : selectClause.getSqlSelections()) {
            if (sqlSelection.isVirtual()) {
                continue;
            }
            if (!(sqlSelection.getExpression() instanceof ColumnReference columnReference)) {
                throw new FeatureNotSupportedException();
            }
            var field = acceptAndYield(columnReference, FIELD_PATH);
            AstProjectStageSpecification spec;
            if (field.startsWith(JOIN_ALIAS_PREFIX)) {
                spec = new AstProjectStageFieldPathSpecification(joinFieldProjectionKey(field), field);
            } else if (field.contains(".")) {
                spec = new AstProjectStageFieldPathSpecification(nestFieldProjectionKey(field), field);
            } else {
                spec = new AstProjectStageIncludeSpecification(field);
            }
            projectStageSpecifications.add(spec);
        }
        astVisitorValueHolder.yield(PROJECT_STAGE_SPECIFICATIONS, projectStageSpecifications);
    }

    @Override
    public void visitColumnReference(ColumnReference columnReference) {
        if (columnReference.isColumnExpressionFormula()) {
            throw new FeatureNotSupportedException("Formula is not supported");
        }
        if (elemMatchInnerAlias != null) {
            var qualifier = assertNotNull(columnReference.getQualifier());
            if (!qualifier.equals(elemMatchInnerAlias)) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-177 https://jira.mongodb.org/browse/HIBERNATE-177");
            }
        }
        astVisitorValueHolder.yield(FIELD_PATH, resolveFieldPath(columnReference));
    }

    private String resolveFieldPath(ColumnReference columnReference) {
        var qualifier = columnReference.getQualifier();
        return (qualifier != null && joinedTableQualifiers.contains(qualifier))
                ? JOIN_ALIAS_PREFIX + qualifier + "." + columnReference.getColumnExpression()
                : columnReference.getColumnExpression();
    }

    // Converts the internal "#qualifier.field" path to the "qualifier#field" projection key.
    private static String joinFieldProjectionKey(String joinedFieldPath) {
        return joinedFieldPath.substring(JOIN_ALIAS_PREFIX.length()).replace('.', '#');
    }

    private static String nestFieldProjectionKey(String field) {
        return field.replace('.', '#');
    }

    private static @Nullable ColumnReference extractColumnReference(Expression expression) {
        if (expression instanceof ColumnReference cr) {
            return cr;
        }
        if (expression instanceof BasicValuedPathInterpretation<?> bvpi) {
            return bvpi.getColumnReference();
        }
        return null;
    }

    @Override
    public void visitQueryLiteral(QueryLiteral<?> queryLiteral) {
        var literalValue = queryLiteral.getLiteralValue();
        astVisitorValueHolder.yield(VALUE, new AstLiteral(toBsonValue(literalValue)));
    }

    @Override
    public void visitJunction(Junction junction) {
        var subFilters = new ArrayList<AstFilter>(junction.getPredicates().size());
        for (var predicate : junction.getPredicates()) {
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
        var literalValue = assertNotNull(unparsedNumericLiteral.getLiteralValue());
        astVisitorValueHolder.yield(VALUE, new AstLiteral(toBsonValue(literalValue)));
    }

    @Override
    public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {
        if (!isFieldPathExpression(booleanExpressionPredicate.getExpression())) {
            throw new FeatureNotSupportedException("Expression not of field path is not supported");
        }
        var fieldPath = acceptAndYield(booleanExpressionPredicate.getExpression(), FIELD_PATH);
        var astFilterOperation =
                new AstComparisonFilterOperation(EQ, booleanExpressionPredicate.isNegated() ? FALSE : TRUE);
        var filter = new AstFieldOperationFilter(fieldPath, astFilterOperation);
        astVisitorValueHolder.yield(FILTER, filter);
    }

    @Override
    public void visitSqlSelectionExpression(SqlSelectionExpression sqlSelectionExpression) {
        sqlSelectionExpression.getSelection().getExpression().accept(this);
    }

    @Override
    public void visitSortSpecification(SortSpecification sortSpecification) {
        var nullPrecedence = sortSpecification.getNullPrecedence();
        if (nullPrecedence == null || nullPrecedence == Nulls.NONE) {
            nullPrecedence = sessionFactory.getSessionFactoryOptions().getDefaultNullPrecedence();
        }
        if (nullPrecedence != null && nullPrecedence != Nulls.NONE) {
            throw new FeatureNotSupportedException(
                    format("%s does not support null precedence: NULLS %s", MONGO_DBMS_NAME, nullPrecedence));
        }
        if (sortSpecification.isIgnoreCase()) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-79 https://jira.mongodb.org/browse/HIBERNATE-79");
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
            throw new FeatureNotSupportedException("TODO-HIBERNATE-79 https://jira.mongodb.org/browse/HIBERNATE-79");
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
        checkMutationStatementSupportability(deleteStatement);
        var collection = addToAffectedTableNames(deleteStatement.getTargetTable());
        var filter = createAstFilter(deleteStatement);

        astVisitorValueHolder.yield(
                MUTATION_RESULT,
                new MutationMqlTranslator.Result(
                        new AstDeleteCommand(collection, filter), parameterBinders, affectedTableNames));
    }

    @Override
    public void visitUpdateStatement(UpdateStatement updateStatement) {
        checkMutationStatementSupportability(updateStatement);
        var collection = addToAffectedTableNames(updateStatement.getTargetTable());
        var filter = createAstFilter(updateStatement);

        var assignments = updateStatement.getAssignments();
        var fieldUpdates = new ArrayList<AstFieldUpdate>(assignments.size());
        for (var assignment : assignments) {
            var fieldReferences = assignment.getAssignable().getColumnReferences();
            assertTrue(fieldReferences.size() == 1);

            var fieldPath = acceptAndYield(fieldReferences.get(0), FIELD_PATH);
            var assignedValue = assignment.getAssignedValue();
            if (!isValueExpression(assignedValue)) {
                throw new FeatureNotSupportedException(
                        getUnsupportedUpdateValueAssignmentMessage(fieldPath, assignedValue));
            }
            var fieldValue = acceptAndYield(assignedValue, VALUE);
            fieldUpdates.add(new AstFieldUpdate(fieldPath, fieldValue));
        }
        astVisitorValueHolder.yield(
                MUTATION_RESULT,
                new MutationMqlTranslator.Result(
                        new AstUpdateCommand(collection, filter, fieldUpdates), parameterBinders, affectedTableNames));
    }

    private String addToAffectedTableNames(NamedTableReference tableRef) {
        var collection = tableRef.getTableExpression();
        affectedTableNames.add(collection);
        return collection;
    }

    private AstFilter createAstFilter(final AbstractUpdateOrDeleteStatement updateOrDeleteStatement) {
        var restriction = updateOrDeleteStatement.getRestriction();
        return restriction == null ? AstEmptyFilter.INSTANCE : acceptAndYield(restriction, FILTER);
    }

    private AstUpdateCommand createAstUpdateCommand(
            final List<ColumnValueBinding> valueBindings, final String tableName, final AstFilter keyFilter) {
        var updates = new ArrayList<AstFieldUpdate>(valueBindings.size());
        for (var valueBinding : valueBindings) {
            var fieldName = acceptAndYield(valueBinding.getColumnReference(), FIELD_PATH);
            var fieldValue = acceptAndYield(valueBinding.getValueExpression(), VALUE);
            updates.add(new AstFieldUpdate(fieldName, fieldValue));
        }
        return new AstUpdateCommand(tableName, keyFilter, updates);
    }

    @Override
    public void visitInsertStatement(InsertSelectStatement insertStatement) {
        checkMutationStatementSupportability(insertStatement);
        if (insertStatement.getConflictClause() != null) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-94 https://jira.mongodb.org/browse/HIBERNATE-94");
        }
        if (insertStatement.getSourceSelectStatement() != null) {
            throw new FeatureNotSupportedException("Insertion statement with source selection is not supported");
        }

        var collection = addToAffectedTableNames(insertStatement.getTargetTable());

        var fieldReferences = insertStatement.getTargetColumns();
        assertFalse(fieldReferences.isEmpty());

        var fieldNames = new ArrayList<String>(fieldReferences.size());
        for (var fieldReference : fieldReferences) {
            fieldNames.add(fieldReference.getColumnExpression());
        }

        var valuesList = insertStatement.getValuesList();
        assertFalse(valuesList.isEmpty());

        var documents = new ArrayList<AstDocument>(valuesList.size());
        for (var values : valuesList) {
            var fieldValueExpressions = values.getExpressions();
            assertTrue(fieldNames.size() == fieldValueExpressions.size());
            var astElements = new ArrayList<AstElement>(fieldValueExpressions.size());
            for (var i = 0; i < fieldNames.size(); i++) {
                var fieldName = fieldNames.get(i);
                var fieldValueExpression = fieldValueExpressions.get(i);
                if (!isValueExpression(fieldValueExpression)) {
                    throw new FeatureNotSupportedException();
                }
                var fieldValue = acceptAndYield(fieldValueExpression, VALUE);
                astElements.add(new AstElement(fieldName, fieldValue));
            }
            documents.add(new AstDocument(astElements));
        }

        astVisitorValueHolder.yield(
                MUTATION_RESULT,
                new MutationMqlTranslator.Result(
                        new AstInsertCommand(collection, documents), parameterBinders, affectedTableNames));
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
        if (!(selfRenderingExpression instanceof SelfRenderingFunctionSqlAstExpression)) {
            throw new FeatureNotSupportedException("Only function expressions are supported");
        }
        selfRenderingExpression.renderToSql(FeatureNotSupportedSqlAppender.INSTANCE, this, sessionFactory);
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
        if (!isFieldPathExpression(betweenPredicate.getExpression())
                || !isValueExpression(betweenPredicate.getLowerBound())
                || !isValueExpression(betweenPredicate.getUpperBound())) {
            throw new FeatureNotSupportedException(
                    "Only the following predicates are supported: field [not] between literal|parameter and literal|parameter");
        }
        var fieldPath = acceptAndYield(betweenPredicate.getExpression(), FIELD_PATH);
        var lowerBound = acceptAndYield(betweenPredicate.getLowerBound(), VALUE);
        var upperBound = acceptAndYield(betweenPredicate.getUpperBound(), VALUE);

        astVisitorValueHolder.yield(
                FILTER,
                new AstLogicalFilter(
                        betweenPredicate.isNegated() ? OR : AND,
                        List.of(
                                new AstFieldOperationFilter(
                                        fieldPath,
                                        new AstComparisonFilterOperation(
                                                betweenPredicate.isNegated() ? LT : GTE, lowerBound)),
                                new AstFieldOperationFilter(
                                        fieldPath,
                                        new AstComparisonFilterOperation(
                                                betweenPredicate.isNegated() ? GT : LTE, upperBound)))));
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
        var expression = inListPredicate.getTestExpression();
        if (!isFieldPathExpression(expression)) {
            throw new FeatureNotSupportedException(
                    "Only the following list predicates are supported: field in [not] (...)");
        }
        var fieldPath = acceptAndYield(expression, FIELD_PATH);
        var operator = inListPredicate.isNegated() ? NIN : IN;
        var operation = new AstListComparisonFilterOperation(
                operator,
                inListPredicate.getListExpressions().stream()
                        .map(item -> acceptAndYield(item, VALUE))
                        .toList());
        astVisitorValueHolder.yield(FILTER, new AstFieldOperationFilter(fieldPath, operation));
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
        astVisitorValueHolder.yield(FILTER, translateExistsOverUnnest(existsPredicate));
    }

    private AstFilter translateExistsOverUnnest(ExistsPredicate existsPredicate) {
        var shape = recognizeExistsOverUnnest(existsPredicate)
                .orElseThrow(() -> new FeatureNotSupportedException(
                        "TODO-HIBERNATE-178 https://jira.mongodb.org/browse/HIBERNATE-178"));
        AstFilter bodyFilter;
        if (shape.body() != null) {
            var previousInnerAlias = elemMatchInnerAlias;
            elemMatchInnerAlias = shape.innerAlias();
            try {
                bodyFilter = acceptAndYield(shape.body(), FILTER);
            } finally {
                elemMatchInnerAlias = previousInnerAlias;
            }
        } else {
            bodyFilter = AstEmptyFilter.INSTANCE;
        }
        AstFilter filter =
                new AstFieldOperationFilter(shape.arrayFieldName(), new AstElemMatchFilterOperation(bodyFilter));
        if (existsPredicate.isNegated()) {
            filter = new AstLogicalFilter(NOR, List.of(filter));
        }
        return filter;
    }

    private static Optional<ExistsOverUnnestShape> recognizeExistsOverUnnest(ExistsPredicate existsPredicate) {
        var select = existsPredicate.getExpression();
        if (!(select.getQueryPart() instanceof QuerySpec qs)) {
            return Optional.empty();
        }
        if (qs.getFromClause().getRoots().size() != 1
                || !qs.getGroupByClauseExpressions().isEmpty()
                || qs.hasSortSpecifications()
                || qs.hasOffsetOrFetchClause()) {
            return Optional.empty();
        }
        var root = qs.getFromClause().getRoots().get(0);
        if (!root.getTableGroupJoins().isEmpty()
                || !root.getNestedTableGroupJoins().isEmpty()) {
            return Optional.empty();
        }
        if (!(root.getPrimaryTableReference() instanceof FunctionTableReference ftr)
                || !MongoUnnestFunction.FUNCTION_NAME.equals(
                        ftr.getFunctionExpression().getFunctionName())) {
            return Optional.empty();
        }
        var args = ftr.getFunctionExpression().getArguments();
        if (args.size() != 1) {
            return Optional.empty();
        }
        var arg = args.get(0);
        if (!(arg instanceof BasicValuedPathInterpretation<?> bvpi)) {
            return Optional.empty();
        }
        var columnReference = bvpi.getColumnReference();
        if (columnReference == null) {
            return Optional.empty();
        }
        var arrayFieldName = columnReference.getColumnExpression();
        return Optional.of(new ExistsOverUnnestShape(
                arrayFieldName, ftr.getIdentificationVariable(), qs.getWhereClauseRestrictions()));
    }

    private record ExistsOverUnnestShape(
            String arrayFieldName,
            String innerAlias,
            @Nullable Predicate body) {}

    @Override
    public void visitLikePredicate(LikePredicate likePredicate) {
        Character escape = null;
        if (likePredicate.getEscapeCharacter() != null) {
            escape = extractLiteral(likePredicate.getEscapeCharacter(), Character.class, "escape character in LIKE");
        }
        var pattern = extractLiteral(likePredicate.getPattern(), String.class, "pattern in LIKE");

        var fieldPath = acceptAndYield(likePredicate.getMatchExpression(), FIELD_PATH);
        final var filter = new AstFieldOperationFilter(
                fieldPath,
                new AstRegularExpressionFilterOperation(
                        quoteMeta(pattern, escape), likePredicate.isCaseSensitive() ? "s" : "is"));
        astVisitorValueHolder.yield(
                FILTER, likePredicate.isNegated() ? new AstLogicalFilter(NOR, List.of(filter)) : filter);
    }

    @Override
    public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {
        var expression = nullnessPredicate.getExpression();
        if (!isFieldPathExpression(expression)) {
            throw new FeatureNotSupportedException(
                    "Only the following nullness predicates are supported: field is [not] null");
        }
        var fieldPath = acceptAndYield(expression, FIELD_PATH);
        var operator = nullnessPredicate.isNegated() ? NE : EQ;
        var operation = new AstComparisonFilterOperation(operator, new AstLiteral(BsonNull.VALUE));
        astVisitorValueHolder.yield(FILTER, new AstFieldOperationFilter(fieldPath, operation));
    }

    @Override
    public void visitThruthnessPredicate(ThruthnessPredicate thruthnessPredicate) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate) {
        assertFalse(selfRenderingPredicate.isEmpty());
        selfRenderingPredicate.getSelfRenderingExpression().accept(this);
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
        if (optionalTableUpdate.getMutatingTable().getTableMapping().isOptional()) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-69 https://jira.mongodb.org/browse/HIBERNATE-69");
        }
        var mutationResult = createMutationResult(
                optionalTableUpdate.getValueBindings(),
                optionalTableUpdate.getMutatingTable().getTableName(),
                createKeyFilter(optionalTableUpdate));
        astVisitorValueHolder.yield(MODEL_MUTATION_RESULT, mutationResult);
    }

    @Override
    public void visitCustomTableUpdate(TableUpdateCustomSql tableUpdateCustomSql) {
        throw new FeatureNotSupportedException();
    }

    static String renderMongoAstNode(AstNode rootAstNode) {
        try (var stringWriter = new StringWriter();
                var jsonWriter = new JsonWriter(stringWriter, EXTENDED_JSON_WRITER_SETTINGS)) {
            rootAstNode.render(jsonWriter);
            jsonWriter.flush();
            return stringWriter.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkQueryOptionsSupportability(QueryOptions queryOptions) {
        if (queryOptions.getTimeout() != null) {
            throw new FeatureNotSupportedException("'timeout' inQueryOptions is not supported");
        }
        if (queryOptions.getFlushMode() != null) {
            throw new FeatureNotSupportedException("'flushMode' in QueryOptions is not supported");
        }
        if (Boolean.TRUE.equals(queryOptions.isReadOnly())) {
            throw new FeatureNotSupportedException("'readOnly' in QueryOptions is not supported");
        }
        if (queryOptions.getAppliedGraph() != null
                && queryOptions.getAppliedGraph().getGraph() != null) {
            throw new FeatureNotSupportedException("'appliedGraph' in QueryOptions is not supported");
        }
        if (queryOptions.getTupleTransformer() != null) {
            throw new FeatureNotSupportedException("'tupleTransformer' in QueryOptions is not supported");
        }
        if (queryOptions.getResultListTransformer() != null) {
            throw new FeatureNotSupportedException("'resultListTransformer' in QueryOptions is not supported");
        }
        if (Boolean.TRUE.equals(queryOptions.isResultCachingEnabled())) {
            throw new FeatureNotSupportedException("'resultCaching' in QueryOptions is not supported");
        }
        if (queryOptions.getDisabledFetchProfiles() != null
                && !queryOptions.getDisabledFetchProfiles().isEmpty()) {
            throw new FeatureNotSupportedException("'disabledFetchProfiles' in QueryOptions is not supported");
        }
        if (queryOptions.getEnabledFetchProfiles() != null
                && !queryOptions.getEnabledFetchProfiles().isEmpty()) {
            throw new FeatureNotSupportedException("'enabledFetchProfiles' in QueryOptions is not supported");
        }
        if (queryOptions.getLockOptions() != null
                && !queryOptions.getLockOptions().isEmpty()) {
            throw new FeatureNotSupportedException("'lockOptions' in QueryOptions is not supported");
        }
        if (queryOptions.getDatabaseHints() != null
                && !queryOptions.getDatabaseHints().isEmpty()) {
            throw new FeatureNotSupportedException("'databaseHints' in QueryOptions is not supported");
        }
        if (queryOptions.getFetchSize() != null) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-54 https://jira.mongodb.org/browse/HIBERNATE-54");
        }
    }

    private static AstComparisonFilterOperator createAstComparisonFilterOperator(ComparisonOperator operator) {
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

    private static BsonValue toBsonValue(@Nullable Object value) {
        try {
            return ValueConversions.toBsonValue(value);
        } catch (SQLFeatureNotSupportedException e) {
            throw new FeatureNotSupportedException(e);
        }
    }

    private static void checkCteContainerSupportability(CteContainer cteContainer) {
        if (!cteContainer.getCteStatements().isEmpty()
                || !cteContainer.getCteObjects().isEmpty()) {
            throw new FeatureNotSupportedException("CTE is not supported");
        }
    }

    private static void checkMutationStatementSupportability(AbstractMutationStatement mutationStatement) {
        checkCteContainerSupportability(mutationStatement);
        if (!mutationStatement.getReturningColumns().isEmpty()) {
            throw new FeatureNotSupportedException("Returning columns from mutation statements is not supported");
        }
        if (mutationStatement instanceof AbstractUpdateOrDeleteStatement updateOrDeleteStatement) {
            var fromClause = updateOrDeleteStatement.getFromClause();
            if (!fromClause.getRoots().isEmpty() && fromClause.getRoots().get(0).hasRealJoins()) {
                throw new FeatureNotSupportedException("Joins in UPDATE/DELETE statements are not supported");
            }
            checkFromClauseSupportability(fromClause);
        }
    }

    private static void checkFromClauseSupportability(FromClause fromClause) {
        if (fromClause.getRoots().size() != 1) {
            throw new FeatureNotSupportedException("Only single root from clause is supported");
        }
        var root = fromClause.getRoots().get(0);
        if (root instanceof PluralTableGroup pluralRoot) {
            var elementDescriptor = pluralRoot.getModelPart().getElementDescriptor();
            if (elementDescriptor instanceof EmbeddableValuedModelPart embeddablePart
                    && embeddablePart.getEmbeddableTypeDescriptor().getAggregateMapping() == null) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-169 https://jira.mongodb.org/browse/HIBERNATE-169");
            }
            if (!(root.getPrimaryTableReference() instanceof NamedTableReference)) {
                throw new FeatureNotSupportedException("Only named table references are supported");
            }
        } else {
            if (!(root.getModelPart() instanceof EntityPersister entityPersister)) {
                throw new FeatureNotSupportedException("Only single table from clause is supported");
            }
            if (entityPersister.getQuerySpaces().length != 1) {
                if (entityPersister instanceof JoinedSubclassEntityPersister) {
                    throw new FeatureNotSupportedException(
                            "TODO-HIBERNATE-69 https://jira.mongodb.org/browse/HIBERNATE-69 JOINED inheritance is not supported");
                } else if (entityPersister instanceof UnionSubclassEntityPersister) {
                    throw new FeatureNotSupportedException("TABLE_PER_CLASS inheritance is not supported");
                } else if (entityPersister instanceof SingleTableEntityPersister) {
                    throw new FeatureNotSupportedException(
                            "TODO-HIBERNATE-181 https://jira.mongodb.org/browse/HIBERNATE-181 @SecondaryTable is not supported");
                }
                throw new FeatureNotSupportedException("Only single table from clause is supported");
            }
        }
    }

    private static <T> T extractLiteral(Expression expression, Class<T> type, String context) {
        if (expression instanceof Literal literal) {
            if (type.isInstance(literal.getLiteralValue())) {
                return type.cast(literal.getLiteralValue());
            }
        }
        throw new FeatureNotSupportedException(String.format(
                "Expression must be a literal %s in %s, but other expression was found.",
                type.getSimpleName(), context));
    }

    private record JoinColumns(ColumnReference outer, ColumnReference joined, boolean joinedOnLeft) {}

    private List<AstStage> buildJoinStages(TableGroup tableGroup) {
        var stages = new ArrayList<AstStage>();
        for (var tgj : tableGroup.getTableGroupJoins()) {
            var joinedGroup = tgj.getJoinedGroup();

            // Uninitialized groups are FK-only path navigation; virtual groups are synthetic joins
            // not rendered to SQL. Both match Hibernate's hasRealJoins() semantics.
            if (!joinedGroup.isInitialized() || joinedGroup.isVirtual()) {
                continue;
            }

            // TODO-HIBERNATE-169: when non-@Struct @ElementCollection join targets are supported, add a
            // PluralTableGroup embeddable check here mirroring the root-level guard in checkFromClauseSupportability.

            var preserve =
                    switch (tgj.getJoinType()) {
                        case INNER -> false;
                        case LEFT -> true;
                        case RIGHT ->
                            throw new FeatureNotSupportedException(
                                    "TODO-HIBERNATE-161 https://jira.mongodb.org/browse/HIBERNATE-161");
                        case FULL ->
                            throw new FeatureNotSupportedException(
                                    "TODO-HIBERNATE-162 https://jira.mongodb.org/browse/HIBERNATE-162");
                        case CROSS ->
                            throw new FeatureNotSupportedException(
                                    "TODO-HIBERNATE-163 https://jira.mongodb.org/browse/HIBERNATE-163");
                    };

            if (!joinedGroup.getNestedTableGroupJoins().isEmpty()) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-168 https://jira.mongodb.org/browse/HIBERNATE-168");
            }

            var primaryRef = joinedGroup.getPrimaryTableReference();

            if (primaryRef instanceof FunctionTableReference) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-111 https://jira.mongodb.org/browse/HIBERNATE-111");
            }
            if (primaryRef instanceof QueryPartTableReference) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-167 https://jira.mongodb.org/browse/HIBERNATE-167");
            }
            if (primaryRef instanceof UnionTableReference) {
                throw new FeatureNotSupportedException("TABLE_PER_CLASS inheritance joins are not supported");
            }
            if (!(primaryRef instanceof NamedTableReference joinedNtr)) {
                throw new FeatureNotSupportedException("Unsupported table reference type: "
                        + primaryRef.getClass().getSimpleName());
            }

            // TODO-HIBERNATE-69 TODO-HIBERNATE-181: if the joined entity has JOINED inheritance or @SecondaryTable,
            // its persister spans multiple tables — we need to emit additional $lookup stages for each
            // TableReferenceJoin.
            var joinedCollection = joinedNtr.getTableExpression();
            var joinedAlias = joinedNtr.getIdentificationVariable();

            affectedTableNames.add(joinedCollection);

            var lookupStage = buildJoinLookupStage(tgj.getPredicate(), joinedCollection, joinedAlias);

            joinedTableQualifiers.add(joinedAlias);

            stages.add(lookupStage);
            stages.add(new AstUnwindStage(JOIN_ALIAS_PREFIX + joinedAlias, preserve));
            stages.addAll(buildJoinStages(joinedGroup));
        }
        return stages;
    }

    /**
     * Builds the {@code $lookup} stage for a join {@code ON} condition. An {@code EQUAL} comparison maps to the simple
     * {@code localField}/{@code foreignField} form; the ordering and inequality operators ({@code <}, {@code <=},
     * {@code >}, {@code >=}, {@code !=}) require the pipeline form, which binds the outer column into a {@code let}
     * variable and compares it against the joined column with {@code $expr}.
     */
    private AstStage buildJoinLookupStage(@Nullable Predicate predicate, String joinedCollection, String joinedAlias) {
        if (predicate instanceof Junction) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-164 https://jira.mongodb.org/browse/HIBERNATE-164");
        }
        if (!(predicate instanceof ComparisonPredicate cp)) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-200 https://jira.mongodb.org/browse/HIBERNATE-200");
        }
        var operator = cp.getOperator();
        var columns = extractJoinColumns(cp, joinedAlias);
        var joinAlias = JOIN_ALIAS_PREFIX + joinedAlias;

        if (operator == ComparisonOperator.EQUAL) {
            return new AstLookupStage(
                    joinedCollection,
                    resolveFieldPath(columns.outer()),
                    columns.joined().getColumnExpression(),
                    joinAlias);
        }

        // The $expr array is always [<outer>, <joined>]; invert the operator when Hibernate placed the joined column
        // on the left so the operand order stays outer-then-joined.
        var exprOperator = createAstExprComparisonFilterOperator(columns.joinedOnLeft() ? operator.invert() : operator);

        var letVariable = "v0";
        var expr = new AstExprFilter(
                exprOperator,
                new AstFieldPathValue("$$" + letVariable),
                new AstFieldPathValue("$" + columns.joined().getColumnExpression()));
        return new AstLookupStageWithPipeline(
                joinedCollection,
                List.of(new AstElement(letVariable, new AstFieldPathValue("$" + resolveFieldPath(columns.outer())))),
                List.of(new AstMatchStage(expr)),
                joinAlias);
    }

    private JoinColumns extractJoinColumns(ComparisonPredicate cp, String joinedAlias) {
        var lhsCr = extractColumnReference(cp.getLeftHandExpression());
        var rhsCr = extractColumnReference(cp.getRightHandExpression());
        if (lhsCr == null || rhsCr == null) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-166 https://jira.mongodb.org/browse/HIBERNATE-166");
        }
        if (lhsCr.isColumnExpressionFormula() || rhsCr.isColumnExpressionFormula()) {
            throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-182 https://jira.mongodb.org/browse/HIBERNATE-182 @JoinFormula is not supported");
        }
        var lhsIsJoined = joinedAlias.equals(lhsCr.getQualifier());
        var rhsIsJoined = joinedAlias.equals(rhsCr.getQualifier());
        if (lhsIsJoined == rhsIsJoined) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-170 https://jira.mongodb.org/browse/HIBERNATE-170");
        }
        return lhsIsJoined ? new JoinColumns(rhsCr, lhsCr, true) : new JoinColumns(lhsCr, rhsCr, false);
    }

    /**
     * Maps the non-equality comparison operators supported for non-equijoin {@code ON} conditions to their
     * aggregation-expression counterparts. {@code EQUAL} never reaches here — it is routed to the simple
     * {@code $lookup} form by the caller — and {@code DISTINCT_FROM}/{@code NOT_DISTINCT_FROM} are not yet supported.
     */
    private static AstExprComparisonFilterOperator createAstExprComparisonFilterOperator(ComparisonOperator operator) {
        return switch (operator) {
            case NOT_EQUAL -> AstExprComparisonFilterOperator.NE;
            case LESS_THAN -> AstExprComparisonFilterOperator.LT;
            case LESS_THAN_OR_EQUAL -> AstExprComparisonFilterOperator.LTE;
            case GREATER_THAN -> AstExprComparisonFilterOperator.GT;
            case GREATER_THAN_OR_EQUAL -> AstExprComparisonFilterOperator.GTE;
            default ->
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-200 https://jira.mongodb.org/browse/HIBERNATE-200");
        };
    }

    private static final class OffsetJdbcParameter extends AbstractJdbcParameter {

        OffsetJdbcParameter(BasicType<Integer> type) {
            super(type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void bindParameterValue(
                PreparedStatement statement,
                int startPosition,
                JdbcParameterBindings jdbcParamBindings,
                ExecutionContext executionContext)
                throws SQLException {
            getJdbcMapping()
                    .getJdbcValueBinder()
                    .bind(
                            statement,
                            executionContext.getQueryOptions().getLimit().getFirstRow(),
                            startPosition,
                            executionContext.getSession());
        }
    }

    private static final class LimitJdbcParameter extends AbstractJdbcParameter {

        LimitJdbcParameter(BasicType<Integer> type) {
            super(type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void bindParameterValue(
                PreparedStatement statement,
                int startPosition,
                JdbcParameterBindings jdbcParamBindings,
                ExecutionContext executionContext)
                throws SQLException {
            getJdbcMapping()
                    .getJdbcValueBinder()
                    .bind(
                            statement,
                            executionContext.getQueryOptions().getLimit().getMaxRows(),
                            startPosition,
                            executionContext.getSession());
        }
    }

    /**
     * This {@link SqlAppender} makes any {@link SelfRenderingExpression} explicitly unsupported, unless we implemented
     * its rendering such that it avoids using this appender. Unfortunately, this class does not give us protection if a
     * {@link SelfRenderingExpression} delegates rendering to its {@link SqlAstTranslator}, and does not explicitly use
     * its {@link SqlAppender}.
     */
    private static final class FeatureNotSupportedSqlAppender implements SqlAppender {
        static final FeatureNotSupportedSqlAppender INSTANCE = new FeatureNotSupportedSqlAppender();

        private FeatureNotSupportedSqlAppender() {}

        @Override
        public void appendSql(String fragment) {
            throw new FeatureNotSupportedException();
        }
    }

    private static String getUnsupportedUpdateValueAssignmentMessage(final String fieldPath, Expression assignedValue) {
        if (assignedValue instanceof FunctionExpression ex) {
            return "Function expression [%s] as update assignment value for field path [%s] is not supported"
                    .formatted(ex.getFunctionName(), fieldPath);
        } else if (assignedValue instanceof Predicate) {
            return "Predicate expression as update assignment value for field path [%s] is not supported"
                    .formatted(fieldPath);
        } else if (assignedValue instanceof SqmPathInterpretation) {
            return "Path expression as update assignment value for field path [%s] is not supported"
                    .formatted(fieldPath);
        } else {
            return "Update assignment value for field path [%s] is not supported".formatted(fieldPath);
        }
    }
}
