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

package com.mongodb.hibernate.internal.translate.rewrite;

import static com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement.Kind.UPSERT;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.hibernate.internal.translate.mongoast.AstArithmeticExpressionOperator;
import com.mongodb.hibernate.internal.translate.mongoast.AstBinaryOperatorExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstComputedFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstDocument;
import com.mongodb.hibernate.internal.translate.mongoast.AstElement;
import com.mongodb.hibernate.internal.translate.mongoast.AstExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldPathExpression;
import com.mongodb.hibernate.internal.translate.mongoast.AstFieldUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteral;
import com.mongodb.hibernate.internal.translate.mongoast.AstLiteralExpression;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstDocumentUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstPipelineUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdate;
import com.mongodb.hibernate.internal.translate.mongoast.command.AstUpdateStatement;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstGroupStageSpecification;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLetVariable;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstLookupStageWithPipeline;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstMatchStage;
import com.mongodb.hibernate.internal.translate.mongoast.command.aggregate.AstStage;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstElemMatchFilterOperation;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstExprFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFieldOperationFilter;
import com.mongodb.hibernate.internal.translate.mongoast.filter.AstFilter;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonInt32;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The rule pipeline itself: pre-rules top-down with the first match short-circuiting descent, then post-rules
 * bottom-up, chaining. Rules here are deliberately trivial — what a real rule substitutes belongs to that rule's own
 * tests, and how a node reaches its children belongs to its {@code mapChildren} test.
 */
class AstRewriterTests {

    private static AstExpression field(String path) {
        return new AstFieldPathExpression(path);
    }

    private static AstExpression lit(int i) {
        return new AstLiteralExpression(new AstLiteral(new BsonInt32(i)));
    }

    private static AstExpression add(AstExpression l, AstExpression r) {
        return new AstBinaryOperatorExpression(AstArithmeticExpressionOperator.ADD, l, r);
    }

    /** Renames a field path, declining everything else, so descent is never short-circuited above it. */
    private record RenameField(String from, String to) implements RewriteRule {
        @Override
        public @Nullable AstExpression tryMatch(AstExpression node) {
            return node instanceof AstFieldPathExpression fp && fp.fieldPath().equals(from)
                    ? new AstFieldPathExpression(to)
                    : null;
        }
    }

    /** Replaces any binary operator expression outright, recording every node it was offered on the way. */
    private static final class ReplaceAnyAddition implements RewriteRule {
        private final String with;
        private final List<String> seen = new ArrayList<>();

        private ReplaceAnyAddition(String with) {
            this.with = with;
        }

        @Override
        public @Nullable AstExpression tryMatch(AstExpression node) {
            seen.add(node.toString());
            return node instanceof AstBinaryOperatorExpression ? new AstFieldPathExpression(with) : null;
        }
    }

    /** Appends a marker so the order post-rules run in, and whether they chain, is visible in the result. */
    private record AppendMarker(String marker) implements RewriteRule {
        @Override
        public @Nullable AstExpression tryMatch(AstExpression node) {
            return node instanceof AstFieldPathExpression fp
                    ? new AstFieldPathExpression(fp.fieldPath() + marker)
                    : null;
        }
    }

    /** Records every node handed to it and never matches, so a walk can be observed without being altered. */
    private static final class Recorder implements RewriteRule {
        private final List<String> seen = new ArrayList<>();

        @Override
        public @Nullable AstExpression tryMatch(AstExpression node) {
            seen.add(node.toString());
            return null;
        }
    }

    @Test
    void preRuleReplacesMatchedNode() {
        var rewriter = new AstRewriter(new RenameField("x", "z"), null);

        assertThat(rewriter.rewrite(add(field("x"), lit(1)))).isEqualTo(add(field("z"), lit(1)));
    }

    @Test
    void unmatchedTreeComesBackUnchanged() {
        var rewriter = new AstRewriter(new RenameField("nothing", "z"), null);
        var input = add(field("x"), lit(1));

        assertThat(rewriter.rewrite(input)).isEqualTo(input);
    }

    @Test
    void preRuleMatchSkipsDescentIntoTheMatchedNode() {
        // The rule matches the root, so it must never be offered the operands beneath it.
        var rule = new ReplaceAnyAddition("replaced");

        assertThat(new AstRewriter(rule, null).rewrite(add(field("x"), lit(1)))).isEqualTo(field("replaced"));
        assertThat(rule.seen).containsExactly(add(field("x"), lit(1)).toString());
    }

