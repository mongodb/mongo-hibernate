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

import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_MUTATION;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FIELD_VALUE;

import com.mongodb.hibernate.internal.NotYetImplementedException;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstPlaceholder;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bson.json.JsonWriter;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.persister.internal.SqlFragmentPredicate;
import org.hibernate.query.spi.QueryOptions;
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
import org.hibernate.sql.ast.tree.expression.ExtractUnit;
import org.hibernate.sql.ast.tree.expression.Format;
import org.hibernate.sql.ast.tree.expression.JdbcLiteral;
import org.hibernate.sql.ast.tree.expression.JdbcParameter;
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
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.ast.TableInsert;
import org.hibernate.sql.model.ast.TableMutation;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.internal.TableDeleteCustomSql;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateCustomSql;
import org.hibernate.sql.model.internal.TableUpdateStandard;

/** This class is not part of the public API and may be removed or changed at any time */
final class MqlTranslator<T extends JdbcOperation> implements SqlAstTranslator<T> {

    private final SessionFactoryImplementor sessionFactory;
    private final Statement statement;

    private final AstVisitorValueHolder astVisitorValueHolder = new AstVisitorValueHolder();

    private final List<JdbcParameterBinder> parameterBinders = new ArrayList<>();

    MqlTranslator(Statement statement, SessionFactoryImplementor sessionFactory) {
        this.statement = statement;
        this.sessionFactory = sessionFactory;
    }

    @Override
    public SessionFactoryImplementor getSessionFactory() {
        return sessionFactory;
    }

    @Override
    public void render(SqlAstNode sqlAstNode, SqlAstNodeRenderingMode sqlAstNodeRenderingMode) {
        throw new NotYetImplementedException();
    }

    @Override
    public boolean supportsFilterClause() {
        throw new NotYetImplementedException();
    }

    @Override
    public QueryPart getCurrentQueryPart() {
        throw new NotYetImplementedException();
    }

    @Override
    public Stack<Clause> getCurrentClauseStack() {
        throw new NotYetImplementedException();
    }

