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

package com.mongodb.hibernate.internal;

import java.io.Serial;

/**
 * A temporary marker exception to denote that the feature in question is in the scope of MongoDB dialect but has not
 * been implemented yet.
 *
 * <p>TODO-HIBERNATE-45 Ultimately all of its references should be eliminated sooner or later, and then this class is
 * supposed to be deleted prior to product release.
 *
 * <p>It is recommended to provide a {@code TODO-<Jira ticket ID> ...} message, in which case one must mention in the
 * ticket description that resolving the {@code TODO-<Jira ticket ID>} notes is in scope of the ticket. Examples:
 *
 * <pre>{@code
 * throw new NotYetImplementedException("TODO-HIBERNATE-13 https://jira.mongodb.org/browse/HIBERNATE-13")
 * }</pre>
 *
 * <pre>{@code
 * throw new NotYetImplementedException("TODO-HIBERNATE-13")
 * }</pre>
 *
 * <p>This class is not part of the public API and may be removed or changed at any time.
 */
public final class NotYetImplementedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     *
     * <p>It is recommended to use the other constructor with some explanation.
     */
    public NotYetImplementedException() {}

    /**
     * Constructor with message parameter.
     *
     * @param message explanation on when the feature is to be implemented
     */
    public NotYetImplementedException(String message) {
        super(message);
    }
}
