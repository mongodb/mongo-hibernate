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

// The following describes the usual approach we use when implementing JDBC interfaces
// using the example of `java.sql.Connection`:
// - Create abstract `ConnectionAdapter` that implements all abstract methods from `java.sql.Connection`
//   (note how we do not override default methods)
//   such that they always complete abruptly with `java.sql.SQLFeatureNotSupportedException`.
//   This provides a fallback implementation while all the methods Hibernate really called
//   should have been overridden in `MongoConnection' below.
// - Create concrete `MongoConnection` that overrides implementations from `ConnectionAdapter`
//   only if they are called by Hibernate ORM:
//   - if we support the corresponding Hibernate ORM functionality, we either provide a sufficient implementation,
//     or defer doing so by throwing `NotYetImplementedException`;
//   - if we do not support the corresponding functionality, we throw `java.sql.SQLFeatureNotSupportedException`,
//     with a message explaining why MongoDB Dialect opted for not supporting it.
@NullMarked
package com.mongodb.hibernate.jdbc;

import org.jspecify.annotations.NullMarked;
