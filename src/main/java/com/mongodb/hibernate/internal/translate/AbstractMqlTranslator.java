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

import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.COLLECTION_MUTATION;
import static com.mongodb.hibernate.internal.translate.AstVisitorValueDescriptor.FIELD_VALUE;

import com.mongodb.hibernate.internal.NotYetImplementedException;
import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstNode;
import com.mongodb.hibernate.internal.translate.mongoast.AstPlaceholder;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstInsertCommand;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriter;
import org.bson.json.JsonWriterSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.collections.Stack;
import org.hibernate.persister.internal.SqlFragmentPredicate;
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
import org.hibernate.sql.model.ast.ColumnWriteFragment;
import org.hibernate.sql.model.internal.OptionalTableUpdate;
import org.hibernate.sql.model.internal.TableDeleteCustomSql;
import org.hibernate.sql.model.internal.TableDeleteStandard;
import org.hibernate.sql.model.internal.TableInsertCustomSql;
import org.hibernate.sql.model.internal.TableInsertStandard;
import org.hibernate.sql.model.internal.TableUpdateCustomSql;
import org.hibernate.sql.model.internal.TableUpdateStandard;

/** This class is not part of the public API and may be removed or changed at any time */
abstract class AbstractMqlTranslator<T extends JdbcOperation> implements SqlAstTranslator<T> {
    private static final JsonWriterSettings JSON_WRITER_SETTINGS =
            JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    private final SessionFactoryImplementor sessionFactory;

    private final AstVisitorValueHolder astVisitorValueHolder = new AstVisitorValueHolder();

    private final List<JdbcParameterBinder> parameterBinders = new ArrayList<>();

