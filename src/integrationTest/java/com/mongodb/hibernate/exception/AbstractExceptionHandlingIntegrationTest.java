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

package com.mongodb.hibernate.exception;

import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.junit.InjectMongoClient;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.SessionFactoryScopeAware;
import org.junit.jupiter.api.AfterEach;

public class AbstractExceptionHandlingIntegrationTest implements SessionFactoryScopeAware {
    private static final String FAIL_COMMAND_NAME = "failCommand";

    @InjectMongoClient
    private static MongoClient mongoClient;

    SessionFactoryScope sessionFactoryScope;

    @Override
    public void injectSessionFactoryScope(SessionFactoryScope sessionFactoryScope) {
        this.sessionFactoryScope = sessionFactoryScope;
    }

    static void configureFailPointErrorCode(int errorCode) {
        BsonDocument failPointCommand = BsonDocument.parse("{"
                + "  configureFailPoint: \"failCommand\","
                + "  mode: { times: 1 },"
                + "  data: {"
                + "    failCommands: [\"insert\"],"
                + "    errorCode: " + errorCode
                + "    errorLabels: [\"TransientTransactionError\"]"
                + "  }"
                + "}");
        mongoClient.getDatabase("admin").runCommand(failPointCommand);
    }

    private static void disableFailPoint(final String failPoint) {
        BsonDocument failPointDocument =
                new BsonDocument("configureFailPoint", new BsonString(failPoint)).append("mode", new BsonString("off"));
        mongoClient.getDatabase("admin").runCommand(failPointDocument);
    }

    @AfterEach
    void tearDown() {
        disableFailPoint(FAIL_COMMAND_NAME);
    }
}
