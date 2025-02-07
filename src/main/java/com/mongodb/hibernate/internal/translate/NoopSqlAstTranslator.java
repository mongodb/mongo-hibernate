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

import java.util.Set;
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
import org.hibernate.sql.exec.spi.JdbcParameterBindings;
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.internal.TableDeleteCustomSql;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateCustomSql;
import org.hibernate.sql.model.internal.TableUpdateStandard;
import org.jspecify.annotations.NullUnmarked;

/**
 * An {@link SqlAstTranslator} no-op adapter class which is used temporarily as a feature placeholder prior to its
 * implementation is done, when throwing {@link com.mongodb.hibernate.internal.NotYetImplementedException} would not be
 * an option. Ultimately this adapter class should be phased out and deleted after the following tickets are done:
 *
 * <ul>
 *     <li><a href="https://jira.mongodb.org/browse/HIBERNATE-22">implement load MQL translation</a>
 *     <li><a href="https://jira.mongodb.org/browse/HIBERNATE-14">statement DML implementation</a>
 * </ul>
 *
 * <p>This class is not part of the public API and may be removed or changed at any time
 *
 * @param <T> {@link JdbcOperation} generics type
 */
@NullUnmarked
final class NoopSqlAstTranslator<T extends JdbcOperation> implements SqlAstTranslator<T> {

    NoopSqlAstTranslator() {}

    @Override
    public SessionFactoryImplementor getSessionFactory() {
        return null;
    }

    @Override
    public void render(SqlAstNode sqlAstNode, SqlAstNodeRenderingMode renderingMode) {}

    @Override
    public boolean supportsFilterClause() {
        return false;
    }

    @Override
    public QueryPart getCurrentQueryPart() {
        return null;
    }

    @Override
    public Stack<Clause> getCurrentClauseStack() {
        return null;
    }

    @Override
    public Set<String> getAffectedTableNames() {
        return Set.of();
    }

    @Override
    public T translate(JdbcParameterBindings jdbcParameterBindings, QueryOptions queryOptions) {
        return null;
    }

    @Override
    public void visitSelectStatement(SelectStatement statement) {}

    @Override
    public void visitDeleteStatement(DeleteStatement statement) {}

    @Override
    public void visitUpdateStatement(UpdateStatement statement) {}

    @Override
    public void visitInsertStatement(InsertSelectStatement statement) {}

    @Override
    public void visitAssignment(Assignment assignment) {}

    @Override
    public void visitQueryGroup(QueryGroup queryGroup) {}

    @Override
    public void visitQuerySpec(QuerySpec querySpec) {}

    @Override
    public void visitSortSpecification(SortSpecification sortSpecification) {}

    @Override
    public void visitOffsetFetchClause(QueryPart querySpec) {}

    @Override
    public void visitSelectClause(SelectClause selectClause) {}

    @Override
    public void visitSqlSelection(SqlSelection sqlSelection) {}

    @Override
    public void visitFromClause(FromClause fromClause) {}

    @Override
    public void visitTableGroup(TableGroup tableGroup) {}

    @Override
    public void visitTableGroupJoin(TableGroupJoin tableGroupJoin) {}

    @Override
    public void visitNamedTableReference(NamedTableReference tableReference) {}

    @Override
    public void visitValuesTableReference(ValuesTableReference tableReference) {}

    @Override
    public void visitQueryPartTableReference(QueryPartTableReference tableReference) {}

    @Override
    public void visitFunctionTableReference(FunctionTableReference tableReference) {}

    @Override
    public void visitTableReferenceJoin(TableReferenceJoin tableReferenceJoin) {}

    @Override
    public void visitColumnReference(ColumnReference columnReference) {}

    @Override
    public void visitNestedColumnReference(NestedColumnReference nestedColumnReference) {}

    @Override
    public void visitAggregateColumnWriteExpression(AggregateColumnWriteExpression aggregateColumnWriteExpression) {}

    @Override
    public void visitExtractUnit(ExtractUnit extractUnit) {}

    @Override
    public void visitFormat(Format format) {}

    @Override
    public void visitDistinct(Distinct distinct) {}