    AbstractMqlTranslator(SessionFactoryImplementor sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public SessionFactoryImplementor getSessionFactory() {
        return sessionFactory;
    }

    @Override
    public void render(SqlAstNode sqlAstNode, SqlAstNodeRenderingMode sqlAstNodeRenderingMode) {
        fail();
    }

    @Override
    public boolean supportsFilterClause() {
        throw fail();
    }

    @Override
    public QueryPart getCurrentQueryPart() {
        throw fail();
    }

    @Override
    public Stack<Clause> getCurrentClauseStack() {
        throw fail();
    }

    @Override
    public Set<String> getAffectedTableNames() {
        throw new NotYetImplementedException("TODO-HIBERNATE-22 https://jira.mongodb.org/browse/HIBERNATE-22");
    }

    List<JdbcParameterBinder> getParameterBinders() {
        return parameterBinders;
    }

    static String renderMongoAstNode(AstNode rootAstNode) {
        var writer = new StringWriter();
        rootAstNode.render(new JsonWriter(writer, JSON_WRITER_SETTINGS));
        return writer.toString();
    }

    <R extends AstNode> R acceptAndYield(Statement statement, AstVisitorValueDescriptor<R> resultDescriptor) {
        return astVisitorValueHolder.execute(resultDescriptor, () -> statement.accept(this));
    }

    <R extends AstNode> R acceptAndYield(SqlAstNode node, AstVisitorValueDescriptor<R> resultDescriptor) {
        return astVisitorValueHolder.execute(resultDescriptor, () -> node.accept(this));
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Table Mutation: insertion

    @Override
    public void visitStandardTableInsert(TableInsertStandard tableInsertStandard) {
        var tableName = tableInsertStandard.getTableName();
        var astElements = new ArrayList<AstElement>(tableInsertStandard.getNumberOfValueBindings());
        for (var columnValueBinding : tableInsertStandard.getValueBindings()) {
            var astValue = acceptAndYield(columnValueBinding.getValueExpression(), FIELD_VALUE);
            var columnExpression = columnValueBinding.getColumnReference().getColumnExpression();
            astElements.add(new AstElement(columnExpression, astValue));
        }
        astVisitorValueHolder.yield(COLLECTION_MUTATION, new AstInsertCommand(tableName, new AstDocument(astElements)));
    }

    @Override
    public void visitColumnWriteFragment(ColumnWriteFragment columnWriteFragment) {
        if (columnWriteFragment.getParameters().size() != 1) {
            fail();
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
        throw new NotYetImplementedException("TODO-HIBERNATE-22 https://jira.mongodb.org/browse/HIBERNATE-22");
    }

    @Override
    public void visitDeleteStatement(DeleteStatement deleteStatement) {
        throw new NotYetImplementedException("TODO-HIBERNATE-46 https://jira.mongodb.org/browse/HIBERNATE-46");
    }

    @Override
    public void visitUpdateStatement(UpdateStatement updateStatement) {
        throw new NotYetImplementedException("TODO-HIBERNATE-46 https://jira.mongodb.org/browse/HIBERNATE-46");
    }

    @Override
    public void visitInsertStatement(InsertSelectStatement insertSelectStatement) {
        throw new NotYetImplementedException("TODO-HIBERNATE-46 https://jira.mongodb.org/browse/HIBERNATE-46");
    }

    @Override
    public void visitAssignment(Assignment assignment) {
        fail();
    }

    @Override
    public void visitQueryGroup(QueryGroup queryGroup) {
        fail();
    }

    @Override
    public void visitQuerySpec(QuerySpec querySpec) {
        fail();
    }

    @Override
    public void visitSortSpecification(SortSpecification sortSpecification) {
        fail();
    }

    @Override
    public void visitOffsetFetchClause(QueryPart queryPart) {
        fail();
    }

    @Override
    public void visitSelectClause(SelectClause selectClause) {
        fail();
    }

    @Override
    public void visitSqlSelection(SqlSelection sqlSelection) {
        fail();
    }

    @Override
    public void visitFromClause(FromClause fromClause) {
        fail();
    }

    @Override
    public void visitTableGroup(TableGroup tableGroup) {
        fail();
    }

    @Override
    public void visitTableGroupJoin(TableGroupJoin tableGroupJoin) {
        fail();
    }

    @Override
    public void visitNamedTableReference(NamedTableReference namedTableReference) {
        fail();
    }

    @Override
    public void visitValuesTableReference(ValuesTableReference valuesTableReference) {
        fail();
    }

    @Override
    public void visitQueryPartTableReference(QueryPartTableReference queryPartTableReference) {
        fail();
    }

    @Override
    public void visitFunctionTableReference(FunctionTableReference functionTableReference) {
        fail();
    }

    @Override
    public void visitTableReferenceJoin(TableReferenceJoin tableReferenceJoin) {
        fail();
    }

    @Override
    public void visitColumnReference(ColumnReference columnReference) {
        fail();
    }

    @Override
    public void visitNestedColumnReference(NestedColumnReference nestedColumnReference) {
        fail();
    }

    @Override
    public void visitAggregateColumnWriteExpression(AggregateColumnWriteExpression aggregateColumnWriteExpression) {
        fail();
    }

    @Override
    public void visitExtractUnit(ExtractUnit extractUnit) {
        fail();
    }

    @Override
    public void visitFormat(Format format) {
        fail();
    }

    @Override
    public void visitDistinct(Distinct distinct) {
        fail();
    }

    @Override
    public void visitOverflow(Overflow overflow) {
        fail();
    }

    @Override
    public void visitStar(Star star) {
        fail();
    }

    @Override
    public void visitTrimSpecification(TrimSpecification trimSpecification) {
        fail();
    }

    @Override
    public void visitCastTarget(CastTarget castTarget) {
        fail();
    }

    @Override
    public void visitBinaryArithmeticExpression(BinaryArithmeticExpression binaryArithmeticExpression) {
        fail();
    }

    @Override
    public void visitCaseSearchedExpression(CaseSearchedExpression caseSearchedExpression) {
        fail();
    }

    @Override
    public void visitCaseSimpleExpression(CaseSimpleExpression caseSimpleExpression) {
        fail();
    }

    @Override
    public void visitAny(Any any) {
        fail();
    }

    @Override
    public void visitEvery(Every every) {
        fail();
    }

    @Override
    public void visitSummarization(Summarization summarization) {
        fail();
    }

    @Override
    public void visitOver(Over<?> over) {
        fail();
    }

    @Override
    public void visitSelfRenderingExpression(SelfRenderingExpression selfRenderingExpression) {
        fail();
    }

    @Override
    public void visitSqlSelectionExpression(SqlSelectionExpression sqlSelectionExpression) {
        fail();
    }

    @Override
    public void visitEntityTypeLiteral(EntityTypeLiteral entityTypeLiteral) {
        fail();
    }

    @Override
    public void visitEmbeddableTypeLiteral(EmbeddableTypeLiteral embeddableTypeLiteral) {
        fail();
    }

    @Override
    public void visitTuple(SqlTuple sqlTuple) {
        fail();
    }

    @Override
    public void visitCollation(Collation collation) {
        fail();
    }

    @Override
    public void visitJdbcLiteral(JdbcLiteral<?> jdbcLiteral) {
        fail();
    }

    @Override
    public void visitQueryLiteral(QueryLiteral<?> queryLiteral) {
        fail();
    }

    @Override
    public <N extends Number> void visitUnparsedNumericLiteral(UnparsedNumericLiteral<N> unparsedNumericLiteral) {
        fail();
    }

    @Override
    public void visitUnaryOperationExpression(UnaryOperation unaryOperation) {
        fail();
    }

    @Override
    public void visitModifiedSubQueryExpression(ModifiedSubQueryExpression modifiedSubQueryExpression) {
        fail();
    }

    @Override
    public void visitBooleanExpressionPredicate(BooleanExpressionPredicate booleanExpressionPredicate) {
        fail();
    }

    @Override
    public void visitBetweenPredicate(BetweenPredicate betweenPredicate) {
        fail();
    }

    @Override
    public void visitFilterPredicate(FilterPredicate filterPredicate) {
        fail();
    }

    @Override
    public void visitFilterFragmentPredicate(FilterPredicate.FilterFragmentPredicate filterFragmentPredicate) {
        fail();
    }

    @Override
    public void visitSqlFragmentPredicate(SqlFragmentPredicate sqlFragmentPredicate) {
        fail();
    }

    @Override
    public void visitGroupedPredicate(GroupedPredicate groupedPredicate) {
        fail();
    }

    @Override
    public void visitInListPredicate(InListPredicate inListPredicate) {
        fail();
    }

    @Override
    public void visitInSubQueryPredicate(InSubQueryPredicate inSubQueryPredicate) {
        fail();
    }

    @Override
    public void visitInArrayPredicate(InArrayPredicate inArrayPredicate) {
        fail();
    }

    @Override
    public void visitExistsPredicate(ExistsPredicate existsPredicate) {
        fail();
    }

    @Override
    public void visitJunction(Junction junction) {
        fail();
    }

    @Override
    public void visitLikePredicate(LikePredicate likePredicate) {
        fail();
    }

    @Override
    public void visitNegatedPredicate(NegatedPredicate negatedPredicate) {
        fail();
    }

    @Override
    public void visitNullnessPredicate(NullnessPredicate nullnessPredicate) {
        fail();
    }

    @Override
    public void visitThruthnessPredicate(ThruthnessPredicate thruthnessPredicate) {
        fail();
    }

    @Override
    public void visitRelationalPredicate(ComparisonPredicate comparisonPredicate) {
        fail();
    }

    @Override
    public void visitSelfRenderingPredicate(SelfRenderingPredicate selfRenderingPredicate) {
        fail();
    }

    @Override
    public void visitDurationUnit(DurationUnit durationUnit) {
        fail();
    }

    @Override
    public void visitDuration(Duration duration) {
        fail();
    }

    @Override
    public void visitConversion(Conversion conversion) {
        fail();
    }

    @Override
    public void visitCustomTableInsert(TableInsertCustomSql tableInsertCustomSql) {
        fail();
    }

    @Override
    public void visitStandardTableDelete(TableDeleteStandard tableDeleteStandard) {
        throw new NotYetImplementedException("TODO-HIBERNATE-17 https://jira.mongodb.org/browse/HIBERNATE-17");
    }

    @Override
    public void visitCustomTableDelete(TableDeleteCustomSql tableDeleteCustomSql) {
        fail();
    }

    @Override
    public void visitStandardTableUpdate(TableUpdateStandard tableUpdateStandard) {
        throw new NotYetImplementedException("TODO-HIBERNATE-19 https://jira.mongodb.org/browse/HIBERNATE-19");
    }

    @Override
    public void visitOptionalTableUpdate(OptionalTableUpdate optionalTableUpdate) {
        fail();
    }

    @Override
    public void visitCustomTableUpdate(TableUpdateCustomSql tableUpdateCustomSql) {
        fail();
    }
}
