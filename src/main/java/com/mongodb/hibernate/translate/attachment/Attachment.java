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

import static java.lang.String.format;

import com.mongodb.hibernate.internal.VisibleForTesting;
import org.jspecify.annotations.Nullable;

/**
 * Manages global state while translating SQL AST via {@link org.hibernate.sql.ast.SqlAstWalker}.
 *
 * <p>{@code SqlAstWalker} is based on {@code visitor} pattern without returning value from the various visitor methods.
 * The original goal of {@code SqlAstWalker} is for concatenating SQL string internally; but when translating SQL AST
 * into MQL, it is highly desirable to return value (e.g. {@link com.mongodb.hibernate.translate.mongoast.AstNode}). To
 * avoid such dilemma, some attachment/detachment mechanism is implemented as follows:
 *
 * <ul>
 *   <li>when some visitor method needs to return some value, it attaches the value by invoking
 *       {@link #attach(AttachmentKey, Object)}.
 *   <li>when some coordinating detacher needs to access the value attached above, it invokes
 *       {@link #expect(AttachmentKey, Runnable)}; internally the {@code Runnable} will attach value by the above
 *       attacher.
 *   <li>both attacher and detacher should provide identical {@link AttachmentKey} to ensure data transferring safety.
 * </ul>
 *
 * @see org.hibernate.sql.ast.SqlAstWalker
 * @see AttachmentKey
 */
@SuppressWarnings("unchecked")
public final class Attachment {

    private @Nullable AttachmentKey<?> attachmentKey;
    private @Nullable Object attachedValue;

    @Override
    public String toString() {
        return "Attachment{" + "attachmentKey=" + attachmentKey + ", attachedValue=" + attachedValue + "}";
    }

    @VisibleForTesting(otherwise = VisibleForTesting.AccessModifier.PRIVATE)
    boolean isBlank() {
        return this.attachmentKey == null && this.attachedValue == null;
    }

    /**
     * Detaches the attached value by invoking some visitor method which returns {@code void} but internally the method
     * is expected to attach some value by invoking the other {@link #attach(AttachmentKey, Object)} method.
     *
     * @param attachmentKey type safe attachment type enum; detacher and attacher should share the identical
     *     {@code AttachmentKey}; never null
     * @param attacher some {@link Runnable} wrapper of visitor method which is supposed to attach some value
     *     internally; never {@code null}
     * @return the value attached by the attacher; after this method returns, the previous state will be restored; never
     *     {@code null}
     * @param <T> the expected Java generic type
     */
    public <T> T expect(AttachmentKey<T> attachmentKey, Runnable attacher) {
        var previousAttachmentKey = this.attachmentKey;
        try {
            this.attachmentKey = attachmentKey;
            this.attachedValue = null;
            attacher.run();
            if (this.attachedValue == null) {
                throw new IllegalArgumentException(format("No value attached for attachment key: %s", attachmentKey));
            }
            return (T) this.attachedValue;
        } finally {
            // revert back old state
            this.attachmentKey = previousAttachmentKey;
            this.attachedValue = null;
        }
    }

    /**
     * Attaches value matching some {@link AttachmentKey}, so coordinating detacher could grab it by invoking the
     * {@link #expect(AttachmentKey, Runnable)} method, sharing identical {@code AttachmentKey}.
     *
     * @param attachmentKey key for validation purpose; never null
     * @param attachedValue value to be attached; never {@code null}
     * @param <T> the expected Java generics type
     */
    public <T> void attach(AttachmentKey<T> attachmentKey, T attachedValue) {
        if (attachmentKey != this.attachmentKey) {
            throw new IllegalArgumentException(format(
                    "Provided attachment key [%s] different from expected key [%s]",
                    attachmentKey, this.attachmentKey));
        }
        if (this.attachedValue != null) {
            throw new IllegalStateException(format(
                    "Current attachment with key [%s] has attached value [%s] already when another new value is provided [%s]",
                    this.attachmentKey, this.attachedValue, attachedValue));
        }
        this.attachedValue = attachedValue;
    }
}
