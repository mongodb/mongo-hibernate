/*
 * Copyright 2026-present MongoDB, Inc.
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

package com.mongodb.hibernate.internal.boot;

import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;

final class NameChecks {

    /**
     * A schema folds into the name a collection or a sequence counter is stored under, as {@code schema.name}, so the
     * '.' is the extension's own separator. A '.' written by the user makes the folded name ambiguous:
     * {@code @Table(schema = "a", name = "b")} and {@code @Table(name = "a.b")} would resolve to the same collection,
     * and nothing downstream could tell the two qualifiers apart.
     */
    static void forbidDot(String name, String kind) {
        if (name.contains(".")) {
            throw new FeatureNotSupportedException(format(
                    "The character [.] in a %s name is not supported, but is present in [%s]. A schema folds into the"
                            + " name as [schema.name], so a '.' written by the user would make the folded name"
                            + " ambiguous.",
                    kind, name));
        }
    }

    private NameChecks() {}
}
