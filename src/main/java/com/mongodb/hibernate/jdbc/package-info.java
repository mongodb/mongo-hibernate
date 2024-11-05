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

/**
 * Support for implementing various JDBC APIs for MongoDB Dialect's JDBC adapter.
 *
 * <p>The JDBC adapter is only meant for Hibernate ORM integration purposes, so only those JDBC API methods really
 * called by Hibernate will be implemented. To make code maintainable, some common JDBC API implementation pattern is
 * adopted.
 *
 * <p>Take {@link java.sql.Connection} JDBC API implementation as an example,
 *
 * <ul>
 *   <li>an {@link com.mongodb.hibernate.jdbc.AbstractMongoConnection} dummy implementation class will be created,
 *       implementing all the API methods Hibernate won't use by throwing exceptions;
 *   <li>a {@link com.mongodb.hibernate.jdbc.MongoConnection} class will inherit from {@code AbstractMongoConnection}
 *       and focuses on implementing those API methods Hibernate really uses.
 * </ul>
 *
 * <p>Note that all the {@code sql} API method parameters have been renamed to {@code mql}.
 */
@NullMarked
package com.mongodb.hibernate.jdbc;

import org.jspecify.annotations.NullMarked;
