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

package com.mongodb.hibernate.internal.translate.mongoast;

import java.util.function.Consumer;
import org.bson.BsonWriter;
import org.hibernate.sql.exec.spi.JdbcParameterBinder;
import org.jspecify.annotations.Nullable;

/**
 * @param parameterId {@link org.hibernate.sql.ast.tree.expression.JdbcParameter#getParameterId()} of the parameter this
 *     marker stands for, or {@code null} where Hibernate supplies none.
 * @see org.hibernate.cfg.AvailableSettings#DIALECT_NATIVE_PARAM_MARKERS
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public record AstParameterMarker(
        JdbcParameterBinder binder, @Nullable Integer parameterId) implements AstValue {

    @Override
    public void render(BsonWriter writer, Consumer<JdbcParameterBinder> binderConsumer) {
        writer.writeUndefined();
        binderConsumer.accept(binder);
    }

    /**
     * Two markers stand for the same query parameter when Hibernate assigned them the same parameter id: one query
     * parameter occurring in several clauses yields a separate {@code JdbcParameter} node, with its own binder, per
     * occurrence. Where an id is absent, distinct parameters must not be conflated, so those markers compare by binder
     * identity.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AstParameterMarker other)) {
            return false;
        }
        if (parameterId == null || other.parameterId == null) {
            return parameterId == null && other.parameterId == null && binder == other.binder;
        } else {
            return parameterId.equals(other.parameterId);
        }
    }

    @Override
    public int hashCode() {
        return parameterId != null ? parameterId.hashCode() : System.identityHashCode(binder);
    }
}
