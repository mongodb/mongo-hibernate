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

package com.mongodb.hibernate.internal.dialect;

import com.mongodb.hibernate.internal.translate.mongoast.AstParameterMarker;
import java.io.Serial;
import org.hibernate.sql.ast.spi.ParameterMarkerStrategy;
import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * Renders the parameter marker for native queries. This class overrides {@link ParameterMarkerStrategy} to use MQL
 * {$undefined:true}
 *
 * @see AstParameterMarker
 * @see org.hibernate.cfg.AvailableSettings#DIALECT_NATIVE_PARAM_MARKERS
 * @hidden
 */
public final class MongoParameterMarkerStrategy implements ParameterMarkerStrategy {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String MARKER = "{\"$undefined\": true}";

    @Override
    public String createMarker(int position, JdbcType jdbcType) {
        return MARKER;
    }
}
