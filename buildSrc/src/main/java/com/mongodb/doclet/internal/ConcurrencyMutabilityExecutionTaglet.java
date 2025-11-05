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
package com.mongodb.doclet.internal;

import com.mongodb.doclet.DocTreeVisitorAdapter;
import com.sun.source.doctree.DocTree;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;

import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Taglet;

/**
 * This {@link Taglet} is not ready to render most of the {@link DocTree nodes},
 * it fails to properly render even {@linkplain DocTreeVisitor#visitLink(LinkTree, StringBuilder) links}.
 */
public final class ConcurrencyMutabilityExecutionTaglet implements Taglet {
    public ConcurrencyMutabilityExecutionTaglet() {}

    @Override
    public Set<Location> getAllowedLocations() {
        return EnumSet.of(Location.TYPE, Location.METHOD);
    }

    @Override
    public boolean isInlineTag() {
        return false;
    }

    @Override
    public String getName() {
        return "mongoConcurrencyMutabilityExecution";
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element element) {
        if (tags.isEmpty()) {
            throw new AssertionError();
        }
        if (tags.size() > 1) {
            throw new RuntimeException("At most one instance of the tag is allowed per comment");
        }
        var tag = tags.get(0);
        return tag.accept(new DocTreeVisitor(), new StringBuilder("<dt>Concurrency, Mutability, Execution:</dt>")).toString();
    }

    private static final class DocTreeVisitor implements DocTreeVisitorAdapter {
        DocTreeVisitor() {}

        @Override
        public StringBuilder visitUnknownBlockTag(UnknownBlockTagTree node, StringBuilder p) {
            var content = node.getContent();
            if (content.isEmpty()) {
                throw new RuntimeException("The tag must have content");
            }
            p.append("<dd>");
            for (var contentElement : content) {
                contentElement.accept(this, p);
            }
            p.append("</dd>");
            return p;
        }

        @Override
        public StringBuilder visitText(TextTree node, StringBuilder p) {
            p.append(node.getBody());
            return p;
        }

        /**
         * We probably could extract all the information needed to properly render the {@code node} as a link
         * from the objects passed to {@link ConcurrencyMutabilityExecutionTaglet#init(DocletEnvironment, Doclet)},
         * but that seems unnecessary for now.
         */
        @Override
        public StringBuilder visitLink(LinkTree node, StringBuilder p) {
            var label = node.getLabel();
            if (label.size() > 1) {
                throw new RuntimeException("At most one label is allowed per link");
            }
            var reference = node.getReference();
            var plainReference = reference.getKind() == DocTree.Kind.LINK_PLAIN;
            var referenceSignature = reference.getSignature();
            p.append("<a href=\"\">");
            if (!plainReference) {
                p.append("<code>");
            }
            if (!label.isEmpty()) {
                label.get(0).accept(this, p);
            } else {
                p.append(referenceSignature);
            }
            if (!plainReference) {
                p.append("</code>");
            }
            p.append("</a>");
            return p;
        }
    }
}
