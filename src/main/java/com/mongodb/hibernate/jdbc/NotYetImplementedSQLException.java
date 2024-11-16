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

package com.mongodb.hibernate.jdbc;

import java.io.Serial;
import java.sql.SQLException;

/**
 * The JDBC counterpart of {@link com.mongodb.hibernate.internal.NotYetImplementedException}.
 *
 * <p>Used only in {@link com.mongodb.hibernate.jdbc} package when implementing various JDBC API methods throwing
 * {@link SQLException} invariably,
 *
 * <p>Hibernate expects the checked exception contract to go about business logic, so a {@link RuntimeException} like
 * {@link com.mongodb.hibernate.internal.NotYetImplementedException} will break the contract and lead to unexpected
 * behaviour.
 *
 * @see com.mongodb.hibernate.internal.NotYetImplementedException
 */
final class NotYetImplementedSQLException extends SQLException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     *
     * <p>It is recommended to use the other constructor with some explanation.
     */
    NotYetImplementedSQLException() {}

    /**
     * Constructor with message parameter.
     *
     * @param message explanation on when the feature is to be implemented
     */
    NotYetImplementedSQLException(String message) {
        super(message);
    }
}
