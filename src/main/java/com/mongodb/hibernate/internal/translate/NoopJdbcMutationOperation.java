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

import java.util.List;
import org.hibernate.engine.jdbc.mutation.ParameterUsage;
import org.hibernate.jdbc.Expectation;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.hibernate.sql.model.MutationTarget;
import org.hibernate.sql.model.MutationType;
import org.hibernate.sql.model.TableMapping;
import org.hibernate.sql.model.jdbc.JdbcMutationOperation;
import org.hibernate.sql.model.jdbc.JdbcValueDescriptor;
import org.jspecify.annotations.NullUnmarked;

/**
 * A {@link JdbcMutationOperation} no-op adapter class which is used temporarily as feature placeholder prior to its
 * implementation is done, when throwing {@link com.mongodb.hibernate.internal.NotYetImplementedException} would not be
 * an option. Ultimately this adapter class should be phased out and deleted after the completion of the following tickets:
 * <ul>
 *     <li><a href="https://jira.mongodb.org/browse/HIBERNATE-17">deletion MQL translation</a>
 *     <li><a href="https://jira.mongodb.org/browse/HIBERNATE-18">updating MQL translation</a>
 * </ul>
 *
 * <p>This class is not part of the public API and may be removed or changed at any time
 */
@NullUnmarked
final class NoopJdbcMutationOperation implements JdbcMutationOperation {

    NoopJdbcMutationOperation() {}

    @Override
    public String getSqlString() {
        return "";
    }

    @Override
    public List<JdbcParameterBinder> getParameterBinders() {
        return List.of();
    }

    @Override
    public boolean isCallable() {
        return false;
    }

    @Override
    public Expectation getExpectation() {
        return null;
    }

    @Override
    public MutationType getMutationType() {
        return null;
    }

    @Override
    public MutationTarget<?> getMutationTarget() {
        return null;
    }

    @Override
    public TableMapping getTableDetails() {
        return null;
    }

    @Override
    public JdbcValueDescriptor findValueDescriptor(String columnName, ParameterUsage usage) {
        return null;
    }
}
