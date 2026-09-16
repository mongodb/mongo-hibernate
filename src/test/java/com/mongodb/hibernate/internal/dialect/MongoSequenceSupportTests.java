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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

class MongoSequenceSupportTests {

    private final MongoSequenceSupport sequenceSupport = MongoSequenceSupport.INSTANCE;

    @Test
    void supportsPooledSequences() {
        assertAll(
                () -> assertThat(sequenceSupport.supportsSequences()).isTrue(),
                () -> assertThat(sequenceSupport.supportsPooledSequences()).isTrue());
    }

    @Test
    void nextValueAddsStoredIncrementAndReturnsPreImage() {
        assertThat(BsonDocument.parse(sequenceSupport.getSequenceNextValString("books_SEQ")))
                .isEqualTo(
                        BsonDocument.parse(
                                """
                                {
                                  "findAndModify": "hibernate_sequences",
                                  "query": {
                                    "_id": "books_SEQ",
                                    "next_value": {"$type": "long"},
                                    "increment": {"$type": "long"}
                                  },
                                  "update": [
                                    {"$set": {"next_value": {"$add": ["$next_value", "$increment"]}}}
                                  ],
                                  "new": false,
                                  "fields": {"_id": {"$numberInt": "0"}, "next_value": {"$numberInt": "1"}},
                                  "nonTransactional": true
                                }"""));
    }

    @Test
    void createSeedsIdempotentlyWithInitialValueAndIncrement() {
        assertThat(onlyCommand(sequenceSupport.getCreateSequenceStrings("books_SEQ", 1, 50, null)))
                .isEqualTo(
                        BsonDocument.parse(
                                """
                                {
                                  "update": "hibernate_sequences",
                                  "updates": [
                                    {
                                      "q": {"_id": "books_SEQ"},
                                      "u": {
                                        "$setOnInsert": {
                                          "next_value": {"$numberLong": "1"},
                                          "increment": {"$numberLong": "50"}
                                        }
                                      },
                                      "upsert": true
                                    }
                                  ]
                                }"""));
    }

    @Test
    void restartSetsNextValueDirectly() {
        assertThat(BsonDocument.parse(sequenceSupport.getRestartSequenceString("books_SEQ", 500)))
                .isEqualTo(
                        BsonDocument.parse(
                                """
                                {
                                  "update": "hibernate_sequences",
                                  "updates": [
                                    {
                                      "q": {"_id": "books_SEQ"},
                                      "u": {"$set": {"next_value": {"$numberLong": "500"}}}
                                    }
                                  ]
                                }"""));
    }

    @Test
    void dropRemovesOnlyTheSequenceDocument() {
        assertThat(onlyCommand(sequenceSupport.getDropSequenceStrings("books_SEQ")))
                .isEqualTo(
                        BsonDocument.parse(
                                """
                                {
                                  "delete": "hibernate_sequences",
                                  "deletes": [
                                    {"q": {"_id": "books_SEQ"}, "limit": {"$numberInt": "1"}}
                                  ]
                                }"""));
    }

    @Test
    void negativeIncrementIsStoredAsGiven() {
        assertThat(onlyCommand(sequenceSupport.getCreateSequenceStrings("countdown_SEQ", 100, -5, null)))
                .isEqualTo(
                        BsonDocument.parse(
                                """
                                {
                                  "update": "hibernate_sequences",
                                  "updates": [
                                    {
                                      "q": {"_id": "countdown_SEQ"},
                                      "u": {
                                        "$setOnInsert": {
                                          "next_value": {"$numberLong": "100"},
                                          "increment": {"$numberLong": "-5"}
                                        }
                                      },
                                      "upsert": true
                                    }
                                  ]
                                }"""));
    }

    @Test
    void inlineAllocationIsRejected() {
        assertThatThrownBy(() -> sequenceSupport.getSelectSequenceNextValString("books_SEQ"))
                .isInstanceOf(FeatureNotSupportedException.class)
                .hasMessageContaining("books_SEQ")
                .hasMessageContaining("cannot embed an allocation");
    }

    @Test
    void sequenceOptionsAreRejected() {
        assertThatThrownBy(() -> sequenceSupport.getCreateSequenceStrings("books_SEQ", 1, 50, "cache 20"))
                .isInstanceOf(FeatureNotSupportedException.class)
                .hasMessageContaining("cache 20");
    }

    @Test
    void blankSequenceOptionsAreIgnored() {
        assertThat(sequenceSupport.getCreateSequenceStrings("books_SEQ", 1, 50, "  "))
                .isEqualTo(sequenceSupport.getCreateSequenceStrings("books_SEQ", 1, 50, null));
    }

    /** Hibernate ORM's multi-command forms; this dialect never needs more than one command for either. */
    private static BsonDocument onlyCommand(String[] commands) {
        assertThat(commands).hasSize(1);
        return BsonDocument.parse(commands[0]);
    }
}
