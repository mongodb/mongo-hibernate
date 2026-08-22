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
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.MONGO_DBMS_NAME;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_NAME;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.EXPRESSION;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FIELD_PATH;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FILTER;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MODEL_MUTATION_RESULT;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.MUTATION_RESULT;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.PROJECT_STAGE_SPECIFICATIONS;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.SELECT_RESULT;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.SORT_FIELDS;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.TUPLE;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.UPSERT_MODEL_MUTATION_RESULT;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.VALUE;
import static com.mongodb.hibernate.internal.translate.mongoast.AstLiteral.FALSE;
import static com.mongodb.hibernate.internal.translate.mongoast.AstLiteral.TRUE;
import static com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement.createMultiUpdateStatement;
import static com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement.createUpsertStatement;
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
import static com.mongodb.hibernate.internal.translate.mongoast.filter.AstRegularExpressionFilterOperation.quoteMeta;
import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static org.hibernate.query.common.FetchClauseType.ROWS_ONLY;
import static org.hibernate.sql.ast.tree.expression.SqlTupleContainer.getSqlTuple;

import com.mongodb.hibernate.internal.EmbeddedIdColumnName;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.dialect.function.ExpressionFunction;
import com.mongodb.hibernate.internal.dialect.function.array.MongoUnnestFunction;
import com.mongodb.hibernate.internal.service.StandardServiceRegistryScopedState;
import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstComparisonExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstComputedFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstConversionExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstInExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstLogicalOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstLogicalOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstParameterMarker;
import com.mongodb.hibernate.internal.translate.mongoast.AstRegexMatchExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstSwitchCase;
import com.mongodb.hibernate.internal.translate.mongoast.AstSwitchExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstUnaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstValue;
import com.mongodb.hibernate.internal.translate.mongoast.AstValueExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstVariableExpression;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstDeleteCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstDocumentUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstPipelineUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstAggregateCommand;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLetVariable;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLimitStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLookupStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLookupStageWithPipeline;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstMatchStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstProjectStageExpressionSpecification;
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
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstListComparisonFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstLogicalFilterOperator;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstRegularExpressionFilterOperation;
import com.mongodb.hibernate.internal.type.ValueConversions;
import jakarta.persistence.criteria.Nulls;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonWriter;
import org.hibernate.dialect.Replacer;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.metamodel.mapping.internal.EmbeddedAttributeMapping;
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
import org.hibernate.sql.model.ast.ColumnValueParameter;
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.ast.MutatingTableReference;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.internal.TableDeleteCustomSql;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateCustomSql;
import org.hibernate.sql.model.internal.TableUpdateStandard;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.type.BasicType;
import org.hibernate.type.SqlTypes;
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
    // Match a quoted SQL string with the contents of the string being in match group 1
    private static final Pattern SQL_STRING = Pattern.compile("^'((?:''|[^'])*)'$");

    private final SessionFactoryImplementor sessionFactory;

    private final AstVisitorValueHolder astVisitorValueHolder = new AstVisitorValueHolder();

    private @Nullable String elemMatchInnerAlias;

    private @Nullable JoinLookupContext joinLookupContext;

    private final Set<String> affectedTableNames = new HashSet<>();

    private final Set<String> joinedTableQualifiers = new HashSet<>();

    private final GroupByContext groupByContext = new GroupByContext();

    /**
     * Per-query state for GROUP BY translation. Populated by {@link #createGroupStage} and consulted by
     * {@link #resolveFieldPath} to rewrite grouped column references to {@code $_id.<subKey>}.
     */
    static final class GroupByContext {
        enum Phase {
            INACTIVE,
            POPULATING,
            AFTER_GROUP
        }

        private Phase phase = Phase.INACTIVE;
        private final Map<Expression, String> exprMappings = new LinkedHashMap<>();

        void beginPopulating() {
            phase = Phase.POPULATING;
        }

        void finishPopulating() {
            phase = Phase.AFTER_GROUP;
        }

        boolean isAfterGroup() {
            return phase == Phase.AFTER_GROUP;
        }

        void put(Expression key, String subKey) {
            exprMappings.put(key, subKey);
        }

        @Nullable String get(Expression key) {
            return exprMappings.get(key);
        }
    }

    // Per-query counter for naming $lookup `let` variables; see nextLetVariableName.
    private int letVariableCounter;

    private @Nullable QueryOptionsLimit queryOptionsLimit;

    private @Nullable Map<Integer, String> projectionKeyMap;

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

    // These yield a non-EXPRESSION descriptor from their visitors (tuple, $elemMatch), so reject them
    // up front with a clear FeatureNotSupportedException rather than tripping the holder's assertion.
    // Other unsupported nodes throw in their own visit methods and surface cleanly on their own.
    private AstExpression acceptAndYieldExpression(Expression expression) {
        if (expression instanceof SqlTuple || expression instanceof ExistsPredicate) {
            throw new FeatureNotSupportedException(
                    "Expression not supported: " + expression.getClass().getSimpleName());
        }
        return acceptAndYield(expression, EXPRESSION);
    }

    @SuppressWarnings("NamedLikeContextualKeyword")
    public <R> void yield(AstVisitorValueDescriptor<R> valueDescriptor, R value) {
        astVisitorValueHolder.yield(valueDescriptor, value);
    }

    // Column bindings for a composite id are flattened by boot-time metadata into sibling
    // "_id.<component>" columns; gather them back into a single leading "_id" sub-document, with
    // components ordered by name for a canonical shape, since the "_id" unique index is sensitive
    // to BSON field order.
    private static List<AstElement> assembleWithIdSubdocument(List<AstElement> flat) {
        var idComponents = new TreeSet<>(comparing(AstElement::name));
        var result = new ArrayList<AstElement>(flat.size());
        for (var element : flat) {
            if (EmbeddedIdColumnName.isComponent(element.name())) {
                assertTrue(idComponents.add(
                        new AstElement(EmbeddedIdColumnName.componentName(element.name()), element.value())));
            } else {
                result.add(element);
            }
        }
        if (!idComponents.isEmpty()) {
            result.add(0, new AstElement(ID_FIELD_NAME, new AstDocument(idComponents)));
        }
        return result;
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
                ModelMutationMqlTranslator.Result.create(new AstInsertCommand(
                        tableInsert.getMutatingTable().getTableName(),
                        List.of(new AstDocument(assembleWithIdSubdocument(astElements))))));
    }

    @Override
    public void visitColumnWriteFragment(ColumnWriteFragment columnWriteFragment) {
        switch (columnWriteFragment.getParameters().size()) {
            case 0 -> {
                // Hibernate inheritance discriminators should be string/char or integer. It is possible that other
                // fragments are generated with other types through other code paths (_e.g._, @Generated).
                var matcher = SQL_STRING.matcher(columnWriteFragment.getFragment());
                BsonValue result;
                if (matcher.matches()) {
                    result = new BsonString(matcher.group(1).replace("''", "'"));
                } else {
                    try {
                        result = new BsonInt32(Integer.parseInt(columnWriteFragment.getFragment()));
                    } catch (NumberFormatException e) {
                        throw new FeatureNotSupportedException(
                                "Cannot translate fragment %s into MQL. Expecting string or integer literal."
                                        .formatted(columnWriteFragment.getFragment()));
                    }
                }
                astVisitorValueHolder.yield(VALUE, new AstLiteral(result));
            }
            case 1 -> {
                if (columnWriteFragment.getFragment().equals("?")) {
                    columnWriteFragment.getParameters().iterator().next().accept(this);
                } else {
                    throw new FeatureNotSupportedException("@ColumnTransformer expressions are not supported");
                }
            }
            default -> throw fail("unexpected multi-parameter discriminator write fragment: " + columnWriteFragment);
        }
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
                        new AstDeleteCommand(tableDelete.getMutatingTable().getTableName(), keyFilter)));
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
        return ModelMutationMqlTranslator.Result.create(astUpdateCommand);
    }

    private AstFilter createKeyFilter(AbstractRestrictedTableMutation<? extends MutationOperation> tableMutation) {
        var predicates = new ArrayList<AstFilter>(
                tableMutation.getNumberOfKeyBindings() + tableMutation.getNumberOfOptimisticLockBindings());
        for (var keyBinding : tableMutation.getKeyBindings()) {
            predicates.add(createEqualityFilter(keyBinding));
        }
        for (var lockBinding : tableMutation.getOptimisticLockBindings()) {
            predicates.add(createEqualityFilter(lockBinding));
        }
        return predicates.size() == 1
                ? predicates.get(0)
                : new AstLogicalFilter(AstLogicalFilterOperator.AND, predicates);
    }

    private AstFieldOperationFilter createEqualityFilter(ColumnValueBinding binding) {
        var fieldPath = acceptAndYield(binding.getColumnReference(), FIELD_PATH);
        var fieldValue = acceptAndYield(binding.getValueExpression(), VALUE);
        return new AstFieldOperationFilter(fieldPath, new AstComparisonFilterOperation(EQ, fieldValue));
    }

    @Override
    public void visitParameter(JdbcParameter jdbcParameter) {
        yieldValueOrExpression(
                new AstParameterMarker(jdbcParameter.getParameterBinder()),
                parameterNeedsLiteralWrapping(jdbcParameter));
    }

    // A parameter's bound value is unknown at translation time, so a string-typed parameter (which could
    // bind a $-prefixed value) is wrapped in $literal in expression position; numeric/boolean/etc. are
    // verbatim-safe. Unknown types are wrapped defensively.
    private static boolean parameterNeedsLiteralWrapping(JdbcParameter jdbcParameter) {
        var expressionType = jdbcParameter.getExpressionType();
        if (expressionType == null || expressionType.getJdbcTypeCount() != 1) {
            return true;
        }
        return expressionType.getSingleJdbcMapping().getJdbcType().isStringLike();
    }

    @Override
    public void visitSelectStatement(SelectStatement selectStatement) {
        if (!selectStatement.getQueryPart().isRoot()) {
            throw new FeatureNotSupportedException("Subquery not supported");
        }
        checkCteContainerSupportability(selectStatement);
        projectionKeyMap = buildProjectionKeyMap(selectStatement.getDomainResultDescriptors());
        selectStatement.getQueryPart().accept(this);
    }

    @Override
    public void visitQuerySpec(QuerySpec querySpec) {
        var collection = acceptAndYield(querySpec.getFromClause(), COLLECTION_NAME);

        var stages = new ArrayList<AstStage>();

        var root = querySpec.getFromClause().getRoots().get(0);
        stages.addAll(buildJoinStages(root));

        createMatchStage(querySpec.getWhereClauseRestrictions()).ifPresent(stages::add);
        createGroupStage(querySpec).ifPresent(stages::add);
        createMatchStage(querySpec.getHavingClauseRestrictions()).ifPresent(stages::add);
        createSortStage(querySpec).ifPresent(stages::add);

        var skipLimitStagesAndJdbcParams =
                assertNotNull(queryOptionsLimit).createSkipLimitStagesAndJdbcParams(querySpec);
        stages.addAll(skipLimitStagesAndJdbcParams.stages());

        stages.add(createProjectStage(querySpec.getSelectClause()));

        astVisitorValueHolder.yield(
                SELECT_RESULT,
                new SelectMqlTranslator.Result(
                        new AstAggregateCommand(collection, stages),
                        affectedTableNames,
                        skipLimitStagesAndJdbcParams.offset(),
                        skipLimitStagesAndJdbcParams.limit()));
    }

    private Optional<AstGroupStage> createGroupStage(final QuerySpec querySpec) {
        if (querySpec.getGroupByClauseExpressions().isEmpty()) {
            return Optional.empty();
        }
        groupByContext.beginPopulating();
        try {
            List<AstGroupStageSpecification> specifications = new ArrayList<>();
            for (Expression groupByClauseExpression : querySpec.getGroupByClauseExpressions()) {
                if (groupByClauseExpression.getColumnReference() != null) {
                    var columnReference = groupByClauseExpression.getColumnReference();
                    var fieldPath = acceptAndYield(columnReference, FIELD_PATH);
                    var groupKey = fieldPath.replace('.', '#');
                    groupByContext.put(columnReference, groupKey);
                    specifications.add(new AstGroupStageSpecification(groupKey, new AstFieldPathExpression(fieldPath)));
                } else {
                    throw new FeatureNotSupportedException(
                            "TODO-HIBERNATE-241 Only column references are supported in group by");
                }
            }
            return Optional.of(new AstGroupStage(specifications));
        } finally {
            groupByContext.finishPopulating();
        }
    }

    private Optional<AstMatchStage> createMatchStage(Predicate whereClauseRestrictions) {
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
        // A comparison used as a value (e.g. `select x > 1`) yields a bare aggregation expression;
        // otherwise it is a filter.
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            astVisitorValueHolder.yield(EXPRESSION, toComparisonExpression(comparisonPredicate));
        } else {
            astVisitorValueHolder.yield(FILTER, toFilter(comparisonPredicate));
        }
    }

    private AstFilter toFilter(ComparisonPredicate comparisonPredicate) {
        return isFieldValueComparison(comparisonPredicate)
                ? toFieldValueFilter(comparisonPredicate)
                : new AstExprFilter(toComparisonExpression(comparisonPredicate));
    }

    private AstFilter toFieldValueFilter(ComparisonPredicate comparisonPredicate) {
        if (getSqlTuple(comparisonPredicate.getLeftHandExpression()) != null) {
            return toRowValueFieldFilter(comparisonPredicate);
        } else {
            var lhs = comparisonPredicate.getLeftHandExpression();
            var rhs = comparisonPredicate.getRightHandExpression();
            var isFieldOnLeftHandSide = isFieldPathExpression(lhs);
            if (!isFieldOnLeftHandSide) {
                assertTrue(isFieldPathExpression(rhs));
            }
            var fieldPath = acceptAndYield(isFieldOnLeftHandSide ? lhs : rhs, FIELD_PATH);
            var comparisonValue = acceptAndYield(isFieldOnLeftHandSide ? rhs : lhs, VALUE);
            // The MQL operator always reads field-then-value, so invert when the field is on the right.
            var operator = isFieldOnLeftHandSide
                    ? comparisonPredicate.getOperator()
                    : comparisonPredicate.getOperator().invert();
            return new AstFieldOperationFilter(
                    fieldPath,
                    new AstComparisonFilterOperation(createAstComparisonFilterOperator(operator), comparisonValue));
        }
    }

    private AstExpression toComparisonExpression(ComparisonPredicate comparisonPredicate) {
        if (getSqlTuple(comparisonPredicate.getLeftHandExpression()) != null) {
            var operator = comparisonPredicate.getOperator();
            if (operator != ComparisonOperator.EQUAL && operator != ComparisonOperator.NOT_EQUAL) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-211 https://jira.mongodb.org/browse/HIBERNATE-211");
            }
            var lhsTuple = getSqlTuple(comparisonPredicate.getLeftHandExpression());
            var rhsTuple = assertNotNull(getSqlTuple(comparisonPredicate.getRightHandExpression()));
            var lhsComponents = lhsTuple.getExpressions();
            var rhsComponents = rhsTuple.getExpressions();
            assertTrue(lhsComponents.size() == rhsComponents.size());
            var componentEqualities = new ArrayList<AstExpression>(lhsComponents.size());
            for (var i = 0; i < lhsComponents.size(); i++) {
                componentEqualities.add(new AstBinaryOperatorExpression(
                        toExprComparisonOperator(ComparisonOperator.EQUAL),
                        acceptAndYieldExpression(lhsComponents.get(i)),
                        acceptAndYieldExpression(rhsComponents.get(i))));
            }
            var conjunction = new AstLogicalOperatorExpression(AstLogicalOperator.AND, componentEqualities);
            return operator == ComparisonOperator.EQUAL
                    ? conjunction
                    : new AstLogicalOperatorExpression(AstLogicalOperator.NOT, List.of(conjunction));
        } else {
            var lhsExpr = acceptAndYieldExpression(comparisonPredicate.getLeftHandExpression());
            var rhsExpr = acceptAndYieldExpression(comparisonPredicate.getRightHandExpression());
            return new AstBinaryOperatorExpression(
                    toExprComparisonOperator(comparisonPredicate.getOperator()), lhsExpr, rhsExpr);
        }
    }

    // Compact form of a row-value = / <> comparison: AND of per-component {field: {$eq: value}} (field side
    // detected per component), wrapped in $nor for <>. Only reached when isFieldValueComparison is true.
    private AstFilter toRowValueFieldFilter(ComparisonPredicate comparisonPredicate) {
        var lhsComponents = assertNotNull(getSqlTuple(comparisonPredicate.getLeftHandExpression()))
                .getExpressions();
        var rhsComponents = assertNotNull(getSqlTuple(comparisonPredicate.getRightHandExpression()))
                .getExpressions();
        var componentFilters = new ArrayList<AstFilter>(lhsComponents.size());
        for (var i = 0; i < lhsComponents.size(); i++) {
            var left = lhsComponents.get(i);
            var right = rhsComponents.get(i);
            var fieldOnLeft = isFieldPathExpression(left);
            componentFilters.add(new AstFieldOperationFilter(
                    acceptAndYield(fieldOnLeft ? left : right, FIELD_PATH),
                    new AstComparisonFilterOperation(EQ, acceptAndYield(fieldOnLeft ? right : left, VALUE))));
        }
        var conjunction = new AstLogicalFilter(AstLogicalFilterOperator.AND, componentFilters);
        return comparisonPredicate.getOperator() == ComparisonOperator.EQUAL
                ? conjunction
                : new AstLogicalFilter(AstLogicalFilterOperator.NOR, List.of(conjunction));
    }

    @Override
    public void visitNegatedPredicate(NegatedPredicate negatedPredicate) {
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            var operand = acceptAndYieldExpression(negatedPredicate.getPredicate());
            astVisitorValueHolder.yield(
                    EXPRESSION, new AstLogicalOperatorExpression(AstLogicalOperator.NOT, List.of(operand)));
        } else {
            var filter = acceptAndYield(negatedPredicate.getPredicate(), FILTER);
            astVisitorValueHolder.yield(FILTER, new AstLogicalFilter(AstLogicalFilterOperator.NOR, List.of(filter)));
        }
    }

    @Override
    public void visitGroupedPredicate(GroupedPredicate groupedPredicate) {
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            var expression = acceptAndYieldExpression(groupedPredicate.getSubPredicate());
            astVisitorValueHolder.yield(EXPRESSION, expression);
        } else {
            var filter = acceptAndYield(groupedPredicate.getSubPredicate(), FILTER);
            astVisitorValueHolder.yield(FILTER, filter);
        }
    }

    @Override
    public void visitSelectClause(SelectClause selectClause) {
        if (selectClause.isDistinct()) {
            throw new FeatureNotSupportedException("TODO-HIBERNATE-205 SELECT DISTINCT is not supported");
        }
        var projectStageSpecifications = new ArrayList<AstProjectStageSpecification>(
                selectClause.getSqlSelections().size());

        for (var sqlSelection : selectClause.getSqlSelections()) {
            if (sqlSelection.isVirtual()) {
                continue;
            }
            AstProjectStageSpecification spec;
            if (sqlSelection.getExpression() instanceof ColumnReference columnReference) {
                var field = acceptAndYield(columnReference, FIELD_PATH);
                if (field.startsWith(JOIN_ALIAS_PREFIX)) {
                    spec = new AstProjectStageFieldPathSpecification(joinFieldProjectionKey(field), field);
                } else if (field.contains(".")) {
                    spec = new AstProjectStageFieldPathSpecification(nestFieldProjectionKey(field), field);
                } else {
                    spec = new AstProjectStageIncludeSpecification(field);
                }
            } else {
                var key = resolveProjectionKey(sqlSelection);
                var projectionExpression = acceptAndYieldExpression(sqlSelection.getExpression());
                if (projectionExpression instanceof AstValueExpression valueExpression) {
                    // A $project field value is misread whatever the value is, so the verbatim/wrapped
                    // decision the visitor just made does not apply here: a number or boolean is an
                    // inclusion/exclusion flag rather than a constant.
                    spec = new AstProjectStageExpressionSpecification(
                            key, new AstLiteralExpression(valueExpression.value()));
                } else {
                    spec = new AstProjectStageExpressionSpecification(key, projectionExpression);
                }
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
        if (joinLookupContext != null) {
            // Inside a $lookup sub-pipeline the joined collection is the root, so its columns are bare field
            // paths; any other (outer) column is out of scope and must be bound into a `let` variable and
            // referenced as `$$v…`. This lets the ON predicate reuse the general EXPRESSION-mode visitors.
            if (joinLookupContext.isJoinedColumn(columnReference)) {
                yieldFieldPathOrExpression(columnReference.getColumnExpression());
            } else {
                var letVariableName = nextLetVariableName(columnReference);
                joinLookupContext.addLetVariable(new AstLetVariable(
                        letVariableName, new AstFieldPathExpression(resolveFieldPath(columnReference))));
                astVisitorValueHolder.yield(EXPRESSION, new AstVariableExpression(letVariableName));
            }
            return;
        }
        if (elemMatchInnerAlias != null) {
            var qualifier = assertNotNull(columnReference.getQualifier());
            if (!qualifier.equals(elemMatchInnerAlias)) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-177 https://jira.mongodb.org/browse/HIBERNATE-177");
            }
        }
        yieldFieldPathOrExpression(resolveFieldPath(columnReference));
    }

    private String resolveFieldPath(ColumnReference columnReference) {
        var qualifier = columnReference.getQualifier();
        if (groupByContext.isAfterGroup()) {
            String groupKey = groupByContext.get(columnReference);
            if (groupKey != null) {
                return "_id." + groupKey;
            }
            throw new FeatureNotSupportedException(
                    "TODO-HIBERNATE-241 Columns that are not part of group by are not supported");
        }
        return (qualifier != null && joinedTableQualifiers.contains(qualifier))
                ? JOIN_ALIAS_PREFIX + qualifier + "." + columnReference.getColumnExpression()
                : columnReference.getColumnExpression();
    }

    // A literal or parameter is a document value in FIELD/VALUE position, but in aggregation-expression
    // position a value and an expression do not always render the same (a $-prefixed string is a field
    // path there, a document/array is an operator invocation). A value that could be misread is wrapped
    // in $literal via AstLiteralExpression; one that cannot is used verbatim via AstValueExpression.
    // This applies to operand position, the only one reachable from here. A $project field value is the
    // exception, misread whatever the value is, and visitSelectClause wraps it unconditionally.
    private void yieldValueOrExpression(AstValue value, boolean literalWrapped) {
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            astVisitorValueHolder.yield(
                    EXPRESSION, literalWrapped ? new AstLiteralExpression(value) : new AstValueExpression(value));
        } else {
            astVisitorValueHolder.yield(VALUE, value);
        }
    }

    // Whether a literal value would be misread in operand position and so needs $literal: a string
    // beginning with `$` (a field path), or a document/array (an operator invocation).
    private static boolean needsLiteralWrapping(BsonValue value) {
        return value.isDocument()
                || value.isArray()
                || (value.isString() && value.asString().getValue().startsWith("$"));
    }

    // A column reference is a bare field-path string in most positions, but inside an aggregation
    // expression it must be wrapped as an AstFieldPathExpression.
    private void yieldFieldPathOrExpression(String fieldPath) {
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            astVisitorValueHolder.yield(EXPRESSION, new AstFieldPathExpression(fieldPath));
        } else {
            astVisitorValueHolder.yield(FIELD_PATH, fieldPath);
        }
    }

    // Maps each scalar projection's valuesArrayPosition to the $project key it should use: its AS alias
    // when it has one, otherwise a generated "#c_<n>". The alias lives on the DomainResult and the
    // position on the SqlSelection, so this bridges the two up front — resolveProjectionKey is then a
    // plain lookup. collectValueIndexesToCache() sets one bit per column a result consumes; cardinality
    // == 1 marks a scalar (an entity consumes several columns and is projected field-by-field instead,
    // never through this key). The "#c_" prefix keeps a generated key clear of any mapped field name
    // (# is rejected in those); a generated key that would clash with an explicit alias is skipped,
    // since a backtick-quoted HQL alias can be anything (e.g. `select y + 1, x + 1 as `#c_1``).
    private static Map<Integer, String> buildProjectionKeyMap(List<DomainResult<?>> domainResults) {
        var aliases = new HashSet<String>();
        for (DomainResult<?> domainResult : domainResults) {
            var alias = domainResult.getResultVariable();
            if (alias != null) {
                aliases.add(alias);
            }
        }
        var keyMap = new HashMap<Integer, String>();
        var scalarCount = 0;
        for (DomainResult<?> domainResult : domainResults) {
            var bitSet = new BitSet();
            domainResult.collectValueIndexesToCache(bitSet);
            if (bitSet.cardinality() == 1) {
                var alias = domainResult.getResultVariable();
                String key = alias;
                if (key == null) {
                    do {
                        key = "#c_" + ++scalarCount;
                    } while (aliases.contains(key));
                }
                keyMap.put(bitSet.nextSetBit(0), key);
            }
        }
        return keyMap;
    }

    private String resolveProjectionKey(SqlSelection sqlSelection) {
        return assertNotNull(assertNotNull(projectionKeyMap).get(sqlSelection.getValuesArrayPosition()));
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
        var bsonValue = toBsonValue(queryLiteral.getLiteralValue());
        yieldValueOrExpression(new AstLiteral(bsonValue), needsLiteralWrapping(bsonValue));
    }

    @Override
    public void visitJunction(Junction junction) {
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            var operands = new ArrayList<AstExpression>(junction.getPredicates().size());
            for (var predicate : junction.getPredicates()) {
                operands.add(acceptAndYieldExpression(predicate));
            }
            var operator =
                    switch (junction.getNature()) {
                        case DISJUNCTION -> AstLogicalOperator.OR;
                        case CONJUNCTION -> AstLogicalOperator.AND;
                    };
            astVisitorValueHolder.yield(EXPRESSION, new AstLogicalOperatorExpression(operator, operands));
        } else {
            var subFilters = new ArrayList<AstFilter>(junction.getPredicates().size());
            for (var predicate : junction.getPredicates()) {
                subFilters.add(acceptAndYield(predicate, FILTER));
            }
            var operator =
                    switch (junction.getNature()) {
                        case DISJUNCTION -> AstLogicalFilterOperator.OR;
                        case CONJUNCTION -> AstLogicalFilterOperator.AND;
                    };
            astVisitorValueHolder.yield(FILTER, new AstLogicalFilter(operator, subFilters));
        }
    }

    @Override
    public <N extends Number> void visitUnparsedNumericLiteral(UnparsedNumericLiteral<N> unparsedNumericLiteral) {
        var bsonValue = toBsonValue(assertNotNull(unparsedNumericLiteral.getLiteralValue()));
        yieldValueOrExpression(new AstLiteral(bsonValue), needsLiteralWrapping(bsonValue));
    }

    @Override
    public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            var operand = acceptAndYieldExpression(booleanExpressionPredicate.getExpression());
            var expected = new AstValueExpression(booleanExpressionPredicate.isNegated() ? FALSE : TRUE);
            var comparison = new AstBinaryOperatorExpression(AstComparisonExpressionOperator.EQ, operand, expected);
            astVisitorValueHolder.yield(EXPRESSION, comparison);
        } else {
            if (!isFieldPathExpression(booleanExpressionPredicate.getExpression())) {
                throw new FeatureNotSupportedException("Expression not of field path is not supported");
            }
            var fieldPath = acceptAndYield(booleanExpressionPredicate.getExpression(), FIELD_PATH);
            var astFilterOperation =
                    new AstComparisonFilterOperation(EQ, booleanExpressionPredicate.isNegated() ? FALSE : TRUE);
            var filter = new AstFieldOperationFilter(fieldPath, astFilterOperation);
            astVisitorValueHolder.yield(FILTER, filter);
        }
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
        var sqlTuple = getSqlTuple(sortExpression);
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
            if (getSqlTuple(expression) != null) {
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
                new MutationMqlTranslator.Result(new AstDeleteCommand(collection, filter), affectedTableNames));
    }

    @Override
    public void visitUpdateStatement(UpdateStatement updateStatement) {
        checkMutationStatementSupportability(updateStatement);
        var collection = addToAffectedTableNames(updateStatement.getTargetTable());
        var filter = createAstFilter(updateStatement);
        var assignments = updateStatement.getAssignments();
        var allValues = assignments.stream().allMatch(assignment -> isValueExpression(assignment.getAssignedValue()));
        AstUpdate update = allValues ? buildDocumentUpdate(assignments) : buildPipelineUpdate(assignments);
        astVisitorValueHolder.yield(
                MUTATION_RESULT,
                new MutationMqlTranslator.Result(
                        new AstUpdateCommand(collection, List.of(createMultiUpdateStatement(filter, update))),
                        affectedTableNames));
    }

    private AstDocumentUpdate buildDocumentUpdate(List<Assignment> assignments) {
        var fieldUpdates = new ArrayList<AstFieldUpdate>(assignments.size());
        for (var assignment : assignments) {
            var fieldPath = resolveAssignmentFieldPath(assignment);
            var fieldValue = acceptAndYield(assignment.getAssignedValue(), VALUE);
            fieldUpdates.add(new AstFieldUpdate(fieldPath, fieldValue));
        }
        return new AstDocumentUpdate(fieldUpdates);
    }

    private AstPipelineUpdate buildPipelineUpdate(List<Assignment> assignments) {
        var fieldUpdates = new ArrayList<AstComputedFieldUpdate>(assignments.size());
        for (var assignment : assignments) {
            var fieldValue = acceptAndYieldExpression(assignment.getAssignedValue());
            fieldUpdates.add(new AstComputedFieldUpdate(resolveAssignmentFieldPath(assignment), fieldValue));
        }
        return new AstPipelineUpdate(fieldUpdates);
    }

    private String resolveAssignmentFieldPath(Assignment assignment) {
        var fieldReferences = assignment.getAssignable().getColumnReferences();
        assertTrue(fieldReferences.size() == 1);
        return acceptAndYield(fieldReferences.get(0), FIELD_PATH);
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

    private List<AstFieldUpdate> createFieldUpdates(List<ColumnValueBinding> valueBindings) {
        var updates = new ArrayList<AstFieldUpdate>(valueBindings.size());
        for (var valueBinding : valueBindings) {
            updates.add(createFieldUpdate(valueBinding));
        }
        return updates;
    }

    private AstFieldUpdate createFieldUpdate(ColumnValueBinding valueBinding) {
        var fieldName = acceptAndYield(valueBinding.getColumnReference(), FIELD_PATH);
        var fieldValue = acceptAndYield(valueBinding.getValueExpression(), VALUE);
        return new AstFieldUpdate(fieldName, fieldValue);
    }

    /**
     * The merge coordinator decomposes a {@code @Struct} embeddable into one binding per leaf field, but
     * {@code UpdateCoordinatorStandard} binds a single value for the aggregate as a whole. Emitting the leaves would
     * therefore leave the aggregate's bound value without a matching parameter, so each aggregate's leaves collapse
     * into one field update, named after the aggregate column and backed by a parameter carrying the aggregate's own
     * mapping. The leaves' value expressions must not be visited: every visit emits a placeholder carrying a parameter
     * binder, and the JDBC layer pairs binders with placeholders by position.
     *
     * <p>This exists only to work around <a href="https://hibernate.atlassian.net/browse/HHH-20754">HHH-20754</a>.
     * {@code MergeCoordinatorStandard} overrides {@code forEachUpdatable} to call {@code forEachSelectable}, which
     * descends into the leaves, while {@code EmbeddableMappingTypeImpl.forEachUpdatable} short-circuits on
     * {@code shouldMutateAggregateMapping()} and emits the aggregate. That is why the standard update path needs none
     * of this. Once the merge path emits the aggregate, {@link #findAggregate} stops matching and this collapsing
     * becomes dead rather than wrong, so it can be deleted along with {@link #aggregateMappings} and
     * {@link #findAggregate}, restoring plain per-binding rendering.
     */
    private List<AstFieldUpdate> createUpsertFieldUpdates(
            List<ColumnValueBinding> valueBindings,
            List<SelectableMapping> aggregates,
            MutatingTableReference mutatingTable) {
        var updates = new ArrayList<AstFieldUpdate>(valueBindings.size());
        var collapsedAggregates = new HashSet<String>();
        for (var valueBinding : valueBindings) {
            var aggregate = findAggregate(aggregates, valueBinding);
            if (aggregate == null) {
                updates.add(createFieldUpdate(valueBinding));
            } else if (collapsedAggregates.add(aggregate.getSelectionExpression())) {
                var parameter =
                        new ColumnValueParameter(new ColumnReference(mutatingTable, aggregate), ParameterUsage.SET);
                updates.add(new AstFieldUpdate(
                        aggregate.getSelectionExpression(), new AstParameterMarker(parameter.getParameterBinder())));
            }
        }
        return updates;
    }

    private static List<SelectableMapping> aggregateMappings(OptionalTableUpdate optionalTableUpdate) {
        var aggregates = new ArrayList<SelectableMapping>();
        optionalTableUpdate.getMutationTarget().getTargetPart().forEachAttributeMapping(attributeMapping -> {
            if (attributeMapping instanceof EmbeddedAttributeMapping embeddedAttributeMapping) {
                var aggregate =
                        embeddedAttributeMapping.getEmbeddableTypeDescriptor().getAggregateMapping();
                if (aggregate != null) {
                    aggregates.add(aggregate);
                }
            }
        });
        return aggregates;
    }

    private static @Nullable SelectableMapping findAggregate(
            List<SelectableMapping> aggregates, ColumnValueBinding valueBinding) {
        var columnExpression = valueBinding.getColumnReference().getColumnExpression();
        for (var aggregate : aggregates) {
            if (columnExpression.startsWith(aggregate.getSelectionExpression() + ".")) {
                return aggregate;
            }
        }
        return null;
    }

    private AstUpdateCommand createAstUpdateCommand(
            final List<ColumnValueBinding> valueBindings, final String tableName, final AstFilter keyFilter) {
        return new AstUpdateCommand(
                tableName,
                List.of(createMultiUpdateStatement(
                        keyFilter, new AstDocumentUpdate(createFieldUpdates(valueBindings)))));
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
            // A composite id bound as a single VALUES-clause parameter arrives here as one tuple-valued
            // expression covering several consecutive target columns, rather than one expression per column;
            // unwrap it so each sub-expression lines up with its own field name.
            var astElements = new ArrayList<AstElement>(fieldNames.size());
            var fieldIndex = 0;
            for (var fieldValueExpression : values.getExpressions()) {
                var tuple = getSqlTuple(fieldValueExpression);
                var subExpressions = tuple != null ? tuple.getExpressions() : List.of(fieldValueExpression);
                for (var subExpression : subExpressions) {
                    if (!isValueExpression(subExpression)) {
                        throw new FeatureNotSupportedException();
                    }
                    var fieldValue = acceptAndYield(subExpression, VALUE);
                    astElements.add(new AstElement(fieldNames.get(fieldIndex++), fieldValue));
                }
            }
            assertTrue(fieldIndex == fieldNames.size());
            documents.add(new AstDocument(assembleWithIdSubdocument(astElements)));
        }

        astVisitorValueHolder.yield(
                MUTATION_RESULT,
                new MutationMqlTranslator.Result(new AstInsertCommand(collection, documents), affectedTableNames));
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
        var f = new Replacer(format.getFormat(), "'", "\"")
                // era
                .replace("GG", "AD")
                .replace("G", "AD")

                // year
                .replace("yyyy", "%Y")
                .replace("yyy", "%Y")
                .replace("yy", "%Y")
                .replace("y", "%Y")

                // month of year
                .replace("MMMM", "%B")
                .replace("MMM", "%b")
                .replace("MM", "%m")
                .replace("M", "%m")

                // week of year
                .replace("ww", "%U")
                .replace("w", "%U")
                // year for week
                .replace("YYYY", "%G")
                .replace("YYY", "%G")
                .replace("YY", "%G")
                .replace("Y", "%G")

                // week of month
                .replace("W", "W")

                // day of week
                .replace("EEEE", "%u")
                .replace("EEE", "%u")
                .replace("ee", "%u")
                .replace("e", "%u")

                // day of month
                .replace("dd", "%d")
                .replace("d", "%d")

                // day of year
                .replace("DDD", "%j")
                .replace("DD", "%j")
                .replace("D", "%j")

                // am pm (not supported in Mongo; since we're forcing 24 hours, drop)
                .replace("a", "")

                // hour
                .replace("hh", "%H")
                .replace("HH", "%H")
                .replace("h", "%H")
                .replace("H", "%H")

                // minute
                .replace("mm", "%M")
                .replace("m", "%M")

                // second
                .replace("ss", "%S")
                .replace("s", "%S")

                // fractional seconds
                .replace("SSSSSS", "%L")
                .replace("SSSSS", "%L")
                .replace("SSSS", "%L")
                .replace("SSS", "%L")
                .replace("SS", "%L")
                .replace("S", "%L")

                // timezones
                .replace("zzz", "%z")
                .replace("zz", "%z")
                .replace("z", "%z")
                .replace("ZZZ", "%z")
                .replace("ZZ", "%z")
                .replace("Z", "%z")
                .replace("xxx", "%z")
                .replace("xx", "%z")
                .replace("x", "%z");
        this.yield(EXPRESSION, new AstLiteralExpression(new AstLiteral(new BsonString(f.result()))));
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
        var left = acceptAndYieldExpression(binaryArithmeticExpression.getLeftHandOperand());
        var right = acceptAndYieldExpression(binaryArithmeticExpression.getRightHandOperand());
        astVisitorValueHolder.yield(
                EXPRESSION,
                switch (binaryArithmeticExpression.getOperator()) {
                    case ADD -> new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.ADD, left, right);
                    case SUBTRACT ->
                        new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.SUBTRACT, left, right);
                    case MULTIPLY ->
                        new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.MULTIPLY, left, right);
                    // DIVIDE (HQL `/`) and QUOT (CriteriaBuilder.quot) are the same operator, both `/`;
                    // DIVIDE_PORTABLE is always integer division. MongoDB's $divide always yields a
                    // double, but Hibernate infers an integer result type for integer operands and reads
                    // the column back as that integer, so integer division is truncated to match:
                    // $toLong for a 64-bit (BIGINT) result, $toInt for narrower integral types.
                    case DIVIDE, QUOT, DIVIDE_PORTABLE ->
                        divide(left, right, integerDivisionCast(binaryArithmeticExpression));
                    case MODULO -> new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.MOD, left, right);
                });
    }

    private static AstExpression divide(
            AstExpression left, AstExpression right, @Nullable AstConversionExpressionOperator integerCast) {
        var quotient = new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.DIVIDE, left, right);
        return integerCast == null ? quotient : new AstUnaryOperatorExpression(integerCast, quotient);
    }

    // The truncation operator for integer division, or null when the result type is not integral (a
    // plain $divide). $toLong for a BIGINT result, $toInt for narrower integral types.
    private static @Nullable AstConversionExpressionOperator integerDivisionCast(
            BinaryArithmeticExpression expression) {
        var expressionType = expression.getExpressionType();
        if (expressionType == null || expressionType.getJdbcTypeCount() != 1) {
            return null;
        }
        var jdbcType = expressionType.getSingleJdbcMapping().getJdbcType();
        if (!jdbcType.isInteger()) {
            return null;
        }
        return jdbcType.getDdlTypeCode() == SqlTypes.BIGINT
                ? AstConversionExpressionOperator.TO_LONG
                : AstConversionExpressionOperator.TO_INT;
    }

    @Override
    public void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression) {
        // A CASE only produces an aggregation expression ($switch); a position wanting a field path or a
        // value (e.g. a LIKE match expression, or a sort key) cannot take it, so refuse cleanly rather
        // than tripping the value holder's descriptor assertion.
        assertCaseExpressionPosition();
        // Searched CASE: each `when` predicate is a boolean aggregation expression (the branch's `case`),
        // its result the branch's `then`.
        var branches = new ArrayList<AstSwitchCase>(
                caseSearchedExpression.getWhenFragments().size());
        for (var whenFragment : caseSearchedExpression.getWhenFragments()) {
            var caseExpression = acceptAndYield(whenFragment.getPredicate(), EXPRESSION);
            var thenExpression = acceptAndYieldExpression(whenFragment.getResult());
            branches.add(new AstSwitchCase(caseExpression, thenExpression));
        }
        astVisitorValueHolder.yield(
                EXPRESSION,
                new AstSwitchExpression(branches, resolveCaseDefault(caseSearchedExpression.getOtherwise())));
    }

    @Override
    public void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression) {
        assertCaseExpressionPosition();
        var fixture = acceptAndYieldExpression(caseSimpleExpression.getFixture());
        var branches = new ArrayList<AstSwitchCase>(
                caseSimpleExpression.getWhenFragments().size());
        for (var whenFragment : caseSimpleExpression.getWhenFragments()) {
            var checkValue = acceptAndYieldExpression(whenFragment.getCheckValue());
            var caseExpression =
                    new AstBinaryOperatorExpression(AstComparisonExpressionOperator.EQ, fixture, checkValue);
            var thenExpression = acceptAndYieldExpression(whenFragment.getResult());
            branches.add(new AstSwitchCase(caseExpression, thenExpression));
        }
        astVisitorValueHolder.yield(
                EXPRESSION, new AstSwitchExpression(branches, resolveCaseDefault(caseSimpleExpression.getOtherwise())));
    }

    private void assertCaseExpressionPosition() {
        if (!astVisitorValueHolder.expects(EXPRESSION)) {
            throw new FeatureNotSupportedException(
                    "CASE expression is only supported in aggregation-expression position");
        }
    }

    // The CASE `default` (its ELSE). A missing ELSE returns SQL NULL, so it maps to a null literal — which
    // also keeps $switch from erroring at runtime when no branch matches and no default is present.
    private AstExpression resolveCaseDefault(@Nullable Expression otherwise) {
        return otherwise == null
                ? new AstValueExpression(new AstLiteral(BsonNull.VALUE))
                : acceptAndYieldExpression(otherwise);
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
        if (selfRenderingExpression instanceof SelfRenderingFunctionSqlAstExpression<?> sqlAstExpression) {
            if (astVisitorValueHolder.expects(EXPRESSION)
                    && !(sqlAstExpression.getFunctionRenderer() instanceof ExpressionFunction)) {
                // a function call as an operand within an aggregation expression is not yet supported
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-196 https://jira.mongodb.org/browse/HIBERNATE-196");
            }
            selfRenderingExpression.renderToSql(FeatureNotSupportedSqlAppender.INSTANCE, this, sessionFactory);
        } else {
            throw new FeatureNotSupportedException("Only function expressions are supported");
        }
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
        var operand = acceptAndYieldExpression(unaryOperation.getOperand());
        astVisitorValueHolder.yield(
                EXPRESSION,
                switch (unaryOperation.getOperator()) {
                    case UNARY_MINUS ->
                        new AstBinaryOperatorExpression(
                                AstArithmeticExpressionOperator.MULTIPLY,
                                new AstValueExpression(new AstLiteral(new BsonInt32(-1))),
                                operand);
                    case UNARY_PLUS -> operand;
                });
    }

    @Override
    public void visitModifiedSubQueryExpression(ModifiedSubQueryExpression modifiedSubQueryExpression) {
        throw new FeatureNotSupportedException();
    }

    @Override
    public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            astVisitorValueHolder.yield(EXPRESSION, toBetweenExpression(betweenPredicate));
        } else {
            astVisitorValueHolder.yield(FILTER, toBetweenFilter(betweenPredicate));
        }
    }

    // BETWEEN is `operand >= lower AND operand <= upper` (negated: `< lower OR > upper`), joined at the
    // filter level so each bound independently takes the compact `{field: {$op: value}}` form or `$expr`.
    private AstFilter toBetweenFilter(BetweenPredicate betweenPredicate) {
        var operand = betweenPredicate.getExpression();
        return new AstLogicalFilter(
                betweenPredicate.isNegated() ? AstLogicalFilterOperator.OR : AstLogicalFilterOperator.AND,
                List.of(
                        toBoundFilter(
                                operand,
                                betweenPredicate.isNegated()
                                        ? ComparisonOperator.LESS_THAN
                                        : ComparisonOperator.GREATER_THAN_OR_EQUAL,
                                betweenPredicate.getLowerBound()),
                        toBoundFilter(
                                operand,
                                betweenPredicate.isNegated()
                                        ? ComparisonOperator.GREATER_THAN
                                        : ComparisonOperator.LESS_THAN_OR_EQUAL,
                                betweenPredicate.getUpperBound())));
    }

    private AstFilter toBoundFilter(Expression operand, ComparisonOperator operator, Expression bound) {
        if (isFieldPathExpression(operand) && isValueExpression(bound)) {
            var fieldPath = acceptAndYield(operand, FIELD_PATH);
            var value = acceptAndYield(bound, VALUE);
            return new AstFieldOperationFilter(
                    fieldPath, new AstComparisonFilterOperation(createAstComparisonFilterOperator(operator), value));
        } else {
            var operandExpression = acceptAndYieldExpression(operand);
            var boundExpression = acceptAndYieldExpression(bound);
            return new AstExprFilter(new AstBinaryOperatorExpression(
                    toExprComparisonOperator(operator), operandExpression, boundExpression));
        }
    }

    private AstLogicalOperatorExpression toBetweenExpression(BetweenPredicate betweenPredicate) {
        var operand = acceptAndYieldExpression(betweenPredicate.getExpression());
        return new AstLogicalOperatorExpression(
                betweenPredicate.isNegated() ? AstLogicalOperator.OR : AstLogicalOperator.AND,
                List.of(
                        toBoundExpression(
                                operand,
                                betweenPredicate.isNegated()
                                        ? ComparisonOperator.LESS_THAN
                                        : ComparisonOperator.GREATER_THAN_OR_EQUAL,
                                betweenPredicate.getLowerBound()),
                        toBoundExpression(
                                operand,
                                betweenPredicate.isNegated()
                                        ? ComparisonOperator.GREATER_THAN
                                        : ComparisonOperator.LESS_THAN_OR_EQUAL,
                                betweenPredicate.getUpperBound())));
    }

    private AstBinaryOperatorExpression toBoundExpression(
            AstExpression operand, ComparisonOperator operator, Expression bound) {
        return new AstBinaryOperatorExpression(
                toExprComparisonOperator(operator), operand, acceptAndYieldExpression(bound));
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
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            if (getSqlTuple(inListPredicate.getTestExpression()) != null) {
                astVisitorValueHolder.yield(EXPRESSION, toTupleInListExpression(inListPredicate));
            } else {
                var value = acceptAndYieldExpression(inListPredicate.getTestExpression());
                var options = new ArrayList<AstExpression>(
                        inListPredicate.getListExpressions().size());
                for (var item : inListPredicate.getListExpressions()) {
                    options.add(acceptAndYieldExpression(item));
                }
                AstExpression in = new AstInExpression(value, options);
                astVisitorValueHolder.yield(
                        EXPRESSION,
                        inListPredicate.isNegated()
                                ? new AstLogicalOperatorExpression(AstLogicalOperator.NOT, List.of(in))
                                : in);
            }
        } else {
            var expression = inListPredicate.getTestExpression();
            if (getSqlTuple(expression) != null) {
                astVisitorValueHolder.yield(FILTER, createTupleInListFilter(inListPredicate));
            } else if (isFieldPathExpression(expression)) {
                var fieldPath = acceptAndYield(expression, FIELD_PATH);
                var operator = inListPredicate.isNegated() ? NIN : IN;
                var operation = new AstListComparisonFilterOperation(
                        operator,
                        inListPredicate.getListExpressions().stream()
                                .map(item -> acceptAndYield(item, VALUE))
                                .toList());
                astVisitorValueHolder.yield(FILTER, new AstFieldOperationFilter(fieldPath, operation));
            } else {
                throw new FeatureNotSupportedException(
                        "Only the following list predicates are supported: field in [not] (...)");
            }
        }
    }

    // Row-value IN in aggregation-expression position: OR of per-row (AND of per-component $eq); a single-row
    // list collapses to the bare AND; a negated list is wrapped in $not.
    private AstExpression toTupleInListExpression(InListPredicate inListPredicate) {
        if (inListPredicate.getListExpressions().isEmpty()) {
            return new AstLiteralExpression(inListPredicate.isNegated() ? TRUE : FALSE);
        }
        var keyExpressions = getSqlTuple(inListPredicate.getTestExpression()).getExpressions();
        var rowExpressions = new ArrayList<AstExpression>(
                inListPredicate.getListExpressions().size());
        for (var rowExpression : inListPredicate.getListExpressions()) {
            var rowValues = assertNotNull(getSqlTuple(rowExpression)).getExpressions();
            assertTrue(keyExpressions.size() == rowValues.size());
            var componentEqualities = new ArrayList<AstExpression>(keyExpressions.size());
            for (var i = 0; i < keyExpressions.size(); i++) {
                componentEqualities.add(new AstBinaryOperatorExpression(
                        toExprComparisonOperator(ComparisonOperator.EQUAL),
                        acceptAndYieldExpression(keyExpressions.get(i)),
                        acceptAndYieldExpression(rowValues.get(i))));
            }
            rowExpressions.add(new AstLogicalOperatorExpression(AstLogicalOperator.AND, componentEqualities));
        }
        var disjunction = rowExpressions.size() == 1
                ? rowExpressions.get(0)
                : new AstLogicalOperatorExpression(AstLogicalOperator.OR, rowExpressions);
        return inListPredicate.isNegated()
                ? new AstLogicalOperatorExpression(AstLogicalOperator.NOT, List.of(disjunction))
                : disjunction;
    }

    private AstFilter createTupleInListFilter(InListPredicate inListPredicate) {
        if (inListPredicate.getListExpressions().isEmpty()) {
            return new AstExprFilter(new AstValueExpression(inListPredicate.isNegated() ? TRUE : FALSE));
        }
        // Compact form when the test is all field paths and every row is all values; otherwise $expr.
        return isCompactTupleInList(inListPredicate)
                ? toCompactTupleInListFilter(inListPredicate)
                : new AstExprFilter(toTupleInListExpression(inListPredicate));
    }

    private static boolean isCompactTupleInList(InListPredicate inListPredicate) {
        if (!getSqlTuple(inListPredicate.getTestExpression()).getExpressions().stream()
                .allMatch(AbstractMqlTranslator::isFieldPathExpression)) {
            return false;
        }
        for (var rowExpression : inListPredicate.getListExpressions()) {
            var rowValues = assertNotNull(getSqlTuple(rowExpression)).getExpressions();
            if (!rowValues.stream().allMatch(AbstractMqlTranslator::isValueExpression)) {
                return false;
            }
        }
        return true;
    }

    private AstFilter toCompactTupleInListFilter(InListPredicate inListPredicate) {
        var keyExpressions = getSqlTuple(inListPredicate.getTestExpression()).getExpressions();
        var rowFilters =
                new ArrayList<AstFilter>(inListPredicate.getListExpressions().size());
        for (var rowExpression : inListPredicate.getListExpressions()) {
            var rowValues = assertNotNull(getSqlTuple(rowExpression)).getExpressions();
            assertTrue(keyExpressions.size() == rowValues.size());
            var componentFilters = new ArrayList<AstFilter>(keyExpressions.size());
            for (var i = 0; i < keyExpressions.size(); i++) {
                componentFilters.add(new AstFieldOperationFilter(
                        acceptAndYield(keyExpressions.get(i), FIELD_PATH),
                        new AstComparisonFilterOperation(EQ, acceptAndYield(rowValues.get(i), VALUE))));
            }
            rowFilters.add(new AstLogicalFilter(AstLogicalFilterOperator.AND, componentFilters));
        }
        var disjunction = rowFilters.size() == 1
                ? rowFilters.get(0)
                : new AstLogicalFilter(AstLogicalFilterOperator.OR, rowFilters);
        return inListPredicate.isNegated()
                ? new AstLogicalFilter(AstLogicalFilterOperator.NOR, List.of(disjunction))
                : disjunction;
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
            filter = new AstLogicalFilter(AstLogicalFilterOperator.NOR, List.of(filter));
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
        var regex = quoteMeta(pattern, escape);
        var options = likePredicate.isCaseSensitive() ? "s" : "is";

        if (astVisitorValueHolder.expects(EXPRESSION)) {
            var regexMatch = new AstRegexMatchExpression(
                    acceptAndYieldExpression(likePredicate.getMatchExpression()), regex, options);
            astVisitorValueHolder.yield(
                    EXPRESSION,
                    likePredicate.isNegated()
                            ? new AstLogicalOperatorExpression(AstLogicalOperator.NOT, List.of(regexMatch))
                            : regexMatch);
        } else {
            var fieldPath = acceptAndYield(likePredicate.getMatchExpression(), FIELD_PATH);
            var filter =
                    new AstFieldOperationFilter(fieldPath, new AstRegularExpressionFilterOperation(regex, options));
            astVisitorValueHolder.yield(
                    FILTER,
                    likePredicate.isNegated()
                            ? new AstLogicalFilter(AstLogicalFilterOperator.NOR, List.of(filter))
                            : filter);
        }
    }

    @Override
    public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {
        var expression = nullnessPredicate.getExpression();
        if (astVisitorValueHolder.expects(EXPRESSION)) {
            var operand = acceptAndYieldExpression(expression);
            var operator = nullnessPredicate.isNegated()
                    ? AstComparisonExpressionOperator.NE
                    : AstComparisonExpressionOperator.EQ;
            astVisitorValueHolder.yield(
                    EXPRESSION,
                    new AstBinaryOperatorExpression(
                            operator, operand, new AstValueExpression(new AstLiteral(BsonNull.VALUE))));
        } else {
            if (!isFieldPathExpression(expression)) {
                throw new FeatureNotSupportedException(
                        "Only the following nullness predicates are supported: field is [not] null");
            }
            var fieldPath = acceptAndYield(expression, FIELD_PATH);
            var operator = nullnessPredicate.isNegated() ? NE : EQ;
            var operation = new AstComparisonFilterOperation(operator, new AstLiteral(BsonNull.VALUE));
            astVisitorValueHolder.yield(FILTER, new AstFieldOperationFilter(fieldPath, operation));
        }
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
        if (astVisitorValueHolder.expects(UPSERT_MODEL_MUTATION_RESULT)) {
            var aggregates = aggregateMappings(optionalTableUpdate);
            var setBindings = new ArrayList<ColumnValueBinding>();
            var setOnInsertBindings = new ArrayList<ColumnValueBinding>();
            for (var valueBinding : optionalTableUpdate.getValueBindings()) {
                // MongoDialect returns a rejecting operation for non-insertable bindings and the
                // merge coordinator filters out bindings with neither flag, so insertable holds here.
                assertTrue(valueBinding.isAttributeInsertable());
                var aggregate = findAggregate(aggregates, valueBinding);
                // An aggregate's leaves may individually claim to be non-updatable, but the coordinator binds
                // the aggregate as a whole, so the aggregate column's own flag is what governs.
                var updatable = aggregate != null ? aggregate.isUpdateable() : valueBinding.isAttributeUpdatable();
                if (updatable) {
                    setBindings.add(valueBinding);
                } else {
                    setOnInsertBindings.add(valueBinding);
                }
            }
            var keyFilter = createKeyFilter(optionalTableUpdate);
            var mutatingTable = optionalTableUpdate.getMutatingTable();
            var update = new AstDocumentUpdate(
                    createUpsertFieldUpdates(setBindings, aggregates, mutatingTable),
                    createUpsertFieldUpdates(setOnInsertBindings, aggregates, mutatingTable));
            var command = new AstUpdateCommand(
                    optionalTableUpdate.getMutatingTable().getTableName(),
                    List.of(createUpsertStatement(keyFilter, update)));
            astVisitorValueHolder.yield(
                    UPSERT_MODEL_MUTATION_RESULT, ModelMutationMqlTranslator.Result.create(command));
        } else {
            var mutationResult = createMutationResult(
                    optionalTableUpdate.getValueBindings(),
                    optionalTableUpdate.getMutatingTable().getTableName(),
                    createKeyFilter(optionalTableUpdate));
            astVisitorValueHolder.yield(MODEL_MUTATION_RESULT, mutationResult);
        }
    }

    @Override
    public void visitCustomTableUpdate(TableUpdateCustomSql tableUpdateCustomSql) {
        throw new FeatureNotSupportedException();
    }

    // The binders are collected by the rendering itself, so they come out in the order in which
    // MongoPreparedStatement recovers the markers they were rendered as.
    static String renderMongoAstNode(AstNode rootAstNode, Consumer<JdbcParameterBinder> parameterBinderConsumer) {
        try (var stringWriter = new StringWriter();
                var jsonWriter = new JsonWriter(stringWriter, EXTENDED_JSON_WRITER_SETTINGS)) {
            rootAstNode.render(jsonWriter, parameterBinderConsumer);
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

    private static AstComparisonExpressionOperator toExprComparisonOperator(ComparisonOperator operator) {
        return switch (operator) {
            case EQUAL -> AstComparisonExpressionOperator.EQ;
            case NOT_EQUAL -> AstComparisonExpressionOperator.NE;
            case LESS_THAN -> AstComparisonExpressionOperator.LT;
            case LESS_THAN_OR_EQUAL -> AstComparisonExpressionOperator.LTE;
            case GREATER_THAN -> AstComparisonExpressionOperator.GT;
            case GREATER_THAN_OR_EQUAL -> AstComparisonExpressionOperator.GTE;
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

    // Whether the comparison can render as a compact field filter: a scalar field-vs-value, or a row-value
    // = / <> whose components are all field-vs-value pairs. Everything else goes to $expr.
    private static boolean isFieldValueComparison(ComparisonPredicate comparisonPredicate) {
        if (getSqlTuple(comparisonPredicate.getLeftHandExpression()) == null) {
            return isFieldValuePair(
                    comparisonPredicate.getLeftHandExpression(), comparisonPredicate.getRightHandExpression());
        } else {
            var operator = comparisonPredicate.getOperator();
            if (operator != ComparisonOperator.EQUAL && operator != ComparisonOperator.NOT_EQUAL) {
                return false;
            }
            var lhsTuple = getSqlTuple(comparisonPredicate.getLeftHandExpression());
            var rhsTuple = getSqlTuple(comparisonPredicate.getRightHandExpression());
            if (rhsTuple == null) {
                return false;
            }
            var lhsComponents = lhsTuple.getExpressions();
            var rhsComponents = rhsTuple.getExpressions();
            if (lhsComponents.size() != rhsComponents.size()) {
                return false;
            }
            for (var i = 0; i < lhsComponents.size(); i++) {
                if (!isFieldValuePair(lhsComponents.get(i), rhsComponents.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean isFieldValuePair(Expression left, Expression right) {
        return (isFieldPathExpression(left) && isValueExpression(right))
                || (isValueExpression(left) && isFieldPathExpression(right));
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

    private record JoinColumns(ColumnReference outer, ColumnReference joined) {}

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
     * Builds the {@code $lookup} stage for a join {@code ON} condition.
     *
     * <p>A lone one-outer-one-joined equijoin uses the compact, index-friendly {@code localField}/{@code foreignField}
     * form. Every other shape uses the pipeline form: the {@code ON} predicate is always rendered as an {@code $expr},
     * built by delegating to the shared EXPRESSION-mode predicate visitors — the same expression logic and operator
     * mapping the {@code WHERE} translator uses whenever it needs an aggregation expression. (Unlike a {@code WHERE}
     * clause, which also has a compact {@code {field: {$op: value}}} {@code $match} form for field-vs-value
     * comparisons, an {@code ON} condition inside the sub-pipeline always uses {@code $expr}.) The
     * {@link #joinLookupContext} makes {@link #visitColumnReference} bind outer columns into {@code let} variables and
     * treat joined columns as sub-pipeline field paths.
     */
    private AstStage buildJoinLookupStage(@Nullable Predicate predicate, String joinedCollection, String joinedAlias) {
        var joinAlias = JOIN_ALIAS_PREFIX + joinedAlias;

        // unwrapGrouped call lets a parenthesized equijoin ON (a = b) use the compact form instead of pipeline.
        var simpleEquijoin = trySimpleEquijoinColumns(unwrapGrouped(predicate), joinedAlias);
        if (simpleEquijoin.isPresent()) {
            var columns = simpleEquijoin.get();
            return new AstLookupStage(
                    joinedCollection,
                    resolveFieldPath(columns.outer()),
                    columns.joined().getColumnExpression(),
                    joinAlias);
        }

        if (joinLookupContext != null) {
            throw fail("Nested join ON conditions are not supported");
        }
        joinLookupContext = new JoinLookupContext(joinedAlias);
        var expr = acceptAndYieldExpression(assertNotNull(predicate));
        var lookupStage = new AstLookupStageWithPipeline(
                joinedCollection,
                joinLookupContext.letVariables(),
                List.of(new AstMatchStage(new AstExprFilter(expr))),
                joinAlias);
        joinLookupContext = null;
        return lookupStage;
    }

    /**
     * Names the joined alias whose collection is the {@code $lookup} sub-pipeline root and accumulates its {@code let}
     * bindings while a join {@code ON} condition is translated; see {@link #visitColumnReference}.
     */
    private record JoinLookupContext(String joinedAlias, List<AstLetVariable> letVariables) {
        JoinLookupContext(String joinedAlias) {
            this(joinedAlias, new ArrayList<>());
        }

        /**
         * Determines whether the given column belongs to the joined collection.
         *
         * @param columnReference the column being translated within the join {@code ON} condition
         * @return {@code true} if the column's qualifier matches the joined alias; {@code false} if it references an
         *     outer table
         */
        boolean isJoinedColumn(ColumnReference columnReference) {
            return joinedAlias.equals(columnReference.getQualifier());
        }

        void addLetVariable(AstLetVariable letVariable) {
            letVariables.add(letVariable);
        }
    }

    /** Strips any {@link GroupedPredicate} (parenthesis) wrappers, returning the innermost predicate. */
    private static @Nullable Predicate unwrapGrouped(@Nullable Predicate predicate) {
        while (predicate instanceof GroupedPredicate groupedPredicate) {
            predicate = groupedPredicate.getSubPredicate();
        }
        return predicate;
    }

    /**
     * Returns the outer/joined column split for a lone equijoin eligible for the compact
     * {@code localField}/{@code foreignField} {@code $lookup} form: both operands must be plain (non-formula) columns
     * with exactly one referencing the joined table. Any other shape returns empty and is handled by the pipeline form.
     */
    private static Optional<JoinColumns> trySimpleEquijoinColumns(@Nullable Predicate predicate, String joinedAlias) {
        if (!(predicate instanceof ComparisonPredicate cp) || cp.getOperator() != ComparisonOperator.EQUAL) {
            return Optional.empty();
        }
        var lhs = extractColumnReference(cp.getLeftHandExpression());
        var rhs = extractColumnReference(cp.getRightHandExpression());
        if (lhs == null || rhs == null || lhs.isColumnExpressionFormula() || rhs.isColumnExpressionFormula()) {
            return Optional.empty();
        }
        var lhsIsJoined = joinedAlias.equals(lhs.getQualifier());
        var rhsIsJoined = joinedAlias.equals(rhs.getQualifier());
        if (lhsIsJoined == rhsIsJoined) {
            return Optional.empty();
        }
        return Optional.of(lhsIsJoined ? new JoinColumns(rhs, lhs) : new JoinColumns(lhs, rhs));
    }

    /**
     * Generates a unique {@code let} variable name for a {@code $lookup} sub-pipeline. The leading {@code v<n>} counter
     * guarantees uniqueness within a query — needed once a single {@code $lookup} binds multiple outer columns (see
     * HIBERNATE-164) — while the sanitized {@code <qualifier>_<column>} suffix is human-readable context that makes it
     * clear which outer column each variable binds when reading query logs.
     */
    private String nextLetVariableName(ColumnReference outer) {
        var qualifier = outer.getQualifier();
        var suffix = ((qualifier != null ? qualifier + "_" : "") + outer.getColumnExpression())
                .replaceAll("[^a-zA-Z0-9_]", "_");
        return "v" + letVariableCounter++ + "_" + suffix;
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
}
