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

package com.mongodb.hibernate.internal.dialect;

import static com.mongodb.hibernate.internal.MongoConstants.EXTENDED_JSON_WRITER_SETTINGS;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.NON_TRANSACTIONAL_COMMAND_FIELD_NAME;
import static com.mongodb.hibernate.internal.MongoConstants.SEQUENCE_COLLECTION_NAME;
import static java.lang.String.format;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.jspecify.annotations.Nullable;

/**
 * Backs Hibernate ORM sequences with one document per sequence in the
 * {@value com.mongodb.hibernate.internal.MongoConstants#SEQUENCE_COLLECTION_NAME} collection, keyed by sequence name.
 *
 * <p>The document holds the value the next allocation will hand out, so the seed and a restart store their target
 * verbatim and only the allocation does arithmetic.
 *
 * <p>The increment is stored in the document because {@code SequenceStructure} only ever calls
 * {@link #getSequenceNextValString(String)}, which has no increment parameter.
 *
 * @hidden
 */
@SuppressWarnings("MissingSummary")
public final class MongoSequenceSupport implements SequenceSupport {

    public static final MongoSequenceSupport INSTANCE = new MongoSequenceSupport();

    private static final String NEXT_VALUE_FIELD_NAME = "next_value";

    /** Package-private so {@link MongoSequenceInformationExtractor} can read the same column by name. */
    static final String INCREMENT_FIELD_NAME = "increment";

    private MongoSequenceSupport() {}

    /**
     * Returns the pre-image, which is the value being handed out, and narrows the reply to one field because
     * {@code SequenceStructure} reads column 1 positionally.
     *
     * <p>Carries {@link com.mongodb.hibernate.internal.MongoConstants#NON_TRANSACTIONAL_COMMAND_FIELD_NAME} because a
     * native SQL sequence advances outside the caller's transaction and {@code SequenceStructure} relies on that:
     * joining the transaction would make concurrent allocations conflict and let a rollback rewind the counter.
     */
    @Override
    public String getSequenceNextValString(String sequenceName) {
        var addStoredIncrement = new BsonDocument(
                "$add",
                new BsonArray(List.of(
                        new BsonString("$" + NEXT_VALUE_FIELD_NAME), new BsonString("$" + INCREMENT_FIELD_NAME))));
        return new BsonDocument("findAndModify", new BsonString(SEQUENCE_COLLECTION_NAME))
                .append("query", allocatableSequenceFilter(sequenceName))
                .append(
                        "update",
                        new BsonArray(List.of(
                                new BsonDocument("$set", new BsonDocument(NEXT_VALUE_FIELD_NAME, addStoredIncrement)))))
                .append("new", BsonBoolean.FALSE)
                .append(
                        "fields",
                        new BsonDocument(ID_FIELD_NAME, new BsonInt32(0))
                                .append(NEXT_VALUE_FIELD_NAME, new BsonInt32(1)))
                .append(NON_TRANSACTIONAL_COMMAND_FIELD_NAME, BsonBoolean.TRUE)
                .toJson(EXTENDED_JSON_WRITER_SETTINGS);
    }

    @Override
    public String[] getCreateSequenceStrings(
            String sequenceName, int initialValue, int incrementSize, @Nullable String options) {
        if (options != null && !options.isBlank()) {
            throw new FeatureNotSupportedException(format(
                    "Sequence [%s] declares options [%s], which are not supported: options are a SQL fragment and have"
                            + " no MQL form.",
                    sequenceName, options));
        }
        return new String[] {getCreateSequenceString(sequenceName, initialValue, incrementSize)};
    }

    @Override
    public String getCreateSequenceString(String sequenceName, int initialValue, int incrementSize) {
        return updateCommand(
                sequenceName,
                new BsonDocument(
                        "$setOnInsert",
                        new BsonDocument(NEXT_VALUE_FIELD_NAME, new BsonInt64(initialValue))
                                .append(INCREMENT_FIELD_NAME, new BsonInt64(incrementSize))),
                true);
    }

    @Override
    public String getRestartSequenceString(String sequenceName, long startWith) {
        return updateCommand(
                sequenceName,
                new BsonDocument("$set", new BsonDocument(NEXT_VALUE_FIELD_NAME, new BsonInt64(startWith))),
                false);
    }

    @Override
    public String getDropSequenceString(String sequenceName) {
        return new BsonDocument("delete", new BsonString(SEQUENCE_COLLECTION_NAME))
                .append(
                        "deletes",
                        new BsonArray(List.of(
                                new BsonDocument("q", sequenceFilter(sequenceName)).append("limit", new BsonInt32(1)))))
                .toJson(EXTENDED_JSON_WRITER_SETTINGS);
    }

    /** Its only caller is bulk insertion, {@code insert into ... select}. */
    @Override
    public String getSelectSequenceNextValString(String sequenceName) {
        throw new FeatureNotSupportedException(format(
                "Inline allocation from sequence [%s] is not supported: MQL cannot embed an allocation inside another"
                        + " statement.",
                sequenceName));
    }

    private static String updateCommand(String sequenceName, BsonDocument modification, boolean upsert) {
        var statement = new BsonDocument("q", sequenceFilter(sequenceName)).append("u", modification);
        if (upsert) {
            statement.append("upsert", BsonBoolean.TRUE);
        }
        return new BsonDocument("update", new BsonString(SEQUENCE_COLLECTION_NAME))
                .append("updates", new BsonArray(List.of(statement)))
                .toJson(EXTENDED_JSON_WRITER_SETTINGS);
    }

    /**
     * Matches the sequence document only when it carries both 64-bit fields the allocation needs, so a document that is
     * missing one, or that stores it as a 32-bit integer, produces the same "matched no document" error a missing
     * document does. Without this an absent {@value #INCREMENT_FIELD_NAME} would make {@code $add} evaluate to null,
     * writing null into {@value #NEXT_VALUE_FIELD_NAME} and leaving the sequence handing out the same value forever.
     *
     * <p>Only the allocation filters on shape. The create and restart commands are upserts keyed on {@code _id}, and a
     * shape predicate there would make a malformed document miss and be inserted again under the same {@code _id}.
     */
    private static BsonDocument allocatableSequenceFilter(String sequenceName) {
        var int64 = new BsonDocument("$type", new BsonString("long"));
        return sequenceFilter(sequenceName).append(NEXT_VALUE_FIELD_NAME, int64).append(INCREMENT_FIELD_NAME, int64);
    }

    private static BsonDocument sequenceFilter(String sequenceName) {
        return new BsonDocument(ID_FIELD_NAME, new BsonString(sequenceName));
    }
}
