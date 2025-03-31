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

package com.mongodb.hibernate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.junit.InjectMongoClient;
import com.mongodb.hibernate.junit.MongoExtension;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MongoExtension.class)
class FailedBootstrappingIntegrationTests {
    @InjectMongoClient
    private static MongoClient mongoClient;

    @Test
    @SuppressWarnings("try")
    void couldNotInstantiateDialect() {
        assertThatThrownBy(() -> {
                    try (var failPoint = FailPoint.enable(
                            BsonDocument.parse(
                                    """
                                    {
                                        "configureFailPoint": "failCommand",
                                        "mode": {
                                            "times": 1
                                        },
                                        "data": {
                                            "failCommands": ["buildInfo"],
                                            "errorCode": 1
                                        }
                                    }
                                    """))) {
                        new Configuration().buildSessionFactory().close();
                    }
                })
                .hasRootCause(
                        new RuntimeException(
                                "Could not instantiate [com.mongodb.hibernate.dialect.MongoDialect], see the earlier exceptions to find out why"));
    }

    static final class FailPoint implements AutoCloseable {
        private final BsonDocument failPointDocument;

        private FailPoint(BsonDocument failPointDocument) {
            this.failPointDocument = failPointDocument;
        }

        static FailPoint enable(BsonDocument configureFailPointDoc) {
            var result = new FailPoint(configureFailPointDoc);
            mongoClient.getDatabase("admin").runCommand(configureFailPointDoc);
            return result;
        }

        @Override
        public void close() {
            mongoClient
                    .getDatabase("admin")
                    .runCommand(new BsonDocument()
                            .append("configureFailPoint", failPointDocument.getString("configureFailPoint"))
                            .append("mode", new BsonString("off")));
        }
    }
}
