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
import java.sql.SQLException;

/**
 * a {@link NotYetImplementedException} JDBC counterpart to satisfy the {@link java.sql.SQLException} throwing method
 * signature.
 *
 * <p>Note that the vast majority of JDBC API methods throws {@link java.sql.SQLException} which is expected by
 * Hibernate ORM internally. Throwing {@link RuntimeException} might bring about unexpected Hibernate behaviour.
 *
 * <p>This class is not part of the public API and may be removed or changed at any time.
 */
public final class NotYetImplementedSQLException extends SQLException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     *
     * <p>It is recommended to use the other constructor with some explanation.
     */
    public NotYetImplementedSQLException() {}

    /**
     * Constructor with message parameter.
     *
     * @param message explanation on when the feature is to be implemented
     */
    public NotYetImplementedSQLException(String message) {
        super(message);
    }
}