    @Override
    public Set<String> getAffectedTableNames() {
        throw new NotYetImplementedException();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public T translate(JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {
        if (statement instanceof TableMutation<?> tableMutation) {
            if (tableMutation instanceof TableInsert) {
                return translateTableMutation(tableMutation);
            } else {
                // TODO-HIBERNATE-17 https://jira.mongodb.org/browse/HIBERNATE-17
                // TODO-HIBERNATE-19 https://jira.mongodb.org/browse/HIBERNATE-19
                // after the above deletion and updating translation is done, we can delete this branch.
                return (T) new NoopJdbcMutationOperation();
            }
        }
        throw new NotYetImplementedException("TODO-HIBERNATE-22 https://jira.mongodb.org/browse/HIBERNATE-22");
    }

    @SuppressWarnings({"unchecked"})
    private T translateTableMutation(TableMutation<?> mutation) {
        var rootAstNode = astVisitorValueHolder.execute(COLLECTION_MUTATION, () -> mutation.accept(this));
        return (T) mutation.createMutationOperation(renderMongoAstNode(rootAstNode), parameterBinders);
    }

    private String renderMongoAstNode(AstNode rootAstNode) {
        var writer = new StringWriter();
        rootAstNode.render(new JsonWriter(writer));
        return writer.toString();
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Table Mutation: insertion

    @Override
    public void visitStandardTableInsert(TableInsertStandard tableInsertStandard) {
        var tableName = tableInsertStandard.getTableName();
        var astElements = new ArrayList<AstElement>(tableInsertStandard.getNumberOfValueBindings());
        for (var columnValueBinding : tableInsertStandard.getValueBindings()) {
            var astValue = astVisitorValueHolder.execute(
                    FIELD_VALUE, () -> columnValueBinding.getValueExpression().accept(this));
            var columnExpression = columnValueBinding.getColumnReference().getColumnExpression();
            astElements.add(new AstElement(columnExpression, astValue));
        }
        astVisitorValueHolder.yield(COLLECTION_MUTATION, new AstInsertCommand(tableName, astElements));
    }

    @Override
    public void visitColumnWriteFragment(ColumnWriteFragment columnWriteFragment) {
        if (columnWriteFragment.getParameters().size() != 1) {
            throw new NotYetImplementedException();
        }
        columnWriteFragment.getParameters().iterator().next().accept(this);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Override
    public void visitParameter(JdbcParameter jdbcParameter) {
        parameterBinders.add(jdbcParameter.getParameterBinder());
        astVisitorValueHolder.yield(FIELD_VALUE, AstPlaceholder.INSTANCE);
    }

    @Override
    public void visitSelectStatement(SelectStatement selectStatement) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitDeleteStatement(DeleteStatement deleteStatement) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitUpdateStatement(UpdateStatement updateStatement) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitInsertStatement(InsertSelectStatement insertSelectStatement) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitAssignment(Assignment assignment) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitQueryGroup(QueryGroup queryGroup) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitQuerySpec(QuerySpec querySpec) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitSortSpecification(SortSpecification sortSpecification) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitOffsetFetchClause(QueryPart queryPart) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitSelectClause(SelectClause selectClause) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitSqlSelection(SqlSelection sqlSelection) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitFromClause(FromClause fromClause) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitTableGroup(TableGroup tableGroup) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitTableGroupJoin(TableGroupJoin tableGroupJoin) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitNamedTableReference(NamedTableReference namedTableReference) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitValuesTableReference(ValuesTableReference valuesTableReference) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitQueryPartTableReference(QueryPartTableReference queryPartTableReference) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitFunctionTableReference(FunctionTableReference functionTableReference) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitTableReferenceJoin(TableReferenceJoin tableReferenceJoin) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitColumnReference(ColumnReference columnReference) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitNestedColumnReference(NestedColumnReference nestedColumnReference) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitAggregateColumnWriteExpression(AggregateColumnWriteExpression aggregateColumnWriteExpression) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitExtractUnit(ExtractUnit extractUnit) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitFormat(Format format) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitDistinct(Distinct distinct) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitOverflow(Overflow overflow) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitStar(Star star) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitTrimSpecification(TrimSpecification trimSpecification) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitCastTarget(CastTarget castTarget) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitBinaryArithmeticExpression(BinaryArithmeticExpression binaryArithmeticExpression) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitAny(Any any) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitEvery(Every every) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitSummarization(Summarization summarization) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitOver(Over<?> over) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitSelfRenderingExpression(SelfRenderingExpression selfRenderingExpression) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitSqlSelectionExpression(SqlSelectionExpression sqlSelectionExpression) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitEntityTypeLiteral(EntityTypeLiteral entityTypeLiteral) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitEmbeddableTypeLiteral(EmbeddableTypeLiteral embeddableTypeLiteral) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitTuple(SqlTuple sqlTuple) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitCollation(Collation collation) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitJdbcLiteral(JdbcLiteral<?> jdbcLiteral) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitQueryLiteral(QueryLiteral<?> queryLiteral) {
        throw new NotYetImplementedException();
    }

    @Override
    public <N extends Number> void visitUnparsedNumericLiteral(UnparsedNumericLiteral<N> unparsedNumericLiteral) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitUnaryOperationExpression(UnaryOperation unaryOperation) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitModifiedSubQueryExpression(ModifiedSubQueryExpression modifiedSubQueryExpression) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitFilterPredicate(FilterPredicate filterPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitFilterFragmentPredicate(FilterPredicate.FilterFragmentPredicate filterFragmentPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitSqlFragmentPredicate(SqlFragmentPredicate sqlFragmentPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitGroupedPredicate(GroupedPredicate groupedPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitInListPredicate(InListPredicate inListPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitInArrayPredicate(InArrayPredicate inArrayPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitExistsPredicate(ExistsPredicate existsPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitJunction(Junction junction) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitLikePredicate(LikePredicate likePredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitNegatedPredicate(NegatedPredicate negatedPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitThruthnessPredicate(ThruthnessPredicate thruthnessPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitRelationalPredicate(ComparisonPredicate comparisonPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitDurationUnit(DurationUnit durationUnit) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitDuration(Duration duration) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitConversion(Conversion conversion) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitCustomTableInsert(TableInsertCustomSql tableInsertCustomSql) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitStandardTableDelete(TableDeleteStandard tableDeleteStandard) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitCustomTableDelete(TableDeleteCustomSql tableDeleteCustomSql) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitStandardTableUpdate(TableUpdateStandard tableUpdateStandard) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitOptionalTableUpdate(OptionalTableUpdate optionalTableUpdate) {
        throw new NotYetImplementedException();
    }

    @Override
    public void visitCustomTableUpdate(TableUpdateCustomSql tableUpdateCustomSql) {
        throw new NotYetImplementedException();
    }
}
