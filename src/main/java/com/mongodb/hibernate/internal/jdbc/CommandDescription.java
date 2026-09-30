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

package com.mongodb.hibernate.internal.jdbc;

import static com.mongodb.hibernate.internal.MongoConstants.NON_TRANSACTIONAL_COMMAND_FIELD_NAME;

import java.sql.SQLFeatureNotSupportedException;
import java.util.Iterator;
import java.util.Set;
import org.bson.BsonDocument;

/** The first key is always the command name, e.g. "insert", "update", "delete". */
enum CommandDescription {
    /** See <a href="https://www.mongodb.com/docs/manual/reference/command/insert/">{@code insert}</a>. */
    INSERT("insert", false, true),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/command/update/">{@code update}</a>. */
    UPDATE("update", false, true),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/command/delete/">{@code delete}</a>. */
    DELETE("delete", false, true),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/command/aggregate/">{@code aggregate}</a>. */
    AGGREGATE("aggregate", true, false),
    /**
     * See <a href="https://www.mongodb.com/docs/manual/reference/command/findAndModify/">{@code findAndModify}</a>.
     *
     * <p>A write that Hibernate ORM submits through {@code executeQuery}, because that is how it reads an allocated
     * sequence value. The flags below say which JDBC method may carry a command, not whether it mutates.
     */
    FIND_AND_MODIFY("findAndModify", true, false),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/command/create/">{@code create}</a>. */
    CREATE("create", false, false),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/command/createIndexes/">{@code createIndexes}</a>. */
    CREATE_INDEXES("createIndexes", false, false),
    /** See <a href="https://www.mongodb.com/docs/manual/reference/command/drop/">{@code drop}</a>. */
    DROP("drop", false, false);

    private static final String UNSUPPORTED_MESSAGE_TEMPLATE_COMMAND_FIELD = "Unsupported field in [%s] command: [%s]";
    private static final String UNSUPPORTED_MESSAGE_TEMPLATE_STATEMENT_FIELD =
            "Unsupported field in [%s] statement: [%s]";

    private final String commandName;
    private final boolean isQuery;
    private final boolean isUpdate;

    CommandDescription(String commandName, boolean isQuery, boolean isUpdate) {
        this.commandName = commandName;
        this.isQuery = isQuery;
        this.isUpdate = isUpdate;
    }

    String getCommandName() {
        return commandName;
    }

    boolean isUpdate() {
        return isUpdate;
    }

    boolean isQuery() {
        return isQuery;
    }

    static CommandDescription of(String commandName) throws SQLFeatureNotSupportedException {
        return switch (commandName) {
            case "insert" -> INSERT;
            case "update" -> UPDATE;
            case "delete" -> DELETE;
            case "aggregate" -> AGGREGATE;
            case "findAndModify" -> FIND_AND_MODIFY;
            case "create" -> CREATE;
            case "createIndexes" -> CREATE_INDEXES;
            case "drop" -> DROP;
            default -> throw new SQLFeatureNotSupportedException("Unsupported command: %s".formatted(commandName));
        };
    }

    /**
     * Checks that the command contains no fields other than the command name and those returned by
     * {@link #getSupportedCommandFields()}.
     */
    void checkCommandFields(BsonDocument command) throws SQLFeatureNotSupportedException {
        var iterator = command.keySet().iterator();
        iterator.next(); // skip the command name
        checkFields(getSupportedCommandFields(), UNSUPPORTED_MESSAGE_TEMPLATE_COMMAND_FIELD, iterator);
    }

    /**
     * Checks that the statement contains no fields other than those returned by {@link #getSupportedStatementFields()}.
     */
    void checkStatementFields(BsonDocument statement) throws SQLFeatureNotSupportedException {
        checkFields(
                getSupportedStatementFields(),
                UNSUPPORTED_MESSAGE_TEMPLATE_STATEMENT_FIELD,
                statement.keySet().iterator());
    }

    private void checkFields(
            Set<String> supportedFields, String exceptionMessageTemplate, Iterator<String> fieldNameIterator)
            throws SQLFeatureNotSupportedException {
        while (fieldNameIterator.hasNext()) {
            var field = fieldNameIterator.next();
            if (!supportedFields.contains(field)) {
                throw new SQLFeatureNotSupportedException(exceptionMessageTemplate.formatted(commandName, field));
            }
        }
    }

    private Set<String> getSupportedCommandFields() {
        return switch (this) {
            case INSERT -> Set.of("documents");
            case UPDATE -> Set.of("updates");
            case DELETE -> Set.of("deletes");
            // The command fields of `aggregate` are variable, so they are never checked against this set.
            case AGGREGATE -> Set.of();
            case FIND_AND_MODIFY -> Set.of("query", "update", "new", "fields", NON_TRANSACTIONAL_COMMAND_FIELD_NAME);
            case CREATE, DROP -> Set.of();
            case CREATE_INDEXES -> Set.of("indexes");
        };
    }

    private Set<String> getSupportedStatementFields() {
        return switch (this) {
            case UPDATE -> Set.of("q", "u", "multi", "upsert");
            case DELETE -> Set.of("q", "limit");
            case CREATE_INDEXES -> Set.of("key", "name", "unique");
            default -> Set.of();
        };
    }
}