    @Override
    public void visitOverflow(Overflow overflow) {}

    @Override
    public void visitStar(Star star) {}

    @Override
    public void visitTrimSpecification(TrimSpecification trimSpecification) {}

    @Override
    public void visitCastTarget(CastTarget castTarget) {}

    @Override
    public void visitBinaryArithmeticExpression(BinaryArithmeticExpression arithmeticExpression) {}

    @Override
    public void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression) {}

    @Override
    public void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression) {}

    @Override
    public void visitAny(Any any) {}

    @Override
    public void visitEvery(Every every) {}

    @Override
    public void visitSummarization(Summarization every) {}

    @Override
    public void visitOver(Over<?> over) {}

    @Override
    public void visitSelfRenderingExpression(SelfRenderingExpression expression) {}

    @Override
    public void visitSqlSelectionExpression(SqlSelectionExpression expression) {}

    @Override
    public void visitEntityTypeLiteral(EntityTypeLiteral expression) {}

    @Override
    public void visitEmbeddableTypeLiteral(EmbeddableTypeLiteral expression) {}

    @Override
    public void visitTuple(SqlTuple tuple) {}

    @Override
    public void visitCollation(Collation collation) {}

    @Override
    public void visitParameter(JdbcParameter jdbcParameter) {}

    @Override
    public void visitJdbcLiteral(JdbcLiteral<?> jdbcLiteral) {}

    @Override
    public void visitQueryLiteral(QueryLiteral<?> queryLiteral) {}

    @Override
    public <N extends Number> void visitUnparsedNumericLiteral(UnparsedNumericLiteral<N> literal) {}

    @Override
    public void visitUnaryOperationExpression(UnaryOperation unaryOperationExpression) {}

    @Override
    public void visitModifiedSubQueryExpression(ModifiedSubQueryExpression expression) {}

    @Override
    public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {}

    @Override
    public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {}

    @Override
    public void visitFilterPredicate(FilterPredicate filterPredicate) {}

    @Override
    public void visitFilterFragmentPredicate(FilterPredicate.FilterFragmentPredicate fragmentPredicate) {}

    @Override
    public void visitSqlFragmentPredicate(SqlFragmentPredicate predicate) {}

    @Override
    public void visitGroupedPredicate(GroupedPredicate groupedPredicate) {}

    @Override
    public void visitInListPredicate(InListPredicate inListPredicate) {}

    @Override
    public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {}

    @Override
    public void visitInArrayPredicate(InArrayPredicate inArrayPredicate) {}

    @Override
    public void visitExistsPredicate(ExistsPredicate existsPredicate) {}

    @Override
    public void visitJunction(Junction junction) {}

    @Override
    public void visitLikePredicate(LikePredicate likePredicate) {}

    @Override
    public void visitNegatedPredicate(NegatedPredicate negatedPredicate) {}

    @Override
    public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {}

    @Override
    public void visitThruthnessPredicate(ThruthnessPredicate predicate) {}

    @Override
    public void visitRelationalPredicate(ComparisonPredicate comparisonPredicate) {}

    @Override
    public void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate) {}

    @Override
    public void visitDurationUnit(DurationUnit durationUnit) {}

    @Override
    public void visitDuration(Duration duration) {}

    @Override
    public void visitConversion(Conversion conversion) {}

    @Override
    public void visitStandardTableInsert(TableInsertStandard tableInsert) {}

    @Override
    public void visitCustomTableInsert(TableInsertCustomSql tableInsert) {}

    @Override
    public void visitStandardTableDelete(TableDeleteStandard tableDelete) {}

    @Override
    public void visitCustomTableDelete(TableDeleteCustomSql tableDelete) {}

    @Override
    public void visitStandardTableUpdate(TableUpdateStandard tableUpdate) {}

    @Override
    public void visitOptionalTableUpdate(OptionalTableUpdate tableUpdate) {}

    @Override
    public void visitCustomTableUpdate(TableUpdateCustomSql tableUpdate) {}

    @Override
    public void visitColumnWriteFragment(ColumnWriteFragment columnWriteFragment) {}
}
