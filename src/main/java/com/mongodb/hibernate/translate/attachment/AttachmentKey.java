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

/**
 * A type safe attachment type enum.
 *
 * <p>It is used as the common contract between coordinating attachment producer and consumer. To ensure safe attachment
 * transferring, both parties need to present the identical {@link AttachmentKey}.
 *
 * <p>Note that Java {@code Enum} doesn't support generic type support. This interface combines both {@code Enum} and
 * type safety together.
 *
 * @param <T> generic type
 * @see SQLAstVisitorStateManager#attach(AttachmentKey, Object)
 * @see SQLAstVisitorStateManager#detach(AttachmentKey, Runnable)
 */
public interface AttachmentKey<T> {
    AttachmentKey<String> COLUMN_NAME = new AttachmentKey<>() {};
    AttachmentKey<String> COLLECTION_NAME = new AttachmentKey<>() {};
}
