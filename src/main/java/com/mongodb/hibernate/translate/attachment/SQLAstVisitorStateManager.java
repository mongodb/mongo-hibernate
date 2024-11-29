/*
 * Copyright 2024-present MongoDB, Inc.
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

package com.mongodb.hibernate.translate.attachment;

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.VisibleForTesting;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

/**
 * Manages internal states while translating SQL AST via {@link org.hibernate.sql.ast.SqlAstWalker}.
 *
 * <p>{@code SqlAstWalker} is based on {@code visitor} pattern without returning value from the various visitor methods.
 * The original goal of {@code SqlAstWalker} is for concatenating SQL string internally; but when translating SQL AST
 * into MQL, it is highly desirable to return value (e.g. {@link com.mongodb.hibernate.translate.mongoast.AstNode}). To
 * avoid such dilemma, some attachment/detachment mechanism is implemented as follows:
 *
 * <ul>
 *   <li>when some visitor method needs to return some value, it attaches the value to the top {@link Attachment} node
 *       of a global stack; the node should have been pushed with empty value by some detacher below.
 *   <li>when some coordinated detacher needs to access the value attached above, it first pushes an {@link Attachment}
 *       node into global stack with empty value; then invoke some {@link Runnable} instance which is the wrapper of the
 *       above attacher and then grabs the attached value by popping from the stack.
 *   <li>both attacher and detacher should provide identical {@link AttachmentKey} to ensure they are on the same pages.
 * </ul>
 *
 * @see org.hibernate.sql.ast.SqlAstWalker
 * @see AttachmentKey
 */
@SuppressWarnings("unchecked")
public final class SQLAstVisitorStateManager {

    private static final class Attachment<T> {
        private final AttachmentKey<T> attachmentKey;
        private @Nullable T attachedValue;

        Attachment(AttachmentKey<T> attachmentKey) {
            this.attachmentKey = attachmentKey;
        }

        @Override
        public String toString() {
            return "Attachment{" + "attachmentKey=" + attachmentKey + ", attachedValue=" + attachedValue + '}';
        }
    }

    @VisibleForTesting(otherwise = PRIVATE)
    final Deque<Attachment<?>> state = new ArrayDeque<>();

    /**
     * Detaches the attached value by invoking some visitor method which returns {@code void} but internally the method
     * is expected to attach some value by invoking the other {@link #attach(AttachmentKey, Object)} method.
     *
     * @param attachmentKey type safe attachment type enum; detacher and attacher should share the identical
     *     {@code AttachmentKey}; never null
     * @param attacher some {@link Runnable} wrapper of visitor method which is supposed to attach some value; never
     *     {@code null}
     * @return the value attached by visitor method by storing at some external public storage; after this method
     *     returns, the value will be deleted from the external storage; never {@code null}
     * @param <T> the expected Java generic type
     */
    public <T> T detach(AttachmentKey<T> attachmentKey, Runnable attacher) {
        state.push(new Attachment<>(attachmentKey));
        try {
            attacher.run();
            if (state.isEmpty()) {
                throw new IllegalStateException(format("Attachment not found when detaching (key: %s)", attachmentKey));
            }
            if (state.peek().attachedValue == null) {
                throw new IllegalArgumentException(
                        format("No value attached for attachment key: %s", state.peek().attachmentKey));
            }
            return (T) assertNotNull(assertNotNull(state.peek()).attachedValue);
        } finally {
            state.pop();
        }
    }

    /**
     * Attaches some value matching some {@link AttachmentKey} to external storage location, which serves as an
     * intermediary so detacher could grab it by invoking the {@link #detach(AttachmentKey, Runnable)} method.
     *
     * @param attachmentKey type safe attachment type enum; detacher and attacher should share the identical
     *     {@code AttachmentKey}; never null
     * @param attachedValue value to be attached; never {@code null}
     * @param <T> the expected Java generic type
     */
    public <T> void attach(AttachmentKey<T> attachmentKey, T attachedValue) {
        var attachment = state.peek();
        if (attachment == null) {
            throw new IllegalStateException("Attachment not found!");
        }
        if (attachmentKey != attachment.attachmentKey) {
            throw new IllegalArgumentException(format(
                    "Provided attachment key [%s] different from expected key [%s]",
                    attachmentKey, attachment.attachmentKey));
        }
        if (attachment.attachedValue != null) {
            throw new IllegalStateException(format(
                    "Current attachment with key [%s] has been attached value [%s] already when another new value is provided: %s",
                    attachment.attachmentKey, attachment.attachedValue, attachedValue));
        }
        ((Attachment<T>) attachment).attachedValue = attachedValue;
    }
}