    @Test
    void thePreRuleIsOfferedEveryNodeTopDown() {
        var recorder = new Recorder();
        var rewriter = new AstRewriter(recorder, null);
        rewriter.rewrite(add(field("x"), lit(1)));

        // Root first, then its operands.
        assertThat(recorder.seen).hasSize(3);
        assertThat(recorder.seen.get(0)).contains("AstBinaryOperatorExpression");
    }

    @Test
    void postRuleRunsAfterChildrenAreRewritten() {
        // The pre-rule renames the operand, so the marker the post-rule appends lands on the renamed one.
        var rewriter = new AstRewriter(new RenameField("x", "z"), new AppendMarker("!"));

        assertThat(rewriter.rewrite(add(field("x"), lit(1)))).isEqualTo(add(field("z!"), lit(1)));
    }

    @Test
    void postRuleSeesWhatThePreRuleReplaced() {
        // A pre-rule match skips descent, not post-processing: the two are separate questions.
        var rewriter = new AstRewriter(new RenameField("x", "z"), new AppendMarker("!"));

        assertThat(rewriter.rewrite(field("x"))).isEqualTo(field("z!"));
    }

    @Test
    void postRuleAppliesWhereNoPreRuleMatched() {
        var rewriter = new AstRewriter(null, new AppendMarker("!"));

        assertThat(rewriter.rewrite(add(field("x"), lit(1)))).isEqualTo(add(field("x!"), lit(1)));
    }

    @Test
    void descentReachesFilterAndStageHierarchies() {
        var rewriter = new AstRewriter(new RenameField("x", "z"), null);
        AstStage input = new AstMatchStage(new AstExprFilter(add(field("x"), lit(1))));

        assertThat(rewriter.rewrite(input)).isEqualTo(new AstMatchStage(new AstExprFilter(add(field("z"), lit(1)))));
    }

    @Test
    void descentReachesAFilterNestedInAFilterOperation() {
        var rewriter = new AstRewriter(new RenameField("x", "z"), null);
        AstFilter input = new AstFieldOperationFilter(
                "tags", new AstElemMatchFilterOperation(new AstExprFilter(add(field("x"), lit(1)))));

        assertThat(rewriter.rewrite(input))
                .isEqualTo(new AstFieldOperationFilter(
                        "tags", new AstElemMatchFilterOperation(new AstExprFilter(add(field("z"), lit(1))))));
    }

    @Test
    void descentReachesGroupStageAndLookupPipelines() {
        var rewriter = new AstRewriter(new RenameField("x", "z"), null);
        AstStage group = new AstGroupStage(List.of(new AstGroupStageSpecification("k", field("x"))));
        AstStage lookup =
                new AstLookupStageWithPipeline("c", List.of(new AstLetVariable("v", field("x"))), List.of(group), "as");

        assertThat(rewriter.rewrite(lookup))
                .isEqualTo(new AstLookupStageWithPipeline(
                        "c",
                        List.of(new AstLetVariable("v", field("z"))),
                        List.of(new AstGroupStage(List.of(new AstGroupStageSpecification("k", field("z"))))),
                        "as"));
    }

    @Test
    void descentReachesTheUpdateHierarchy() {
        var rewriter = new AstRewriter(new RenameField("x", "z"), null);
        var statement = new AstUpdateStatement(
                new AstExprFilter(field("x")),
                new AstPipelineUpdate(List.of(new AstComputedFieldUpdate("n", field("x")))),
                UPSERT);

        assertThat(rewriter.rewrite(statement))
                .isEqualTo(new AstUpdateStatement(
                        new AstExprFilter(field("z")),
                        new AstPipelineUpdate(List.of(new AstComputedFieldUpdate("n", field("z")))),
                        UPSERT));
    }

    @Test
    void descentReachesDocumentsAndFieldUpdates() {
        // A rule that declines everything, so the pipeline runs over these hierarchies without altering them.
        var rewriter = new AstRewriter(new RenameField("absent", "z"), null);
        var element = new AstElement("n", new AstLiteral(new BsonInt32(1)));
        var document = new AstDocument(List.of(element));
        AstUpdate documentUpdate =
                new AstDocumentUpdate(List.of(new AstFieldUpdate("s", new AstLiteral(new BsonInt32(1)))), List.of());

        // No rule matches these hierarchies today; the point is that their entry points are exercised at all.
        assertThat(rewriter.rewrite(document)).isEqualTo(document);
        assertThat(rewriter.rewrite(element)).isEqualTo(element);
        assertThat(rewriter.rewrite(documentUpdate)).isEqualTo(documentUpdate);
    }
}
