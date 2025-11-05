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
package com.mongodb.doclet;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.AuthorTree;
import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocRootTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTreeVisitor;
import com.sun.source.doctree.DocTypeTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.HiddenTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ProvidesTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.SummaryTree;
import com.sun.source.doctree.SystemPropertyTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.doctree.UsesTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.VersionTree;

public interface DocTreeVisitorAdapter extends com.sun.source.doctree.DocTreeVisitor<StringBuilder, StringBuilder> {
    @Override
    default StringBuilder visitAttribute(AttributeTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitAuthor(AuthorTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitComment(CommentTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitDeprecated(DeprecatedTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitDocComment(DocCommentTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitDocRoot(DocRootTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitDocType(DocTypeTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitEndElement(EndElementTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitEntity(EntityTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitErroneous(ErroneousTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitHidden(HiddenTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitIdentifier(IdentifierTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitIndex(IndexTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitInheritDoc(InheritDocTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitLink(LinkTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitLiteral(LiteralTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitParam(ParamTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitProvides(ProvidesTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitReference(ReferenceTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitReturn(ReturnTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitSee(SeeTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitSerial(SerialTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitSerialData(SerialDataTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitSerialField(SerialFieldTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitSince(SinceTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitStartElement(StartElementTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitSummary(SummaryTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitSystemProperty(SystemPropertyTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitText(TextTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitThrows(ThrowsTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitUnknownBlockTag(UnknownBlockTagTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitUnknownInlineTag(UnknownInlineTagTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitUses(UsesTree node, StringBuilder p) {
        return DocTreeVisitor.super.visitUses(node, p);
    }

    @Override
    default StringBuilder visitValue(ValueTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitVersion(VersionTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }

    @Override
    default StringBuilder visitOther(DocTree node, StringBuilder p) {
        throw new AssertionError("node=%s, p=%s".formatted(node, p));
    }
}
